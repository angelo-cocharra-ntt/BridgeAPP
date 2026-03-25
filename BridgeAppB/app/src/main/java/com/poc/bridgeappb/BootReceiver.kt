package com.poc.bridgeappb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Arranca o SensorServerService automaticamente quando o tablet liga.
 * Requer a permissão RECEIVE_BOOT_COMPLETED no AndroidManifest.
 *
 * Desta forma, o utilizador da App A (Power Apps) pode abrir a app
 * imediatamente após ligar o tablet, sem precisar de abrir manualmente a App B.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val serviceIntent = Intent(context, SensorServerService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}
