package com.raj.wifiwatch
import android.content.*
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (c.getSharedPreferences("p", 0).getBoolean("on", false))
            c.startForegroundService(Intent(c, MonitorService::class.java))
    }
}
