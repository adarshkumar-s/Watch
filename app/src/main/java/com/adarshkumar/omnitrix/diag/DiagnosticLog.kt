package com.adarshkumar.omnitrix.diag

import com.adarshkumar.omnitrix.protocol.Direction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Central, in-memory diagnostic log. Everything the app does to or receives from the
 * watch is recorded here with a timestamp and a direction tag so the protocol can be
 * reconstructed later — without ever fabricating packet meaning.
 *
 * Pure-ish (no Android deps) so it can be unit-tested on the JVM.
 */
object DiagnosticLog {

    data class Entry(
        val epochMillis: Long,
        val direction: Direction,
        val category: String,
        val message: String,
    ) {
        fun render(clock: SimpleDateFormat): String =
            "${clock.format(Date(epochMillis))} [${direction.tag}] [$category] $message"
    }

    private const val MAX_ENTRIES = 4000

    private val lock = Any()
    private val entries = ArrayDeque<Entry>()
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    private val clockFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    /** Exposed for instrumentation/testing. */
    var now: () -> Long = { System.currentTimeMillis() }

    fun log(direction: Direction, category: String, message: String) {
        synchronized(lock) {
            entries.addLast(Entry(now(), direction, category, message))
            while (entries.size > MAX_ENTRIES) entries.removeFirst()
        }
        for (l in listeners) runCatching { l.invoke() }
    }

    fun info(category: String, message: String) = log(Direction.LOCAL, category, message)
    fun tx(category: String, message: String) = log(Direction.PHONE_TO_WATCH, category, message)
    fun rx(category: String, message: String) = log(Direction.WATCH_TO_PHONE, category, message)

    fun snapshot(): List<Entry> = synchronized(lock) { entries.toList() }

    fun clear() {
        synchronized(lock) { entries.clear() }
        for (l in listeners) runCatching { l.invoke() }
    }

    fun addListener(listener: () -> Unit) = listeners.add(listener)
    fun removeListener(listener: () -> Unit) = listeners.remove(listener)

    /** Full-text rendering used by the log screen and the diagnostic export. */
    fun render(): String = synchronized(lock) {
        entries.joinToString("\n") { it.render(clockFormat) }
    }

    fun size(): Int = synchronized(lock) { entries.size }
}
