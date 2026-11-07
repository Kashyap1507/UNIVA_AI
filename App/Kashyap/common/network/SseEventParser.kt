package com.kashyap.wizlyai.network

/** One Server-Sent Event: `event:` name (default "message") and joined `data:` lines. */
data class SseEvent(val event: String, val data: String)

/**
 * Incremental SSE parser. Feed it lines as they arrive; it returns an event
 * each time a blank line ends one. Comment lines (`:`) and unknown fields are ignored.
 */
class SseEventParser {
    private var event: String? = null
    private val data = StringBuilder()
    private var hasData = false

    fun feed(line: String): SseEvent? {
        val clean = line.removeSuffix("\r")
        if (clean.isEmpty()) return flush()
        if (clean.startsWith(":")) return null
        val colon = clean.indexOf(':')
        val field = if (colon == -1) clean else clean.substring(0, colon)
        var value = if (colon == -1) "" else clean.substring(colon + 1)
        if (value.startsWith(" ")) value = value.substring(1)
        when (field) {
            "event" -> event = value
            "data" -> {
                if (hasData) data.append('\n')
                data.append(value)
                hasData = true
            }
        }
        return null
    }

    /** Emit any pending event (call at end of stream). */
    fun flush(): SseEvent? {
        if (!hasData && event == null) return null
        val result = SseEvent(event ?: "message", data.toString())
        event = null
        data.clear()
        hasData = false
        return result
    }
}
