package com.raj.wifiwatch
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import java.util.Date

class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val p = getSharedPreferences("p", 0)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 64, 32, 32) }
        val ssid = EditText(this).apply { hint = "Home WiFi name (exact)"; setText(p.getString("ssid", "")) }
        val tv = TextView(this)
        fun refresh() {
            val l = Store.all(this).takeLast(40).reversed()
            tv.text = if (l.isEmpty()) "No outages logged yet." else
                l.joinToString("\n") { "${Report.df.format(Date(it.first))}  ${Report.dur(it.second - it.first)}  ${it.third}" }
        }
        fun btn(t: String, a: () -> Unit) = root.addView(Button(this).apply { text = t; setOnClickListener { a() } })
        root.addView(ssid)
        btn("Start monitoring") {
            p.edit().putString("ssid", ssid.text.toString().trim()).putBoolean("on", true).apply()
            startForegroundService(Intent(this, MonitorService::class.java))
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.ACCESS_FINE_LOCATION), 1)
        }
        btn("Stop") { p.edit().putBoolean("on", false).apply(); stopService(Intent(this, MonitorService::class.java)) }
        btn("Report: last 7 days") { Toast.makeText(this, Report.make(this, 7), Toast.LENGTH_LONG).show() }
        btn("Report: last 30 days") { Toast.makeText(this, Report.make(this, 30), Toast.LENGTH_LONG).show() }
        btn("Refresh log") { refresh() }
        btn("Clear log") { Store.f(this).delete(); refresh() }
        root.addView(ScrollView(this).apply { addView(tv) })
        setContentView(root); refresh()
    }

    override fun onRequestPermissionsResult(rc: Int, perms: Array<String>, r: IntArray) {
        if (rc == 1) requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 2)
        else startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
    }
}
