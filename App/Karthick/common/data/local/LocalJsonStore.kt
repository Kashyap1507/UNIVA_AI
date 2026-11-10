package com.kashyap.wizlyai.data.local

/**
 * Small named JSON documents kept on the device (flashcards and the offline
 * review queue). Android stores files under filesDir, iOS under Application
 * Support. Methods are blocking and must be called off the main thread.
 */
interface LocalJsonStore {
    fun read(name: String): String?

    fun write(
        name: String,
        text: String,
    )

    fun delete(name: String)

    /** Remove everything (logout). */
    fun clear()
}

/** Used until the platform registers a real store, and in tests. */
class InMemoryJsonStore : LocalJsonStore {
    private val files = mutableMapOf<String, String>()

    override fun read(name: String): String? = files[name]

    override fun write(
        name: String,
        text: String,
    ) {
        files[name] = text
    }

    override fun delete(name: String) {
        files.remove(name)
    }

    override fun clear() = files.clear()
}

/** Platform code registers its store at startup (AndroidCourseCache / MainViewController). */
object LocalJson {
    var store: LocalJsonStore = InMemoryJsonStore()

    /** File names are built from ids; keep them to safe characters. */
    fun safeName(vararg parts: String): String = parts.joinToString("_") { p -> p.filter { it.isLetterOrDigit() || it == '-' || it == '_' } } + ".json"
}
