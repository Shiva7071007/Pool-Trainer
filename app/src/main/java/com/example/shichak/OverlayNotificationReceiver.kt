package com.example.shichak

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class OverlayNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            OverlayService.ACTION_DISMISS -> {
                context.stopService(Intent(context, OverlayService::class.java))
            }
            OverlayService.ACTION_REVEAL,
            OverlayService.ACTION_CONCEAL,
            OverlayService.ACTION_TOGGLE -> {
                val serviceIntent = Intent(context, OverlayService::class.java).apply {
                    action = intent.action
                }
                ContextCompat.startForegroundService(context, serviceIntent)
            }
            OverlayService.ACTION_CONFIG_CHANGED -> {
                val serviceIntent = Intent(context, OverlayService::class.java).apply {
                    action = intent.action
                }
                ContextCompat.startForegroundService(context, serviceIntent)
            }
            OverlayService.ACTION_PROFILE_CHANGED -> {
                val serviceIntent = Intent(context, OverlayService::class.java).apply {
                    action = intent.action
                }
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }
    }
}
