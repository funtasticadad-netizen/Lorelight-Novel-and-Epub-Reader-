package com.example.lorelight

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch

@Composable
fun CrawlerScreen(onClose: () -> Unit, onAddNovel: (Novel) -> Unit) {
    val context = LocalContext.current
    var url by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var previewNovel by remember { mutableStateOf<CrawledNovel?>(null) }
    
    val coroutineScope = rememberCoroutineScope()
    
    val cyanAccent = Color(0xFF00E5FF)
    val purpleAccent = Color(0xFFD500F9)
    val bgGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF0D0B14), Color(0xFF141021))
    )

    Box(modifier = Modifier.fillMaxSize().background(bgGradient).clickable(enabled = false) {}) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha=0.1f))
                ) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Close", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    "NovelCrawler",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Input Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1E1933))
                    .border(
                        1.dp,
                        Brush.horizontalGradient(listOf(cyanAccent, purpleAccent)),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(16.dp)
            ) {
                Column {
                     Text(
                        "ENTER NOVEL URL",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = purpleAccent,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    BasicTextField(
                        value = url,
                        onValueChange = { url = it },
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                        cursorBrush = SolidColor(cyanAccent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha=0.05f))
                            .padding(12.dp),
                        decorationBox = { innerTextField ->
                            if (url.isEmpty()) {
                                Text("https://example.com/novel...", color = Color.White.copy(0.3f), fontSize = 16.sp)
                            }
                            innerTextField()
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = {
                            if (url.isBlank()) return@Button
                            coroutineScope.launch {
                                isLoading = true
                                error = null
                                previewNovel = null
                                try {
                                    if (CrawlerEngine.isProbablyHomepageUrl(url)) {
                                        throw Exception("This URL points to a website homepage or listing. Please browse to a specific novel details/landing page first!")
                                    }
                                    val parsed = CrawlerEngine.analyzeUrl(url)
                                    if (parsed.chapters.isEmpty()) {
                                        throw Exception("No chapter list found on this page. Please make sure you are on a specific novel details page.")
                                    }
                                    previewNovel = parsed
                                } catch (e: Exception) {
                                    error = e.localizedMessage ?: "Failed to crawl novel."
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = cyanAccent),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                        } else {
                            Text("ANALYZE URL", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            
            if (error != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = error!!,
                    color = Color.Red,
                    modifier = Modifier.padding(16.dp)
                )
            }
            
            // Preview
            AnimatedVisibility(
                visible = previewNovel != null,
                enter = fadeIn() + expandVertically()
            ) {
                previewNovel?.let { novel ->
                    Column(modifier = Modifier.padding(top = 24.dp)) {
                        
                        Row(modifier = Modifier.fillMaxWidth()) {
                            NovelCoverImage(
                                coverBase64 = novel.coverUrl,
                                title = novel.title,
                                modifier = Modifier
                                    .width(120.dp)
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(0.1f))
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = novel.title,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "By ${novel.author}",
                                    fontSize = 14.sp,
                                    color = Color.White.copy(alpha=0.7f)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Status: ${novel.status}",
                                    fontSize = 13.sp,
                                    color = Color.White.copy(alpha=0.6f)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "${novel.chapters.size} Chapters",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = purpleAccent
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                if (novel.genres.isNotEmpty()) {
                                    Text(
                                        text = novel.genres.joinToString(", "),
                                        fontSize = 12.sp,
                                        color = cyanAccent,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))

                        var startChapterIdx by remember { mutableStateOf(0) }
                        var endChapterIdx by remember { mutableIntStateOf((novel.chapters.size - 1).coerceAtLeast(0)) }

                        var showStartDialog by remember { mutableStateOf(false) }
                        var showEndDialog by remember { mutableStateOf(false) }

                        val startText = novel.chapters.getOrNull(startChapterIdx)?.title ?: "Start"
                        val endText = novel.chapters.getOrNull(endChapterIdx)?.title ?: "End"

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            OutlinedButton(
                                onClick = { showStartDialog = true },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Text(startText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(
                                onClick = { showEndDialog = true },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Text(endText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }

                        if (showStartDialog) {
                            ChapterSelectionDialog(
                                chapters = novel.chapters,
                                onDismiss = { showStartDialog = false },
                                onChapterSelected = { startChapterIdx = it }
                            )
                        }

                        if (showEndDialog) {
                            ChapterSelectionDialog(
                                chapters = novel.chapters,
                                onDismiss = { showEndDialog = false },
                                onChapterSelected = { endChapterIdx = it }
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Button(
                            onClick = {
                                if (startChapterIdx > endChapterIdx || novel.chapters.isEmpty()) return@Button
                                val selectedChapters = novel.chapters.subList(startChapterIdx, endChapterIdx + 1)
                                val cUrls = selectedChapters.map { it.url }
                                val cNames = selectedChapters.map { it.title }
                                
                                val newNovel = Novel(
                                    title = novel.title,
                                    currentChapter = cNames.firstOrNull() ?: "Chapter 1",
                                    lastReadTimestamp = System.currentTimeMillis(),
                                    author = novel.author,
                                    status = novel.status,
                                    totalChapters = cNames.size,
                                    language = "English",
                                    genres = novel.genres.takeIf { it.isNotEmpty() } ?: listOf("Online", "Crawler"),
                                    description = novel.description,
                                    chapters = cNames.ifEmpty { listOf("Chapter 1") },
                                    coverImageBase64 = novel.coverUrl,
                                    isOnline = true,
                                    sourceUrl = novel.sourceUrl,
                                    chapterUrls = cUrls
                                )
                                onAddNovel(newNovel)
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = cyanAccent),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ADD TO LIBRARY", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        Text(
                            "CHAPTER LIST",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = cyanAccent,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        var previewIndex by remember { mutableStateOf<Int?>(null) }
                        var chapterContent by remember { mutableStateOf<String?>(null) }
                        var loadingPreview by remember { mutableStateOf(false) }

                        // Limited chapter list shown inline
                        val displayChapters = novel.chapters.take(20)
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(0.05f))
                        ) {
                            displayChapters.forEachIndexed { idx, ch ->
                                val isPreviewing = previewIndex == idx
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { 
                                                if (isPreviewing) {
                                                    previewIndex = null
                                                } else {
                                                    previewIndex = idx
                                                    loadingPreview = true
                                                    chapterContent = null
                                                    coroutineScope.launch {
                                                        try {
                                                            chapterContent = CrawlerEngine.extractChapterText(context, ch.url)
                                                        } catch(e:Exception) {
                                                            chapterContent = "Error: ${e.message}"
                                                        }
                                                        loadingPreview = false
                                                    }
                                                }
                                            }
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(ch.title, color = Color.White, modifier = Modifier.weight(1f))
                                        Icon(
                                            if (isPreviewing) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = Color.White.copy(0.5f)
                                        )
                                    }
                                    
                                    AnimatedVisibility(visible = isPreviewing) {
                                        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                                            if (loadingPreview) {
                                                CircularProgressIndicator(color = cyanAccent, modifier = Modifier.align(Alignment.Center).size(24.dp))
                                            } else {
                                                Text(
                                                    text = chapterContent ?: "",
                                                    color = Color.White.copy(0.8f),
                                                    fontSize = 14.sp
                                                )
                                            }
                                        }
                                    }
                                    
                                    if (idx < displayChapters.size - 1) {
                                        HorizontalDivider(color = Color.White.copy(0.1f))
                                    }
                                }
                            }
                            if (novel.chapters.size > 20) {
                                HorizontalDivider(color = Color.White.copy(0.1f))
                                Text(
                                    text = "... and ${novel.chapters.size - 20} more chapters",
                                    color = Color.White.copy(0.5f),
                                    modifier = Modifier.padding(16.dp),
                                    fontSize = 14.sp
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(48.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ChapterSelectionDialog(
    chapters: List<CrawledChapter>,
    onDismiss: () -> Unit,
    onChapterSelected: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Chapter", color = Color.White) },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                items(chapters.size) { index ->
                    val ch = chapters[index]
                    Text(
                        text = ch.title,
                        color = Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onChapterSelected(index)
                                onDismiss()
                            }
                            .padding(16.dp)
                    )
                    HorizontalDivider(color = Color.White.copy(0.1f))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color(0xFF00E5FF))
            }
        },
        containerColor = Color(0xFF1E1933)
    )
}
