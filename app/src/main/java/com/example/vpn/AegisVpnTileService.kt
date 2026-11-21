package com.example.vpn

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.example.MainActivity
import com.example.auth.OwnerAuthManager
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

@RequiresApi(Build.VERSION_CODES.N)
class AegisVpnTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        syncTileUi()
    }

    override fun onClick() {
        super.onClick()
        val user = Firebase.auth.currentUser
        if (!OwnerAuthManager.isAuthorizedOwner(user) || !VpnTunnelManager.canStartFromBackground(this)) {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pending = android.app.PendingIntent.getActivity(
                    this,
                    201,
                    intent,
                    android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                )
                startActivityAndCollapse(pending)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
            return
        }

        val current = VpnTunnelManager.snapshot.value.state
        if (current == VpnConnectionState.CONNECTED || current == VpnConnectionState.CONNECTING) {
            VpnTunnelManager.stopTunnel(this)
        } else {
            VpnTunnelManager.startSavedTunnelFromBackground(this)
        }
        syncTileUi()
    }

    private fun syncTileUi() {
        val tile = qsTile ?: return
        val snap = VpnTunnelManager.snapshot.value
        val ownerAuthorized = OwnerAuthManager.isAuthorizedOwner(Firebase.auth.currentUser)

        when {
            !ownerAuthorized -> {
                tile.state = Tile.STATE_INACTIVE
                tile.label = "Aegis VPN"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "نیاز به ورود مالک"
                }
            }
            snap.state == VpnConnectionState.CONNECTED -> {
                tile.state = Tile.STATE_ACTIVE
                tile.label = "Aegis VPN"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = snap.activeServerName.ifBlank { "متصل" }
                }
            }
            snap.state == VpnConnectionState.CONNECTING -> {
                tile.state = Tile.STATE_ACTIVE
                tile.label = "Aegis VPN"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "در حال اتصال..."
                }
            }
            else -> {
                tile.state = Tile.STATE_INACTIVE
                tile.label = "Aegis VPN"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "آماده اتصال"
                }
            }
        }
        tile.updateTile()
    }
}
