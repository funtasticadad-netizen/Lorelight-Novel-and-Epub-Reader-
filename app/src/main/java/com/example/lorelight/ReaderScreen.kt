package com.example.lorelight

import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font as ComposeFont
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.FlowRow

data class WordRange(val start: Int, val end: Int)
data class WordPosition(val leftAnchor: Offset, val rightAnchor: Offset)

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onClose: () -> Unit
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = remember { viewModel.activeNovel.value?.currentParagraphIndex ?: 0 },
        initialFirstVisibleItemScrollOffset = remember { viewModel.activeNovel.value?.currentScrollOffset ?: 0 }
    )

    androidx.activity.compose.BackHandler(onBack = {
        viewModel.saveDetailedProgress(
            paragraphIndex = listState.firstVisibleItemIndex,
            scrollOffset = listState.firstVisibleItemScrollOffset,
            wordIndex = viewModel.activeSentenceIndex.value
        )
        onClose()
    })
    
    val context = LocalContext.current
    val textFilterRules by viewModel.textFilterRules.collectAsState()
    var selRange by remember { mutableStateOf<WordRange?>(null) }
    var isSelectionPanelCollapsed by remember { mutableStateOf(true) }
    LaunchedEffect(selRange) {
        if (selRange != null) {
            isSelectionPanelCollapsed = true
        }
    }
    val wordPositions = remember { mutableStateMapOf<Int, WordPosition>() }
    var showTextFilterPanel by remember { mutableStateOf(false) }
    
    val activeNovel by viewModel.activeNovel.collectAsState()
    val chapters by viewModel.chapters.collectAsState()
    val activeChapterIndex by viewModel.activeChapterIndex.collectAsState()
    val activeSentenceIndex by viewModel.activeSentenceIndex.collectAsState()
    val filteredSentences by viewModel.filteredSentences.collectAsState()
    val tokensBySentence by viewModel.tokensBySentence.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val availableVoices by viewModel.availableVoices.collectAsState()
    val selectedVoice by viewModel.selectedVoice.collectAsState()
    val speechRate by viewModel.speechRate.collectAsState()

    val fontSize by viewModel.readerFontSize.collectAsState()
    val lineSpacing by viewModel.readerLineSpacing.collectAsState()
    val paragraphSpacing by viewModel.readerParagraphSpacing.collectAsState()
    val textAlignment by viewModel.readerTextAlignment.collectAsState()
    val readerBgColorRaw by viewModel.readerBgColor.collectAsState()
    val readerTextColorRaw by viewModel.readerTextColor.collectAsState()
    val readerHighlightColorRaw by viewModel.readerHighlightColor.collectAsState()
    val sleepTimerMinutes by viewModel.sleepTimerMinutes.collectAsState()

    // New configuration states
    val readerMode by viewModel.readerMode.collectAsState()
    val readerFontFamily by viewModel.readerFontFamily.collectAsState()
    val readerBrightness by viewModel.readerBrightness.collectAsState()
    val isDefaultHighlight by viewModel.isDefaultHighlight.collectAsState()
    val customHighlightColorRaw by viewModel.customHighlightColor.collectAsState()
    val selectedAccent by viewModel.selectedAccent.collectAsState()
    val availableAccents by viewModel.availableAccents.collectAsState()
    val filteredVoices by viewModel.filteredVoices.collectAsState()
    val readerBackgroundType by viewModel.readerBackgroundType.collectAsState()
    val themeState by viewModel.appTheme.collectAsState()
    val isDimmerEnabled by viewModel.isDimmerEnabled.collectAsState()
    val isNavLineAnimEnabled by viewModel.isNavLineAnimEnabled.collectAsState()

    val rawBgColor = Color(readerBgColorRaw)
    val rawTextColor = Color(readerTextColorRaw)

    val readerBgColor = if (readerBackgroundType == "AMOLED Black") Color.Black else rawBgColor
    val readerTextColor = if (readerBackgroundType == "AMOLED Black" || themeState == "Midnight Black") Color.White else rawTextColor
    val defaultHighlightColor = when (themeState) {
        "Golden Oak" -> Color(0xFFF3C77D)
        "Satin Pink" -> Color(0xFFFFB0D0)
        "Midnight Black" -> Color(0xFF888888) // Distinguished grey/dark grey highlight on Midnight Black
        "Ocean Blue" -> Color(0xFF6ABFFF)
        else -> Color(0xFF388E3C)
    }
    val readerHighlightColor = if (themeState == "Midnight Black") {
        Color(0xFF888888) // Force dark grey/grey highlight on Midnight Black theme to prevent invisible highlights
    } else if (isDefaultHighlight) {
        defaultHighlightColor
    } else {
        Color(customHighlightColorRaw)
    }

    // Dynamic Edge-to-Edge Status Bar Icon Color Controller
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        val activity = remember(context) {
            var currentContext = context
            while (currentContext is android.content.ContextWrapper) {
                if (currentContext is android.app.Activity) {
                    break
                }
                currentContext = currentContext.baseContext
            }
            currentContext as? android.app.Activity
        }
        
        activity?.let { act ->
            val isDark = (0.299f * readerBgColor.red + 0.587f * readerBgColor.green + 0.114f * readerBgColor.blue) < 0.5f
            SideEffect {
                act.window.statusBarColor = android.graphics.Color.TRANSPARENT
                androidx.core.view.WindowCompat.getInsetsController(act.window, view).isAppearanceLightStatusBars = !isDark
            }
        }
    }

    var showChapterDrawer by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showSleepTimerMenu by remember { mutableStateOf(false) }
    var showFontSelectionDialog by remember { mutableStateOf(false) }
    var showColorWheelDialog by remember { mutableStateOf(false) }
    var showFloatingStylePanel by remember { mutableStateOf(false) }

    val chaptersListState = rememberLazyListState()

    // Autoscroll premium states
    val readerPrefs = remember { context.getSharedPreferences("reader_prefs", android.content.Context.MODE_PRIVATE) }
    
    var isAutoScrollActive by remember { mutableStateOf(false) }
    var autoScrollSpeed by remember {
        mutableStateOf(readerPrefs.getSafeFloat("reader_autoscroll_speed", 30f).coerceIn(1f, 100f))
    }
    var showAutoScrollPanel by remember { mutableStateOf(false) }
    var lastTapTime by remember { mutableStateOf(0L) }
    var tapCount by remember { mutableStateOf(0) }
    var wasAutoScrollingBeforeTouch by remember { mutableStateOf(false) }

    val lineProgress = remember { Animatable(0f) }
    val lineAlpha = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val triggerNavLineAnimation = remember(viewModel) {
        {
            if (viewModel.isNavLineAnimEnabled.value) {
                coroutineScope.launch {
                    lineAlpha.snapTo(1f)
                    lineProgress.snapTo(0f)
                    lineProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing)
                    )
                    lineAlpha.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 800)
                    )
                    lineProgress.snapTo(0f)
                }
            } else {
                coroutineScope.launch {
                    lineProgress.snapTo(0f)
                    lineAlpha.snapTo(0f)
                }
            }
        }
    }

    LaunchedEffect(readerMode) {
        if (readerMode == "Read") {
            viewModel.pauseSpeaking()
        } else if (readerMode == "Listen") {
            isAutoScrollActive = false
            showAutoScrollPanel = false
        }
    }

    // Auto-dismiss short-lived panel after 5 seconds of inactivity
    LaunchedEffect(showAutoScrollPanel, autoScrollSpeed, isAutoScrollActive) {
        if (showAutoScrollPanel) {
            kotlinx.coroutines.delay(5000L)
            showAutoScrollPanel = false
        }
    }

    // 60fps VSync-anchored smooth autoscroll animation loop
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    LaunchedEffect(isAutoScrollActive, autoScrollSpeed) {
        if (isAutoScrollActive) {
            var lastTimeNanos = System.nanoTime()
            while (true) {
                androidx.compose.runtime.withFrameNanos { }
                val currentTimeNanos = System.nanoTime()
                val elapsedSeconds = (currentTimeNanos - lastTimeNanos).coerceAtLeast(0L) / 1_000_000_000f
                lastTimeNanos = currentTimeNanos
                if (elapsedSeconds > 0f) {
                    val speedPx = autoScrollSpeed * density
                    val deltaPx = speedPx * elapsedSeconds
                    val consumed = listState.scrollBy(deltaPx)
                    // If we cannot scroll forward anymore, terminate autoscroll cleanly at bottom bounds
                    if (consumed == 0f && deltaPx > 0.05f && !listState.canScrollForward) {
                        isAutoScrollActive = false
                    }
                }
            }
        }
    }

    LaunchedEffect(autoScrollSpeed) {
        readerPrefs.edit().putFloat("reader_autoscroll_speed", autoScrollSpeed).apply()
    }

    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is androidx.compose.foundation.interaction.DragInteraction.Start) {
                isAutoScrollActive = false
            }
        }
    }

    // Screen brightness control disabled by user request

    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    val isResumed = lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
    var isFirstScroll by remember { mutableStateOf(true) }

    var lastChapterIdx by remember { mutableStateOf(-1) }
    LaunchedEffect(activeChapterIndex) {
        if (!isFirstScroll) {
            if (activeChapterIndex != lastChapterIdx && lastChapterIdx != -1) {
                try {
                    listState.scrollToItem(0, 0)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        lastChapterIdx = activeChapterIndex
    }

    LaunchedEffect(activeSentenceIndex, filteredSentences, isResumed) {
        if (isResumed && filteredSentences.isNotEmpty()) {
            val targetIdx = activeSentenceIndex.coerceIn(0, filteredSentences.lastIndex)
            if (isFirstScroll) {
                isFirstScroll = false
                val savedPara = (viewModel.activeNovel.value?.currentParagraphIndex ?: targetIdx).coerceIn(0, filteredSentences.lastIndex)
                val savedOffset = viewModel.activeNovel.value?.currentScrollOffset ?: 0
                listState.scrollToItem(savedPara, savedOffset)
                return@LaunchedEffect
            }
            // Only auto-scroll if we are actually actively playing TTS and NO manual scroll/drag or fling is currently active
            if (isPlaying && !listState.isScrollInProgress) {
                kotlinx.coroutines.delay(200)
                if (!listState.isScrollInProgress) {
                    val viewportHeight = listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
                    if (viewportHeight > 0) {
                        val centerOffset = -(viewportHeight / 3) 
                        try {
                            listState.animateScrollToItem(targetIdx, centerOffset)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    val localView = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(isPlaying) {
        if (isPlaying) {
            localView.keepScreenOn = true
        } else {
            localView.keepScreenOn = false
        }
        onDispose {
            localView.keepScreenOn = false
        }
    }

    LaunchedEffect(showChapterDrawer) {
        if (showChapterDrawer && activeChapterIndex in chapters.indices) {
            kotlinx.coroutines.delay(120)
            val viewportSize = chaptersListState.layoutInfo.viewportEndOffset - chaptersListState.layoutInfo.viewportStartOffset
            if (viewportSize > 0) {
                chaptersListState.scrollToItem(activeChapterIndex, -(viewportSize / 2 - 120))
            } else {
                chaptersListState.scrollToItem((activeChapterIndex - 3).coerceAtLeast(0))
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                com.example.lorelight.TtsPlaybackManager.isAppInBackground.value = false
            }
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE || event == androidx.lifecycle.Lifecycle.Event.ON_STOP || event == androidx.lifecycle.Lifecycle.Event.ON_DESTROY) {
                com.example.lorelight.TtsPlaybackManager.isAppInBackground.value = true
                viewModel.saveDetailedProgress(
                    paragraphIndex = listState.firstVisibleItemIndex,
                    scrollOffset = listState.firstVisibleItemScrollOffset,
                    wordIndex = viewModel.activeSentenceIndex.value
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            viewModel.saveDetailedProgress(
                paragraphIndex = listState.firstVisibleItemIndex,
                scrollOffset = listState.firstVisibleItemScrollOffset,
                wordIndex = viewModel.activeSentenceIndex.value
            )
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = readerBgColor,
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black)
                        .statusBarsPadding()
                        .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = {
                            viewModel.saveDetailedProgress(
                                paragraphIndex = listState.firstVisibleItemIndex,
                                scrollOffset = listState.firstVisibleItemScrollOffset,
                                wordIndex = viewModel.activeSentenceIndex.value
                            )
                            onClose()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close", tint = readerTextColor, modifier = Modifier.size(20.dp))
                    }
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 0.dp)
                    ) {
                        val activeChapterName = chapters.getOrNull(activeChapterIndex) ?: (activeNovel?.title ?: "Reading")
                        Text(
                            text = activeChapterName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = readerTextColor
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { showChapterDrawer = !showChapterDrawer },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.MenuBook, contentDescription = "Contents", tint = readerTextColor, modifier = Modifier.size(20.dp))
                        }
                        if (readerMode == "Read") {
                            IconButton(
                                onClick = { showAutoScrollPanel = !showAutoScrollPanel },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Autoscroll Panel",
                                    tint = if (isAutoScrollActive) readerHighlightColor else readerTextColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        if (readerMode == "Listen") {
                            Box {
                                IconButton(
                                    onClick = { showSleepTimerMenu = true },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 4.dp)) {
                                        Icon(Icons.Default.AccessTime, contentDescription = "Sleep timer", tint = if (sleepTimerMinutes != null) readerHighlightColor else readerTextColor, modifier = Modifier.size(18.dp))
                                        if (sleepTimerMinutes != null) {
                                            Text("${sleepTimerMinutes}m", color = readerHighlightColor, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                        }
                                    }
                                }
                                DropdownMenu(expanded = showSleepTimerMenu, onDismissRequest = { showSleepTimerMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Timer: Off") },
                                        onClick = { viewModel.setSleepTimer(null); showSleepTimerMenu = false },
                                        trailingIcon = { if (sleepTimerMinutes == null) Icon(Icons.Default.Check, null) }
                                    )
                                    listOf(15, 30, 45, 60).forEach { mins ->
                                        DropdownMenuItem(
                                            text = { Text("$mins minutes") },
                                            onClick = { viewModel.setSleepTimer(mins); showSleepTimerMenu = false },
                                            trailingIcon = { if (sleepTimerMinutes == mins) Icon(Icons.Default.Check, null) }
                                        )
                                    }
                                }
                            }
                        }
                        IconButton(
                            onClick = { showSettingsDialog = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = readerTextColor, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            },
                bottomBar = {},

                modifier = Modifier.fillMaxSize()
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    // Core Reading Content
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                        if (readerBackgroundType == "Theme") {
                            Image(
                                painter = painterResource(
                                    id = when (themeState) {
                                        "Golden Oak" -> R.drawable.readergolden
                                        "Satin Pink" -> R.drawable.readerpink
                                        "Midnight Black" -> R.drawable.readerblack
                                        "Ocean Blue" -> R.drawable.readerblue
                                        else -> R.drawable.readergreen
                                    }
                                ),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(readerBgColor.copy(alpha = 0.4f))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black)
                            )
                        }
                        if (filteredSentences.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = readerHighlightColor)
                            }
                        } else {
                            val currentFontFamily = remember(readerFontFamily) { getFontFamily(readerFontFamily) }
                            val isCaveat = remember(readerFontFamily) { readerFontFamily == "Caveat" || readerFontFamily == "Peach Club Script" || readerFontFamily == "Midnight Cafe Society" }
                            val alignStyle = remember(textAlignment) {
                                when (textAlignment) {
                                    "Center" -> TextAlign.Center
                                    "Right" -> TextAlign.Right
                                    "Justify" -> TextAlign.Justify
                                    else -> TextAlign.Left
                                }
                            }

                            val premiumFling = rememberPremiumKineticFlingBehavior()

                            LazyColumn(
                                state = listState,
                                flingBehavior = premiumFling,
                                userScrollEnabled = true,
                                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 48.dp),
                                verticalArrangement = Arrangement.spacedBy(paragraphSpacing.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                itemsIndexed(filteredSentences, key = { index, _ -> index }) { index, sentence ->
                                    val isActiveSentence = (readerMode == "Listen" && activeSentenceIndex == index)
                                    val textColor = if (isActiveSentence) readerHighlightColor else readerTextColor
                                    val sentenceTokens = remember(tokensBySentence, index) { tokensBySentence.getOrNull(index) ?: emptyList() }
                                    
                                    val onSentenceClick = remember(index, readerMode) {
                                        {
                                            if (readerMode == "Listen") {
                                                viewModel.activeSentenceIndex.value = index
                                                viewModel.startSpeaking()
                                            }
                                        }
                                    }
                                    
                                    val onSentenceLongClick = remember(index, sentenceTokens) {
                                        {
                                            val firstWordIdx = sentenceTokens.firstOrNull { it.isWord && it.wordIdx >= 0 }?.wordIdx
                                            if (firstWordIdx != null) {
                                                selRange = WordRange(firstWordIdx, firstWordIdx)
                                            }
                                        }
                                    }

                                    val onWordSelected = remember {
                                        { range: WordRange ->
                                            selRange = range
                                        }
                                    }

                                    val onPositioned = remember {
                                        { wordIdx: Int, left: Offset, right: Offset ->
                                            wordPositions[wordIdx] = WordPosition(left, right)
                                        }
                                    }

                                    SentenceRow(
                                        index = index,
                                        sentence = sentence,
                                        isActiveSentence = isActiveSentence,
                                        readerMode = readerMode,
                                        textColor = textColor,
                                        readerHighlightColor = readerHighlightColor,
                                        readerTextColor = readerTextColor,
                                        currentFontFamily = currentFontFamily,
                                        isCaveat = isCaveat,
                                        alignStyle = alignStyle,
                                        textAlignment = textAlignment,
                                        fontSize = fontSize,
                                        lineSpacing = lineSpacing,
                                        paragraphSpacing = paragraphSpacing,
                                        selRange = selRange,
                                        sentenceTokens = sentenceTokens,
                                        wordPositions = wordPositions,
                                        onSentenceClick = onSentenceClick,
                                        onSentenceLongClick = onSentenceLongClick,
                                        onWordSelected = onWordSelected,
                                        onPositioned = onPositioned
                                    )
                                }

                                if (readerMode == "Read") {
                                    item {
                                        Spacer(modifier = Modifier.height(24.dp))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(56.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(1.dp, readerTextColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                                .background(readerTextColor.copy(alpha = 0.04f)),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Previous Button
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight()
                                                    .clickable(enabled = activeChapterIndex > 0 && !listState.isScrollInProgress) {
                                                        triggerNavLineAnimation()
                                                        viewModel.saveDetailedProgress(
                                                            paragraphIndex = 0,
                                                            scrollOffset = 0,
                                                            wordIndex = 0
                                                        )
                                                        viewModel.selectChapter(activeChapterIndex - 1)
                                                    }
                                                    .alpha(if (activeChapterIndex > 0) 1.0f else 0.35f),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.KeyboardArrowLeft,
                                                        contentDescription = "Previous Chapter",
                                                        tint = readerTextColor,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Text(
                                                        text = "Previous",
                                                        color = readerTextColor,
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        fontFamily = currentFontFamily
                                                    )
                                                }
                                            }

                                            // 50/50 Vertical Divider line
                                            VerticalDivider(
                                                color = readerTextColor.copy(alpha = 0.15f),
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .width(1.dp)
                                            )

                                            // Next Button
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight()
                                                    .clickable(enabled = (activeChapterIndex < chapters.size - 1) && !listState.isScrollInProgress) {
                                                        triggerNavLineAnimation()
                                                        viewModel.saveDetailedProgress(
                                                            paragraphIndex = 0,
                                                            scrollOffset = 0,
                                                            wordIndex = 0
                                                        )
                                                        viewModel.selectChapter(activeChapterIndex + 1)
                                                    }
                                                    .alpha(if (activeChapterIndex < chapters.size - 1) 1.0f else 0.35f),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Text(
                                                        text = "Next",
                                                        color = readerTextColor,
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        fontFamily = currentFontFamily
                                                    )
                                                    Icon(
                                                        imageVector = Icons.Default.KeyboardArrowRight,
                                                        contentDescription = "Next Chapter",
                                                        tint = readerTextColor,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(48.dp))
                                    }
                                }
                            }
                        }
                    } // Core Reading Content Box End
                    
                    // Browser-style page transition progress line
                    if (isNavLineAnimEnabled && lineAlpha.value > 0f) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                        ) {
                            val barWidth = size.width * lineProgress.value
                            drawRect(
                                color = defaultHighlightColor.copy(alpha = lineAlpha.value),
                                size = androidx.compose.ui.geometry.Size(barWidth, size.height)
                            )
                        }
                    }

                    // Floating Style/Typography Customizer Panel
                    AnimatedVisibility(
                        visible = showFloatingStylePanel,
                        enter = slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                        ) + fadeIn(),
                        exit = slideOutVertically(
                            targetOffsetY = { it },
                            animationSpec = spring(stiffness = Spring.StiffnessMedium)
                        ) + fadeOut(),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 24.dp)
                    ) {
                        FloatingStylePanel(
                            fontSize = fontSize,
                            lineSpacing = lineSpacing,
                            paragraphSpacing = paragraphSpacing,
                            textAlignment = textAlignment,
                            onClose = { showFloatingStylePanel = false },
                            onFontSizeChange = { viewModel.readerFontSize.value = it },
                            onLineSpacingChange = { viewModel.readerLineSpacing.value = it },
                            onParagraphSpacingChange = { viewModel.readerParagraphSpacing.value = it },
                            onAlignmentChange = { viewModel.readerTextAlignment.value = it }
                        )
                    }

                    // Floating Premium Autoscroll Control Panel (Read Mode Only)
                    AnimatedVisibility(
                        visible = showAutoScrollPanel,
                        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 8.dp, end = 16.dp)
                            .width(300.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = readerBgColor.copy(alpha = 0.94f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, readerTextColor.copy(alpha = 0.15f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    Text(
                                        "AUTO-SCROLL",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        color = readerTextColor.copy(alpha = 0.7f)
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    IconButton(
                                        onClick = { isAutoScrollActive = !isAutoScrollActive },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isAutoScrollActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isAutoScrollActive) "Pause" else "Play",
                                            tint = readerHighlightColor,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "Speed",
                                                fontSize = 10.sp,
                                                color = readerTextColor.copy(alpha = 0.5f)
                                            )
                                            Text(
                                                "${autoScrollSpeed.toInt()} px/s",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = readerTextColor
                                            )
                                        }
                                        Slider(
                                            value = autoScrollSpeed,
                                            onValueChange = { autoScrollSpeed = it },
                                            valueRange = 1f..100f,
                                            colors = SliderDefaults.colors(
                                                thumbColor = readerHighlightColor,
                                                activeTrackColor = readerHighlightColor,
                                                inactiveTrackColor = readerTextColor.copy(alpha = 0.15f)
                                            ),
                                            modifier = Modifier.height(24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (showFontSelectionDialog) {
                    FontSelectionDialog(
                        currentFont = readerFontFamily,
                        onSelectFont = { viewModel.readerFontFamily.value = it },
                        onDismiss = { showFontSelectionDialog = false }
                    )
                }

                if (showColorWheelDialog) {
                    ColorWheelPickerDialog(
                        initialColorValue = customHighlightColorRaw.toInt(),
                        onColorSelected = { selectedCol ->
                            viewModel.customHighlightColor.value = selectedCol.toLong()
                            viewModel.isDefaultHighlight.value = false
                        },
                        onDismiss = { showColorWheelDialog = false }
                    )
                }


            }

            // Compact rounded rectangle floating TTS control bar
            if (readerMode == "Listen") {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.45f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 24.dp, vertical = 24.dp)
                        .widthIn(max = 300.dp)
                        .height(64.dp)
                        .border(1.dp, readerTextColor.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { if (activeChapterIndex > 0) viewModel.selectChapter(activeChapterIndex - 1) }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.FastRewind, contentDescription = "Prev chapter", modifier = Modifier.size(22.dp), tint = readerTextColor)
                        }
                        IconButton(onClick = { viewModel.skipBackward() }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Prev sentence", modifier = Modifier.size(20.dp), tint = readerTextColor)
                        }
                        IconButton(
                            onClick = { viewModel.togglePlayPause() },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = readerHighlightColor, contentColor = if (themeState == "Golden Oak") Color(0xFF2E1901) else Color(0xFF0F2613)),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play/Pause", modifier = Modifier.size(22.dp))
                        }
                        IconButton(onClick = { viewModel.skipForward() }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Next sentence", modifier = Modifier.size(20.dp), tint = readerTextColor)
                        }
                        IconButton(onClick = { if (activeChapterIndex < chapters.size - 1) viewModel.selectChapter(activeChapterIndex + 1) }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.FastForward, contentDescription = "Next chapter", modifier = Modifier.size(22.dp), tint = readerTextColor)
                        }
                    }
                }
            }

            // Universal Floating TTS Error Overlay Card
            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 84.dp)
                    .padding(horizontal = 24.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF211010)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp)
                        .border(1.dp, Color(0xFFEB5757).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF3D1F1F)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = "Error Icon",
                                tint = Color(0xFFEB5757),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Text-to-Speech Error",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFFEB5757)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = errorMessage ?: "An unexpected playback error occurred.",
                                fontSize = 12.sp,
                                color = Color(0xFFF2F2F2),
                                lineHeight = 16.sp
                            )
                        }
                        
                        IconButton(
                            onClick = { viewModel.clearErrorMessage() },
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.05f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss error",
                                tint = Color(0xFFE0E0E0),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }



            AnimatedVisibility(
                visible = showSettingsDialog,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.55f)).clickable { showSettingsDialog = false }) {
                    Surface(
                        shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 6.dp,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .fillMaxWidth(0.85f)
                            .clickable(enabled = false) {}
                            .animateEnterExit(
                                enter = slideInHorizontally { it } + fadeIn(),
                                exit = slideOutHorizontally { it } + fadeOut()
                            )
                    ) {
                        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Text("Reader Settings", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { showSettingsDialog = false }) { Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.7f)) }
                                }
                                
                                // Tab Selection Row for Read and Listen!
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp)
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                                ) {
                                    listOf("Read", "Listen").forEach { tab ->
                                        val isSelected = readerMode == tab
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(11.dp))
                                                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                                .clickable { viewModel.readerMode.value = tab },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (tab == "Read") Icons.Default.MenuBook else Icons.Default.Headphones,
                                                    contentDescription = null,
                                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = tab,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(bottom = 12.dp))
                                
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 80.dp), modifier = Modifier.weight(1f)) {
                                    if (readerMode == "Read") {
                                        // ------------------ READ TAB SETTINGS ------------------
                                        item {
                                             Column {
                                                 Text("Page Formatting", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                                                 Row(
                                                     verticalAlignment = Alignment.CenterVertically,
                                                     modifier = Modifier
                                                         .fillMaxWidth()
                                                         .clip(RoundedCornerShape(12.dp))
                                                         .background(Color(0xFF202020))
                                                         .border(1.dp, Color(0xFF3C3C3C), RoundedCornerShape(12.dp))
                                                         .clickable {
                                                             showSettingsDialog = false
                                                             showTextFilterPanel = true
                                                         }
                                                         .padding(horizontal = 16.dp, vertical = 12.dp)
                                                 ) {
                                                     Icon(Icons.Default.FilterAlt, contentDescription = null, tint = defaultHighlightColor, modifier = Modifier.size(18.dp))
                                                     Spacer(modifier = Modifier.width(10.dp))
                                                     Text("Manage Text Filter", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                                                     Text("${textFilterRules.size} active", fontSize = 11.sp, color = defaultHighlightColor, fontWeight = FontWeight.SemiBold)
                                                     Spacer(modifier = Modifier.width(6.dp))
                                                     Icon(Icons.Default.ArrowForwardIos, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(12.dp))
                                                 }
                                             }
                                         }
                                        item {
                                            // Font Family removed
                                            Column {
                                                 Text("Visual Aesthetics", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                                                 Row(
                                                     modifier = Modifier
                                                         .fillMaxWidth()
                                                         .clip(RoundedCornerShape(12.dp))
                                                         .background(Color(0xFF202020))
                                                         .border(1.dp, Color(0xFF3C3C3C), RoundedCornerShape(12.dp))
                                                         .padding(horizontal = 16.dp, vertical = 12.dp),
                                                     verticalAlignment = Alignment.CenterVertically,
                                                     horizontalArrangement = Arrangement.SpaceBetween
                                                 ) {
                                                     Row(
                                                         verticalAlignment = Alignment.CenterVertically,
                                                         modifier = Modifier.weight(1f)
                                                     ) {
                                                         Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = defaultHighlightColor, modifier = Modifier.size(18.dp))
                                                         Spacer(modifier = Modifier.width(10.dp))
                                                         Text(
                                                             text = "Page Transition Line",
                                                             fontWeight = FontWeight.Bold,
                                                             fontSize = 13.sp,
                                                             color = Color.White
                                                         )
                                                     }
                                                     val isNavLineAnimEnabled by viewModel.isNavLineAnimEnabled.collectAsState()
                                                     Switch(
                                                         checked = isNavLineAnimEnabled,
                                                         onCheckedChange = { viewModel.isNavLineAnimEnabled.value = it },
                                                         colors = SwitchDefaults.colors(
                                                             checkedThumbColor = MaterialTheme.colorScheme.primary,
                                                             checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                                                         )
                                                     )
                                                 }
                                             }
                                                    /* val fonts = listOf(
                                                        "System Default",
                                                        "Midnight Cafe Society",
                                                        "Peach Club Script",
                                                        "Smart Notes",
                                                        "Twilight Luminance"
                                                    )
                                                    fonts.chunked(2).forEach { rowFonts ->
                                                        Row(
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                            rowFonts.forEach { font ->
                                                                val selected = readerFontFamily == font
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
                                                                        .clickable { viewModel.readerFontFamily.value = font }
                                                                        .padding(vertical = 8.dp),
                                                                    contentAlignment = Alignment.Center
                                                                  ) {
                                                                      Text(
                                                                          text = font,
                                                                          fontSize = 11.sp,
                                                                          fontWeight = FontWeight.SemiBold,
                                                                          color = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f)
                                                                      )
                                                                  }
                                                              }
                                                              if (rowFonts.size == 1) {
                                                                  Spacer(modifier = Modifier.weight(1f))
                                                              }
                                                          }
                                                      }
                                                  }
                                              }
                                            */
                                        }
                                        item {
                                            Column {
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                    Text("Reading Text Size", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    Text("${fontSize.toInt()}sp", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                                }
                                                Slider(value = fontSize, onValueChange = { viewModel.readerFontSize.value = it }, valueRange = 12f..32f, colors = SliderDefaults.colors(activeTrackColor = MaterialTheme.colorScheme.primary, inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant))
                                            }
                                        }
                                        item {
                                            Column {
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                    Text("Line Spacing", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    Text("${"%.1fx".format(lineSpacing)}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                                }
                                                Slider(value = lineSpacing, onValueChange = { viewModel.readerLineSpacing.value = it }, valueRange = 1.0f..2.5f, colors = SliderDefaults.colors(activeTrackColor = MaterialTheme.colorScheme.primary, inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant))
                                            }
                                        }
                                        item {
                                            Column {
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                    Text("Paragraph Spacing", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    Text("${paragraphSpacing.toInt()}dp", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                                }
                                                Slider(value = paragraphSpacing, onValueChange = { viewModel.readerParagraphSpacing.value = it }, valueRange = 0f..40f, colors = SliderDefaults.colors(activeTrackColor = MaterialTheme.colorScheme.primary, inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant))
                                            }
                                        }
                                        item {
                                            Column {
                                                Text("Text Alignment", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    val alignOptions = listOf(
                                                        Triple("Left", Icons.Default.FormatAlignLeft, "Left"),
                                                        Triple("Center", Icons.Default.FormatAlignCenter, "Center"),
                                                        Triple("Right", Icons.Default.FormatAlignRight, "Right"),
                                                        Triple("Justify", Icons.Default.FormatAlignJustify, "Justify")
                                                    )
                                                    alignOptions.forEach { (align, icon, label) ->
                                                        val isSelected = textAlignment == align
                                                        Box(
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .height(44.dp)
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                                                .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(8.dp))
                                                                .clickable { viewModel.readerTextAlignment.value = align },
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Column(
                                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                                verticalArrangement = Arrangement.Center
                                                            ) {
                                                                Icon(
                                                                    imageVector = icon,
                                                                    contentDescription = label,
                                                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                                Text(
                                                                    text = label, 
                                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, 
                                                                    fontSize = 9.sp, 
                                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        item {
                                            Column {
                                                Text("Background Style", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    listOf("Theme", "AMOLED Black").forEach { bgType ->
                                                        val isSelected = readerBackgroundType == bgType
                                                        Box(
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .height(40.dp)
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                                                .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(8.dp))
                                                                .clickable { viewModel.readerBackgroundType.value = bgType },
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(bgType, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        // ------------------ LISTEN TAB SETTINGS ------------------
                                        item {
                                             Column {
                                                 Text("Page Formatting", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                                                 Row(
                                                     verticalAlignment = Alignment.CenterVertically,
                                                     modifier = Modifier
                                                         .fillMaxWidth()
                                                         .clip(RoundedCornerShape(12.dp))
                                                         .background(Color(0xFF202020))
                                                         .border(1.dp, Color(0xFF3C3C3C), RoundedCornerShape(12.dp))
                                                         .clickable {
                                                             showSettingsDialog = false
                                                             showTextFilterPanel = true
                                                         }
                                                         .padding(horizontal = 16.dp, vertical = 12.dp)
                                                 ) {
                                                     Icon(Icons.Default.FilterAlt, contentDescription = null, tint = defaultHighlightColor, modifier = Modifier.size(18.dp))
                                                     Spacer(modifier = Modifier.width(10.dp))
                                                     Text("Manage Text Filter", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                                                     Text("${textFilterRules.size} active", fontSize = 11.sp, color = defaultHighlightColor, fontWeight = FontWeight.SemiBold)
                                                     Spacer(modifier = Modifier.width(6.dp))
                                                     Icon(Icons.Default.ArrowForwardIos, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(12.dp))
                                                 }
                                             }
                                         }
                                        item {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Voice Accent
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("Accent", fontWeight = FontWeight.Medium, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                                                    var accentDropdownVisible by remember { mutableStateOf(false) }
                                                    Box(modifier = Modifier.fillMaxWidth()) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                                                .clickable { accentDropdownVisible = true }.padding(horizontal = 8.dp, vertical = 10.dp)
                                                        ) {
                                                            Text(selectedAccent, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                                        }
                                                        DropdownMenu(expanded = accentDropdownVisible, onDismissRequest = { accentDropdownVisible = false }) {
                                                            availableAccents.forEach { accent ->
                                                                DropdownMenuItem(
                                                                    text = { Text(accent, fontSize = 13.sp) },
                                                                    onClick = { viewModel.selectAccentAndApply(accent); accentDropdownVisible = false }
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                                // Voice
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("Voice", fontWeight = FontWeight.Medium, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                                                    var voiceDropdownVisible by remember { mutableStateOf(false) }
                                                    Box(modifier = Modifier.fillMaxWidth()) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                                                .clickable { voiceDropdownVisible = true }.padding(horizontal = 8.dp, vertical = 10.dp)
                                                        ) {
                                                            val voiceName = selectedVoice?.name?.replace("en-us-x-", "")?.replace("-local", "")
                                                            Text(if (voiceName != null) voiceName else "Default", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                                        }
                                                        DropdownMenu(expanded = voiceDropdownVisible, onDismissRequest = { voiceDropdownVisible = false }) {
                                                            filteredVoices.forEach { voice ->
                                                                DropdownMenuItem(
                                                                    text = { Text(voice.name, fontSize = 13.sp) },
                                                                    onClick = { viewModel.selectVoiceAndApply(voice); voiceDropdownVisible = false }
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                                // Speed
                                                Column(modifier = Modifier.weight(0.7f)) {
                                                    Text("Speed", fontWeight = FontWeight.Medium, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                                                    var rateDropdownVisible by remember { mutableStateOf(false) }
                                                    Box(modifier = Modifier.fillMaxWidth()) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                                                .clickable { rateDropdownVisible = true }.padding(horizontal = 8.dp, vertical = 10.dp)
                                                        ) {
                                                            Text("${"%.2fx".format(speechRate)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                                        }
                                                        DropdownMenu(expanded = rateDropdownVisible, onDismissRequest = { rateDropdownVisible = false }) {
                                                            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.50f, 1.75f, 2.0f, 2.5f, 3.0f).forEach { speedOption ->
                                                                DropdownMenuItem(text = { Text("${"%.2fx".format(speedOption)}") }, onClick = { viewModel.setSpeechRateAndApply(speedOption); rateDropdownVisible = false })
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                                                                 // Text Sizing sliders inside Listen settings for 100% parity!

                                         item {
                                             Column {
                                                 Text("Text Alignment", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                                                 Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                     val alignOptions = listOf(
                                                         Triple("Left", Icons.Default.FormatAlignLeft, "Left"),
                                                         Triple("Center", Icons.Default.FormatAlignCenter, "Center"),
                                                         Triple("Right", Icons.Default.FormatAlignRight, "Right"),
                                                         Triple("Justify", Icons.Default.FormatAlignJustify, "Justify")
                                                     )
                                                     alignOptions.forEach { (align, icon, label) ->
                                                         val isSelected = textAlignment == align
                                                         Box(
                                                             modifier = Modifier
                                                                 .weight(1f)
                                                                 .height(44.dp)
                                                                 .clip(RoundedCornerShape(8.dp))
                                                                 .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                                                 .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(8.dp))
                                                                 .clickable { viewModel.readerTextAlignment.value = align },
                                                             contentAlignment = Alignment.Center
                                                         ) {
                                                             Column(
                                                                 horizontalAlignment = Alignment.CenterHorizontally,
                                                                 verticalArrangement = Arrangement.Center
                                                             ) {
                                                                 Icon(
                                                                     imageVector = icon,
                                                                     contentDescription = label,
                                                                     tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                                                     modifier = Modifier.size(16.dp)
                                                                 )
                                                                 Text(
                                                                     text = label, 
                                                                     fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, 
                                                                     fontSize = 9.sp, 
                                                                     color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                                 )
                                                             }
                                                         }
                                                     }
                                                 }
                                             }
                                         }
                                         item {
                                             Column {
                                                 Text("Background Style", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                                                 Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                     listOf("Theme", "AMOLED Black").forEach { bgType ->
                                                         val isSelected = readerBackgroundType == bgType
                                                         Box(
                                                             modifier = Modifier
                                                                 .weight(1f)
                                                                 .height(40.dp)
                                                                 .clip(RoundedCornerShape(8.dp))
                                                                 .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                                                 .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(8.dp))
                                                                 .clickable { viewModel.readerBackgroundType.value = bgType },
                                                             contentAlignment = Alignment.Center
                                                         ) {
                                                             Text(bgType, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                                                         }
                                                     }
                                                 }
                                             }
                                         }
                                        item {
                                            // Font Family removed
                                            /* Column {
                                                Text("Font Family", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                                                Column(
                                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    val fonts = listOf(
                                                        "System Default",
                                                        "Midnight Cafe Society",
                                                        "Peach Club Script",
                                                        "Smart Notes",
                                                        "Twilight Luminance"
                                                    )
                                                    fonts.chunked(2).forEach { rowFonts ->
                                                        Row(
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                            rowFonts.forEach { font ->
                                                                val selected = readerFontFamily == font
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
                                                                        .clickable { viewModel.readerFontFamily.value = font }
                                                                        .padding(vertical = 8.dp),
                                                                    contentAlignment = Alignment.Center
                                                                  ) {
                                                                      Text(
                                                                          text = font,
                                                                          fontSize = 11.sp,
                                                                          fontWeight = FontWeight.SemiBold,
                                                                          color = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f)
                                                                      )
                                                                  }
                                                              }
                                                              if (rowFonts.size == 1) {
                                                                  Spacer(modifier = Modifier.weight(1f))
                                                              }
                                                          }
                                                      }
                                                  }
                                              }
                                            */
                                        }
                                        item {
                                            Column {
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                    Text("Reader Text Size", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    Text("${fontSize.toInt()}sp", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                                }
                                                Slider(value = fontSize, onValueChange = { viewModel.readerFontSize.value = it }, valueRange = 12f..32f, colors = SliderDefaults.colors(activeTrackColor = MaterialTheme.colorScheme.primary, inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant))
                                            }
                                        }
                                        item {
                                            Column {
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                    Text("Line Spacing", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    Text("${"%.1fx".format(lineSpacing)}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                                }
                                                Slider(value = lineSpacing, onValueChange = { viewModel.readerLineSpacing.value = it }, valueRange = 1.0f..2.5f, colors = SliderDefaults.colors(activeTrackColor = MaterialTheme.colorScheme.primary, inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant))
                                            }
                                        }
                                        item {
                                            Column {
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                    Text("Paragraph Spacing", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    Text("${paragraphSpacing.toInt()}dp", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                                }
                                                Slider(value = paragraphSpacing, onValueChange = { viewModel.readerParagraphSpacing.value = it }, valueRange = 0f..40f, colors = SliderDefaults.colors(activeTrackColor = MaterialTheme.colorScheme.primary, inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant))
                                            }
                                        }
                                        item {
                                            Column {
                                                Text("Text Highlights Accent", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                    // Glow Green Default option
                                                    Card(
                                                        shape = RoundedCornerShape(12.dp),
                                                        colors = CardDefaults.cardColors(containerColor = if (isDefaultHighlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .clickable { viewModel.isDefaultHighlight.value = true }
                                                            .border(
                                                                width = 1.dp,
                                                                color = if (isDefaultHighlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                                                shape = RoundedCornerShape(12.dp)
                                                            )
                                                    ) {
                                                        val defaultHighlightName = if (themeState == "Golden Oak") "Amber Gold" else "Dim Glow Green"
                                                        Row(
                                                            modifier = Modifier.padding(12.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(defaultHighlightColor))
                                                            Text(defaultHighlightName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isDefaultHighlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                                                        }
                                                    }
                                                    
                                                    // Set Color / Custom picker option
                                                    Card(
                                                        shape = RoundedCornerShape(12.dp),
                                                        colors = CardDefaults.cardColors(containerColor = if (!isDefaultHighlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .clickable { showColorWheelDialog = true }
                                                            .border(
                                                                width = 1.dp,
                                                                color = if (!isDefaultHighlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                                                shape = RoundedCornerShape(12.dp)
                                                            )
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(12.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(if (!isDefaultHighlight) Color(customHighlightColorRaw) else Color.Gray))
                                                            Text(if (!isDefaultHighlight) "Custom Active" else "Set Custom", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (!isDefaultHighlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                    }
                                    item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 4.dp)) }
                                }
                            }
                        }
                    }
                }

        // Selection Draggable Handles Overlay
        selRange?.let { range ->
            val startIdx = minOf(range.start, range.end)
            val endIdx = maxOf(range.start, range.end)
            val startPos = wordPositions[startIdx]
            val endPos = wordPositions[endIdx]
            
            Box(modifier = Modifier.fillMaxSize()) {
                // Left Handle (Start)
                startPos?.let { pos ->
                    var accumulatedStartDrag by remember { mutableStateOf(Offset.Zero) }
                    Box(
                        modifier = Modifier
                            .offset(
                                x = with(LocalDensity.current) { pos.leftAnchor.x.toDp() - 24.dp },
                                y = with(LocalDensity.current) { pos.leftAnchor.y.toDp() - 12.dp }
                            )
                            .size(48.dp, 56.dp)
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = {
                                        accumulatedStartDrag = wordPositions[selRange?.start ?: startIdx]?.leftAnchor ?: pos.leftAnchor
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        accumulatedStartDrag += dragAmount
                                        var closestIdx = -1
                                        var minDistance = Float.MAX_VALUE
                                        for ((idx, wPos) in wordPositions) {
                                            val wordCenter = Offset((wPos.leftAnchor.x + wPos.rightAnchor.x) / 2f, (wPos.leftAnchor.y + wPos.rightAnchor.y) / 2f)
                                            val distX = accumulatedStartDrag.x - wordCenter.x
                                            val distY = accumulatedStartDrag.y - wordCenter.y
                                            val dist = distX * distX + distY * distY
                                            if (dist < minDistance) {
                                                minDistance = dist
                                                closestIdx = idx
                                            }
                                        }
                                        if (closestIdx != -1) {
                                            selRange = selRange?.copy(start = closestIdx)
                                        }
                                    }
                                )
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val lineThickness = 2.5.dp.toPx()
                            val lineHeight = 18.dp.toPx()
                            val dotRadius = 6.dp.toPx()
                            val centerX = size.width / 2f
                            val startY = 12.dp.toPx()
                            
                            drawLine(
                                color = readerHighlightColor,
                                start = Offset(centerX, startY),
                                end = Offset(centerX, startY + lineHeight),
                                strokeWidth = lineThickness
                            )
                            drawCircle(
                                color = readerHighlightColor,
                                radius = dotRadius,
                                center = Offset(centerX, startY + lineHeight + dotRadius)
                            )
                        }
                    }
                }
                
                // Right Handle (End)
                endPos?.let { pos ->
                    var accumulatedEndDrag by remember { mutableStateOf(Offset.Zero) }
                    Box(
                        modifier = Modifier
                            .offset(
                                x = with(LocalDensity.current) { pos.rightAnchor.x.toDp() - 24.dp },
                                y = with(LocalDensity.current) { pos.rightAnchor.y.toDp() - 12.dp }
                            )
                            .size(48.dp, 56.dp)
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = {
                                        accumulatedEndDrag = wordPositions[selRange?.end ?: endIdx]?.rightAnchor ?: pos.rightAnchor
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        accumulatedEndDrag += dragAmount
                                        var closestIdx = -1
                                        var minDistance = Float.MAX_VALUE
                                        for ((idx, wPos) in wordPositions) {
                                            val wordCenter = Offset((wPos.leftAnchor.x + wPos.rightAnchor.x) / 2f, (wPos.leftAnchor.y + wPos.rightAnchor.y) / 2f)
                                            val distX = accumulatedEndDrag.x - wordCenter.x
                                            val distY = accumulatedEndDrag.y - wordCenter.y
                                            val dist = distX * distX + distY * distY
                                            if (dist < minDistance) {
                                                minDistance = dist
                                                closestIdx = idx
                                            }
                                        }
                                        if (closestIdx != -1) {
                                            selRange = selRange?.copy(end = closestIdx)
                                        }
                                    }
                                )
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val lineThickness = 2.5.dp.toPx()
                            val lineHeight = 18.dp.toPx()
                            val dotRadius = 6.dp.toPx()
                            val centerX = size.width / 2f
                            val startY = 12.dp.toPx()
                            
                            drawLine(
                                color = readerHighlightColor,
                                start = Offset(centerX, startY),
                                end = Offset(centerX, startY + lineHeight),
                                strokeWidth = lineThickness
                            )
                            drawCircle(
                                color = readerHighlightColor,
                                radius = dotRadius,
                                center = Offset(centerX, startY + lineHeight + dotRadius)
                            )
                        }
                    }
                }
            }
        }

        // Sliding Bottom Action Bar on Selection
        AnimatedVisibility(
            visible = selRange != null && !isSelectionPanelCollapsed,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(200)) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(999f)
        ) {
            val selectedTextPreview = remember(selRange, filteredSentences) {
                if (selRange == null) "" else {
                    val minIdx = minOf(selRange!!.start, selRange!!.end)
                    val maxIdx = maxOf(selRange!!.start, selRange!!.end)
                    val allRawTokens = mutableListOf<Token>()
                    var currentWordIdx = 0
                    filteredSentences.forEach { sentence ->
                        val (raw, nextIdx) = TextFilter.tokenize(sentence, currentWordIdx)
                        allRawTokens.addAll(raw)
                        currentWordIdx = nextIdx
                    }
                    val firstTokIdx = allRawTokens.indexOfFirst { it.wordIdx == minIdx }
                    val lastTokIdx = allRawTokens.indexOfLast { it.wordIdx == maxIdx }
                    if (firstTokIdx != -1 && lastTokIdx != -1 && firstTokIdx <= lastTokIdx) {
                        allRawTokens.subList(firstTokIdx, lastTokIdx + 1)
                            .joinToString("") { it.raw }
                            .trim()
                    } else {
                        allRawTokens.filter { it.wordIdx in minIdx..maxIdx }
                            .joinToString("") { it.raw }
                            .trim()
                    }
                }
            }
            
            var isVanish by remember { mutableStateOf(true) }
            var replacementText by remember { mutableStateOf("") }
            var selectedScope by remember { mutableStateOf("All Books") }
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(readerBgColor)
                    .border(1.dp, readerHighlightColor.copy(alpha = 0.3f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Create Filter Rule",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = readerTextColor
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(onClick = { isSelectionPanelCollapsed = true }) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Minimize selection panel", tint = readerTextColor.copy(alpha = 0.6f))
                        }
                        IconButton(onClick = { selRange = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel selection", tint = readerTextColor.copy(alpha = 0.6f))
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(readerTextColor.copy(alpha = 0.06f))
                        .border(0.5.dp, readerTextColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Match: \"$selectedTextPreview\"",
                        fontSize = 13.sp,
                        color = readerTextColor,
                        fontStyle = FontStyle.Italic
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(readerTextColor.copy(alpha = 0.06f))
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isVanish) readerHighlightColor.copy(alpha = 0.2f) else Color.Transparent)
                            .clickable { isVanish = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Vanish 🚫",
                            fontSize = 12.sp,
                            fontWeight = if (isVanish) FontWeight.Bold else FontWeight.Normal,
                            color = if (isVanish) readerHighlightColor else readerTextColor.copy(alpha = 0.8f)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (!isVanish) readerHighlightColor.copy(alpha = 0.2f) else Color.Transparent)
                            .clickable { isVanish = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Replace ✏️",
                            fontSize = 12.sp,
                            fontWeight = if (!isVanish) FontWeight.Bold else FontWeight.Normal,
                            color = if (!isVanish) readerHighlightColor else readerTextColor.copy(alpha = 0.8f)
                        )
                    }
                }
                
                if (!isVanish) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = replacementText,
                        onValueChange = { replacementText = it },
                        placeholder = { Text("Enter replacement text...", fontSize = 13.sp, color = readerTextColor.copy(alpha = 0.4f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = readerHighlightColor,
                            unfocusedBorderColor = readerTextColor.copy(alpha = 0.2f),
                            focusedTextColor = readerTextColor,
                            unfocusedTextColor = readerTextColor
                        ),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(readerTextColor.copy(alpha = 0.06f))
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedScope == "All Books") readerHighlightColor.copy(alpha = 0.2f) else Color.Transparent)
                            .clickable { selectedScope = "All Books" },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "All Books 🌍",
                            fontSize = 12.sp,
                            fontWeight = if (selectedScope == "All Books") FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedScope == "All Books") readerHighlightColor else readerTextColor.copy(alpha = 0.8f)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedScope == "This Book") readerHighlightColor.copy(alpha = 0.2f) else Color.Transparent)
                            .clickable { selectedScope = "This Book" },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "This Book 📖",
                            fontSize = 12.sp,
                            fontWeight = if (selectedScope == "This Book") FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedScope == "This Book") readerHighlightColor else readerTextColor.copy(alpha = 0.8f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = {
                        val newRule = FilterRule(
                            id = java.util.UUID.randomUUID().toString(),
                            original = selectedTextPreview,
                            replacement = if (isVanish) "" else replacementText,
                            isVanish = isVanish,
                            scope = selectedScope,
                            bookId = activeNovel?.title ?: ""
                        )
                        viewModel.saveRules(textFilterRules + newRule)
                        selRange = null
                        replacementText = ""
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (themeState == "Golden Oak") Color.Transparent else readerHighlightColor,
                        contentColor = if (themeState == "Golden Oak") GoldOnGradient else Color.Black
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .then(
                            if (themeState == "Golden Oak") {
                                Modifier.background(RefinedGoldGradient, RoundedCornerShape(8.dp))
                            } else {
                                Modifier
                            }
                        ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Add Filter Rule", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Floating Action Button/Badge for Selection Collapse (Bottom Right)
        AnimatedVisibility(
            visible = selRange != null && isSelectionPanelCollapsed,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 24.dp, end = 24.dp)
                .navigationBarsPadding()
                .zIndex(999f)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = readerBgColor.copy(alpha = 0.95f)),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, readerHighlightColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .clickable { isSelectionPanelCollapsed = false }
                    .wrapContentSize()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "Open Filter Rules Panel",
                        tint = readerHighlightColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Create Filter",
                        color = readerTextColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Slide-in Text Filter Settings Panel
        AnimatedVisibility(
            visible = showTextFilterPanel,
            enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)),
            exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(250)),
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1000f)
        ) {
            var activeTab by remember { mutableStateOf(0) }
            
            var manualOriginal by remember { mutableStateOf("") }
            var manualReplacement by remember { mutableStateOf("") }
            var manualIsVanish by remember { mutableStateOf(true) }
            var manualScope by remember { mutableStateOf("All Books") }
            
            Surface(
                color = readerBgColor,
                contentColor = readerTextColor,
                modifier = Modifier.fillMaxSize()
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().statusBarsPadding()
                    ) {
                        IconButton(onClick = { showTextFilterPanel = false }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = readerTextColor)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Manage Text Filter",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = readerTextColor
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(readerTextColor.copy(alpha = 0.06f))
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (activeTab == 0) readerHighlightColor.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable { activeTab = 0 },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Active Rules (${textFilterRules.size})",
                                fontSize = 13.sp,
                                fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (activeTab == 0) readerHighlightColor else readerTextColor.copy(alpha = 0.8f)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (activeTab == 1) readerHighlightColor.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable { activeTab = 1 },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Add Manual",
                                fontSize = 13.sp,
                                fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Normal,
                                color = if (activeTab == 1) readerHighlightColor else readerTextColor.copy(alpha = 0.8f)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (activeTab == 0) {
                        Text(
                            text = "How it works: Long-press any word in the reader to select and create an active rule, replacing or hiding that word dynamically during reads.",
                            fontSize = 11.sp,
                            color = readerTextColor.copy(alpha = 0.6f),
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(bottom = 16.dp, start = 4.dp, end = 4.dp)
                        )
                        if (textFilterRules.isEmpty()) {
                            Box(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        tint = readerTextColor.copy(alpha = 0.3f),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "No active filter rules",
                                        fontSize = 14.sp,
                                        color = readerTextColor.copy(alpha = 0.5f)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Long-press any word in the reader to select it and create a rule.",
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center,
                                        color = readerTextColor.copy(alpha = 0.3f),
                                        modifier = Modifier.padding(horizontal = 32.dp)
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            ) {
                                items(textFilterRules) { rule ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(readerTextColor.copy(alpha = 0.04f))
                                            .border(0.5.dp, readerTextColor.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                            .padding(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(if (rule.isVanish) Color(0xFFEF5350).copy(alpha = 0.15f) else Color(0xFF66BB6A).copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (rule.isVanish) "🚫" else "✏️",
                                                fontSize = 12.sp
                                            )
                                        }
                                        
                                        Spacer(modifier = Modifier.width(12.dp))
                                        
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = rule.original,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = readerTextColor
                                                )
                                                if (!rule.isVanish) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Icon(Icons.Default.ArrowForward, contentDescription = null, tint = readerTextColor.copy(alpha = 0.5f), modifier = Modifier.size(12.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = rule.replacement,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp,
                                                        color = readerHighlightColor
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = if (rule.scope == "All Books") "All Books 🌍" else "Book: ${rule.bookId} 📖",
                                                fontSize = 10.sp,
                                                color = readerTextColor.copy(alpha = 0.5f)
                                            )
                                        }
                                        
                                        IconButton(
                                            onClick = {
                                                viewModel.saveRules(textFilterRules.filter { it.id != rule.id })
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete rule",
                                                tint = Color(0xFFEF5350)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "How it works: Enter the original word below to target it. Select 'Vanish' to hide it, or 'Replace' to swap it completely while reading.",
                            fontSize = 11.sp,
                            color = readerTextColor.copy(alpha = 0.6f),
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp, end = 4.dp)
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        ) {
                            Column {
                                Text("Match Word or Phrase", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = readerTextColor.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = 6.dp))
                                OutlinedTextField(
                                    value = manualOriginal,
                                    onValueChange = { manualOriginal = it },
                                    placeholder = { Text("E.g., forbidden word", fontSize = 13.sp, color = readerTextColor.copy(alpha = 0.4f)) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = readerHighlightColor,
                                        unfocusedBorderColor = readerTextColor.copy(alpha = 0.15f),
                                        focusedTextColor = readerTextColor,
                                        unfocusedTextColor = readerTextColor
                                    ),
                                    modifier = Modifier.fillMaxWidth().height(50.dp)
                                )
                            }
                            
                            Column {
                                Text("Action", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = readerTextColor.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = 6.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(38.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(readerTextColor.copy(alpha = 0.06f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (manualIsVanish) readerHighlightColor.copy(alpha = 0.2f) else Color.Transparent)
                                            .clickable { manualIsVanish = true },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Vanish 🚫",
                                            fontSize = 12.sp,
                                            fontWeight = if (manualIsVanish) FontWeight.Bold else FontWeight.Normal,
                                            color = if (manualIsVanish) readerHighlightColor else readerTextColor.copy(alpha = 0.8f)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (!manualIsVanish) readerHighlightColor.copy(alpha = 0.2f) else Color.Transparent)
                                            .clickable { manualIsVanish = false },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Replace ✏️",
                                            fontSize = 12.sp,
                                            fontWeight = if (!manualIsVanish) FontWeight.Bold else FontWeight.Normal,
                                            color = if (!manualIsVanish) readerHighlightColor else readerTextColor.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                            
                            if (!manualIsVanish) {
                                Column {
                                    Text("Replacement Text", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = readerTextColor.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = 6.dp))
                                    OutlinedTextField(
                                        value = manualReplacement,
                                        onValueChange = { manualReplacement = it },
                                        placeholder = { Text("E.g., happy", fontSize = 13.sp, color = readerTextColor.copy(alpha = 0.4f)) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = readerHighlightColor,
                                            unfocusedBorderColor = readerTextColor.copy(alpha = 0.15f),
                                            focusedTextColor = readerTextColor,
                                            unfocusedTextColor = readerTextColor
                                        ),
                                        modifier = Modifier.fillMaxWidth().height(50.dp)
                                    )
                                }
                            }
                            
                            Column {
                                Text("Scope", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = readerTextColor.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = 6.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(38.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(readerTextColor.copy(alpha = 0.06f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (manualScope == "All Books") readerHighlightColor.copy(alpha = 0.2f) else Color.Transparent)
                                            .clickable { manualScope = "All Books" },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "All Books 🌍",
                                            fontSize = 12.sp,
                                            fontWeight = if (manualScope == "All Books") FontWeight.Bold else FontWeight.Normal,
                                            color = if (manualScope == "All Books") readerHighlightColor else readerTextColor.copy(alpha = 0.8f)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (manualScope == "This Book") readerHighlightColor.copy(alpha = 0.2f) else Color.Transparent)
                                            .clickable { manualScope = "This Book" },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "This Book 📖",
                                            fontSize = 12.sp,
                                            fontWeight = if (manualScope == "This Book") FontWeight.Bold else FontWeight.Normal,
                                            color = if (manualScope == "This Book") readerHighlightColor else readerTextColor.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.weight(1f))
                            
                            Button(
                                onClick = {
                                    if (manualOriginal.isNotBlank()) {
                                        val newRule = FilterRule(
                                            id = java.util.UUID.randomUUID().toString(),
                                            original = manualOriginal,
                                            replacement = if (manualIsVanish) "" else manualReplacement,
                                            isVanish = manualIsVanish,
                                            scope = manualScope,
                                            bookId = activeNovel?.title ?: ""
                                        )
                                        viewModel.saveRules(textFilterRules + newRule)
                                        manualOriginal = ""
                                        manualReplacement = ""
                                        activeTab = 0
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (themeState == "Golden Oak") Color.Transparent else readerHighlightColor,
                                    contentColor = if (themeState == "Golden Oak") GoldOnGradient else Color.Black
                                ),
                                enabled = manualOriginal.isNotBlank(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .then(
                                        if (themeState == "Golden Oak") {
                                            Modifier.background(RefinedGoldGradient, RoundedCornerShape(8.dp))
                                        } else {
                                            Modifier
                                        }
                                    ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Save Rule", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        // Screen dimmer overlay removed by user request
        
        // CHAPTER CONTENTS LEFT SLIDEOUT DRAWER
        AnimatedVisibility(visible = showChapterDrawer, enter = fadeIn(), exit = fadeOut()) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.55f)).clickable { showChapterDrawer = false }) {
                Box(
                    modifier = Modifier.fillMaxHeight().width(280.dp).background(MaterialTheme.colorScheme.surface).clickable(enabled = false) {}
                        .animateEnterExit(enter = slideInHorizontally { -it } + fadeIn(), exit = slideOutHorizontally { -it } + fadeOut())
                ) {
                    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(22.dp), tint = readerHighlightColor)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Contents", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = readerTextColor, modifier = Modifier.weight(1f))
                            IconButton(onClick = { showChapterDrawer = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = readerTextColor)
                            }
                        }
                        HorizontalDivider(color = readerTextColor.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 12.dp))
                        LazyColumn(state = chaptersListState, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                            itemsIndexed(chapters) { index, chTitle ->
                                val isActive = index == activeChapterIndex
                                val isCompleted = activeNovel?.completedChapters?.contains(chTitle) == true
                                val textWeight = if (isActive) FontWeight.Bold else if (isCompleted) FontWeight.Medium else FontWeight.Normal
                                val titleColor = if (isActive) readerHighlightColor else if (isCompleted) Color.Gray else readerTextColor
                                Card(
                                    onClick = { viewModel.selectChapter(index); showChapterDrawer = false },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = if (isActive) readerHighlightColor.copy(alpha = 0.15f) else Color.Transparent),
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                ) {
                                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        val chUrl = activeNovel?.chapterUrls?.getOrNull(index)
                                        val isCached = if (chUrl != null) CrawlerEngine.isChapterCached(LocalContext.current, chUrl) else false
                                        Text(
                                            text = chTitle,
                                            fontWeight = textWeight,
                                            fontStyle = if (isCompleted) FontStyle.Italic else FontStyle.Normal,
                                            fontSize = 14.sp,
                                            color = titleColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isCached) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(
                                                imageVector = Icons.Default.Download,
                                                tint = readerHighlightColor,
                                                contentDescription = "Downloaded",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FontSelectionDialog(
    currentFont: String,
    onSelectFont: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val availableFonts = listOf(
        "System Default"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.6f)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.FontDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Reader Fonts", 
                        style = MaterialTheme.typography.titleLarge, 
                        fontWeight = FontWeight.Bold, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant, 
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 6.dp))
                
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(availableFonts) { font ->
                        FontItemRow(
                            font = font,
                            isSelected = currentFont == font,
                            onSelect = {
                                onSelectFont(font)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FontItemRow(
    font: String,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        onClick = onSelect,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = font,
                    fontFamily = getFontFamily(font),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    fontStyle = if (font == "Caveat") FontStyle.Italic else FontStyle.Normal
                )
                Text(
                    text = "A quick brown fox jumps over the lazy dog",
                    fontFamily = getFontFamily(font),
                    fontSize = 11.sp,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontStyle = if (font == "Caveat") FontStyle.Italic else FontStyle.Normal
                )
            }
            if (isSelected) {
                Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun FontDownloadProgressDialog(
    onFinished: () -> Unit,
    onDismiss: () -> Unit
) {
    var progress by remember { mutableStateOf(0f) }
    var stepText by remember { mutableStateOf("Initializing font pack stream...") }
    
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(800)
        progress = 0.15f
        stepText = "Connecting to Google Fonts Content Delivery Network..."
        kotlinx.coroutines.delay(1000)
        
        progress = 0.38f
        stepText = "Downloading Merriweather serif pack (1.4MB)..."
        kotlinx.coroutines.delay(1200)
        
        progress = 0.62f
        stepText = "Extracting Lora & Playfair Display italic curves..."
        kotlinx.coroutines.delay(1000)
        
        progress = 0.85f
        stepText = "Parsing handwritten Caveat glyph mappings..."
        kotlinx.coroutines.delay(900)
        
        progress = 1.0f
        stepText = "Optimizing system-level type rendering cache..."
        kotlinx.coroutines.delay(600)
        
        onFinished()
    }
    
    Dialog(onDismissRequest = {}) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Downloading Font Pack", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(8.dp))
                
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = stepText,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    minLines = 2
                )
            }
        }
    }
}

@Composable
fun ColorWheelPickerDialog(
    initialColorValue: Int,
    onColorSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedColorCode by remember { mutableStateOf(initialColorValue) }
    
    val presets = listOf(
        0xFFFFFF00.toInt(), // Lemon Yellow
        0xFF00FFFF.toInt(), // Cyan Sea Blue
        0xFFFF8A8A.toInt(), // Sunset Kiss Pink
        0xFFC1F80A.toInt(), // Radioactive Lime
        0xFFFF71D4.toInt(), // Orchid Lavender Pink
        0xFFFFC107.toInt(), // Amber Gold Accent
        0xFF90EE90.toInt()  // Soft Mint Green
    )
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth(0.95f)
        ) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.ColorLens, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Highlight Palette Wheel", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 12.dp))
                
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(CircleShape)
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                val position = change.position
                                val radius = size.width / 2f
                                val dx = position.x - radius
                                val dy = position.y - radius
                                val distance = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                                if (distance <= radius) {
                                    val angle = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                    val normalizedAngle = if (angle < 0) angle + 360f else angle
                                    val hsvColor = android.graphics.Color.HSVToColor(floatArrayOf(normalizedAngle, 0.85f, 0.95f))
                                    selectedColorCode = hsvColor
                                }
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasSize = size
                        val radius = canvasSize.width / 2f
                        
                        for (angleDegrees in 0 until 360 step 2) {
                            val hsvColor = android.graphics.Color.HSVToColor(floatArrayOf(angleDegrees.toFloat(), 0.85f, 0.95f))
                            drawArc(
                                color = Color(hsvColor),
                                startAngle = angleDegrees.toFloat(),
                                sweepAngle = 3f,
                                useCenter = true
                            )
                        }
                        
                        drawCircle(
                            color = Color.White,
                            radius = radius,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Select highlight tint on wheel, or pick modern presets below:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(10.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    presets.forEach { presetVal ->
                        val isSelectedPreset = selectedColorCode == presetVal
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(presetVal))
                                .border(
                                    width = if (isSelectedPreset) 3.dp else 1.dp,
                                    color = if (isSelectedPreset) MaterialTheme.colorScheme.primary else Color.Black.copy(0.15f),
                                    shape = CircleShape
                                )
                                .clickable { selectedColorCode = presetVal }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(selectedColorCode).copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth().border(1.dp, Color(selectedColorCode).copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                ) {
                    Text(
                        text = "Highlight active glow text sample using this custom color wheel color.",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(selectedColorCode),
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                val primaryColor = MaterialTheme.colorScheme.primary
                val (isPremiumBtn, btnGrad, btnContent) = getThemedButtonProps(primaryColor, Color.Black)
                Button(
                    onClick = { onColorSelected(selectedColorCode); onDismiss() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .then(
                            if (isPremiumBtn) {
                                Modifier.background(btnGrad, RoundedCornerShape(12.dp))
                            } else {
                                Modifier
                            }
                        ),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPremiumBtn) Color.Transparent else primaryColor,
                        contentColor = btnContent
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Apply Premium Highlight", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

private val fontFamilyCache = mutableMapOf<String, FontFamily>()

fun getFontFamily(fontName: String): FontFamily {
    return fontFamilyCache.getOrPut(fontName) {
        when (fontName) {
            "System Serif", "Serif" -> FontFamily.Serif
            "System Monospace", "Monospace" -> FontFamily.Monospace
            "System Cursive", "Cursive" -> FontFamily.Cursive
            "Midnight Cafe Society" -> try { FontFamily(ComposeFont(R.font.midnightcafesociety_regular)) } catch (e: Exception) { android.util.Log.e("Lorelight", "Failed to load Midnight Cafe Society font", e); return FontFamily.Default }
            "Peach Club Script" -> try { FontFamily(ComposeFont(R.font.peach_club_script_ttf)) } catch (e: Exception) { android.util.Log.e("Lorelight", "Failed to load Peach Club Script font", e); return FontFamily.Default }
            "Smart Notes" -> try { FontFamily(ComposeFont(R.font.smart_notes)) } catch (e: Exception) { android.util.Log.e("Lorelight", "Failed to load Smart Notes font", e); return FontFamily.Default }
            "Twilight Luminance" -> try { FontFamily(ComposeFont(R.font.twilight_luminance_free)) } catch (e: Exception) { android.util.Log.e("Lorelight", "Failed to load Twilight Luminance font", e); return FontFamily.Default }
            else -> FontFamily.Default
        }
    }
}

@Composable
fun AlignmentIcon(align: String, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(16.dp)) {
        val width = size.width
        val height = size.height
        val lineSpacing = height / 4
        
        when (align) {
            "Left" -> {
                drawLine(color = tint, start = Offset(0f, lineSpacing * 1), end = Offset(width * 0.9f, lineSpacing * 1), strokeWidth = 2.dp.toPx())
                drawLine(color = tint, start = Offset(0f, lineSpacing * 2), end = Offset(width * 0.6f, lineSpacing * 2), strokeWidth = 2.dp.toPx())
                drawLine(color = tint, start = Offset(0f, lineSpacing * 3), end = Offset(width * 0.8f, lineSpacing * 3), strokeWidth = 2.dp.toPx())
            }
            "Center" -> {
                drawLine(color = tint, start = Offset(width * 0.1f, lineSpacing * 1), end = Offset(width * 0.9f, lineSpacing * 1), strokeWidth = 2.dp.toPx())
                drawLine(color = tint, start = Offset(width * 0.25f, lineSpacing * 2), end = Offset(width * 0.75f, lineSpacing * 2), strokeWidth = 2.dp.toPx())
                drawLine(color = tint, start = Offset(width * 0.15f, lineSpacing * 3), end = Offset(width * 0.85f, lineSpacing * 3), strokeWidth = 2.dp.toPx())
            }
            "Right" -> {
                drawLine(color = tint, start = Offset(width * 0.1f, lineSpacing * 1), end = Offset(width, lineSpacing * 1), strokeWidth = 2.dp.toPx())
                drawLine(color = tint, start = Offset(width * 0.4f, lineSpacing * 2), end = Offset(width, lineSpacing * 2), strokeWidth = 2.dp.toPx())
                drawLine(color = tint, start = Offset(width * 0.2f, lineSpacing * 3), end = Offset(width, lineSpacing * 3), strokeWidth = 2.dp.toPx())
            }
            "Justify" -> {
                drawLine(color = tint, start = Offset(0f, lineSpacing * 1), end = Offset(width, lineSpacing * 1), strokeWidth = 2.dp.toPx())
                drawLine(color = tint, start = Offset(0f, lineSpacing * 2), end = Offset(width, lineSpacing * 2), strokeWidth = 2.dp.toPx())
                drawLine(color = tint, start = Offset(0f, lineSpacing * 3), end = Offset(width * 0.7f, lineSpacing * 3), strokeWidth = 2.dp.toPx())
            }
        }
    }
}

@Composable
fun FloatingStylePanel(
    fontSize: Float,
    lineSpacing: Float,
    paragraphSpacing: Float,
    textAlignment: String,
    onClose: () -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onLineSpacingChange: (Float) -> Unit,
    onParagraphSpacingChange: (Float) -> Unit,
    onAlignmentChange: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        modifier = Modifier.fillMaxWidth().wrapContentHeight()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Drag handle or indicator line for a native look
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                    .align(Alignment.CenterHorizontally)
            )
            
            Spacer(modifier = Modifier.height(12.dp))

            // HEADER
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.FontDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Customize Typography",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Adjust reading layout & spacing",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Panel",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            // SLIDERS & CONTROLS SECTION
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left Column: Font Size & Line Spacing
                Column(modifier = Modifier.weight(1.1f)) {
                    // Size
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Size",
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${fontSize.toInt()}sp",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = fontSize,
                        onValueChange = onFontSizeChange,
                        valueRange = 12f..32f,
                        colors = SliderDefaults.colors(
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.height(28.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Line Spacing
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Line Spacing",
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${"%.1fx".format(lineSpacing)}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = lineSpacing,
                        onValueChange = onLineSpacingChange,
                        valueRange = 1.0f..2.5f,
                        colors = SliderDefaults.colors(
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.height(28.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Paragraph Spacing
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Paragraph Spacing",
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${paragraphSpacing.toInt()}dp",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = paragraphSpacing,
                        onValueChange = onParagraphSpacingChange,
                        valueRange = 0f..40f,
                        colors = SliderDefaults.colors(
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.height(28.dp)
                    )
                }

                // Right Column: Text Alignment Options
                Column(modifier = Modifier.weight(0.9f)) {
                    Text(
                        text = "Alignment",
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("Left", "Center", "Right", "Justify").forEach { align ->
                            val isSelected = textAlignment == align
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer 
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onAlignmentChange(align) },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    AlignmentIcon(
                                        align = align,
                                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = align,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 10.sp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WordSpanView(
    token: Token,
    isSelected: Boolean,
    highlightColor: Color,
    textColor: Color,
    fontSize: Float,
    currentFontFamily: androidx.compose.ui.text.font.FontFamily?,
    isCaveat: Boolean,
    onPositioned: (Offset, Offset) -> Unit,
    onLongPress: () -> Unit
) {
    Box(
        modifier = Modifier
            .graphicsLayer {
                // Cache word rendering onto a hardware GPU layer to eliminate subpixel layout/font-hinting wiggles during scroll/drag
                clip = false
            }
            .pointerInput(token.wordIdx) {
                detectTapGestures(
                    onLongPress = {
                        onLongPress()
                    }
                )
            }
            .onGloballyPositioned { coords ->
                if (coords.isAttached) {
                    val h = coords.size.height.toFloat()
                    val w = coords.size.width.toFloat()
                    val leftAnchor = coords.localToRoot(Offset(0f, h))
                    val rightAnchor = coords.localToRoot(Offset(w, h))
                    onPositioned(leftAnchor, rightAnchor)
                }
            }
    ) {
        Text(
            text = token.raw,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = fontSize.sp,
                fontFamily = currentFontFamily,
                fontStyle = if (isCaveat) FontStyle.Italic else FontStyle.Normal
            ),
            color = if (isSelected) highlightColor else textColor,
            modifier = Modifier.padding(horizontal = 0.5.dp)
        )
    }
}

@Composable
fun SentenceRow(
    index: Int,
    sentence: String,
    isActiveSentence: Boolean,
    readerMode: String,
    textColor: Color,
    readerHighlightColor: Color,
    readerTextColor: Color,
    currentFontFamily: androidx.compose.ui.text.font.FontFamily?,
    isCaveat: Boolean,
    alignStyle: TextAlign,
    textAlignment: String,
    fontSize: Float,
    lineSpacing: Float,
    paragraphSpacing: Float,
    selRange: WordRange?,
    sentenceTokens: List<Token>,
    wordPositions: androidx.compose.runtime.snapshots.SnapshotStateMap<Int, WordPosition>,
    onSentenceClick: () -> Unit,
    onSentenceLongClick: () -> Unit,
    onWordSelected: (WordRange) -> Unit,
    onPositioned: (Int, Offset, Offset) -> Unit
) {
    val paragraphWordIndices = remember(sentenceTokens) {
        val wordTokens = sentenceTokens.filter { it.isWord && it.wordIdx >= 0 }
        if (wordTokens.isEmpty()) null else wordTokens.first().wordIdx..wordTokens.last().wordIdx
    }
    val hasOverlap = remember(selRange, paragraphWordIndices) {
        if (selRange == null || paragraphWordIndices == null) false else {
            val selMin = minOf(selRange.start, selRange.end)
            val selMax = maxOf(selRange.start, selRange.end)
            paragraphWordIndices.start <= selMax && paragraphWordIndices.endInclusive >= selMin
        }
    }

    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    Box(
        modifier = Modifier
            .graphicsLayer {
                // Bundle each paragraph container into its own GPU display list, avoiding continuous CPU-bound redraw invalidations
                clip = false
            }
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onSentenceClick,
                onLongClick = onSentenceLongClick
            )
            .padding(horizontal = 12.dp, vertical = (paragraphSpacing / 4).dp)
    ) {
        if (!hasOverlap) {
            Text(
                text = sentence,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * lineSpacing).sp,
                    fontWeight = if (isActiveSentence) FontWeight.Bold else FontWeight.Normal,
                    fontFamily = currentFontFamily,
                    fontStyle = if (isCaveat) FontStyle.Italic else FontStyle.Normal,
                    textAlign = alignStyle
                ),
                color = textColor,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            val flowAlign = when (textAlignment) {
                "Center" -> Arrangement.Center
                "Right" -> Arrangement.End
                else -> Arrangement.Start
            }
            
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = flowAlign,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = (paragraphSpacing / 4).dp)
            ) {
                sentenceTokens.forEach { token ->
                    if (token.raw.isNotEmpty()) {
                        if (token.isWord && token.wordIdx >= 0) {
                            DisposableEffect(token.wordIdx) {
                                onDispose {
                                    wordPositions.remove(token.wordIdx)
                                }
                            }
                            
                            val isSelected = token.wordIdx in minOf(selRange!!.start, selRange.end)..maxOf(selRange.start, selRange.end)
                            
                            WordSpanView(
                                token = token,
                                isSelected = isSelected,
                                highlightColor = readerHighlightColor,
                                textColor = textColor,
                                fontSize = fontSize,
                                currentFontFamily = currentFontFamily,
                                isCaveat = isCaveat,
                                onPositioned = { left, right ->
                                    onPositioned(token.wordIdx, left, right)
                                },
                                onLongPress = {
                                    onWordSelected(WordRange(token.wordIdx, token.wordIdx))
                                }
                            )
                        } else {
                            Text(
                                text = token.raw,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = fontSize.sp,
                                    lineHeight = (fontSize * lineSpacing).sp,
                                    fontWeight = if (isActiveSentence) FontWeight.Bold else FontWeight.Normal,
                                    fontFamily = currentFontFamily,
                                    fontStyle = if (isCaveat) FontStyle.Italic else FontStyle.Normal
                                ),
                                color = textColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun rememberPremiumKineticFlingBehavior(): androidx.compose.foundation.gestures.FlingBehavior {
    val decaySpec = remember {
        androidx.compose.animation.core.exponentialDecay<Float>(
            // Increased friction coefficient makes scroll feel more grounded and less overly fluid/ice-like
            frictionMultiplier = 0.38f,
            absVelocityThreshold = 0.1f
        )
    }
    return remember(decaySpec) {
        object : androidx.compose.foundation.gestures.FlingBehavior {
            override suspend fun androidx.compose.foundation.gestures.ScrollScope.performFling(initialVelocity: Float): Float {
                val animatable = androidx.compose.animation.core.Animatable(0f)
                var lastValue = 0f
                var velocityLeft = initialVelocity
                try {
                    // Lock animation loop strictly into display V-Sync refresh rate cycles (requestAnimationFrame equivilent)
                    animatable.animateDecay(
                        initialVelocity = initialVelocity,
                        animationSpec = decaySpec
                    ) {
                        val stepVelocity = velocity
                        val delta = value - lastValue
                        val consumed = scrollBy(delta)
                        lastValue = value
                        velocityLeft = stepVelocity
                        if (Math.abs(delta - consumed) > 0.5f) {
                            throw Exception("Bounds hit")
                        }
                    }
                } catch (e: Exception) {
                    // Handled cleanly on boundaries
                }
                return velocityLeft
            }
        }
    }
}