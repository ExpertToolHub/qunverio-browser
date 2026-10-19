package com.qunverio.browser.data

import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

data class HistoryEntry(val url: String, val title: String, val time: Long)

/** Local-only browsing history, newest first, capped at [MAX] entries. Main-thread use only. */
class HistoryStore(private val file: File) {
    val entries = mutableStateListOf<HistoryEntry>()
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r).apply { isDaemon = true } }

    init {
        try {
            if (file.exists()) {
                val arr = JSONArray(file.readText())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    entries.add(HistoryEntry(o.getString("u"), o.optString("t"), o.optLong("ts")))
                }
            }
        } catch (e: Exception) {
            entries.clear()
        }
    }

    fun add(url: String, title: String) {
        val now = System.currentTimeMillis()
        if (entries.isNotEmpty() && entries[0].url == url) {
            val old = entries[0]
            entries[0] = old.copy(title = title.ifBlank { old.title }, time = now)
        } else {
            entries.add(0, HistoryEntry(url, title.ifBlank { url }, now))
            while (entries.size > MAX) entries.removeAt(entries.lastIndex)
        }
        persist()
    }

    fun remove(entry: HistoryEntry) {
        entries.remove(entry)
        persist()
    }

    fun clear() {
        entries.clear()
        persist()
    }

    private fun persist() {
        val snapshot = entries.toList()
        io.execute {
            try {
                val arr = JSONArray()
                snapshot.forEach { e ->
                    arr.put(JSONObject().put("u", e.url).put("t", e.title).put("ts", e.time))
                }
                writeTextAtomic(file, arr.toString())
            } catch (e: Exception) {
                // History is best-effort.
            }
        }
    }

    private companion object {
        const val MAX = 500
    }
}
