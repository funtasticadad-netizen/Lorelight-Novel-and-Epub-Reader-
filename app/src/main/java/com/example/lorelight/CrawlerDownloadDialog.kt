package com.example.lorelight

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.widget.Toast
import java.io.File

enum class DownloadMode {
    SELECT_DOWNLOAD,
    SAVE_EPUB
}

@Composable
fun CrawlerDownloadDialog(
    novel: Novel,
    mode: DownloadMode,
    onDismiss: () -> Unit
) {
    if (!novel.isOnline) {
        onDismiss()
        return
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var startChapterStr by remember { mutableStateOf("1") }
    var endChapterStr by remember { mutableStateOf(novel.totalChapters.toString()) }
    
    val startIdx = startChapterStr.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val endIdx = endChapterStr.toIntOrNull()?.coerceAtMost(novel.totalChapters) ?: novel.totalChapters
    
    val estimatedChapters = (endIdx - startIdx + 1).coerceAtLeast(0)
    val estimatedKb = estimatedChapters * 15 // Roughly 15KB per chapter html

    var isProcessing by remember { mutableStateOf(false) }
    var actionProgress by remember { mutableStateOf(0f) }
    var actionStatus by remember { mutableStateOf("") }
    
    // Scan which chapters are downloaded/cached in internal memory
    val downloadedChapters = remember(novel) {
        val list = mutableListOf<CrawledChapter>()
        for (i in 0 until novel.chapters.size) {
            val title = novel.chapters[i]
            val url = novel.chapterUrls.getOrNull(i) ?: continue
            if (CrawlerEngine.isChapterCached(context, url)) {
                list.add(CrawledChapter(index = i + 1, title = title, url = url))
            }
        }
        list
    }

    val dialogTitle = when (mode) {
        DownloadMode.SELECT_DOWNLOAD -> "Select Download"
        DownloadMode.SAVE_EPUB -> "Save EPUB"
    }

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        title = {
            Text(
                text = dialogTitle,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            if (isProcessing) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(actionStatus, color = Color.White, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { actionProgress },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Column {
                    when (mode) {
                        DownloadMode.SELECT_DOWNLOAD -> {
                            Text(
                                "Download chapters directly into the app's database/cache for offline reading inside the reader.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = startChapterStr,
                                    onValueChange = { startChapterStr = it },
                                    label = { Text("Start Chapter") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                                OutlinedTextField(
                                    value = endChapterStr,
                                    onValueChange = { endChapterStr = it },
                                    label = { Text("End Chapter") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha=0.3f), RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Text("Chapters to Download: $estimatedChapters", color = Color.White, fontSize = 13.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Est. Cache Size: ~${estimatedKb} KB", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        DownloadMode.SAVE_EPUB -> {
                            Text(
                                "Generate and save an EPUB file to your mobile's Downloads folder.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha=0.3f), RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Text("App Cache Information", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Downloaded Chapters: ${downloadedChapters.size} / ${novel.totalChapters}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    if (downloadedChapters.isEmpty()) {
                                        Text(
                                            "No chapters have been downloaded to the app yet! Use 'Download whole' or 'Select download' first to store chapters offline, and then save them as an EPUB.",
                                            color = MaterialTheme.colorScheme.error,
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp
                                        )
                                    } else {
                                        val sampleNumbers = downloadedChapters.map { it.index }
                                        val summary = if (sampleNumbers.size <= 25) {
                                            sampleNumbers.joinToString(", ") { "Ch $it" }
                                        } else {
                                            val firstFew = sampleNumbers.take(15).joinToString(", ") { "Ch $it" }
                                            val lastFew = sampleNumbers.takeLast(5).joinToString(", ") { "Ch $it" }
                                            "$firstFew ... (and ${sampleNumbers.size - 20} more) ... $lastFew"
                                        }
                                        Text(
                                            "EPUB will only contain these downloaded offline chapters:\n$summary",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!isProcessing) {
                when (mode) {
                    DownloadMode.SELECT_DOWNLOAD -> {
                        Button(
                            onClick = {
                                if (estimatedChapters <= 0) return@Button
                                
                                coroutineScope.launch {
                                    isProcessing = true
                                    
                                    val targetUrls = mutableListOf<Pair<Int, String>>()
                                    val startIndex0 = startIdx - 1
                                    val endIndex0 = (endIdx - 1).coerceAtMost(novel.chapters.lastIndex)
                                    for (i in startIndex0..endIndex0) {
                                        val u = novel.chapterUrls.getOrNull(i) ?: ""
                                        if (u.isNotEmpty()) {
                                            targetUrls.add((i + 1) to u)
                                        }
                                    }
                                    
                                    val totalToDl = targetUrls.size
                                    var downloadedCount = 0
                                    
                                    for (index in 0 until totalToDl) {
                                        val (chNum, chUrl) = targetUrls[index]
                                        actionProgress = index.toFloat() / totalToDl.toFloat()
                                        actionStatus = "Downloading chapter $chNum of $endIdx..."
                                        
                                        if (!CrawlerEngine.isChapterCached(context, chUrl)) {
                                            try {
                                                CrawlerEngine.extractChapterText(context, chUrl)
                                                downloadedCount++
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                            // Exact 2 sec gap between downloads requested by user
                                            if (index < totalToDl - 1) {
                                                delay(2000L)
                                            }
                                        }
                                    }
                                    isProcessing = false
                                    Toast.makeText(context, "Successfully downloaded selected chapters to app database!", Toast.LENGTH_LONG).show()
                                    onDismiss()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Download Chapters", fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                    DownloadMode.SAVE_EPUB -> {
                        Button(
                            onClick = {
                                if (downloadedChapters.isEmpty()) return@Button
                                
                                coroutineScope.launch {
                                    isProcessing = true
                                    actionProgress = 0f
                                    actionStatus = "Assembling downloaded chapters..."
                                    
                                    val epFile = EpubGenerator.generateAndSave(
                                        context = context,
                                        novelTitle = novel.title,
                                        author = novel.author,
                                        coverUrl = novel.coverImageBase64,
                                        description = novel.description,
                                        sourceUrl = novel.sourceUrl,
                                        chapters = downloadedChapters,
                                        progressCallback = { current, total ->
                                            actionProgress = current.toFloat() / total.toFloat()
                                            actionStatus = "Assembling EPUB: Chapter $current of $total..."
                                        }
                                    )
                                    
                                    isProcessing = false
                                    if (epFile != null) {
                                        Toast.makeText(context, "Saved to Downloads: ${epFile.name}", Toast.LENGTH_LONG).show()
                                        onDismiss()
                                    } else {
                                        Toast.makeText(context, "Failed to generate EPUB.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            enabled = downloadedChapters.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Save EPUB", fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                }
            }
        },
        dismissButton = {
            if (!isProcessing) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp)
    )
}
