package dev.localzet.rschs

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    companion object { const val ACTION_REFRESH = "dev.localzet.rschs.REFRESH" }

    private val io = Executors.newSingleThreadExecutor()
    private lateinit var root: LinearLayout
    private lateinit var scroll: ScrollView
    private var selectedDay = startOfDay(System.currentTimeMillis())
    private var scanning = false

    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = loadAndRender()
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.statusBarColor = C.bg
        window.navigationBarColor = C.bg
        scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(C.bg) }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(36))
        }
        scroll.addView(root)
        setContentView(scroll)
        registerReceiver(refreshReceiver, IntentFilter(ACTION_REFRESH), RECEIVER_NOT_EXPORTED)
        loadAndRender()

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS), 7)
        } else scanExisting()
    }

    override fun onRequestPermissionsResult(r: Int, p: Array<out String>, g: IntArray) {
        super.onRequestPermissionsResult(r, p, g)
        if (r == 7 && g.firstOrNull() == PackageManager.PERMISSION_GRANTED) scanExisting()
    }

    override fun onDestroy() {
        unregisterReceiver(refreshReceiver)
        io.shutdownNow()
        super.onDestroy()
    }

    private fun scanExisting() {
        if (scanning) return
        scanning = true
        renderLoading()
        io.execute {
            val count = try { AlertStore.scanExisting(this) } catch (_: Exception) { -1 }
            runOnUiThread {
                scanning = false
                loadAndRender()
                if (count >= 0) Toast.makeText(this, "Найдено сообщений РСЧС: $count", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadAndRender() {
        io.execute {
            val events = AlertStore.all(this)
            runOnUiThread { render(events) }
        }
    }

    private fun renderLoading() {
        if (!::root.isInitialized) return
        root.removeAllViews()
        header("РСЧС", "Сканирую существующие сообщения…")
        card { addView(text("Это выполняется в фоне — интерфейс не должен зависать.", 14f, C.muted)) }
    }

    private fun render(all: List<AlertEvent>) {
        root.removeAllViews()
        val last = all.lastOrNull()
        val active = last != null && last.type != "CLEAR"

        header("РСЧС", "Локальный монитор официальных SMS")
        card(accent = if (active) C.red else C.cyan) {
            addView(text("ТЕКУЩИЙ СТАТУС", 11f, C.muted, true))
            addView(text(statusName(last), 27f, if (active) C.red else C.green, true))
            addView(text(last?.let { "Последнее изменение • " + fmt("dd MMM, HH:mm", it.time) } ?: "Сообщения РСЧС пока не найдены", 13f, C.muted))
        }

        section("7 ДНЕЙ")
        val week = all.filter { it.time >= System.currentTimeMillis() - 7L * 86400000L }
        val starts = week.filter { it.type != "CLEAR" }
        val activeDays = starts.map { fmt("yyyy-MM-dd", it.time) }.distinct().size
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(metric(starts.size.toString(), "сигналов"), LinearLayout.LayoutParams(0, dp(94), 1f))
        row.addView(space(10))
        row.addView(metric(activeDays.toString(), "дней с сигналами"), LinearLayout.LayoutParams(0, dp(94), 1f))
        root.addView(row)

        val hourly = IntArray(24)
        starts.forEach { hourly[Calendar.getInstance().apply { timeInMillis = it.time }.get(Calendar.HOUR_OF_DAY)]++ }
        val best = hourly.indices.maxByOrNull { hourly[it] } ?: 0
        card {
            addView(text("Исторический паттерн", 16f, C.text, true))
            addView(text(if (starts.isEmpty()) "Пока недостаточно данных" else "Чаще всего сообщения начинались в интервале %02d:00–%02d:00".format(best, (best + 1) % 24), 14f, C.muted))
            addView(text("Это статистика прошлых SMS, а не прогноз угрозы.", 12f, C.dim))
        }

        section("ИСТОРИЯ")
        val nav = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        nav.addView(navButton("‹") { selectedDay = shiftDay(selectedDay, -1); render(all) })
        val date = text(SimpleDateFormat("dd MMMM, EEEE", Locale("ru")).format(Date(selectedDay)), 17f, C.text, true).apply { gravity = Gravity.CENTER }
        nav.addView(date, LinearLayout.LayoutParams(0, dp(54), 1f))
        nav.addView(navButton("›") { selectedDay = shiftDay(selectedDay, 1); render(all) })
        root.addView(nav)

        val dayEnd = shiftDay(selectedDay, 1)
        val dayEvents = all.filter { it.time >= selectedDay && it.time < dayEnd }
        if (dayEvents.isEmpty()) {
            card { addView(text("За этот день сообщений нет", 14f, C.muted)) }
        } else dayEvents.forEach { eventCard(it) }

        val scan = Button(this).apply {
            text = "Пересканировать SMS РСЧС"
            isAllCaps = false
            setTextColor(C.bg)
            textSize = 15f
            background = round(C.cyan, 14)
            setOnClickListener { scanExisting() }
        }
        val lp = LinearLayout.LayoutParams(-1, dp(52)); lp.topMargin = dp(18); root.addView(scan, lp)
    }

    private fun eventCard(e: AlertEvent) {
        val accent = when (e.type) { "CLEAR" -> C.green; "MISSILE" -> C.red; "UAV" -> C.orange; else -> C.cyan }
        card(accent) {
            val line = LinearLayout(this@MainActivity).apply { gravity = Gravity.CENTER_VERTICAL }
            line.addView(text(fmt("HH:mm", e.time), 19f, C.text, true))
            line.addView(text("  " + typeName(e.type), 12f, accent, true))
            addView(line)
            addView(text(e.body.replace("\n", " "), 14f, C.muted))
        }
    }

    private fun header(title: String, subtitle: String) {
        root.addView(text(title, 28f, C.text, true))
        root.addView(text(subtitle, 13f, C.muted))
        root.addView(space(14))
    }

    private fun section(s: String) {
        val v = text(s, 11f, C.dim, true)
        val lp = LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(22); lp.bottomMargin = dp(8); root.addView(v, lp)
    }

    private fun metric(value: String, label: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(12)); background = round(C.card, 16, C.border)
        addView(text(value, 25f, C.text, true)); addView(text(label, 12f, C.muted))
    }

    private fun card(accent: Int? = null, build: LinearLayout.() -> Unit) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(17), dp(15), dp(17), dp(15))
            background = round(C.card, 16, accent ?: C.border); build()
        }
        val lp = LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(8); root.addView(box, lp)
    }

    private fun navButton(label: String, click: () -> Unit) = Button(this).apply {
        text = label; textSize = 24f; isAllCaps = false; setTextColor(C.text); background = round(C.card, 14, C.border); setOnClickListener { click() }
    }

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color); includeFontPadding = false; setPadding(0, dp(3), 0, dp(3))
        if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private fun round(fill: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(radius).toFloat(); if (stroke != null) setStroke(dp(1), stroke)
    }
    private fun space(px: Int) = Space(this).apply { layoutParams = LinearLayout.LayoutParams(dp(px), dp(px)) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun fmt(pattern: String, t: Long) = SimpleDateFormat(pattern, Locale("ru")).format(Date(t))
    private fun statusName(e: AlertEvent?) = when (e?.type) { "MISSILE" -> "Ракетная опасность"; "UAV" -> "Опасность БПЛА"; "ALERT" -> "Тревога"; "CLEAR" -> "Отбой"; else -> "Нет данных" }
    private fun typeName(t: String) = when (t) { "MISSILE" -> "РАКЕТНАЯ"; "UAV" -> "БПЛА"; "CLEAR" -> "ОТБОЙ"; else -> "ТРЕВОГА" }

    private fun shiftDay(t: Long, amount: Int): Long = Calendar.getInstance().apply { timeInMillis = t; add(Calendar.DAY_OF_MONTH, amount) }.timeInMillis
    private fun startOfDay(t: Long): Long = Calendar.getInstance().apply { timeInMillis = t; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

    object C {
        val bg = Color.rgb(13,17,23); val card = Color.rgb(24,29,36); val border = Color.rgb(48,54,61)
        val text = Color.rgb(240,246,252); val muted = Color.rgb(139,148,158); val dim = Color.rgb(110,118,129)
        val cyan = Color.rgb(6,182,212); val green = Color.rgb(63,185,80); val orange = Color.rgb(240,136,62); val red = Color.rgb(248,81,73)
    }
}