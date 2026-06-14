package com.example.lorelight

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupManager {
    private const val TAG = "BackupManager"
    private const val PREFS_NAME = "lorelight_backup_prefs"
    private const val KEY_LAST_BACKUP = "last_auto_backup_time"
    
    const val KEY_LOCATION_TYPE = "backup_location_type" // "external", "internal", "custom"
    const val KEY_CUSTOM_URI = "backup_custom_uri"
    const val KEY_CUSTOM_PATH_NAME = "backup_custom_path_name"
    
    const val BACKUP_FILENAME = "lorelight_backup.json"

    fun init(context: Context) {
        // Trigger auto backup check and rolling backups cleanup in a background thread to keep startup completely fast
        Thread {
            try {
                cleanOldRollingBackups(context)
                performAutoBackupIfNeeded(context)
            } catch (e: Exception) {
                Log.e(TAG, "Error in automatic daily backup check", e)
            }
        }.start()
    }

    private fun cleanOldRollingBackups(context: Context) {
        try {
            // Clean external rolling backups
            val extDir = context.getExternalFilesDir("AutoBackups")
            if (extDir != null && extDir.exists()) {
                extDir.listFiles()?.forEach { file ->
                    if (file.name.startsWith("backup_") && file.name.endsWith(".json") && file.name != BACKUP_FILENAME) {
                        file.delete()
                    }
                }
            }
            
            // Clean internal rolling backups
            val intDir = context.filesDir
            if (intDir.exists()) {
                intDir.listFiles()?.forEach { file ->
                    if (file.name.startsWith("backup_") && file.name.endsWith(".json") && file.name != BACKUP_FILENAME) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning old rolling backups", e)
        }
    }

    private fun performAutoBackupIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastBackupTime = prefs.getLong(KEY_LAST_BACKUP, 0L)
        val currentTime = System.currentTimeMillis()
        
        // 24 hours in milliseconds
        val oneDayMillis = 86400000L
        if (currentTime - lastBackupTime >= oneDayMillis) {
            Log.d(TAG, "Triggering automatic daily backup...")
            val success = createBackup(context)
            if (success) {
                prefs.edit().putLong(KEY_LAST_BACKUP, currentTime).commit()
                Log.d(TAG, "Automatic daily backup generated successfully.")
            } else {
                Log.e(TAG, "Automatic daily backup failed.")
            }
        } else {
            val minsDiff = (currentTime - lastBackupTime) / 60000
            Log.d(TAG, "Auto-backup skipped: Last auto-backup was $minsDiff minutes ago (less than 24 hours).")
        }
    }

    fun createBackup(context: Context): Boolean {
        return try {
            // Uses generateBackupJson defined in SettingsScreen
            val backupJson = generateBackupJson(context)
            if (backupJson.isEmpty()) {
                Log.e(TAG, "Generated backup JSON is empty.")
                return false
            }

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val type = prefs.getString(KEY_LOCATION_TYPE, "external") ?: "external"

            when (type) {
                "internal" -> {
                    val file = File(context.filesDir, BACKUP_FILENAME)
                    file.writeText(backupJson)
                    Log.d(TAG, "Successfully updated single internal backup file.")
                }
                "custom" -> {
                    val uriStr = prefs.getString(KEY_CUSTOM_URI, null)
                    if (!uriStr.isNullOrEmpty()) {
                        try {
                            val treeUri = Uri.parse(uriStr)
                            val treeFile = DocumentFile.fromTreeUri(context, treeUri)
                            if (treeFile != null && treeFile.exists() && treeFile.isDirectory) {
                                var backupDocFile = treeFile.findFile(BACKUP_FILENAME)
                                if (backupDocFile == null) {
                                    backupDocFile = treeFile.createFile("application/json", BACKUP_FILENAME)
                                }
                                if (backupDocFile != null) {
                                    context.contentResolver.openOutputStream(backupDocFile.uri)?.use { out ->
                                        out.write(backupJson.toByteArray())
                                    }
                                    Log.d(TAG, "Successfully updated single custom folder backup file.")
                                } else {
                                    throw Exception("Could not create backup file in custom directory.")
                                }
                            } else {
                                throw Exception("Selected custom folder is no longer accessible or exists.")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to write to custom SAF folder, writing to external app storage as backup copy", e)
                            writeToExternal(context, backupJson)
                        }
                    } else {
                        writeToExternal(context, backupJson)
                    }
                }
                else -> { // "external"
                    writeToExternal(context, backupJson)
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write automatic backup file", e)
            false
        }
    }

    private fun writeToExternal(context: Context, backupJson: String) {
        val extDir = context.getExternalFilesDir("AutoBackups")
        if (extDir != null) {
            if (!extDir.exists()) {
                extDir.mkdirs()
            }
            val extBackupFile = File(extDir, BACKUP_FILENAME)
            extBackupFile.writeText(backupJson)
            Log.d(TAG, "Successfully updated single external backup file.")
        }
    }

    fun getSingleBackupInfo(context: Context): BackupInfo? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val type = prefs.getString(KEY_LOCATION_TYPE, "external") ?: "external"
        
        return when (type) {
            "internal" -> {
                val file = File(context.filesDir, BACKUP_FILENAME)
                if (file.exists()) {
                    BackupInfo(
                        name = BACKUP_FILENAME,
                        locationType = "internal",
                        locationName = "Internal Private App Storage",
                        dateFormatted = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(file.lastModified())),
                        sizeFormatted = formatFileSize(file.length()),
                        file = file,
                        uri = null
                    )
                } else null
            }
            "custom" -> {
                val uriStr = prefs.getString(KEY_CUSTOM_URI, null)
                val pathName = prefs.getString(KEY_CUSTOM_PATH_NAME, "Custom Folder") ?: "Custom Folder"
                if (!uriStr.isNullOrEmpty()) {
                    try {
                        val treeUri = Uri.parse(uriStr)
                        val treeFile = DocumentFile.fromTreeUri(context, treeUri)
                        val backupDocFile = treeFile?.findFile(BACKUP_FILENAME)
                        if (backupDocFile != null && backupDocFile.exists()) {
                            BackupInfo(
                                name = BACKUP_FILENAME,
                                locationType = "custom",
                                locationName = "Custom Folder: $pathName",
                                dateFormatted = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(backupDocFile.lastModified())),
                                sizeFormatted = formatFileSize(backupDocFile.length()),
                                file = null,
                                uri = backupDocFile.uri
                            )
                        } else null
                    } catch (e: Exception) {
                        null
                    }
                } else null
            }
            else -> { // "external"
                val file = File(context.getExternalFilesDir("AutoBackups"), BACKUP_FILENAME)
                if (file.exists()) {
                    BackupInfo(
                        name = BACKUP_FILENAME,
                        locationType = "external",
                        locationName = "External App Storage (Android/data/...)",
                        dateFormatted = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(file.lastModified())),
                        sizeFormatted = formatFileSize(file.length()),
                        file = file,
                        uri = null
                    )
                } else null
            }
        }
    }

    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", size / 1024.0)
            else -> String.format(Locale.US, "%.1f MB", size / (1024.0 * 1024.0))
        }
    }
}

data class BackupInfo(
    val name: String,
    val locationType: String,
    val locationName: String,
    val dateFormatted: String,
    val sizeFormatted: String,
    val file: File?,
    val uri: Uri?
)
