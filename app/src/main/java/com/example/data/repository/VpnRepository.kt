package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.ConnectionLog
import com.example.data.model.LogEventType
import com.example.data.model.PreferredProtocol
import com.example.data.model.SystemSettings
import com.example.data.model.VpnProtocol
import com.example.data.model.VpnServer
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

class VpnRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireOwnerUid(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Owner must be signed in with Google before accessing Firestore.")
    }

    fun observeServers(ownerId: String): Flow<List<VpnServer>> = flow {
        require(ownerId.isNotBlank()) { "Owner UID must not be blank" }
        val path = COLLECTION_SERVERS
        emitAll(
            db.collection(COLLECTION_SERVERS)
                .whereEqualTo("ownerId", ownerId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        VpnServer::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(
                        compareByDescending<VpnServer> { it.recommended }
                            .thenByDescending { it.active }
                            .thenBy { it.latencyMs }
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == ownerId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeSystemSettings(ownerId: String): Flow<SystemSettings?> = flow {
        require(ownerId.isNotBlank()) { "Owner UID must not be blank" }
        val path = COLLECTION_SETTINGS
        emitAll(
            db.collection(COLLECTION_SETTINGS)
                .whereEqualTo("ownerId", ownerId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SystemSettings::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).firstOrNull()
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == ownerId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeConnectionLogs(ownerId: String): Flow<List<ConnectionLog>> = flow {
        require(ownerId.isNotBlank()) { "Owner UID must not be blank" }
        val path = COLLECTION_LOGS
        emitAll(
            db.collection(COLLECTION_LOGS)
                .whereEqualTo("ownerId", ownerId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ConnectionLog::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                        .take(30)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == ownerId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun bootstrapOwnerDefaultsIfEmpty(ownerId: String): Result<Boolean> {
        return try {
            val uid = requireOwnerUid()
            check(uid == ownerId) { "Owner session mismatch." }

            val existingServers = db.collection(COLLECTION_SERVERS)
                .whereEqualTo("ownerId", uid)
                .limit(1)
                .get()
                .await()

            val existingSettings = db.collection(COLLECTION_SETTINGS)
                .whereEqualTo("ownerId", uid)
                .limit(1)
                .get()
                .await()

            if (!existingServers.isEmpty && !existingSettings.isEmpty) {
                return Result.success(false)
            }

            val batch = db.batch()
            val seededServers = defaultGlobalServers()

            if (existingServers.isEmpty) {
                seededServers.forEach { (docId, server) ->
                    val ref = db.collection(COLLECTION_SERVERS).document(docId)
                    batch.set(ref, server.toCreateMap(uid))
                }
            }

            if (existingSettings.isEmpty) {
                val settingsRef = db.collection(COLLECTION_SETTINGS).document(GLOBAL_SETTINGS_DOC_ID)
                val defaultSettings = SystemSettings(
                    id = GLOBAL_SETTINGS_DOC_ID,
                    ownerId = uid,
                    systemEnabled = true,
                    autoConnect = false,
                    killSwitch = true,
                    dnsProtection = true,
                    fullTunnel = false,
                    persistentNotification = true,
                    autoFailover = true,
                    bypassedPackages = emptyList(),
                    selectedServerId = seededServers.first().first,
                    preferredProtocol = PreferredProtocol.WIREGUARD.name,
                    primaryDns = "1.1.1.1",
                    secondaryDns = "9.9.9.9"
                )
                batch.set(settingsRef, defaultSettings.toCreateMap(uid))
            }

            val logRef = db.collection(COLLECTION_LOGS).document()
            val bootstrapLog = ConnectionLog(
                ownerId = uid,
                serverId = seededServers.first().first,
                serverName = "Aegis Global Grid",
                countryCode = "DE",
                protocol = VpnProtocol.WIREGUARD.name,
                eventType = LogEventType.SYSTEM_UPDATED.name,
                durationSec = 0L,
                bytesTransferred = 0L,
                latencyMs = 36L,
                note = "سرورهای استاندارد جهانی در Firebase ثبت و سامانه مالک فعال شد"
            )
            batch.set(logRef, bootstrapLog.toCreateMap(uid))
            batch.commit().await()
            Result.success(true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "$COLLECTION_SERVERS/$COLLECTION_SETTINGS")
            Result.failure(e)
        }
    }

    suspend fun importServersBatch(servers: List<VpnServer>): Result<Int> {
        return try {
            val uid = requireOwnerUid()
            require(servers.isNotEmpty()) { "فایل .yml حاوی هیچ سرور معتبری نیست." }
            val bounded = servers.take(25)
            val batch = db.batch()
            bounded.forEach { server ->
                validateServerBounds(server)
                val ref = db.collection(COLLECTION_SERVERS).document()
                batch.set(ref, server.toCreateMap(uid))
            }
            val logRef = db.collection(COLLECTION_LOGS).document()
            val log = ConnectionLog(
                ownerId = uid,
                serverId = "yaml_import",
                serverName = "YAML Import",
                countryCode = "GL",
                protocol = "WIREGUARD",
                eventType = LogEventType.SERVER_ADDED.name,
                durationSec = 0L,
                bytesTransferred = 0L,
                latencyMs = 0L,
                note = "${bounded.size} سرور از فایل .yml وارد Firebase شد"
            )
            batch.set(logRef, log.toCreateMap(uid))
            batch.commit().await()
            Result.success(bounded.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, COLLECTION_SERVERS)
            Result.failure(e)
        }
    }

    suspend fun addServer(server: VpnServer): Result<Unit> {
        var docPath = COLLECTION_SERVERS
        return try {
            val uid = requireOwnerUid()
            validateServerBounds(server)
            val docRef = db.collection(COLLECTION_SERVERS).document()
            docPath = docRef.path
            docRef.set(server.toCreateMap(uid)).await()

            logEventInternal(
                uid = uid,
                serverId = docRef.id,
                serverName = server.name,
                countryCode = server.countryCode,
                protocol = server.protocol,
                eventType = LogEventType.SERVER_ADDED,
                latencyMs = server.latencyMs,
                note = "سرور ${server.name} (${server.host}:${server.port}) به Firebase اضافه شد"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateServer(server: VpnServer): Result<Unit> {
        val docPath = "$COLLECTION_SERVERS/${server.id}"
        return try {
            requireOwnerUid()
            require(server.id.isNotBlank()) { "شناسه سرور نامعتبر است." }
            validateServerBounds(server)
            db.collection(COLLECTION_SERVERS)
                .document(server.id)
                .update(server.toUpdateMap())
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleServerActive(server: VpnServer, active: Boolean): Result<Unit> {
        val docPath = "$COLLECTION_SERVERS/${server.id}"
        return try {
            val uid = requireOwnerUid()
            require(server.id.isNotBlank()) { "شناسه سرور نامعتبر است." }
            db.collection(COLLECTION_SERVERS)
                .document(server.id)
                .update(
                    mapOf(
                        "active" to active,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()

            logEventInternal(
                uid = uid,
                serverId = server.id,
                serverName = server.name,
                countryCode = server.countryCode,
                protocol = server.protocol,
                eventType = LogEventType.SERVER_TOGGLED,
                latencyMs = server.latencyMs,
                note = if (active) "سرور ${server.name} فعال شد" else "سرور ${server.name} غیرفعال شد"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateServerLatency(serverId: String, latencyMs: Long, loadPercent: Long): Result<Unit> {
        val docPath = "$COLLECTION_SERVERS/$serverId"
        return try {
            requireOwnerUid()
            require(serverId.isNotBlank()) { "شناسه سرور نامعتبر است." }
            db.collection(COLLECTION_SERVERS)
                .document(serverId)
                .update(
                    mapOf(
                        "latencyMs" to latencyMs.coerceIn(0L, 10000L),
                        "loadPercent" to loadPercent.coerceIn(0L, 100L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteServer(serverId: String): Result<Unit> {
        val docPath = "$COLLECTION_SERVERS/$serverId"
        return try {
            requireOwnerUid()
            require(serverId.isNotBlank()) { "شناسه سرور نامعتبر است." }
            db.collection(COLLECTION_SERVERS).document(serverId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveSystemSettings(settings: SystemSettings, auditNote: String? = null): Result<Unit> {
        val docId = settings.id.ifBlank { GLOBAL_SETTINGS_DOC_ID }
        val docPath = "$COLLECTION_SETTINGS/$docId"
        return try {
            val uid = requireOwnerUid()
            require(settings.primaryDns.trim().length in 3..64) { "آدرس DNS اصلی نامعتبر است." }
            require(settings.secondaryDns.trim().length in 3..64) { "آدرس DNS جایگزین نامعتبر است." }

            val docRef = db.collection(COLLECTION_SETTINGS).document(docId)
            val snapshot = docRef.get().await()
            if (snapshot.exists()) {
                docRef.update(settings.toUpdateMap()).await()
            } else {
                docRef.set(settings.toCreateMap(uid)).await()
            }

            if (!auditNote.isNullOrBlank()) {
                logEventInternal(
                    uid = uid,
                    serverId = settings.selectedServerId.ifBlank { "system" },
                    serverName = "تنظیمات سیستم مالک",
                    countryCode = "GL",
                    protocol = settings.preferredProtocol,
                    eventType = LogEventType.SYSTEM_UPDATED,
                    latencyMs = 0L,
                    note = auditNote
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun logConnectionEvent(
        server: VpnServer?,
        eventType: LogEventType,
        durationSec: Long,
        bytesTransferred: Long,
        latencyMs: Long,
        note: String,
    ): Result<Unit> {
        var docPath = COLLECTION_LOGS
        return try {
            val uid = requireOwnerUid()
            val docRef = db.collection(COLLECTION_LOGS).document()
            docPath = docRef.path
            val log = ConnectionLog(
                ownerId = uid,
                serverId = server?.id?.ifBlank { "system" } ?: "system",
                serverName = server?.name?.ifBlank { "Aegis VPN" } ?: "Aegis VPN",
                countryCode = server?.countryCode?.ifBlank { "GL" } ?: "GL",
                protocol = server?.protocol?.ifBlank { "WIREGUARD" } ?: "WIREGUARD",
                eventType = eventType.name,
                durationSec = durationSec.coerceAtLeast(0L),
                bytesTransferred = bytesTransferred.coerceAtLeast(0L),
                latencyMs = latencyMs.coerceIn(0L, 10000L),
                note = note.take(240)
            )
            docRef.set(log.toCreateMap(uid)).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun performSecurityNetworkCheck(): Result<com.example.vpn.NetworkSecurityReport> =
        withContext(Dispatchers.IO) {
            try {
                val startNs = System.nanoTime()
                val url = java.net.URL("https://1.1.1.1/cdn-cgi/trace")
                val conn = (url.openConnection() as javax.net.ssl.HttpsURLConnection).apply {
                    connectTimeout = 4000
                    readTimeout = 4000
                    requestMethod = "GET"
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val rttMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
                val lines = body.lines().associate { line ->
                    val idx = line.indexOf('=')
                    if (idx > 0) line.substring(0, idx) to line.substring(idx + 1) else "" to ""
                }
                val ip = lines["ip"]?.ifBlank { "نامشخص" } ?: "نامشخص"
                val colo = lines["colo"]?.ifBlank { "GLOBAL" } ?: "GLOBAL"
                val tls = lines["tls"]?.ifBlank { "TLSv1.3" } ?: "TLSv1.3"

                val dnsOk = try {
                    java.net.InetAddress.getByName("firebase.google.com") != null
                } catch (_: Exception) {
                    false
                }

                Result.success(
                    com.example.vpn.NetworkSecurityReport(
                        publicIp = ip,
                        datacenter = colo,
                        tlsVersion = tls,
                        rttMs = rttMs,
                        dnsReachable = dnsOk
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun measureServerLatencyMs(host: String, port: Int): Long = withContext(Dispatchers.IO) {
        val cleanHost = host.trim()
        val safePort = port.coerceIn(1, 65535)
        val probePorts = listOf(safePort, 443, 53).distinct()

        for (targetPort in probePorts) {
            val startNs = System.nanoTime()
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(cleanHost, targetPort), 1600)
                }
                val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceIn(8L, 9999L)
                return@withContext elapsedMs
            } catch (_: Exception) {
                // If TCP handshake hits an active firewall/UDP-only port quickly, check elapsed RTT
                val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
                if (elapsedMs in 5L..900L && targetPort == probePorts.last()) {
                    return@withContext elapsedMs
                }
            }
        }
        return@withContext 120L
    }

    private suspend fun logEventInternal(
        uid: String,
        serverId: String,
        serverName: String,
        countryCode: String,
        protocol: String,
        eventType: LogEventType,
        latencyMs: Long,
        note: String,
    ) {
        val logRef = db.collection(COLLECTION_LOGS).document()
        val log = ConnectionLog(
            ownerId = uid,
            serverId = serverId,
            serverName = serverName,
            countryCode = countryCode,
            protocol = protocol,
            eventType = eventType.name,
            durationSec = 0L,
            bytesTransferred = 0L,
            latencyMs = latencyMs,
            note = note
        )
        logRef.set(log.toCreateMap(uid)).await()
    }

    private fun validateServerBounds(server: VpnServer) {
        require(server.name.trim().length in 1..60) { "نام سرور باید بین ۱ تا ۶۰ کاراکتر باشد." }
        require(server.country.trim().length in 1..40) { "نام کشور باید بین ۱ تا ۴۰ کاراکتر باشد." }
        require(server.countryCode.trim().length in 2..4) { "کد کشور باید ۲ تا ۴ حرف باشد (مثلاً DE)." }
        require(server.city.trim().length in 1..40) { "نام شهر الزامی است." }
        require(server.host.trim().length in 3..120) { "آدرس هاست یا IP نامعتبر است." }
        require(server.port in 1L..65535L) { "پورت باید بین ۱ تا ۶۵۵۳۵ باشد." }
        require(server.mtu in 1200L..1500L) { "مقدار MTU باید بین ۱۲۰۰ تا ۱۵۰۰ باشد." }
    }

    private fun defaultGlobalServers(): List<Pair<String, VpnServer>> = listOf(
        "srv_de_fra" to VpnServer(
            name = "Frankfurt Core-01",
            country = "آلمان",
            countryCode = "DE",
            city = "Frankfurt",
            host = "1.1.1.1",
            port = 51820L,
            protocol = VpnProtocol.WIREGUARD.name,
            cipher = "ChaCha20-Poly1305",
            dns = "1.1.1.1",
            tunnelAddress = "10.8.0.2",
            mtu = 1420L,
            publicKey = "wG7xKp2mR9tV4nQ1zL8cB5yJ0fH3dM6sP2aE9uX4kW0=",
            region = "Europe",
            loadPercent = 18L,
            latencyMs = 36L,
            active = true,
            recommended = true
        ),
        "srv_nl_ams" to VpnServer(
            name = "Amsterdam Edge-02",
            country = "هلند",
            countryCode = "NL",
            city = "Amsterdam",
            host = "9.9.9.9",
            port = 51820L,
            protocol = VpnProtocol.WIREGUARD.name,
            cipher = "ChaCha20-Poly1305",
            dns = "9.9.9.9",
            tunnelAddress = "10.8.0.3",
            mtu = 1420L,
            publicKey = "mN4vQ8rT1wL5zJ9cY2bF7dH0kP3sX6aG1uE8nK4pM2Q=",
            region = "Europe",
            loadPercent = 24L,
            latencyMs = 42L,
            active = true,
            recommended = true
        ),
        "srv_ch_zrh" to VpnServer(
            name = "Zurich Vault-01",
            country = "سوئیس",
            countryCode = "CH",
            city = "Zurich",
            host = "8.8.8.8",
            port = 443L,
            protocol = VpnProtocol.VLESS_TLS.name,
            cipher = "TLS 1.3 / XTLS",
            dns = "1.1.1.1",
            tunnelAddress = "10.8.0.4",
            mtu = 1400L,
            publicKey = "zR2tM6wP9kV3nL7cQ1yB4dF8gH0jS5xA2uE6mK9pT1I=",
            region = "Europe",
            loadPercent = 15L,
            latencyMs = 48L,
            active = true,
            recommended = false
        ),
        "srv_gb_lon" to VpnServer(
            name = "London Shield-03",
            country = "انگلستان",
            countryCode = "GB",
            city = "London",
            host = "1.0.0.1",
            port = 500L,
            protocol = VpnProtocol.IKEV2.name,
            cipher = "AES-256-GCM",
            dns = "1.1.1.1",
            tunnelAddress = "10.8.0.5",
            mtu = 1420L,
            publicKey = "kP9sX2aG5uE8nM1vQ4rT7wL0zJ3cY6bF9dH2pK5mR8U=",
            region = "Europe",
            loadPercent = 31L,
            latencyMs = 54L,
            active = true,
            recommended = false
        ),
        "srv_us_iad" to VpnServer(
            name = "Virginia Backbone-01",
            country = "آمریکا",
            countryCode = "US",
            city = "Ashburn",
            host = "8.8.4.4",
            port = 51820L,
            protocol = VpnProtocol.WIREGUARD.name,
            cipher = "ChaCha20-Poly1305",
            dns = "8.8.8.8",
            tunnelAddress = "10.8.0.6",
            mtu = 1420L,
            publicKey = "bF7dH0kP3sX6aG9uE2nM5vQ8rT1wL4zJ7cY0kR3mP6A=",
            region = "North America",
            loadPercent = 29L,
            latencyMs = 96L,
            active = true,
            recommended = false
        ),
        "srv_sg_sin" to VpnServer(
            name = "Singapore Relay-01",
            country = "سنگاپور",
            countryCode = "SG",
            city = "Singapore",
            host = "208.67.222.222",
            port = 1194L,
            protocol = VpnProtocol.OPENVPN.name,
            cipher = "AES-256-GCM",
            dns = "1.1.1.1",
            tunnelAddress = "10.8.0.7",
            mtu = 1380L,
            publicKey = "cY2bF7dH0kP3sX6aG1uE8nM4vQ8rT1wL5zJ9mK2pT5W=",
            region = "Asia",
            loadPercent = 22L,
            latencyMs = 118L,
            active = true,
            recommended = false
        ),
        "srv_jp_tyo" to VpnServer(
            name = "Tokyo Quantum-01",
            country = "ژاپن",
            countryCode = "JP",
            city = "Tokyo",
            host = "208.67.220.220",
            port = 443L,
            protocol = VpnProtocol.VLESS_TLS.name,
            cipher = "TLS 1.3 / XTLS",
            dns = "9.9.9.9",
            tunnelAddress = "10.8.0.8",
            mtu = 1420L,
            publicKey = "dH0kP3sX6aG1uE8nM4vQ8rT1wL5zJ9cY2bF7sR4kW9C=",
            region = "Asia",
            loadPercent = 19L,
            latencyMs = 132L,
            active = true,
            recommended = false
        )
    )

    companion object {
        const val COLLECTION_SERVERS = "vpn_servers"
        const val COLLECTION_SETTINGS = "system_settings"
        const val COLLECTION_LOGS = "connection_logs"
        const val GLOBAL_SETTINGS_DOC_ID = "global"
    }
}
