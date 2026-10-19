package com.qunverio.browser.data

import java.io.File

/** Writes via a temp file so a crash mid-write never corrupts the stored data. */
internal fun writeTextAtomic(file: File, text: String) {
    val tmp = File(file.parentFile, file.name + ".tmp")
    tmp.writeText(text)
    if (!tmp.renameTo(file)) {
        file.delete()
        tmp.renameTo(file)
    }
}
