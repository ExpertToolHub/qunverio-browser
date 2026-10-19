package com.qunverio.browser.data

import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

data class Bookmark(val url: String, val title: String)

/** Local bookmarks, newest first. Main-thread use only. */
class BookmarkStore(private val file: File) {
    val items = mutableStateListOf<Bookmark>()
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r).apply { isDaemon = true } }

    init {
        try {
            if (file.exists()) {
                val arr = JSONArray(file.readText())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    items.add(Bookmark(o.getString("u"), o.optString("t")))
                }
            }
        } catch (e: Exception) {
            items.clear()
        }
    }

    fun contains(url: String?): Boolean = url != null && items.any { it.url == url }

    fun toggle(url: String, title: String) {
        val index = items.indexOfFirst { it.url == url }
        if (index >= 0) items.removeAt(index) else items.add(0, Bookmark(url, title.ifBlank { url }))
        persist()
    }

    fun remove(bookmark: Bookmark) {
        items.remove(bookmark)
        persist()
    }

    private fun persist() {
        val snapshot = items.toList()
        io.execute {
            try {
                val arr = JSONArray()
                snapshot.forEach { b -> arr.put(JSONObject().put("u", b.url).put("t", b.title)) }
                writeTextAtomic(file, arr.toString())
            } catch (e: Exception) {
                // Best-effort.
            }
        }
    }
}
