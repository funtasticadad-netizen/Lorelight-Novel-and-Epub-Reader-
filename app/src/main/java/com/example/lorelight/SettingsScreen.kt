package com.example.lorelight

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CacheStats(val fileCount: Int, val totalSizeFormatted: String, val sizeInBytes: Long)

fun getCacheStats(context: Context): CacheStats {
    val cacheDir = File(context.filesDir, "chapters_cache")
    if (!cacheDir.exists() || !cacheDir.isDirectory) {
        return CacheStats(0, "0 B", 0L)
    }
    val files = cacheDir.listFiles() ?: return CacheStats(0, "0 B", 0L)
    val txtFiles = files.filter { it.isFile && it.name.endsWith(".txt") }
    val count = txtFiles.size
    val totalBytes = txtFiles.sumOf { it.length() }
    
    val formattedSize = when {
        totalBytes < 1024 -> "$totalBytes B"
        totalBytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", totalBytes / 1024.0)
        else -> String.format(Locale.US, "%.1f MB", totalBytes / (1024.0 * 1024.0))
    }
    return CacheStats(count, formattedSize, totalBytes)
}

fun deleteChaptersCache(context: Context): Boolean {
    return try {
        val cacheDir = File(context.filesDir, "chapters_cache")
        if (cacheDir.exists() && cacheDir.isDirectory) {
            val files = cacheDir.listFiles()
            if (files != null) {
                for (file in files) {
                    if (file.isFile && file.name.endsWith(".txt")) {
                        file.delete()
                    }
                }
            }
        }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

fun generateBackupJson(context: Context): String {
    val backupObj = JSONObject()
    
    // 1. SharedPreferences: lorelight_settings
    val lorePrefs = context.getSharedPreferences("lorelight_settings", Context.MODE_PRIVATE)
    val savedNovels = lorePrefs.getString("saved_novels", "[]")
    val savedWebsites = lorePrefs.getString("saved_websites", "[]")
    backupObj.put("saved_novels", savedNovels)
    backupObj.put("saved_websites", savedWebsites)
    
    // 2. SharedPreferences: reader_text_filters
    val filterPrefs = context.getSharedPreferences("reader_text_filters", Context.MODE_PRIVATE)
    val rules = filterPrefs.getString("rules", "[]")
    backupObj.put("reader_text_filters_rules", rules)
    
    // 3. SharedPreferences: reader_prefs
    val readerPrefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    val readerPrefsObj = JSONObject()
    val allReaderPrefs = readerPrefs.all
    allReaderPrefs.forEach { (key, value) ->
        readerPrefsObj.put(key, value)
    }
    backupObj.put("reader_prefs", readerPrefsObj)
    
    // Metadata
    backupObj.put("backup_timestamp", System.currentTimeMillis())
    backupObj.put("app_version", "1.0.0")
    
    return backupObj.toString()
}

fun restoreBackupJson(context: Context, backupStr: String): Boolean {
    try {
        val backupObj = JSONObject(backupStr)
        
        // 1. SharedPreferences: lorelight_settings
        val lorePrefs = context.getSharedPreferences("lorelight_settings", Context.MODE_PRIVATE)
        val currentSavedNovelsStr = lorePrefs.getString("saved_novels", "[]") ?: "[]"
        val currentSavedWebsitesStr = lorePrefs.getString("saved_websites", "[]") ?: "[]"
        
        val backupSavedNovelsStr = backupObj.optString("saved_novels", "[]")
        val backupSavedWebsitesStr = backupObj.optString("saved_websites", "[]")
        
        // Quick structural validation of backup input
        try {
            JSONArray(backupSavedNovelsStr)
        } catch (e: Exception) {
            return false
        }
        
        // Merge Saved Novels without overwriting/deleting any current ones in the user library
        val mergedNovels = JSONArray()
        val existingNovelTitles = mutableSetOf<String>()
        
        val currentNovelsArr = JSONArray(currentSavedNovelsStr)
        for (i in 0 until currentNovelsArr.length()) {
            val novelObj = currentNovelsArr.getJSONObject(i)
            val title = novelObj.optString("title", "")
            if (title.isNotEmpty()) {
                existingNovelTitles.add(title)
            }
            mergedNovels.put(novelObj)
        }
        
        val backupNovelsArr = JSONArray(backupSavedNovelsStr)
        for (i in 0 until backupNovelsArr.length()) {
            val novelObj = backupNovelsArr.getJSONObject(i)
            val title = novelObj.optString("title", "")
            if (title.isNotEmpty() && !existingNovelTitles.contains(title)) {
                mergedNovels.put(novelObj)
                existingNovelTitles.add(title)
            }
        }
        
        // Merge Saved Websites
        val mergedWebsites = JSONArray()
        val existingWebsitesUrls = mutableSetOf<String>()
        
        val currentWebsitesArr = JSONArray(currentSavedWebsitesStr)
        for (i in 0 until currentWebsitesArr.length()) {
            val webObj = currentWebsitesArr.getJSONObject(i)
            val url = webObj.optString("url", "")
            if (url.isNotEmpty()) {
                existingWebsitesUrls.add(url)
            }
            mergedWebsites.put(webObj)
        }
        
        val backupWebsitesArr = JSONArray(backupSavedWebsitesStr)
        for (i in 0 until backupWebsitesArr.length()) {
            val webObj = backupWebsitesArr.getJSONObject(i)
            val url = webObj.optString("url", "")
            if (url.isNotEmpty() && !existingWebsitesUrls.contains(url)) {
                mergedWebsites.put(webObj)
                existingWebsitesUrls.add(url)
            }
        }
        
        lorePrefs.edit()
            .putString("saved_novels", mergedNovels.toString())
            .putString("saved_websites", mergedWebsites.toString())
            .commit()
            
        // 2. SharedPreferences: reader_text_filters (Merge unique rules based on rule ID)
        val filterPrefs = context.getSharedPreferences("reader_text_filters", Context.MODE_PRIVATE)
        val currentRulesStr = filterPrefs.getString("rules", "[]") ?: "[]"
        val backupRulesStr = backupObj.optString("reader_text_filters_rules", "[]")
        
        val mergedRules = JSONArray()
        val existingRuleIds = mutableSetOf<String>()
        
        val currentRulesArr = JSONArray(currentRulesStr)
        for (i in 0 until currentRulesArr.length()) {
            val ruleObj = currentRulesArr.getJSONObject(i)
            val id = ruleObj.optString("id", "")
            if (id.isNotEmpty()) {
                existingRuleIds.add(id)
            }
            mergedRules.put(ruleObj)
        }
        
        val backupRulesArr = JSONArray(backupRulesStr)
        for (i in 0 until backupRulesArr.length()) {
            val ruleObj = backupRulesArr.getJSONObject(i)
            val id = ruleObj.optString("id", "")
            if (id.isNotEmpty() && !existingRuleIds.contains(id)) {
                mergedRules.put(ruleObj)
                existingRuleIds.add(id)
            }
        }
        filterPrefs.edit().putString("rules", mergedRules.toString()).commit()
        
        // 3. SharedPreferences: reader_prefs (Safely overlay settings from backup)
        val readerPrefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
        val readerPrefsObj = backupObj.optJSONObject("reader_prefs")
        val edit = readerPrefs.edit()
        if (readerPrefsObj != null) {
            val keys = readerPrefsObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = readerPrefsObj.get(key)
                when (value) {
                    is Boolean -> edit.putBoolean(key, value)
                    is Float -> edit.putFloat(key, value)
                    is Int -> edit.putInt(key, value)
                    is Long -> edit.putLong(key, value)
                    is String -> edit.putString(key, value)
                    is Double -> edit.putFloat(key, value.toFloat())
                }
            }
        }
        edit.commit()
        
        // 4. Chapters Cache Files (Write backup caches if they exist, never overwriting local ones)
        val cacheObj = backupObj.optJSONObject("chapters_cache")
        if (cacheObj != null) {
            val cacheDir = File(context.filesDir, "chapters_cache")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val keys = cacheObj.keys()
            while (keys.hasNext()) {
                val fileName = keys.next()
                if (fileName.endsWith(".txt")) {
                    val file = File(cacheDir, fileName)
                    if (!file.exists()) {
                        try {
                            val content = cacheObj.getString(fileName)
                            file.writeText(content)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
        return true
    } catch (e: Exception) {
        e.printStackTrace()
        return false
    }
}

@Composable
fun SettingsScreen(
    importedNovels: List<Novel>,
    websites: List<Website>,
    readerViewModel: ReaderViewModel,
    onRestoreSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var cacheStats by remember { mutableStateOf(getCacheStats(context)) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var isOperating by remember { mutableStateOf(false) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var pendingRestoreContent by remember { mutableStateOf("") }
    
    // Auto Backups & Crash Reports states
    var singleBackupInfo by remember { mutableStateOf(BackupManager.getSingleBackupInfo(context)) }
    var crashReports by remember { mutableStateOf(CrashReporter.getCrashReports(context)) }
    var viewingCrashReport by remember { mutableStateOf<CrashReport?>(null) }
    var showAutoRestoreConfirmDialog by remember { mutableStateOf<BackupInfo?>(null) }

    val backupPrefs = remember { context.getSharedPreferences("lorelight_backup_prefs", Context.MODE_PRIVATE) }
    var backupLocationType by remember { mutableStateOf(backupPrefs.getString(BackupManager.KEY_LOCATION_TYPE, "external") ?: "external") }
    var customFolderPathName by remember { mutableStateOf(backupPrefs.getString(BackupManager.KEY_CUSTOM_PATH_NAME, "No Folder Selected") ?: "No Folder Selected") }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                
                val doc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
                val folderName = doc?.name ?: "Custom Folder"
                
                backupPrefs.edit().apply {
                    putString(BackupManager.KEY_LOCATION_TYPE, "custom")
                    putString(BackupManager.KEY_CUSTOM_URI, uri.toString())
                    putString(BackupManager.KEY_CUSTOM_PATH_NAME, folderName)
                }.commit()
                
                backupLocationType = "custom"
                customFolderPathName = folderName
                
                val success = BackupManager.createBackup(context)
                singleBackupInfo = BackupManager.getSingleBackupInfo(context)
                if (success) {
                    Toast.makeText(context, "Location changed & backup created successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Location updated.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Failed to persist folder permission: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    // Backup Launcher (Saves JSON document)
    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            isOperating = true
            coroutineScope.launch {
                val success = withContext(Dispatchers.IO) {
                    try {
                        val backupText = generateBackupJson(context)
                        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                            outputStream.write(backupText.toByteArray())
                            outputStream.flush()
                        }
                        true
                    } catch (e: Exception) {
                        e.printStackTrace()
                        false
                    }
                }
                isOperating = false
                if (success) {
                    Toast.makeText(context, "Backup exported successfully!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Failed to export backup.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    // Restore Launcher (Opens JSON document)
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isOperating = true
            coroutineScope.launch {
                val fileContent = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            inputStream.reader().readText()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    }
                }
                isOperating = false
                if (!fileContent.isNullOrEmpty()) {
                    pendingRestoreContent = fileContent
                    showRestoreConfirmDialog = true
                } else {
                    Toast.makeText(context, "Failed to read backup file.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    LaunchedEffect(importedNovels) {
        cacheStats = getCacheStats(context)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp)
        ) {
            // Screen title & subtitle
            item {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        text = "Lorelight Settings",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Manage your data, offline storage, and app backup",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            
            // App State / Data Stats Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    border = borderIndicator(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "CURRENT DATA SUMMARY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatItem(
                                label = "Novels",
                                value = importedNovels.size.toString(),
                                modifier = Modifier.weight(1f)
                            )
                            StatItem(
                                label = "Websites",
                                value = websites.size.toString(),
                                modifier = Modifier.weight(1f)
                            )
                            StatItem(
                                label = "Offline Cache",
                                value = cacheStats.totalSizeFormatted,
                                subtext = "${cacheStats.fileCount} chapters",
                                modifier = Modifier.weight(1.2f)
                            )
                        }
                    }
                }
            }
            
            // Appearance & Theme Options
            item {
                val activeTheme by readerViewModel.appTheme.collectAsState()

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "APPEARANCE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                    )
                    
                    // Selected Theme Card with visual cue and chooser
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        border = borderIndicator(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Palette,
                                        contentDescription = "Theme",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Active Theme",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = when (activeTheme) {
                                            "Golden Oak" -> "Golden Oak (Amber Dust & Warm Woods)"
                                            "Satin Pink" -> "Satin Pink (Velvet Plum & Pastel Rose)"
                                            "Midnight Black" -> "Midnight Black (Pure Dark & Cool Chrome)"
                                            "Ocean Blue" -> "Ocean Blue (Marine Cyan & Deep Sea)"
                                            else -> "Forest Green (Lush Wood & Pines)"
                                        },
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (activeTheme) {
                                                "Golden Oak" -> Color(0xFFD4AF37)
                                                "Satin Pink" -> Color(0xFFFFB0D0)
                                                "Midnight Black" -> Color(0xFFE5E5E5)
                                                "Ocean Blue" -> Color(0xFF6ABFFF)
                                                else -> Color(0xFF2E7D32)
                                            }
                                        ) // Golden, pink, black or green indicator accent
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Custom Selectable Theme Option Buttons (Organised for readability across compact & wide displays)
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Forest Green Option
                                    val isForest = activeTheme != "Golden Oak" && activeTheme != "Satin Pink" && activeTheme != "Midnight Black" && activeTheme != "Ocean Blue"
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isForest) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                            .border(
                                                1.dp,
                                                if (isForest) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                readerViewModel.appTheme.value = "Forest Green"
                                                // Reset highlight colors
                                                readerViewModel.readerHighlightColor.value = 0xFF2E7D32L
                                                readerViewModel.customHighlightColor.value = 0xFF35A048L
                                            }
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF2E7D32))
                                            )
                                            Text(
                                                text = "Forest Green",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isForest) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f)
                                            )
                                        }
                                    }

                                    // Golden Oak Option
                                    val isGolden = activeTheme == "Golden Oak"
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .then(
                                                if (isGolden) {
                                                    Modifier.background(RefinedGoldGradient)
                                                } else {
                                                    Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                                }
                                            )
                                            .border(
                                                1.dp,
                                                if (isGolden) Color.Transparent else Color.Transparent,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                readerViewModel.appTheme.value = "Golden Oak"
                                                // Set gold-themed reader highlight defaults
                                                readerViewModel.readerHighlightColor.value = 0xFFE2A942L
                                                readerViewModel.customHighlightColor.value = 0xFFE5A93CL
                                            }
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isGolden) Color(0xFF1E1405) else Color(0xFFD4AF37))
                                            )
                                            Text(
                                                text = "Golden",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isGolden) Color(0xFF1E1405) else Color.White.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Satin Pink Option
                                    val isPink = activeTheme == "Satin Pink"
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .then(
                                                if (isPink) {
                                                    Modifier.background(RefinedPinkGradient)
                                                } else {
                                                    Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                                }
                                            )
                                            .border(
                                                1.dp,
                                                if (isPink) Color.Transparent else Color.Transparent,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                readerViewModel.appTheme.value = "Satin Pink"
                                                // Set pink-themed reader highlight defaults
                                                readerViewModel.readerHighlightColor.value = 0xFFFF8DA9L
                                                readerViewModel.customHighlightColor.value = 0xFFFF869FL
                                            }
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isPink) Color(0xFF3B0B24) else Color(0xFFFFB0D0))
                                            )
                                            Text(
                                                text = "Satin Pink",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isPink) Color(0xFF3B0B24) else Color.White.copy(alpha = 0.7f)
                                            )
                                        }
                                    }

                                    // Midnight Black Option
                                    val isMidnight = activeTheme == "Midnight Black"
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .then(
                                                if (isMidnight) {
                                                    Modifier.background(RefinedMidnightGradient)
                                                } else {
                                                    Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                                }
                                            )
                                            .border(
                                                1.dp,
                                                if (isMidnight) Color.Transparent else Color.Transparent,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                readerViewModel.appTheme.value = "Midnight Black"
                                                // Set midnight-themed reader highlight defaults
                                                readerViewModel.readerHighlightColor.value = 0xFFFFFFFFL
                                                readerViewModel.customHighlightColor.value = 0xFFE5E5E5L
                                            }
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isMidnight) Color.Black else Color(0xFFE5E5E5))
                                            )
                                            Text(
                                                text = "Midnight",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isMidnight) Color.Black else Color.White.copy(alpha = 0.7f)
                                            )
                                        }
                                    }

                                    // Ocean Blue Option
                                    val isOcean = activeTheme == "Ocean Blue"
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .then(
                                                if (isOcean) {
                                                    Modifier.background(RefinedOceanGradient)
                                                } else {
                                                    Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                                }
                                            )
                                            .border(
                                                1.dp,
                                                if (isOcean) Color.Transparent else Color.Transparent,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                readerViewModel.appTheme.value = "Ocean Blue"
                                                // Set ocean-themed reader highlight defaults
                                                readerViewModel.readerHighlightColor.value = 0xFF76C4FFL
                                                readerViewModel.customHighlightColor.value = 0xFF6ABFFFL
                                            }
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isOcean) Color(0xFF001F3F) else Color(0xFF6ABFFF))
                                            )
                                            Text(
                                                text = "Ocean Blue",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isOcean) Color(0xFF001F3F) else Color.White.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Reader Text Options
            item {
                val fontSize by readerViewModel.readerFontSize.collectAsState()
                val lineSpacing by readerViewModel.readerLineSpacing.collectAsState()
                val paragraphSpacing by readerViewModel.readerParagraphSpacing.collectAsState()
                val textAlignment by readerViewModel.readerTextAlignment.collectAsState()
                val fontFamily by readerViewModel.readerFontFamily.collectAsState()

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "APPEARANCE - TEXT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp)
                    )

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        border = borderIndicator(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Text size slider
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Reader Text Size",
                                    fontSize = 13.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${fontSize.toInt()}sp",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Slider(
                                value = fontSize,
                                onValueChange = { readerViewModel.readerFontSize.value = it },
                                valueRange = 12f..32f,
                                colors = SliderDefaults.colors(
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Text Align
                            Text(
                                text = "Text Alignment",
                                fontSize = 13.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val alignments = listOf("Left", "Center", "Right", "Justify")
                                alignments.forEach { align ->
                                    val selected = textAlignment.equals(align, ignoreCase = true)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                            .border(
                                                1.dp,
                                                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable { readerViewModel.readerTextAlignment.value = align }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = align,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Line spacing
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Line Spacing",
                                    fontSize = 13.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = String.format(Locale.getDefault(), "%.1fx", lineSpacing),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Slider(
                                value = lineSpacing,
                                onValueChange = { readerViewModel.readerLineSpacing.value = it },
                                valueRange = 1.0f..2.5f,
                                colors = SliderDefaults.colors(
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // Paragraph spacing
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Paragraph Spacing",
                                    fontSize = 13.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${paragraphSpacing.toInt()}dp",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Slider(
                                value = paragraphSpacing,
                                onValueChange = { readerViewModel.readerParagraphSpacing.value = it },
                                valueRange = 0f..40f,
                                colors = SliderDefaults.colors(
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            )
                        }
                    }
                }
            }

            // Voice Settings Section
            item {
                val speechRate by readerViewModel.speechRate.collectAsState()
                val selectedAccent by readerViewModel.selectedAccent.collectAsState()
                val availableAccents by readerViewModel.availableAccents.collectAsState()
                
                var accentDropdownVisible by remember { mutableStateOf(false) }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "VOICE & SPEECH (TTS)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp)
                    )

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        border = borderIndicator(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Speech speed slider
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Speech Pacing / Speed",
                                    fontSize = 13.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = String.format(Locale.getDefault(), "%.1fx", speechRate),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Slider(
                                value = speechRate,
                                onValueChange = { readerViewModel.setSpeechRateAndApply(it) },
                                valueRange = 0.5f..4.0f,
                                colors = SliderDefaults.colors(
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Voice Accent Country Dropdown
                            Text(
                                text = "Speech Accent / Geography",
                                fontSize = 13.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Box(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                        .clickable { accentDropdownVisible = true }
                                        .padding(horizontal = 12.dp, vertical = 12.dp)
                                ) {
                                    Text(
                                        text = selectedAccent,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                
                                DropdownMenu(
                                    expanded = accentDropdownVisible,
                                    onDismissRequest = { accentDropdownVisible = false },
                                    modifier = Modifier
                                        .fillMaxWidth(0.85f)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    availableAccents.forEach { accent ->
                                        DropdownMenuItem(
                                            text = { Text(accent, color = Color.White, fontSize = 13.sp) },
                                            onClick = {
                                                readerViewModel.selectAccentAndApply(accent)
                                                accentDropdownVisible = false
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                            Spacer(modifier = Modifier.height(14.dp))

                            val browserDarkMode by readerViewModel.browserForceDarkMode.collectAsState()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                    .clickable { readerViewModel.setBrowserForceDarkMode(!browserDarkMode) }
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DarkMode,
                                        contentDescription = "Browser Dark Mode",
                                        tint = if (browserDarkMode) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Forced Dark Mode for Browser",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Forces web pages loaded in the browser to render using a deep dark theme CSS style.",
                                            fontSize = 11.sp,
                                            color = Color.White.copy(alpha = 0.6f),
                                            lineHeight = 14.sp
                                        )
                                    }
                                }
                                Switch(
                                    checked = browserDarkMode,
                                    onCheckedChange = { readerViewModel.setBrowserForceDarkMode(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                )
                            }
                        }
                    }
                }
            }
            
            // Backup & Restore Actions Card
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "BACKUP & MIGRATION",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                    )
                    
                    // Export Backup Option
                    SettingActionCard(
                        title = "Backup to File (Export)",
                        description = "Saves your imported novels list, current reading progress, websites, text replacement rules, and custom reading settings into a highly compact JSON file. (No giant chapter files included, making the backup extremely lightweight!).",
                        icon = Icons.Default.Backup,
                        onClick = {
                            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                            val defaultName = "lorelight_backup_${sdf.format(Date())}.json"
                            backupLauncher.launch(defaultName)
                        }
                    )
                    
                    // Import Backup Option
                    SettingActionCard(
                        title = "Restore from File (Import)",
                        description = "Imports your library, progress, websites, and settings from an exported backup file. This safely keeps your current novels and merges any new ones from the backup file.",
                        icon = Icons.Default.Restore,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        onClick = {
                            restoreLauncher.launch("application/json")
                        }
                    )

                    // Automatic Daily Backups Section
                    Text(
                        text = "AUTOMATIC DAILY BACKUPS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                    
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        border = borderIndicator(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Daily Auto-Backup",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Keeps a single daily backup file updated so you can access it via device file managers even if the app won't open.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                                Button(
                                    onClick = {
                                        val runSuccess = BackupManager.createBackup(context)
                                        if (runSuccess) {
                                            singleBackupInfo = BackupManager.getSingleBackupInfo(context)
                                            Toast.makeText(context, "Backup file updated successfully!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Failed to update backup file.", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Backup Now", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Storage location configuration
                            Text(
                                text = "SELECT STORAGE LOCATION",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 0.5.sp
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // 3 Location chips/radio buttons
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                // 1. External Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (backupLocationType == "external") MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                                        .border(1.dp, if (backupLocationType == "external") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                        .clickable {
                                            backupPrefs.edit().putString(BackupManager.KEY_LOCATION_TYPE, "external").commit()
                                            backupLocationType = "external"
                                            val success = BackupManager.createBackup(context)
                                            singleBackupInfo = BackupManager.getSingleBackupInfo(context)
                                        }
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = (backupLocationType == "external"),
                                        onClick = {
                                            backupPrefs.edit().putString(BackupManager.KEY_LOCATION_TYPE, "external").commit()
                                            backupLocationType = "external"
                                            val success = BackupManager.createBackup(context)
                                            singleBackupInfo = BackupManager.getSingleBackupInfo(context)
                                        },
                                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Column {
                                        Text("External App Folder (Recommended)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Text("Saved in Android/data/.../AutoBackups for easy MTP access.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                    }
                                }
                                
                                // 2. Internal Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (backupLocationType == "internal") MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                                        .border(1.dp, if (backupLocationType == "internal") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                        .clickable {
                                            backupPrefs.edit().putString(BackupManager.KEY_LOCATION_TYPE, "internal").commit()
                                            backupLocationType = "internal"
                                            val success = BackupManager.createBackup(context)
                                            singleBackupInfo = BackupManager.getSingleBackupInfo(context)
                                        }
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = (backupLocationType == "internal"),
                                        onClick = {
                                            backupPrefs.edit().putString(BackupManager.KEY_LOCATION_TYPE, "internal").commit()
                                            backupLocationType = "internal"
                                            val success = BackupManager.createBackup(context)
                                            singleBackupInfo = BackupManager.getSingleBackupInfo(context)
                                        },
                                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Column {
                                        Text("Internal App Space (Secure)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Text("Stored in app's private directory. Unreachable unless rooted.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                    }
                                }
                                
                                // 3. Custom Tree Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (backupLocationType == "custom") MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                                        .border(1.dp, if (backupLocationType == "custom") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                        .clickable {
                                            folderPickerLauncher.launch(null)
                                        }
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = (backupLocationType == "custom"),
                                        onClick = {
                                            folderPickerLauncher.launch(null)
                                        },
                                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Custom Shared Directory", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Text("Selected folder: $customFolderPathName", fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    OutlinedButton(
                                        onClick = { folderPickerLauncher.launch(null) },
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                        modifier = Modifier.height(28.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                    ) {
                                        Text("Select Folder", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Active backup item status!
                            Text(
                                text = "ACTIVE AUTOMATIC BACKUP FILE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                                letterSpacing = 0.5.sp
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            if (singleBackupInfo == null) {
                                Text(
                                    text = "No backup file has been initialized yet. Click 'Backup Now' to generate it.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                val info = singleBackupInfo!!
                                Card(
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.InsertDriveFile,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = info.name,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Location: ${info.locationName}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "Updated: ${info.dateFormatted} • Size: ${info.sizeFormatted}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                        }
                                        
                                        Button(
                                            onClick = {
                                                showAutoRestoreConfirmDialog = info
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                            contentPadding = PaddingValues(horizontal = 12.dp),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Restore,
                                                    contentDescription = null,
                                                    tint = Color.Black,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Restore", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Storage Maintenance Section
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "STORAGE OPTIMIZATION",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                    )
                    
                    // Clear Offline Cache Option
                    SettingActionCard(
                        title = "Clear Downloaded Offline Cache",
                        description = "Deletes all downloaded offline chapters texts saved in cache (${cacheStats.fileCount} chapters, ${cacheStats.totalSizeFormatted}). Your imported books list, sources, reading progress, and settings are NOT deleted.",
                        icon = Icons.Default.DeleteSweep,
                        iconTint = MaterialTheme.colorScheme.error,
                        onClick = {
                            if (cacheStats.fileCount > 0) {
                                showClearConfirmDialog = true
                            } else {
                                Toast.makeText(context, "No offline chapters cached yet.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }

            // Crash & Diagnostics Section
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "CRASH & DIAGNOSTICS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                    )
                    
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        border = borderIndicator(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Stored Crash Reports",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Mirrored as plain text '.txt' files to your selected custom directory or standard 'Android/data/.../files/CrashReports/' folder, making them easily retrievable externally even if the app won't start.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                                if (crashReports.isNotEmpty()) {
                                    TextButton(
                                        onClick = {
                                            CrashReporter.clearCrashReports(context)
                                            crashReports = CrashReporter.getCrashReports(context)
                                            Toast.makeText(context, "Crash history cleared.", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Text("Clear All", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                            
                            if (crashReports.isEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF66BB6A),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Stable. No crash records found.",
                                        fontSize = 12.sp,
                                        color = Color(0xFF66BB6A),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.height(8.dp))
                                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                crashReports.forEach { report ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp)
                                            .clickable { viewingCrashReport = report },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = report.message.take(65) + (if (report.message.length > 65) "..." else ""),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                            Text(
                                                text = "Time: ${report.timestamp} • Thread: ${report.thread}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                        }
                                        
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = "View Details",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Software About Credits Card
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "About",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Lorelight Web novel Reader",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Version 1.1.0 • Stable Release",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
        
        // Modal Loading Indicator during export/import processing
        if (isOperating) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Processing data...",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
        
        // Confirmation dialog for clearing cache
        if (showClearConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showClearConfirmDialog = false },
                title = { Text("Clear Offline Chapters?", fontWeight = FontWeight.Bold) },
                text = { Text("This will permanently delete ${cacheStats.fileCount} downloaded chapter texts from offline cache, freeing up ${cacheStats.totalSizeFormatted} of space. You will need internet connections to load these chapters next time. Progress is kept.") },
                confirmButton = {
                    Button(
                        onClick = {
                            showClearConfirmDialog = false
                            val done = deleteChaptersCache(context)
                            if (done) {
                                cacheStats = getCacheStats(context)
                                Toast.makeText(context, "Offline chapters cache cleared successfully!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed to clear cache.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearConfirmDialog = false }) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(20.dp)
            )
        }
        
        // Confirmation dialog for restoring backup
        if (showRestoreConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showRestoreConfirmDialog = false },
                title = { Text("Confirm Library Restore & Merge?", fontWeight = FontWeight.Bold) },
                text = { Text("This will safely merge the backup's novels, websites, custom text replacement rules, and custom settings into your current application state. Any current novels and websites will remain completely untouched.") },
                confirmButton = {
                    Button(
                        onClick = {
                            showRestoreConfirmDialog = false
                            isOperating = true
                            coroutineScope.launch {
                                val success = withContext(Dispatchers.IO) {
                                    restoreBackupJson(context, pendingRestoreContent)
                                }
                                isOperating = false
                                if (success) {
                                    onRestoreSuccess()
                                    cacheStats = getCacheStats(context)
                                    Toast.makeText(context, "Data successfully restored and merged!", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Failed to restore backup. File is corrupted or invalid.", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Restore Now", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRestoreConfirmDialog = false }) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(20.dp)
            )
        }

        // Confirmation dialog for auto backup restore
        if (showAutoRestoreConfirmDialog != null) {
            val targetInfo = showAutoRestoreConfirmDialog!!
            AlertDialog(
                onDismissRequest = { showAutoRestoreConfirmDialog = null },
                title = { Text("Restore Daily Auto-Backup?", fontWeight = FontWeight.Bold) },
                text = { Text("This will restore library progress, custom replacement rules, configured websites and client setup from the daily auto-backup updated on ${targetInfo.dateFormatted}.\n\nYour library will merge safely without losing newer additions.") },
                confirmButton = {
                    Button(
                        onClick = {
                            showAutoRestoreConfirmDialog = null
                            isOperating = true
                            coroutineScope.launch {
                                val success = withContext(Dispatchers.IO) {
                                    try {
                                        val content = if (targetInfo.file != null) {
                                            targetInfo.file.readText()
                                        } else if (targetInfo.uri != null) {
                                            context.contentResolver.openInputStream(targetInfo.uri)?.use { stream ->
                                                stream.bufferedReader().use { it.readText() }
                                            } ?: ""
                                        } else {
                                            ""
                                        }
                                        if (content.isNotEmpty()) {
                                            restoreBackupJson(context, content)
                                        } else {
                                            false
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        false
                                    }
                                }
                                isOperating = false
                                if (success) {
                                    onRestoreSuccess()
                                    cacheStats = getCacheStats(context)
                                    Toast.makeText(context, "State restored successfully from automated backup!", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Failed to restore backup file.", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Restore Now", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAutoRestoreConfirmDialog = null }) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(20.dp)
            )
        }

        // Large Dialog to view the Crash Report
        if (viewingCrashReport != null) {
            val report = viewingCrashReport!!
            val metaObj = remember(report) {
                try {
                    if (!report.metadata.isNullOrEmpty()) JSONObject(report.metadata) else null
                } catch (e: Exception) {
                    null
                }
            }
            val copyableContext = remember(report, metaObj) {
                if (metaObj != null) {
                    val logsArr = metaObj.optJSONArray("diagnostic_logs")
                    val logsStr = if (logsArr != null && logsArr.length() > 0) {
                        val sb = java.lang.StringBuilder()
                        for (i in 0 until logsArr.length()) {
                            sb.append(logsArr.optString(i)).append("\n")
                        }
                        sb.toString().trim()
                    } else {
                        "No diagnostic trace captured."
                    }

                    """
                    
                    -- ENVIRONMENT & AUDIO ENGINE CONTEXT --
                    Category: ${metaObj.optString("category", "HANDLED_ERROR")}
                    Device Model: ${metaObj.optString("device_model", "Unknown")}
                    Android OS: Android ${metaObj.optString("device_os", "N/A")} (API ${metaObj.optString("device_sdk", "0")})
                    CPU ABIs: ${metaObj.optString("cpu_abis", "N/A")}
                    Active Book: ${metaObj.optString("playback_novel_title", "None")}
                    Selected Chapter Index: ${metaObj.optString("playback_chapter_index", "N/A")}
                    Sentence Index: ${metaObj.optString("playback_sentence_index", "N/A")}
                    Sentence: ${metaObj.optString("playback_sentence_preview", "N/A")}
                    TTS Ready Status: ${metaObj.optBoolean("tts_ready", false)}
                    TTS Voice Selected: ${metaObj.optString("tts_voice", "N/A")}
                    TTS Accent Selected: ${metaObj.optString("tts_accent", "N/A")}
                    TTS Speed Rate: ${metaObj.optDouble("tts_rate", 1.0)}x
                    TTS Pitch level: ${metaObj.optDouble("tts_pitch", 1.0)}
                    
                    -- DIAGNOSTIC TRACE BUFFER --
                    $logsStr
                    """.trimIndent()
                } else {
                    ""
                }
            }

            AlertDialog(
                onDismissRequest = { viewingCrashReport = null },
                title = { 
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Crash Report Details", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        IconButton(onClick = { viewingCrashReport = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 600.dp)
                    ) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Exception: ${report.message}", 
                                        color = MaterialTheme.colorScheme.error, 
                                        fontWeight = FontWeight.Bold, 
                                        fontSize = 13.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    metaObj?.optString("category")?.let { cat ->
                                        Box(
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(text = cat, color = MaterialTheme.colorScheme.error, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "Occurred: ${report.timestamp}", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                                    Text(text = "Thread: ${report.thread}", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                                }
                            }
                        }

                        if (metaObj != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("ENVIRONMENT & RUNTIME DATA:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // Hardware / OS Row
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Device Hardware", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
                                            Text(metaObj.optString("device_model", "Unknown"), fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Android Version", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
                                            Text(
                                                "Android ${metaObj.optString("device_os", "N/A")} (API ${metaObj.optString("device_sdk", "0")})", 
                                                fontSize = 12.sp, 
                                                color = Color.White, 
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }

                                    // Active Reading State Row
                                    val novelTitle = metaObj.optString("playback_novel_title", "None")
                                    if (novelTitle != "None") {
                                        androidx.compose.material3.HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Active Book", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
                                                Text(novelTitle, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Progress", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
                                                Text(
                                                    "Ch ${metaObj.optInt("playback_chapter_index")} / Sent ${metaObj.optInt("playback_sentence_index")}", 
                                                    fontSize = 12.sp, 
                                                    color = Color.White, 
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                        
                                        metaObj.optString("playback_sentence_preview")?.let { preview ->
                                            if (preview.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Card(
                                                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.2f)),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(modifier = Modifier.padding(8.dp)) {
                                                        Text("Target speaking context text at crash moment:", fontSize = 9.sp, color = Color.White.copy(alpha = 0.5f))
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text("\"$preview\"", fontSize = 11.sp, color = Color.White, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // TTS Engine Settings Row
                                    androidx.compose.material3.HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("TTS Voice Config", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
                                            Text(metaObj.optString("tts_voice", "N/A"), fontSize = 11.sp, color = Color.White)
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("TTS Settings (Rate/Pitch)", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
                                            Text(
                                                "Rate: ${String.format("%.2fx", metaObj.optDouble("tts_rate", 1.0))} / Pitch: ${String.format("%.2f", metaObj.optDouble("tts_pitch", 1.0))}", 
                                                fontSize = 11.sp, 
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text("STACK TRACE (Monospace):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(Color.Black, shape = RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                item {
                                    Text(
                                        text = report.stackTrace,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = Color(0xFFEF9A9A),
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }

                        // Visually display Diagnostic Trace Logs list inside dialog if present
                        if (metaObj != null) {
                            val logsArr = metaObj.optJSONArray("diagnostic_logs")
                            if (logsArr != null && logsArr.length() > 0) {
                                val logText = remember(logsArr) {
                                    val sb = java.lang.StringBuilder()
                                    for (i in 0 until logsArr.length()) {
                                        sb.append(logsArr.optString(i)).append("\n")
                                    }
                                    sb.toString().trim()
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("DIAGNOSTIC LOG BUFFER (LAST 50 EVENTS):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .background(Color.Black, shape = RoundedCornerShape(8.dp))
                                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp))
                                        .padding(8.dp)
                                ) {
                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        item {
                                            Text(
                                                text = logText,
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                color = Color(0xFFA5D6A7),
                                                lineHeight = 14.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            try {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val reportString = """
                                    == LORELIGHT PREMIUM CRASH REPORT ==
                                    Timestamp: ${report.timestamp}
                                    Message: ${report.message}
                                    Cause: ${report.cause}
                                    Thread: ${report.thread}
                                    $copyableContext
                                    
                                    STACKTRACE:
                                    ${report.stackTrace}
                                """.trimIndent()
                                val clip = android.content.ClipData.newPlainText("Lorelight Premium Crash Report", reportString)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Premium crash report copied to clipboard!", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed to copy.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy Report", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewingCrashReport = null }) {
                        Text("Dismiss", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(20.dp),
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            )
        }
    }
}

@Composable
fun StatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    subtext: String? = null
) {
    Column(
        modifier = modifier.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        if (subtext != null) {
            Text(
                text = subtext,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        }
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun SettingActionCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.primary
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun borderIndicator(): BorderStroke {
    return BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
}
