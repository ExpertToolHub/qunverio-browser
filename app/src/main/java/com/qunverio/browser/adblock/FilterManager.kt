package com.qunverio.browser.adblock

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.qunverio.browser.R
import com.qunverio.browser.data.SettingsStore
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Loads the built-in list plus any downloaded lists, and refreshes the downloaded
 * lists over HTTPS. Lists are plain text data and are never executed.
 */
class FilterManager(
    private val context: Context,
    private val engine: FilterEngine,
    private val settings: SettingsStore,
) {
    var updating by mutableStateOf(false)
        private set
    var status by mutableStateOf("")
        private set

    private val io = Executors.newSingleThreadExecutor { r -> Thread(r).apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())
    private val dir = File(context.filesDir, "filters")

    fun loadAsync() {
        io.execute { rebuild() }
    }

    fun updateIfStale() {
        val age = System.currentTimeMillis() - settings.filterUpdatedAt
        if (settings.adBlockEnabled && age > WEEK_MS) updateAsync()
    }

    fun updateAsync() {
        if (updating) return
        updating = true
        status = "Downloading lists…"
        io.execute {
            dir.mkdirs()
            var ok = 0
            for ((name, url) in LISTS) {
                try {
                    download(url, File(dir, "$name.txt"))
                    ok++
                } catch (e: Exception) {
                    // keep the previous copy of this list, if any
                }
            }
            rebuild()
            main.post {
                updating = false
                if (ok > 0) settings.setFilterUpdatedAt(System.currentTimeMillis())
                status = if (ok == LISTS.size) "Updated" else "Updated $ok of ${LISTS.size} lists. Check your connection."
            }
        }
    }

    private fun rebuild() {
        val parsed = FilterListParser.Result()
        try {
            context.resources.openRawResource(R.raw.default_filters).bufferedReader()
                .useLines { FilterListParser.parseInto(it, parsed) }
        } catch (e: Exception) {
            // built-in list unreadable: continue with downloaded lists only
        }
        dir.listFiles()?.filter { it.name.endsWith(".txt") }?.forEach { f ->
            try {
                f.bufferedReader().useLines { FilterListParser.parseInto(it, parsed) }
            } catch (e: Exception) {
                // skip unreadable list
            }
        }
        engine.replaceRules(parsed.block, parsed.allow)
        val count = engine.ruleCount
        main.post { settings.setFilterRuleCount(count) }
    }

    private fun download(urlStr: String, dest: File) {
        require(urlStr.startsWith("https://")) { "HTTPS only" }
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        val tmp = File(dest.parentFile, dest.name + ".tmp")
        try {
            if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
            conn.inputStream.use { input -> tmp.outputStream().use { out -> copyLimited(input, out) } }
            val first = tmp.bufferedReader().use { it.readLine() }.orEmpty()
            if (!first.startsWith("[Adblock")) throw IOException("Not a filter list")
            if (!tmp.renameTo(dest)) {
                dest.delete()
                if (!tmp.renameTo(dest)) throw IOException("Could not save list")
            }
        } finally {
            conn.disconnect()
            tmp.delete()
        }
    }

    private fun copyLimited(input: InputStream, out: OutputStream) {
        val buf = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > MAX_BYTES) throw IOException("List too large")
            out.write(buf, 0, n)
        }
    }

    private companion object {
        const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
        const val MAX_BYTES = 12L * 1024 * 1024
        val LISTS = listOf(
            "easylist" to "https://easylist.to/easylist/easylist.txt",
            "easyprivacy" to "https://easylist.to/easylist/easyprivacy.txt",
        )
    }
}
