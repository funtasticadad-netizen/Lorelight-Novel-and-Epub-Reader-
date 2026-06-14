package com.example.lorelight

import android.content.Context
import android.os.Build
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashReporter {
    private const val PREFS_NAME = "lorelight_crash_reports"
    private const val KEY_CRASHES = "saved_crashes"
    private const val MAX_CRASH_REPORTS = 5
    private const val TAG = "CrashReporter"

    fun init(context: Context) {
        val appContext = context.applicationContext
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                saveCrash(appContext, thread, throwable, "UNCAUGHT_CRASH")
            } catch (e: Exception) {
                e.printStackTrace()
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun recordError(context: Context, tag: String, throwable: Throwable, category: String = "HANDLED_ERROR") {
        try {
            saveCrash(context.applicationContext, Thread.currentThread(), throwable, category)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record handled error", e)
        }
    }

    private fun saveCrash(context: Context, thread: Thread, throwable: Throwable, category: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val crashListJson = prefs.getString(KEY_CRASHES, "[]") ?: "[]"
            val array = JSONArray(crashListJson)
            
            val displayTimestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val fileTimestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
            val stackTrace = Log.getStackTraceString(throwable)
            
            // Build comprehensive state metadata payload
            val metadataObj = JSONObject().apply {
                put("category", category)
                put("device_model", "${Build.MANUFACTURER} ${Build.MODEL}")
                put("device_sdk", Build.VERSION.SDK_INT)
                put("device_os", Build.VERSION.RELEASE)
                put("cpu_abis", Build.SUPPORTED_ABIS.joinToString(", "))
                
                // Keep try-catch narrow to ensure state collection never crashes the crash saver itself
                try {
                    val activeNovel = TtsPlaybackManager.activeNovel.value
                    if (activeNovel != null) {
                        put("playback_novel_title", activeNovel.title)
                        put("playback_novel_author", activeNovel.author ?: "Unknown")
                        put("playback_chapter_index", TtsPlaybackManager.activeChapterIndex.value)
                        put("playback_total_chapters", TtsPlaybackManager.chapters.value.size)
                        put("playback_sentence_index", TtsPlaybackManager.activeSentenceIndex.value)
                        put("playback_total_sentences", TtsPlaybackManager.filteredSentences.value.size)
                        
                        val activeSent = TtsPlaybackManager.filteredSentences.value.getOrNull(TtsPlaybackManager.activeSentenceIndex.value)
                        if (activeSent != null) {
                            put("playback_sentence_preview", if (activeSent.length > 100) activeSent.take(97) + "..." else activeSent)
                        }
                    } else {
                        put("playback_novel_title", "None")
                    }
                    put("playback_is_playing", TtsPlaybackManager.isPlaying.value)
                    put("tts_ready", TtsPlaybackManager.isTtsReady.value)
                    put("tts_voice", TtsPlaybackManager.selectedVoice.value?.name ?: "System Default")
                    put("tts_accent", TtsPlaybackManager.selectedAccent.value)
                    put("tts_rate", TtsPlaybackManager.speechRate.value)
                    put("tts_pitch", TtsPlaybackManager.speechPitch.value)
                    val timer = TtsPlaybackManager.sleepTimerMinutes.value
                    put("sleep_timer", timer ?: -1)
                } catch (e: Exception) {
                    put("playback_state_fetch_error", e.message ?: "Unknown")
                }
                
                // Attach premium buffered diagnostics containing last 50 processed text lines and engine status logs
                try {
                    val logsArray = JSONArray()
                    DiagnosticLogger.getDiagnosticBuffer().forEach { logLine ->
                        logsArray.put(logLine)
                    }
                    put("diagnostic_logs", logsArray)
                } catch (e: Exception) {
                    put("diagnostic_logs_error", e.message ?: "Unknown")
                }
            }

            val crashObj = JSONObject().apply {
                put("timestamp", displayTimestamp)
                put("message", throwable.message ?: "No error message")
                put("cause", throwable.cause?.toString() ?: "Unknown cause")
                put("thread", thread.name)
                put("stackTrace", stackTrace)
                put("metadata", metadataObj.toString())
            }
            
            // Add to start (newest first)
            val newArray = JSONArray()
            newArray.put(crashObj)
            for (i in 0 until minOf(array.length(), MAX_CRASH_REPORTS - 1)) {
                newArray.put(array.get(i))
            }
            
            prefs.edit().putString(KEY_CRASHES, newArray.toString()).commit()

            // Build human-readable representation file
            val textContent = """
                == LORELIGHT PREMIUM CRASH REPORT ==
                Timestamp: $displayTimestamp
                Category: $category
                Thread: ${thread.name}
                Exception Message: ${throwable.message ?: "No error message"}
                Cause: ${throwable.cause?.toString() ?: "Unknown cause"}
                
                -- METADATA & ENGINE CONTEXT --
                Device Model: ${metadataObj.optString("device_model")}
                Android OS: ${metadataObj.optString("device_os")} (API ${metadataObj.optString("device_sdk")})
                CPU ABIs: ${metadataObj.optString("cpu_abis")}
                Active Novel: ${metadataObj.optString("playback_novel_title", "None")}
                Novel Author: ${metadataObj.optString("playback_novel_author", "N/A")}
                Selected Chapter Index: ${metadataObj.optString("playback_chapter_index", "N/A")} (${metadataObj.optString("playback_total_chapters", "0")} total)
                Sentence Index: ${metadataObj.optString("playback_sentence_index", "N/A")} (${metadataObj.optString("playback_total_sentences", "0")} total)
                Sentence Excerpt: ${metadataObj.optString("playback_sentence_preview", "N/A")}
                Playback Playing State: ${metadataObj.optBoolean("playback_is_playing", false)}
                TTS Status Ready: ${metadataObj.optBoolean("tts_ready", false)}
                TTS Voice Selected: ${metadataObj.optString("tts_voice")}
                TTS Accent Selected: ${metadataObj.optString("tts_accent")}
                TTS Speech Rate: ${metadataObj.optDouble("tts_rate", 1.0)}
                TTS Pitch Level: ${metadataObj.optDouble("tts_pitch", 1.0)}
                Sleep Timer (Minutes Remaining): ${if (metadataObj.optInt("sleep_timer", -1) == -1) "Off" else metadataObj.optInt("sleep_timer").toString()}
                
                -- DIAGNOSTIC LOG BUFFER (LAST 50 ENGINE/TEXT EVENTS) --
                ${DiagnosticLogger.getFormattedBuffer()}
                
                STACK TRACE:
                $stackTrace
            """.trimIndent()

            // Mirrored file write to external storage directory (accessible via MTP / File manager under Android/data/)
            val extDir = context.getExternalFilesDir("CrashReports")
            if (extDir != null) {
                if (!extDir.exists()) {
                    extDir.mkdirs()
                }
                val crashFile = File(extDir, "crash_report_$fileTimestamp.txt")
                crashFile.writeText(textContent)
                
                // Prune old external crash reports to prevent pollution
                val files = extDir.listFiles { file -> file.isFile && file.name.startsWith("crash_report_") && file.name.endsWith(".txt") }
                if (files != null && files.size > MAX_CRASH_REPORTS) {
                    val sorted = files.sortedBy { it.lastModified() }
                    val toDeleteCount = sorted.size - MAX_CRASH_REPORTS
                    for (i in 0 until toDeleteCount) {
                        sorted[i].delete()
                    }
                }
            }

            // Also mirror to custom selected SAF storage folder (if configured) for ultimate external convenience
            try {
                val backupPrefs = context.getSharedPreferences("lorelight_backup_prefs", Context.MODE_PRIVATE)
                val type = backupPrefs.getString("backup_location_type", "external") ?: "external"
                if (type == "custom") {
                    val uriStr = backupPrefs.getString("backup_custom_uri", null)
                    if (!uriStr.isNullOrEmpty()) {
                        val treeUri = android.net.Uri.parse(uriStr)
                        val treeFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri)
                        if (treeFile != null && treeFile.exists() && treeFile.isDirectory) {
                            val crashFileName = "crash_report_$fileTimestamp.txt"
                            var crashDocFile = treeFile.findFile(crashFileName)
                            if (crashDocFile == null) {
                                crashDocFile = treeFile.createFile("text/plain", crashFileName)
                            }
                            if (crashDocFile != null) {
                                context.contentResolver.openOutputStream(crashDocFile.uri)?.use { out ->
                                    out.write(textContent.toByteArray())
                                }
                            }

                            // Prune old custom folder crash reports, keep last 5
                            val customFiles = treeFile.listFiles()
                            val customCrashes = customFiles.filter { it.isFile && it.name?.startsWith("crash_report_") == true && it.name?.endsWith(".txt") == true }
                            if (customCrashes.size > MAX_CRASH_REPORTS) {
                                val sorted = customCrashes.sortedBy { it.lastModified() ?: 0L }
                                val toDeleteCount = sorted.size - MAX_CRASH_REPORTS
                                for (i in 0 until toDeleteCount) {
                                    sorted[i].delete()
                                }
                            }
                        }
                    }
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getCrashReports(context: Context): List<CrashReport> {
        val list = mutableListOf<CrashReport>()
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString(KEY_CRASHES, "[]") ?: "[]"
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    CrashReport(
                        timestamp = obj.optString("timestamp", ""),
                        message = obj.optString("message", ""),
                        cause = obj.optString("cause", ""),
                        thread = obj.optString("thread", ""),
                        stackTrace = obj.optString("stackTrace", ""),
                        metadata = obj.optString("metadata", null)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun clearCrashReports(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_CRASHES, "[]").commit()
            
            val extDir = context.getExternalFilesDir("CrashReports")
            if (extDir != null && extDir.exists()) {
                extDir.listFiles()?.forEach { it.delete() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

data class CrashReport(
    val timestamp: String,
    val message: String,
    val cause: String,
    val thread: String,
    val stackTrace: String,
    val metadata: String? = null
)
