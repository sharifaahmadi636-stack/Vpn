const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const OWNER_UID = "owner_uid_001";
const INTRUDER_UID = "intruder_uid_999";
const OWNER_EMAIL = "sharifaahmadi636@gmail.com";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function ownerDb() {
  return testEnv
    .authenticatedContext(OWNER_UID, {
      email: OWNER_EMAIL,
      email_verified: true,
    })
    .firestore();
}

function unverifiedOwnerDb() {
  return testEnv
    .authenticatedContext(OWNER_UID, {
      email: OWNER_EMAIL,
      email_verified: false,
    })
    .firestore();
}

function intruderDb() {
  return testEnv
    .authenticatedContext(INTRUDER_UID, {
      email: "randomuser@gmail.com",
      email_verified: true,
    })
    .firestore();
}

function validServerPayload(uid = OWNER_UID, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    ownerId: uid,
    name: "Frankfurt Core-01",
    country: "آلمان",
    countryCode: "DE",
    city: "Frankfurt",
    host: "185.199.108.153",
    port: 51820,
    protocol: "WIREGUARD",
    cipher: "ChaCha20-Poly1305",
    dns: "1.1.1.1",
    tunnelAddress: "10.8.0.2",
    mtu: 1420,
    publicKey: "xK9vP2mQ8rT5wL1nJ4cZ7yB0dF3gH6kM9pS2vX5zA1E=",
    region: "Europe",
    loadPercent: 24,
    latencyMs: 38,
    active: true,
    recommended: true,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validSettingsPayload(uid = OWNER_UID, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    ownerId: uid,
    systemEnabled: true,
    autoConnect: false,
    killSwitch: true,
    dnsProtection: true,
    fullTunnel: false,
    persistentNotification: true,
    autoFailover: true,
    bypassedPackages: ["com.android.vending"],
    selectedServerId: "srv_de_fra",
    preferredProtocol: "WIREGUARD",
    primaryDns: "1.1.1.1",
    secondaryDns: "9.9.9.9",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validLogPayload(uid = OWNER_UID, overrides = {}) {
  const now = new Date(Date.now() - 1000);
  return {
    ownerId: uid,
    serverId: "srv_de_fra",
    serverName: "Frankfurt Core-01",
    countryCode: "DE",
    protocol: "WIREGUARD",
    eventType: "CONNECTED",
    durationSec: 120,
    bytesTransferred: 2048576,
    latencyMs: 38,
    note: "Tunnel established",
    createdAt: now,
    ...overrides,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: denied on all collections", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("vpn_servers").get());
  await assertFails(unauth.collection("vpn_servers").doc("srv_1").set(validServerPayload()));
  await assertFails(unauth.collection("system_settings").doc("global").get());
  await assertFails(unauth.collection("connection_logs").get());
});

test("Non-owner Google user: denied even if using their own UID", async () => {
  const intruder = intruderDb();
  await assertFails(
    intruder.collection("vpn_servers").where("ownerId", "==", INTRUDER_UID).get()
  );
  await assertFails(
    intruder.collection("vpn_servers").doc("srv_intruder").set(validServerPayload(INTRUDER_UID))
  );
  await assertFails(
    intruder.collection("system_settings").doc("global").set(validSettingsPayload(INTRUDER_UID))
  );
  await assertFails(
    intruder.collection("connection_logs").doc("log_1").set(validLogPayload(INTRUDER_UID))
  );
});

test("Email spoofing (email_verified: false): denied for owner email", async () => {
  const spoofed = unverifiedOwnerDb();
  await assertFails(
    spoofed.collection("vpn_servers").doc("srv_de_fra").set(validServerPayload())
  );
  await assertFails(
    spoofed.collection("vpn_servers").where("ownerId", "==", OWNER_UID).get()
  );
});

test("Verified owner: valid create, filtered list, partial update, and delete on vpn_servers", async () => {
  const db = ownerDb();

  // Create without @DocumentId `id` field
  await assertSucceeds(
    db.collection("vpn_servers").doc("srv_de_fra").set(validServerPayload())
  );

  // Filtered query matching Kotlin repository succeeds; unfiltered fails
  await assertSucceeds(
    db.collection("vpn_servers").where("ownerId", "==", OWNER_UID).get()
  );
  await assertFails(db.collection("vpn_servers").get());

  // Partial toggle active update
  await assertSucceeds(
    db.collection("vpn_servers").doc("srv_de_fra").update({
      active: false,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Partial latency update
  await assertSucceeds(
    db.collection("vpn_servers").doc("srv_de_fra").update({
      latencyMs: 44,
      loadPercent: 30,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Shadow update with ghost field rejected
  await assertFails(
    db.collection("vpn_servers").doc("srv_de_fra").update({
      active: true,
      ghostField: "hack",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Immutable createdAt tampering rejected
  await assertFails(
    db.collection("vpn_servers").doc("srv_de_fra").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Document with @DocumentId `id` inside body rejected
  await assertFails(
    db.collection("vpn_servers").doc("srv_bad").set(validServerPayload(OWNER_UID, { id: "srv_bad" }))
  );

  // Future timestamp rejected
  await assertFails(
    db.collection("vpn_servers").doc("srv_future").set(
      validServerPayload(OWNER_UID, { createdAt: new Date(Date.now() + 60000) })
    )
  );

  // Invalid port rejected
  await assertFails(
    db.collection("vpn_servers").doc("srv_port").set(validServerPayload(OWNER_UID, { port: 70000 }))
  );

  await assertSucceeds(db.collection("vpn_servers").doc("srv_de_fra").delete());
});

test("Verified owner: system_settings and immutable connection_logs", async () => {
  const db = ownerDb();

  await assertSucceeds(
    db.collection("system_settings").doc("global").set(validSettingsPayload())
  );
  await assertSucceeds(
    db.collection("system_settings").where("ownerId", "==", OWNER_UID).get()
  );
  await assertSucceeds(
    db.collection("system_settings").doc("global").update({
      killSwitch: false,
      systemEnabled: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertSucceeds(
    db.collection("connection_logs").doc("log_001").set(validLogPayload())
  );
  await assertSucceeds(
    db.collection("connection_logs").where("ownerId", "==", OWNER_UID).get()
  );
  // Connection logs are append-only (no update)
  await assertFails(
    db.collection("connection_logs").doc("log_001").update({ note: "tampered" })
  );
});
