package com.kashyap.wizlyai.data.local

import java.io.File

/** [LocalJsonStore] as files in the app's private files directory. */
class FileJsonStore(private val dir: File) : LocalJsonStore {
    init {
        dir.mkdirs()
    }

    @Synchronized
    override fun read(name: String): String? = File(dir, name).takeIf { it.exists() }?.readText()

    @Synchronized
    override fun write(
        name: String,
        text: String,
    ) {
        // Write then rename so a crash never leaves half a file
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(File(dir, name))) {
            File(dir, name).writeText(text)
            tmp.delete()
        }
    }

    @Synchronized
    override fun delete(name: String) {
        File(dir, name).delete()
    }

    @Synchronized
    override fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }
}
