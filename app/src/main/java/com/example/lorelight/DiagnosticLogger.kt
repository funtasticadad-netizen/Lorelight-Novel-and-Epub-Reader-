package com.example.lorelight

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.LinkedList

object DiagnosticLogger {
    private const val MAX_LOGS = 50
    // Thread-safe doubly-linked list used for circular buffering
    private val buffer = LinkedList<String>()

    /**
     * Records an engine event or processed text sequence with high-resolution milliseconds.
     */
    @Synchronized
    fun log(tag: String, message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val cleanMsg = message.replace("\n", " ").trim()
        val truncatedMsg = if (cleanMsg.length > 150) cleanMsg.take(147) + "..." else cleanMsg
        val formatted = "[$timestamp] [$tag] $truncatedMsg"
        
        buffer.addLast(formatted)
        while (buffer.size > MAX_LOGS) {
            buffer.removeFirst()
        }
    }

    /**
     * Specialised logger for read sentences/paragraphs to keep track of content.
     */
    fun logProcessedText(text: String) {
        log("TEXT_PROC", text)
    }

    /**
     * Specialised logger for general engine state, variables, audio focus, timer etc.
     */
    fun logEngineStatus(status: String) {
        log("ENGINE_STATE", status)
    }

    /**
     * Returns a snapshot of the current 50-line diagnostic log buffer.
     */
    @Synchronized
    fun getDiagnosticBuffer(): List<String> {
        return ArrayList(buffer)
    }

    /**
     * Formats the current buffer as a single block of raw text for inclusion.
     */
    @Synchronized
    fun getFormattedBuffer(): String {
        if (buffer.isEmpty()) return "No diagnostic trace captured."
        return buffer.joinToString("\n")
    }

    /**
     * Safely clears the existing logging trace.
     */
    @Synchronized
    fun clear() {
        buffer.clear()
    }
}
