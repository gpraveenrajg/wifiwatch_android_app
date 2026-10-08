package com.raj.wifiwatch
import android.content.*
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.provider.MediaStore
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

object Report {
    val df = SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.getDefault())
    fun dur(ms: Long) = "${ms / 60000}m ${ms / 1000 % 60}s"

    fun make(c: Context, days: Int): String {
        val from = System.currentTimeMillis() - days * 86400000L
        val rows = Store.all(c).filter { it.first >= from }.sortedBy { it.first }
        val out = rows.filter { it.third != "NOT_MONITORED" }
        val total = out.sumOf { it.second - it.first }
        val lines = mutableListOf(
            "WIFI / INTERNET OUTAGE REPORT", "Last $days days. Generated ${df.format(Date())}",
            "Outages: ${out.size}   Total downtime: ${dur(total)}   Longest: ${dur(out.maxOfOrNull { it.second - it.first } ?: 0)}",
            "WIFI_DOWN = phone lost WiFi.  LAN_DOWN = WiFi on, router unreachable.",
            "ISP_DOWN = router reachable but no internet.  NOT_MONITORED = app not running.", "")
        rows.forEach { lines += "${df.format(Date(it.first))} -> ${df.format(Date(it.second))}  ${dur(it.second - it.first)}  ${it.third}" }
        val pdf = PdfDocument(); val paint = Paint().apply { textSize = 9f }
        var page: PdfDocument.Page? = null; var y = 0; var n = 0
        for (l in lines) {
            if (page == null || y > 800) {
                page?.let { pdf.finishPage(it) }
                page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, ++n).create()); y = 40
            }
            page!!.canvas.drawText(l, 30f, y.toFloat(), paint); y += 14
        }
        page?.let { pdf.finishPage(it) }
        save(c, "outages_${days}d.pdf", "application/pdf") { pdf.writeTo(it) }
        save(c, "outages_${days}d.csv", "text/csv") { o ->
            o.write(("start,end,seconds,type\n" + rows.joinToString("\n") {
                "${df.format(Date(it.first))},${df.format(Date(it.second))},${(it.second - it.first) / 1000},${it.third}" }).toByteArray()) }
        return "Saved PDF + CSV to Downloads"
    }

    fun save(c: Context, name: String, mime: String, w: (OutputStream) -> Unit) {
        val v = ContentValues().apply { put(MediaStore.Downloads.DISPLAY_NAME, name); put(MediaStore.Downloads.MIME_TYPE, mime) }
        val u = c.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)!!
        c.contentResolver.openOutputStream(u)!!.use(w)
    }
}
