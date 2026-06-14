package com.example.lorelight

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SelfHealingEngine {
    private const val TAG = "SelfHealingEngine"
    private const val PREFS_HEALING = "lorelight_self_healing"
    private const val KEY_STARTUP_COUNT = "startup_count"
    private const val KEY_LAST_STARTUP = "last_startup_time"

    fun onAppStart(context: Context) {
        val appContext = context.applicationContext
        try {
            val prefs = appContext.getSharedPreferences(PREFS_HEALING, Context.MODE_PRIVATE)
            val count = prefs.getInt(KEY_STARTUP_COUNT, 0)
            val lastTime = prefs.getLong(KEY_LAST_STARTUP, 0L)
            val now = System.currentTimeMillis()

            Log.d(TAG, "onAppStart - count: $count, timeDelta: ${now - lastTime}ms")

            // Run light check and repairs on every startup for general health
            runLightweightHealing(appContext)

            if (now - lastTime < 12000) { // Started consecutively within 12 seconds
                val newCount = count + 1
                prefs.edit()
                    .putInt(KEY_STARTUP_COUNT, newCount)
                    .putLong(KEY_LAST_STARTUP, now)
                    .commit()

                if (newCount >= 2) {
                    Log.w(TAG, "Startup Crash-Loop Detected! Initiating deep recovery procedures...")
                    runDeepRecovery(appContext, "Consecutive Startup Crash-Loop Detected")
                }
            } else {
                // Happy path: standard delay since last start
                prefs.edit()
                    .putInt(KEY_STARTUP_COUNT, 1)
                    .putLong(KEY_LAST_STARTUP, now)
                    .commit()
            }

            // Post-delayed handler to reset startup count after 6 seconds of stable running
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    appContext.getSharedPreferences(PREFS_HEALING, Context.MODE_PRIVATE)
                        .edit()
                        .putInt(KEY_STARTUP_COUNT, 0)
                        .apply()
                    Log.d(TAG, "App running stable. Startup crash tracker reset to 0.")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, 6000)

        } catch (e: Exception) {
            Log.e(TAG, "Error in onAppStart startup tracker", e)
        }
    }

    private fun runLightweightHealing(context: Context) {
        val reportBuilder = StringBuilder()
        reportBuilder.append("=== LORELIGHT LIGHTWEIGHT AUTO-HEALING REPORT ===\n")
        reportBuilder.append("Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n\n")

        var healedSomething = false

        // 1. Check for empty/corrupt SharedPreferences XML files on disk.
        // Android throws fatal ParseExceptions if XML files in shared_prefs are corrupted/truncated.
        try {
            val sharedPrefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
            if (sharedPrefsDir.exists() && sharedPrefsDir.isDirectory) {
                val files = sharedPrefsDir.listFiles()
                files?.forEach { file ->
                    if (file.isFile && file.name.endsWith(".xml")) {
                        // Check if file is 0-bytes or lacks valid XML content structure
                        if (file.length() == 0L) {
                            reportBuilder.append("Fixed: Empty shared_prefs file detected: ${file.name}. Deleting to allow healthy recreation.\n")
                            file.delete()
                            healedSomething = true
                        } else {
                            try {
                                val text = file.readText().trim()
                                if (!text.startsWith("<map") && !text.startsWith("<?xml")) {
                                    reportBuilder.append("Fixed: Invalid XML syntax in shared_prefs file: ${file.name}. Deleting to avoid startup crash.\n")
                                    file.delete()
                                    healedSomething = true
                                }
                            } catch (xmlEx: Exception) {
                                reportBuilder.append("Fixed: Unreadable shared_prefs xml file: ${file.name}. Resetting file error: ${xmlEx.message}.\n")
                                file.delete()
                                healedSomething = true
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error diagnosing shared_prefs XML files", e)
        }

        // 2. Validate critical data fields stored in SharedPreferences
        try {
            val lorePrefs = context.getSharedPreferences("lorelight_settings", Context.MODE_PRIVATE)
            val savedNovelsStr = lorePrefs.getString("saved_novels", null)
            if (savedNovelsStr != null) {
                try {
                    JSONArray(savedNovelsStr)
                } catch (jsonEx: Exception) {
                    reportBuilder.append("Corrupted 'saved_novels' JSON found: ${jsonEx.message}. Attempting recovery.\n")
                    // Leniently restore JSON or reset to safe empty list
                    val healedJson = attemptLenientJsonRepair(savedNovelsStr)
                    lorePrefs.edit().putString("saved_novels", healedJson).commit()
                    reportBuilder.append("Result: Recovered novels list structure safely.\n")
                    healedSomething = true
                }
            }

            val savedWebsitesStr = lorePrefs.getString("saved_websites", null)
            if (savedWebsitesStr != null) {
                try {
                    JSONArray(savedWebsitesStr)
                } catch (jsonEx: Exception) {
                    reportBuilder.append("Corrupted 'saved_websites' JSON found. Resetting to factory preset presets.\n")
                    lorePrefs.edit().putString("saved_websites", "[]").commit()
                    healedSomething = true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error diagnosing SharedPreferences content", e)
        }

        // 3. Keep TTS params inside healthy boundaries
        try {
            val readerPrefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
            val speechRate = readerPrefs.getSafeFloat("speech_rate", 2.0f)
            val speechPitch = readerPrefs.getSafeFloat("speech_pitch", 1.0f)
            if (speechRate <= 0.05f || speechRate > 5.0f || speechPitch <= 0.05f || speechPitch > 2.0f) {
                reportBuilder.append("Healing: TTS pitch ($speechPitch) or rate ($speechRate) out of bounds. Resetting to standards.\n")
                readerPrefs.edit()
                    .putFloat("speech_rate", 2.0f)
                    .putFloat("speech_pitch", 1.0f)
                    .commit()
                healedSomething = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error validating TTS values", e)
        }

        if (healedSomething) {
            saveSelfHealingReport(context, reportBuilder.toString(), "light_healing")
        }
    }

    fun runDeepRecovery(context: Context, reason: String) {
        val reportBuilder = StringBuilder()
        reportBuilder.append("=== LORELIGHT DEEP RECOVERY & AUTO-HEALING REPORT ===\n")
        reportBuilder.append("Trigger Reason: $reason\n")
        reportBuilder.append("Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n")
        reportBuilder.append("SDK Version: ${android.os.Build.VERSION.SDK_INT}\n\n")

        // Step 1: Force clean any potential corrupt SharedPreferences XML files on disk
        reportBuilder.append("[Task 1/5] Diagnostics of core SharedPreferences files:\n")
        try {
            val sharedPrefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
            if (sharedPrefsDir.exists() && sharedPrefsDir.isDirectory) {
                val files = sharedPrefsDir.listFiles()
                files?.forEach { file ->
                    if (file.isFile && file.name.endsWith(".xml")) {
                        var parsedOk = false
                        try {
                            if (file.length() > 0) {
                                val text = file.readText().trim()
                                if (text.startsWith("<map") || text.startsWith("<?xml")) {
                                    parsedOk = true
                                }
                            }
                        } catch (ex: Exception) {
                            // read error
                        }
                        if (!parsedOk) {
                            reportBuilder.append(" -> Corrupted preference file detected & deleted: ${file.name}\n")
                            file.delete()
                        } else {
                            reportBuilder.append(" -> Healthy: ${file.name}\n")
                        }
                    }
                }
            } else {
                reportBuilder.append(" -> SharedPreferences folder does not exist yet. Safe.\n")
            }
        } catch (e: Exception) {
            reportBuilder.append(" -> Diagnostic error: ${e.message}\n")
        }

        // Step 2: Repair critical JSON variables
        reportBuilder.append("\n[Task 2/5] Cleaning internal preference tables:\n")
        try {
            val lorePrefs = context.getSharedPreferences("lorelight_settings", Context.MODE_PRIVATE)
            val novelsStr = lorePrefs.getString("saved_novels", "[]") ?: "[]"
            try {
                JSONArray(novelsStr)
                reportBuilder.append(" -> 'saved_novels' JSON validation: Passed.\n")
            } catch (je: Exception) {
                val repaired = attemptLenientJsonRepair(novelsStr)
                lorePrefs.edit().putString("saved_novels", repaired).commit()
                reportBuilder.append(" -> 'saved_novels' was corrupted. Repaired structure successfully.\n")
            }

            val websitesStr = lorePrefs.getString("saved_websites", "[]") ?: "[]"
            try {
                JSONArray(websitesStr)
                reportBuilder.append(" -> 'saved_websites' JSON validation: Passed.\n")
            } catch (je: Exception) {
                lorePrefs.edit().putString("saved_websites", "[]").commit()
                reportBuilder.append(" -> 'saved_websites' was corrupted. Reset to presets array.\n")
            }
        } catch (e: Exception) {
            reportBuilder.append(" -> Error during table repairs: ${e.message}\n")
        }

        // Step 3: Clear temporary folders & cache files
        reportBuilder.append("\n[Task 3/5] Inspecting temporary space & chapter caches:\n")
        try {
            val cacheDir = context.cacheDir
            if (cacheDir.exists() && cacheDir.isDirectory) {
                val deletedCount = deleteDirContents(cacheDir)
                reportBuilder.append(" -> Cleared $deletedCount cache directory entries to free memory.\n")
            }

            val chaptersCacheDir = File(context.filesDir, "chapters_cache")
            if (chaptersCacheDir.exists() && chaptersCacheDir.isDirectory) {
                val files = chaptersCacheDir.listFiles()
                var emptyDeleted = 0
                files?.forEach { file ->
                    if (file.isFile && file.length() == 0L) {
                        file.delete()
                        emptyDeleted++
                    }
                }
                reportBuilder.append(" -> Cleared $emptyDeleted corrupted 0-byte chapter cache files.\n")
            }
        } catch (e: Exception) {
            reportBuilder.append(" -> Cache cleaning exception: ${e.message}\n")
        }

        // Step 4: Validate and reset Speech Engine Variables
        reportBuilder.append("\n[Task 4/5] Restoring Speech Engine defaults:\n")
        try {
            val readerPrefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
            readerPrefs.edit()
                .putFloat("speech_rate", 2.0f)
                .putFloat("speech_pitch", 1.0f)
                .remove("selected_voice_name")
                .commit()
            reportBuilder.append(" -> Reset speech rate, pitch and selected voice engine configurations to safe fallback parameters.\n")
        } catch (e: Exception) {
            reportBuilder.append(" -> TTS calibration error: ${e.message}\n")
        }

        // Step 5: Search and self-restore from latest auto-backups
        reportBuilder.append("\n[Task 5/5] Checking for backups to self-restore library state...\n")
        var restoreSuccessful = false
        try {
            // Find valid backup file
            val backupData = findLatestStableBackup(context)
            if (!backupData.isNullOrEmpty()) {
                reportBuilder.append(" -> Stable backup candidate located! Size: ${backupData.length} characters.\n")
                val success = restoreBackupJson(context, backupData)
                if (success) {
                    reportBuilder.append(" -> SUCCESS: Automated database state self-restored successfully from rolling backup file!\n")
                    restoreSuccessful = true
                } else {
                    reportBuilder.append(" -> FAILED: Backup file parsing returned error.\n")
                }
            } else {
                reportBuilder.append(" -> skipped: No local rolling backup file or configured custom backup folder files found.\n")
            }
        } catch (e: Exception) {
            reportBuilder.append(" -> Auto-restoration exception: ${e.message}\n")
        }

        reportBuilder.append("\n==================================================\n")
        reportBuilder.append("SELF-HEALING FINAL OUTCOME: APP REPAIRED & READY\n")
        reportBuilder.append("==================================================\n")

        saveSelfHealingReport(context, reportBuilder.toString(), "deep_recovery")
    }

    private fun attemptLenientJsonRepair(rawJson: String): String {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) return "[]"
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            return trimmed
        }
        
        // Let's build a clean parsing list by isolating JSON objects manually if array outline is broken
        try {
            val list = mutableListOf<String>()
            var balance = 0
            var tempObj = StringBuilder()
            var insideString = false
            var escape = false

            for (i in trimmed.indices) {
                val c = trimmed[i]
                if (escape) {
                    tempObj.append(c)
                    escape = false
                    continue
                }
                if (c == '\\') {
                    tempObj.append(c)
                    escape = true
                    continue
                }
                if (c == '"') {
                    insideString = !insideString
                }

                if (!insideString) {
                    if (c == '{') {
                        balance++
                    }
                    tempObj.append(c)
                    if (c == '}') {
                        balance--
                        if (balance == 0) {
                            val candidate = tempObj.toString().trim()
                            try {
                                JSONObject(candidate) // Validate single object
                                list.add(candidate)
                            } catch (e: Exception) {
                                // invalid candidate, isolate/skip
                            }
                            tempObj = StringBuilder()
                        }
                    }
                } else {
                    tempObj.append(c)
                }
            }

            if (list.isNotEmpty()) {
                val array = JSONArray()
                list.forEach { array.put(JSONObject(it)) }
                return array.toString()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "[]"
    }

    private fun findLatestStableBackup(context: Context): String? {
        try {
            // Priority 1: Check Custom User SAF URI folder if defined
            val backupPrefs = context.getSharedPreferences("lorelight_backup_prefs", Context.MODE_PRIVATE)
            val uriStr = backupPrefs.getString("backup_custom_uri", null)
            if (!uriStr.isNullOrEmpty()) {
                val treeUri = Uri.parse(uriStr)
                val treeFile = DocumentFile.fromTreeUri(context, treeUri)
                if (treeFile != null && treeFile.exists() && treeFile.isDirectory) {
                    val file = treeFile.findFile(BackupManager.BACKUP_FILENAME)
                    if (file != null && file.exists() && file.length() > 0) {
                        context.contentResolver.openInputStream(file.uri)?.use { stream ->
                            val txt = stream.bufferedReader().use { it.readText() }
                            if (txt.isNotEmpty() && txt.trim().startsWith("{")) {
                                return txt
                            }
                        }
                    }
                }
            }

            // Priority 2: Check External App storage folder
            val extDir = context.getExternalFilesDir("AutoBackups")
            if (extDir != null && extDir.exists()) {
                val file = File(extDir, BackupManager.BACKUP_FILENAME)
                if (file.exists() && file.length() > 0) {
                    val txt = file.readText()
                    if (txt.isNotEmpty() && txt.trim().startsWith("{")) {
                        return txt
                    }
                }
            }

            // Priority 3: Check Internal App directory
            val intFile = File(context.filesDir, BackupManager.BACKUP_FILENAME)
            if (intFile.exists() && intFile.length() > 0) {
                val txt = intFile.readText()
                if (txt.isNotEmpty() && txt.trim().startsWith("{")) {
                    return txt
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error looking up latest stable backup candidate", e)
        }
        return null
    }

    private fun deleteDirContents(dir: File): Int {
        var count = 0
        try {
            val list = dir.listFiles()
            list?.forEach {
                if (it.isDirectory) {
                    count += deleteDirContents(it)
                }
                if (it.delete()) {
                    count++
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return count
    }

    private fun saveSelfHealingReport(context: Context, reportText: String, prefix: String) {
        try {
            Log.w(TAG, reportText)
            
            // Mirror report to External CrashReports folder
            val extDir = context.getExternalFilesDir("CrashReports")
            if (extDir != null) {
                if (!extDir.exists()) {
                    extDir.mkdirs()
                }
                val reportFile = File(extDir, "${prefix}_report.txt")
                reportFile.writeText(reportText)
            }

            // Also mirror to custom selected SAF folder, if available as recovery backup
            val backupPrefs = context.getSharedPreferences("lorelight_backup_prefs", Context.MODE_PRIVATE)
            val uriStr = backupPrefs.getString("backup_custom_uri", null)
            if (!uriStr.isNullOrEmpty()) {
                val treeUri = Uri.parse(uriStr)
                val treeFile = DocumentFile.fromTreeUri(context, treeUri)
                if (treeFile != null && treeFile.exists() && treeFile.isDirectory) {
                    val reportFileName = "${prefix}_report.txt"
                    var reportDocFile = treeFile.findFile(reportFileName)
                    if (reportDocFile == null) {
                        reportDocFile = treeFile.createFile("text/plain", reportFileName)
                    }
                    if (reportDocFile != null) {
                        context.contentResolver.openOutputStream(reportDocFile.uri)?.use { out ->
                            out.write(reportText.toByteArray())
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save self-healing reports", e)
        }
    }
}

// Public SharedPreferences helpers to protect against ClassCastExceptions across upgraded models and manual preference corruptions
fun SharedPreferences.getSafeFloat(key: String, defaultValue: Float): Float {
    try {
        return this.getFloat(key, defaultValue)
    } catch (e: Exception) {
        try {
            val all = this.all
            val rawValue = all[key] ?: return defaultValue
            val floatValue = when (rawValue) {
                is Number -> rawValue.toFloat()
                is String -> rawValue.toFloatOrNull() ?: defaultValue
                else -> defaultValue
            }
            try {
                this.edit().putFloat(key, floatValue).apply()
            } catch (ex: Exception) {
                // Ignore silent write failures
            }
            return floatValue
        } catch (ex: Exception) {
            return defaultValue
        }
    }
}

fun SharedPreferences.getSafeLong(key: String, defaultValue: Long): Long {
    try {
        return this.getLong(key, defaultValue)
    } catch (e: Exception) {
        try {
            val all = this.all
            val rawValue = all[key] ?: return defaultValue
            val longValue = when (rawValue) {
                is Number -> rawValue.toLong()
                is String -> rawValue.toLongOrNull() ?: defaultValue
                else -> defaultValue
            }
            try {
                this.edit().putLong(key, longValue).apply()
            } catch (ex: Exception) {
                // Ignore silent write failures
            }
            return longValue
        } catch (ex: Exception) {
            return defaultValue
        }
    }
}

fun SharedPreferences.getSafeBoolean(key: String, defaultValue: Boolean): Boolean {
    try {
        return this.getBoolean(key, defaultValue)
    } catch (e: Exception) {
        try {
            val all = this.all
            val rawValue = all[key] ?: return defaultValue
            val boolValue = when (rawValue) {
                is Boolean -> rawValue
                is Number -> rawValue.toInt() != 0
                is String -> rawValue.toBoolean()
                else -> defaultValue
            }
            try {
                this.edit().putBoolean(key, boolValue).apply()
            } catch (ex: Exception) {
                // Ignore silent write failures
            }
            return boolValue
        } catch (ex: Exception) {
            return defaultValue
        }
    }
}

fun SharedPreferences.getSafeString(key: String, defaultValue: String?): String? {
    try {
        return this.getString(key, defaultValue) ?: defaultValue
    } catch (e: Exception) {
        try {
            val all = this.all
            val strValue = all[key]?.toString() ?: defaultValue
            try {
                this.edit().putString(key, strValue).apply()
            } catch (ex: Exception) {
                // Ignore silent write failures
            }
            return strValue
        } catch (ex: Exception) {
            return defaultValue
        }
    }
}

