package com.example.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.auth.OwnerAuthManager
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val ownerOk = OwnerAuthManager.isAuthorizedOwner(Firebase.auth.currentUser)
        if (ownerOk &&
            VpnTunnelManager.isAutoConnectEnabled(context) &&
            VpnTunnelManager.canStartFromBackground(context)
        ) {
            VpnTunnelManager.startSavedTunnelFromBackground(context)
        }
    }
}
