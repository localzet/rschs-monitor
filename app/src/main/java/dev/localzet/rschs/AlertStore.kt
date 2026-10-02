package dev.localzet.rschs

import android.content.Context
import android.provider.Telephony
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class AlertEvent(val time: Long, val type: String, val body: String, val sender: String)

object AlertStore {
    private const val PREF = "events"
    private const val KEY = "items"

    fun isRschsSender(sender: String): Boolean {
        val s = sender.trim().uppercase(Locale.ROOT).replace(" ", "")
        return s.contains("RSCHS") || s.contains("РСЧС")
    }

    fun classify(body: String): String? {
        val s = body.lowercase(Locale.ROOT)
        return when {
            s.contains("отбой") -> "CLEAR"
            s.contains("ракет") -> "MISSILE"
            s.contains("бпла") || s.contains("беспилот") -> "UAV"
            s.contains("опасност") || s.contains("тревог") -> "ALERT"
            else -> null
        }
    }

    @Synchronized
    fun add(c: Context, sender: String, body: String, time: Long) {
        if (!isRschsSender(sender)) return
        val type = classify(body) ?: return
        val items = all(c).toMutableList()
        if (items.any { it.time == time && it.body == body }) return
        items += AlertEvent(time, type, body, sender)
        save(c, items)
    }

    fun all(c: Context): List<AlertEvent> {
        val raw = c.getSharedPreferences(PREF, 0).getString(KEY, "[]") ?: "[]"
        val a = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
        val out = ArrayList<AlertEvent>(a.length())
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            out += AlertEvent(o.optLong("t"), o.optString("k"), o.optString("b"), o.optString("s"))
        }
        return out.distinctBy { it.time to it.body }.sortedBy { it.time }
    }

    private fun save(c: Context, items: List<AlertEvent>) {
        val a = JSONArray()
        items.sortedBy { it.time }.forEach {
            a.put(JSONObject().put("t", it.time).put("k", it.type).put("b", it.body).put("s", it.sender))
        }
        c.getSharedPreferences(PREF, 0).edit().putString(KEY, a.toString()).apply()
    }

    fun scanExisting(c: Context): Int {
        val found = mutableListOf<AlertEvent>()
        val cur = c.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.DATE, Telephony.Sms.BODY, Telephony.Sms.ADDRESS),
            null, null, Telephony.Sms.DATE + " ASC"
        ) ?: return 0
        cur.use {
            val dateIx = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val bodyIx = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val addressIx = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            while (it.moveToNext()) {
                val sender = it.getString(addressIx) ?: ""
                if (!isRschsSender(sender)) continue
                val body = it.getString(bodyIx) ?: ""
                val type = classify(body) ?: continue
                found += AlertEvent(it.getLong(dateIx), type, body, sender)
            }
        }
        val merged = (all(c) + found).distinctBy { it.time to it.body }.sortedBy { it.time }
        save(c, merged)
        return found.size
    }
}