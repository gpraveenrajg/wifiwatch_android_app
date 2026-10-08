package com.raj.wifiwatch
import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.net.*
import android.net.wifi.*
import android.os.*
import java.io.File
import java.net.*

object Store {
    fun f(c: Context) = File(c.filesDir, "log.csv")
    fun add(c: Context, s: Long, e: Long, t: String) { f(c).appendText("$s,$e,$t\n") }
    fun all(c: Context): List<Triple<Long, Long, String>> =
        if (!f(c).exists()) emptyList() else f(c).readLines().mapNotNull {
            val x = it.split(","); if (x.size == 3) Triple(x[0].toLong(), x[1].toLong(), x[2]) else null }
}

class MonitorService : Service() {
    @Volatile var on = false
    var wl: PowerManager.WakeLock? = null
    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("w", "Monitor", NotificationManager.IMPORTANCE_LOW))
        startForeground(1, note("Starting..."), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        if (!on) {
            on = true
            wl = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ww:l").apply { acquire() }
            Thread { loop() }.start()
        }
        return START_STICKY
    }

    override fun onDestroy() { on = false; wl?.release() }

    fun note(t: String) = Notification.Builder(this, "w").setContentTitle("WifiWatch")
        .setContentText(t).setSmallIcon(android.R.drawable.ic_dialog_info).setOngoing(true).build()

    fun reach(n: Network, h: String, port: Int) = try {
        n.socketFactory.createSocket().use { it.connect(InetSocketAddress(h, port), 2500) }; true
    } catch (e: Exception) { e.message?.contains("refused", true) == true }

    fun probe(cm: ConnectivityManager, wm: WifiManager): String {
        val n = cm.allNetworks.firstOrNull { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
            ?: return "LAN_DOWN"
        if (reach(n, "1.1.1.1", 443) || reach(n, "8.8.8.8", 53)) return "OK"
        val g = wm.dhcpInfo.gateway
        val gw = "${g and 255}.${g shr 8 and 255}.${g shr 16 and 255}.${g shr 24 and 255}"
        return if (reach(n, gw, 80)) "ISP_DOWN" else "LAN_DOWN"
    }

    fun loop() {
        val p = getSharedPreferences("p", 0)
        val nm = getSystemService(NotificationManager::class.java)
        val cm = getSystemService(ConnectivityManager::class.java)
        val wm = applicationContext.getSystemService(WifiManager::class.java)
        val t0 = System.currentTimeMillis(); val hb = p.getLong("hb", 0)
        if (hb > 0 && t0 - hb > 60000) Store.add(this, hb, t0, "NOT_MONITORED")
        var st = "AWAY"; var since = t0
        while (on) {
            val home = p.getString("ssid", "") ?: ""
            val wi = wm.connectionInfo
            val ssid = wi.ssid?.trim('"') ?: ""
            val linkUp = wi.supplicantState == SupplicantState.COMPLETED && wi.networkId != -1
            val now = System.currentTimeMillis()
            p.edit().putLong("hb", now).apply()
            val nx = when {
                linkUp && ssid == home -> probe(cm, wm)
                linkUp -> "AWAY"
                st == "AWAY" -> "AWAY"
                st == "WIFI_DOWN" && now - since > 1800000 -> "AWAY"
                else -> "WIFI_DOWN"
            }
            if (nx != st) {
                if (st != "OK" && st != "AWAY" && !(st == "WIFI_DOWN" && nx == "AWAY") && now - since >= 5000)
                    Store.add(this, since, now, st)
                st = nx; since = now
                nm.notify(1, note(if (st == "AWAY") "Not at home WiFi - idle" else "Home WiFi: $st"))
            }
            Thread.sleep(if (st == "AWAY") 15000 else 5000)
        }
    }
}
