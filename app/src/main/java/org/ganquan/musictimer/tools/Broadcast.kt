package org.ganquan.musictimer.tools

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class Broadcast {
    companion object {
        fun sendLocal(context: Context, msg: String, info: String = "") {
            val intent = Intent("com.example.broadcast.CUSTOM_EVENT")
            intent.putExtra("msg", msg)
            intent.putExtra("info", info)
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
        }

        fun receiveLocal(context: Context, handler: (String, String) -> Unit) {
            val filter = IntentFilter("com.example.broadcast.CUSTOM_EVENT")
            LocalBroadcastManager.getInstance(context).registerReceiver(receiver(handler), filter)
        }

        fun destroyLocal(context: Context, handler: (String, String) -> Unit) {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(receiver(handler))
        }

        private fun receiver(handler: (String, String) -> Unit): BroadcastReceiver {
            return object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val msg = intent.getStringExtra("msg").toString()
                    val info = intent.getStringExtra("info").toString()
                    handler(msg, info)
                }
            }
        }
    }
}