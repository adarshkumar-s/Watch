package com.adarshkumar.omnitrix.pairing

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local-only persistence of scanned raw QR payloads for diagnostics.
 * Nothing in here ever leaves the device without the user explicitly exporting it.
 */
class QrStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("omnitrix_qr_store", Context.MODE_PRIVATE)

    data class StoredPayload(val raw: String, val formats: List<String>, val scannedAt: Long)

    fun save(payload: QrPayload) {
        val all = JSONArray(prefs.getString(KEY_PAYLOADS, "[]"))
        val obj = JSONObject()
            .put("raw", payload.raw)
            .put("formats", JSONArray(payload.formats.map { it.name }))
            .put("scannedAt", payload.scannedAtEpochMillis)
        // Keep newest last; cap history at 32 entries.
        val trimmed = JSONArray()
        val start = maxOf(0, all.length() - 31)
        for (i in start until all.length()) trimmed.put(all.get(i))
        trimmed.put(obj)
        prefs.edit().putString(KEY_PAYLOADS, trimmed.toString()).apply()
    }

    fun all(): List<StoredPayload> {
        val out = ArrayList<StoredPayload>()
        val all = JSONArray(prefs.getString(KEY_PAYLOADS, "[]"))
        for (i in 0 until all.length()) {
            val o = all.optJSONObject(i) ?: continue
            val formats = ArrayList<String>()
            val fa = o.optJSONArray("formats")
            if (fa != null) for (j in 0 until fa.length()) formats += fa.optString(j)
            out += StoredPayload(o.optString("raw"), formats, o.optLong("scannedAt"))
        }
        return out
    }

    fun latest(): StoredPayload? = all().lastOrNull()

    fun clear() = prefs.edit().remove(KEY_PAYLOADS).apply()

    companion object {
        private const val KEY_PAYLOADS = "payloads"
    }
}
