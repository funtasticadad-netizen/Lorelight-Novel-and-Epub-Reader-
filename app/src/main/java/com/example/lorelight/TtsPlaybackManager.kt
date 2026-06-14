package com.example.lorelight

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

object TtsPlaybackManager : AudioManager.OnAudioFocusChangeListener {

    private const val TAG = "TtsPlaybackManager"
    private var isInitialized = false
    private lateinit var appContext: Context
    private var tts: TextToSpeech? = null
    
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var wasPlayingBeforeFocusLoss = false

    // TTS state flows mirrored from ReaderViewModel
    val isTtsReady = MutableStateFlow(false)
    val availableVoices = MutableStateFlow<List<Voice>>(emptyList())
    val selectedVoice = MutableStateFlow<Voice?>(null)
    
    val speechRate = MutableStateFlow(2.0f)
    val speechPitch = MutableStateFlow(1.0f)

    // Sleep Timer
    val sleepTimerMinutes = MutableStateFlow<Int?>(null)
    private var sleepTimerJob: Job? = null
    private var prefetchJob: Job? = null

    // Voice accent filtering
    val allVoices = MutableStateFlow<List<Voice>>(emptyList())
    val availableAccents = MutableStateFlow<List<String>>(listOf("United States (US)"))
    val selectedAccent = MutableStateFlow("United States (US)")
    val filteredVoices = MutableStateFlow<List<Voice>>(emptyList())

    // Playback state flows
    val activeNovel = MutableStateFlow<Novel?>(null)
    val chapters = MutableStateFlow<List<String>>(emptyList())
    val activeChapterIndex = MutableStateFlow(0)
    val activeSentenceIndex = MutableStateFlow(0)
    val sentences = MutableStateFlow<List<String>>(emptyList())
    val isPlaying = MutableStateFlow(false)
    val isFetchingChapter = MutableStateFlow(false)

    // Battery Saving & Background States
    val isAppInBackground = MutableStateFlow(false)

    // Text Filter Rules
    val textFilterRules = MutableStateFlow<List<FilterRule>>(emptyList())

    private val managerScope = CoroutineScope(Dispatchers.Main + Job())

    val activeNovelTitle = activeNovel.map { it?.title ?: "" }.distinctUntilChanged()

    val filteredSentences = MutableStateFlow<List<String>>(emptyList())

    // TTS error state flow
    val errorMessage = MutableStateFlow<String?>(null)

    fun recalculateFilteredSentences() {
        val rawSentences = sentences.value
        val rules = textFilterRules.value
        val bookTitle = activeNovel.value?.title ?: ""
        filteredSentences.value = if (rawSentences.isEmpty()) {
            emptyList()
        } else {
            rawSentences.map { sentence ->
                TextFilter.applyRawStringRules(sentence, rules, bookTitle)
            }
        }
    }

    private fun updateSentences(newSentences: List<String>) {
        sentences.value = newSentences
        recalculateFilteredSentences()
    }

    fun init(context: Context) {
        if (isInitialized) return
        appContext = context.applicationContext
        audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        isInitialized = true

        loadRules()

        // Load saved settings
        val prefs = appContext.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
        speechRate.value = prefs.getSafeFloat("speech_rate", 2.0f)
        speechPitch.value = prefs.getSafeFloat("speech_pitch", 1.0f)
        selectedAccent.value = prefs.getSafeString("selected_accent", "United States (US)") ?: "United States (US)"

        // Observers to save preferences
        managerScope.launch { speechRate.collect { prefs.edit().putFloat("speech_rate", it).apply() } }
        managerScope.launch { speechPitch.collect { prefs.edit().putFloat("speech_pitch", it).apply() } }
        managerScope.launch { selectedVoice.collect { it?.let { prefs.edit().putString("selected_voice_name", it.name).apply() } } }
        managerScope.launch { activeSentenceIndex.collect { saveProgress() } }
        managerScope.launch { selectedAccent.collect { prefs.edit().putString("selected_accent", it).apply() } }
        managerScope.launch { textFilterRules.collect { recalculateFilteredSentences() } }
        managerScope.launch {
            activeNovel
                .map { it?.title }
                .distinctUntilChanged()
                .collect { recalculateFilteredSentences() }
        }
        managerScope.launch {
            isAppInBackground.collect { isInBg ->
                DiagnosticLogger.logEngineStatus("[Background] App background state changed: isBg=$isInBg")
                forceFlushProgress()
            }
        }

        // Polling loop removed entirely to make speech playback 100% callback-driven, eliminating stutters and saving massive battery.
        initializeTts()
        setupServiceSync()
    }

    private fun setupServiceSync() {
        TtsPlaybackService.onPlayPauseAction = {
            togglePlayPause()
        }
        TtsPlaybackService.onPlayAction = {
            startSpeaking()
        }
        TtsPlaybackService.onPauseAction = {
            pauseSpeaking()
        }
        TtsPlaybackService.onNextAction = {
            skipForward()
        }
        TtsPlaybackService.onPrevAction = {
            skipBackward()
        }
        TtsPlaybackService.onStopAction = {
            stopSpeaking()
        }

        managerScope.launch {
            kotlinx.coroutines.flow.combine(isPlaying, activeNovel, activeSentenceIndex, filteredSentences) { _, _, _, _ -> }
                .collect {
                    val novel = activeNovel.value
                    if (novel != null && filteredSentences.value.isNotEmpty()) {
                        pushPlaybackServiceUpdate()
                    } else {
                        stopPlaybackService()
                    }
                }
        }
    }

    private fun pushPlaybackServiceUpdate() {
        val novel = activeNovel.value ?: return
        val text = filteredSentences.value.getOrNull(activeSentenceIndex.value) ?: ""
        TtsPlaybackService.currentCoverBase64 = novel.coverImageBase64
        val intent = Intent(appContext, TtsPlaybackService::class.java).apply {
            action = TtsPlaybackService.ACTION_UPDATE
            putExtra(TtsPlaybackService.EXTRA_TITLE, novel.title)
            putExtra(TtsPlaybackService.EXTRA_AUTHOR, novel.author)
            putExtra(TtsPlaybackService.EXTRA_PLAYING, isPlaying.value)
            putExtra(TtsPlaybackService.EXTRA_TEXT, text)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service", e)
        }
    }

    private fun stopPlaybackService() {
        try {
            appContext.stopService(Intent(appContext, TtsPlaybackService::class.java))
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun initializeTts() {
        Log.d(TAG, "Initializing TTS with com.google.android.tts...")
        DiagnosticLogger.logEngineStatus("Initializing primary Google TTS engine (com.google.android.tts)")
        errorMessage.value = null
        try {
            tts = TextToSpeech(appContext, { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isTtsReady.value = true
                    errorMessage.value = null
                    DiagnosticLogger.logEngineStatus("Primary Google TTS engine initialized successfully.")
                    setupVoices()
                } else {
                    Log.e(TAG, "Failed to initialize Google TTS engine, attempting fallback to default engine...")
                    DiagnosticLogger.logEngineStatus("Failed to initialize Google TTS engine, status code=$status. Invoking fallback...")
                    fallbackInitializeTts()
                }
            }, "com.google.android.tts")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create com.google.android.tts, trying fallback", e)
            DiagnosticLogger.logEngineStatus("Primary Google TTS constructor exception: ${e.message}. Invoking fallback...")
            fallbackInitializeTts()
        }
    }

    private fun fallbackInitializeTts() {
        try {
            DiagnosticLogger.logEngineStatus("Initializing fallback system default TTS engine...")
            tts = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    Log.d(TAG, "Successfully initialized fallback default platform TTS engine")
                    isTtsReady.value = true
                    errorMessage.value = null
                    DiagnosticLogger.logEngineStatus("Fallback platform system default TTS engine initialized successfully.")
                    setupVoices()
                } else {
                    Log.e(TAG, "Failed to initialize default platform TTS engine as well.")
                    isTtsReady.value = false
                    errorMessage.value = "Failed to initialize TTS. Please configure a Text-To-Speech engine in your Android settings."
                    DiagnosticLogger.logEngineStatus("Critical: Fallback TTS engine failed to initialize, status code=$status.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create default platform TTS engine", e)
            isTtsReady.value = false
            errorMessage.value = "Failed to create local platform TTS component: ${e.message ?: "Unknown error"}"
            DiagnosticLogger.logEngineStatus("Critical: Fallback TTS constructor exception: ${e.message}")
        }
    }

    private fun setupVoices() {
        val engine = tts ?: return
        try {
            engine.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    DiagnosticLogger.logEngineStatus("TTS Utterance OnStart: id=$utteranceId")
                }
                override fun onDone(utteranceId: String?) {
                    DiagnosticLogger.logEngineStatus("TTS Utterance OnDone: id=$utteranceId")
                    managerScope.launch(Dispatchers.Main) { playNextSentenceAutomatically() }
                }
                @Deprecated("Deprecated in Java", ReplaceWith("isPlaying.value = false"))
                override fun onError(utteranceId: String?) {
                    managerScope.launch(Dispatchers.Main) { 
                        isPlaying.value = false 
                        errorMessage.value = "TTS engine reported a speak processing error."
                        DiagnosticLogger.logEngineStatus("TTS Utterance OnError(deprecated): id=$utteranceId")
                        CrashReporter.recordError(appContext, TAG, Exception("TTS speak processing error"), "TTS_PLAYBACK_CALLBACK_ERROR")
                    }
                }
                override fun onError(utteranceId: String?, errorCode: Int) {
                    managerScope.launch(Dispatchers.Main) {
                        isPlaying.value = false
                        val errorStr = when (errorCode) {
                            -14 -> "Audio resource capacity exceeded"
                            -13 -> "Invalid TTS request configuration"
                            -11 -> "Network issue: Selected voice requires internet access"
                            -12 -> "Network connection timeout"
                            -6 -> "Voice asset is still downloading or not fully installed on device"
                            -5 -> "TTS playback is mocked or restricted"
                            -4 -> "TTS engine service connection failure"
                            -3 -> "Synthesis parsing issue or corrupted input string"
                            else -> "Internal Text-to-Speech service error (code $errorCode)"
                        }
                        errorMessage.value = "TTS Error: $errorStr"
                        DiagnosticLogger.logEngineStatus("TTS Utterance OnError: id=$utteranceId, code=$errorCode ($errorStr)")
                        CrashReporter.recordError(appContext, TAG, Exception("TTS Callback Error ($errorCode): $errorStr"), "TTS_PLAYBACK_CALLBACK_ERROR")
                    }
                }
            })

            val voiceList = engine.voices?.toList() ?: emptyList()
            val enVoices = voiceList.filter {
                it.locale.language.equals("en", ignoreCase = true)
            }.sortedBy { it.name }

            allVoices.value = enVoices

            val countryMapping = mapOf(
                "US" to "United States (US)",
                "GB" to "United Kingdom (UK)",
                "IN" to "India (IN)",
                "AU" to "Australia (AU)",
                "CA" to "Canada (CA)",
                "IE" to "Ireland (IE)",
                "SG" to "Singapore (SG)",
                "ZA" to "South Africa (ZA)"
            )

            val foundCountries = enVoices.mapNotNull {
                val c = it.locale.country.uppercase()
                if (c.isNotEmpty()) c else null
            }.distinct()

            val accentList = foundCountries.map { code ->
                countryMapping[code] ?: "${Locale(Locale.ENGLISH.language, code).displayCountry} ($code)"
            }.sortedBy { it }

            val finalAccentList = if (accentList.any { it.contains("(US)") }) {
                accentList
            } else {
                listOf("United States (US)") + accentList
            }
            availableAccents.value = finalAccentList

            val savedAccent = appContext.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE).getSafeString("selected_accent", "United States (US)") ?: "United States (US)"
            if (finalAccentList.contains(savedAccent)) {
                selectedAccent.value = savedAccent
            } else {
                selectedAccent.value = "United States (US)"
            }

            updateFilteredVoicesAndSelectDefault()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateFilteredVoicesAndSelectDefault() {
        val engine = tts ?: return
        try {
            val currentAccent = selectedAccent.value
            val countryCode = getCountryCodeFromAccent(currentAccent)

            val filt = allVoices.value.filter {
                it.locale.country.equals(countryCode, ignoreCase = true)
            }.sortedBy { it.name }

            filteredVoices.value = filt.ifEmpty { allVoices.value }
            availableVoices.value = filteredVoices.value

            val savedName = appContext.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE).getSafeString("selected_voice_name", null)
            val defaultChoice = if (savedName != null) filteredVoices.value.find { it.name == savedName } else null
                ?: filteredVoices.value.find { it.name.contains("en-us-x-iom", ignoreCase = true) }
                ?: filteredVoices.value.find { it.name.contains("iom", ignoreCase = true) }
                ?: filteredVoices.value.firstOrNull()
                ?: engine.voice

            defaultChoice?.let {
                selectedVoice.value = it
                engine.voice = it
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to select default voice or retrieve voice properties from engine", e)
        }
    }

    private fun getCountryCodeFromAccent(accent: String): String {
        val startIndex = accent.indexOf('(')
        val endIndex = accent.indexOf(')')
        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            val code = accent.substring(startIndex + 1, endIndex)
            if (code.equals("UK", ignoreCase = true)) return "GB"
            return code
        }
        return "US"
    }

    fun selectAccentAndApply(accent: String) {
        selectedAccent.value = accent
        updateFilteredVoicesAndSelectDefault()
        filteredVoices.value.firstOrNull()?.let {
            selectVoiceAndApply(it)
        }
    }

    fun startReading(novel: Novel, startChapterName: String? = null) {
        stopSpeaking()
        activeNovel.value = novel
        chapters.value = novel.chapters
        
        val targetChapter = startChapterName ?: novel.currentChapter
        val initialIdx = novel.chapters.indexOf(targetChapter).takeIf { it >= 0 } ?: 0
        
        val startParaIdx = if (startChapterName == null || startChapterName == novel.currentChapter) {
            novel.currentParagraphIndex
        } else {
            0
        }
        
        selectChapter(initialIdx, startParaIdx)
    }

    private var lastSaveTime = 0L
    private var sentenceCountSinceSave = 0

    fun forceFlushProgress() {
        val novel = activeNovel.value ?: return
        writeNovelProgressToDiskImmediate(novel)
    }

    private fun writeNovelProgressToDiskImmediate(novel: Novel) {
        try {
            val list = loadNovelsFromPrefs(appContext)
            val updatedList = list.map {
                if (it.title == novel.title) novel else it
            }
            saveNovelsToPrefs(appContext, updatedList)
            lastSaveTime = System.currentTimeMillis()
            sentenceCountSinceSave = 0
            DiagnosticLogger.logEngineStatus("[BatterySave] Progress flushed to storage disk for '${novel.title}' at sentence ${novel.currentParagraphIndex}.")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun saveProgress() {
        val currentContextNovel = activeNovel.value ?: return
        val currentChapterName = chapters.value.getOrNull(activeChapterIndex.value) ?: return

        val updatedNovel = currentContextNovel.copy(
            currentChapter = currentChapterName,
            currentParagraphIndex = activeSentenceIndex.value,
            lastReadTimestamp = System.currentTimeMillis()
        )
        activeNovel.value = updatedNovel
        saveNovelProgressLocally(updatedNovel)
    }

    fun saveDetailedProgress(paragraphIndex: Int, scrollOffset: Int, wordIndex: Int) {
        val currentContextNovel = activeNovel.value ?: return
        val currentChapterName = chapters.value.getOrNull(activeChapterIndex.value) ?: return

        val updatedNovel = currentContextNovel.copy(
            currentChapter = currentChapterName,
            currentParagraphIndex = paragraphIndex,
            currentScrollOffset = scrollOffset,
            currentWordIndex = wordIndex,
            lastReadTimestamp = System.currentTimeMillis()
        )
        activeNovel.value = updatedNovel
        saveNovelProgressLocally(updatedNovel)
    }

    private fun saveNovelProgressLocally(novel: Novel) {
        val now = System.currentTimeMillis()
        val isBg = isAppInBackground.value

        // Buffer limits optimized for background states
        val timeLimit = if (isBg) 30000L else 12000L // 30s in bg, 12s in normal active foreground
        val sentenceLimit = if (isBg) 12 else 5 // 12 sentences in bg, 5 sentences in normal active foreground

        sentenceCountSinceSave++

        if (now - lastSaveTime >= timeLimit || sentenceCountSinceSave >= sentenceLimit) {
            writeNovelProgressToDiskImmediate(novel)
        } else {
            // Keep memory state 100% reactive, but defer persistent storage write to save battery
            android.util.Log.d(TAG, "Progress cached in memory: sentence count = $sentenceCountSinceSave/$sentenceLimit")
        }
    }

    fun selectChapter(index: Int, initialParagraphIndex: Int = 0, autoPlay: Boolean = false) {
        if (chapters.value.isEmpty()) return
        val previousIdx = activeChapterIndex.value
        val safeIndex = index.coerceIn(0, chapters.value.lastIndex)
        val wasPlaying = isPlaying.value || autoPlay
        val chName = chapters.value.getOrNull(safeIndex) ?: "Chapter"
        DiagnosticLogger.logEngineStatus("Request to change chapter to index $safeIndex ($chName), targetParagraph=$initialParagraphIndex, autoplay=$autoPlay")
        
        if (safeIndex != previousIdx) {
            val prevChapterName = chapters.value.getOrNull(previousIdx)
            val currentContextNovel = activeNovel.value
            if (prevChapterName != null && currentContextNovel != null) {
                if (!currentContextNovel.completedChapters.contains(prevChapterName)) {
                    val updatedList = currentContextNovel.completedChapters + prevChapterName
                    val updatedNovel = currentContextNovel.copy(completedChapters = updatedList)
                    activeNovel.value = updatedNovel
                    saveNovelProgressLocally(updatedNovel)
                }
            }
        }
        
        activeChapterIndex.value = safeIndex

        // Delayed Prefetching: ALWAYS prefetch to prevent TTS interruptions, scheduled on a 3s delay.
        prefetchJob?.cancel()
        val prefetchDelay = 3000L
        prefetchJob = managerScope.launch {
            delay(prefetchDelay)
            val currentNovel = activeNovel.value
            val nextIndex = safeIndex + 1
            if (currentNovel != null && nextIndex < currentNovel.chapterUrls.size) {
                val nextUrl = currentNovel.chapterUrls[nextIndex]
                if (!nextUrl.isNullOrEmpty()) {
                    withContext(Dispatchers.IO) {
                        if (!CrawlerEngine.isChapterCached(appContext, nextUrl)) {
                            try {
                                android.util.Log.d("TtsPlaybackManager", "Smart prefetching next chapter URL in background: $nextUrl")
                                CrawlerEngine.extractChapterText(appContext, nextUrl)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }
        }
        
        val novel = activeNovel.value
        if (novel != null && novel.chapterUrls.size > safeIndex) {
            val url = novel.chapterUrls[safeIndex]
            val cachedText = CrawlerEngine.getCachedChapterText(appContext, url)
            if (cachedText != null) {
                val sList = cachedText.split(Regex("\\n+")).map { it.trim() }.filter { it.isNotEmpty() }
                updateSentences(sList.ifEmpty { listOf("Chapter content is empty or could not be loaded.") })
                isFetchingChapter.value = false
                
                val sSize = sentences.value.size
                val targetPara = if (initialParagraphIndex == -1) {
                    (sSize - 1).coerceAtLeast(0)
                } else {
                    initialParagraphIndex.coerceIn(0, (sSize - 1).coerceAtLeast(0))
                }
                activeSentenceIndex.value = targetPara
                if (wasPlaying) {
                    isPlaying.value = true
                    startSpeaking()
                } else {
                    pauseSpeaking()
                }
                saveProgress()
            } else {
                isFetchingChapter.value = true
                updateSentences(listOf("Loading chapter..."))
                activeSentenceIndex.value = if (initialParagraphIndex == -1) 0 else initialParagraphIndex
                tts?.stop()
                
                managerScope.launch {
                    try {
                        val text = CrawlerEngine.extractChapterText(appContext, url)
                        val sList = text.split(Regex("\\n+")).map { it.trim() }.filter { it.isNotEmpty() }
                        updateSentences(sList.ifEmpty { listOf("Chapter content is empty or could not be loaded.") })
                    } catch (e: Exception) {
                        updateSentences(listOf("Error loading chapter: ${e.localizedMessage}"))
                    } finally {
                        isFetchingChapter.value = false
                        val sSize = sentences.value.size
                        val targetPara = if (initialParagraphIndex == -1) {
                            (sSize - 1).coerceAtLeast(0)
                        } else {
                            initialParagraphIndex.coerceIn(0, (sSize - 1).coerceAtLeast(0))
                        }
                        activeSentenceIndex.value = targetPara
                        if (wasPlaying) {
                            isPlaying.value = true
                            startSpeaking()
                        } else {
                            pauseSpeaking()
                        }
                        saveProgress()
                    }
                }
            }
        } else {
            val name = chapters.value.getOrNull(safeIndex) ?: "Chapter"
            val text = index.let { generateMockChapterContent(safeIndex, name) }
            val sList = text.split(Regex("\\n+")).map { it.trim() }.filter { it.isNotEmpty() }
            updateSentences(sList)
            val targetPara = if (initialParagraphIndex == -1) {
                (sList.size - 1).coerceAtLeast(0)
            } else {
                initialParagraphIndex.coerceIn(0, (sList.size - 1).coerceAtLeast(0))
            }
            activeSentenceIndex.value = targetPara
            if (wasPlaying) {
                isPlaying.value = true
                startSpeaking()
            } else {
                pauseSpeaking()
            }
            saveProgress()
        }
    }

    private fun generateMockChapterContent(index: Int, name: String): String {
        return "$name\n\nChapter overview and focus guide:\n\n" +
               "Every story has a beginning, a rising tide of circumstances, and moments of quiet revelation. " +
               "As you immerse yourself in $name, let the words flow at your preferred paced speed. " +
               "Use the speed controller below to adjust the TTS pacing, and use the bookmarks to save your favorite passages. " +
               "In this section of the work, the author further expands on the central conflicts, " +
               "introducing subtle atmospheric descriptions and developing the characters' underlying journeys. " +
               "Take a deep breath and enjoy your serene, focused reading session."
    }

    fun togglePlayPause() {
        if (isPlaying.value) pauseSpeaking() else startSpeaking()
    }

    fun startSpeaking() {
        if (!isTtsReady.value) return
        val sList = filteredSentences.value
        if (sList.isEmpty()) return
        if (isFetchingChapter.value) return

        if (!requestAudioFocus()) {
            Log.e(TAG, "Failed to get audio focus")
            return
        }

        val sIdx = activeSentenceIndex.value.coerceIn(0, sList.lastIndex)
        activeSentenceIndex.value = sIdx
        isPlaying.value = true

        val speakingText = sList.getOrNull(sIdx) ?: ""
        DiagnosticLogger.logProcessedText(speakingText)
        DiagnosticLogger.logEngineStatus("Attempting speak on sentence index=$sIdx. Rate=${speechRate.value}, Pitch=${speechPitch.value}, Voice=${selectedVoice.value?.name ?: "Default"}")

        val engine = tts ?: return
        try {
            engine.setSpeechRate(speechRate.value)
            engine.setPitch(speechPitch.value)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply speech variables", e)
        }
        
        try {
            selectedVoice.value?.let { engine.voice = it }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply selected voice, continuing with default", e)
        }

        val utteranceId = "reader_utt"
        val params = Bundle().apply { putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId) }
        
        try {
            val result = engine.speak(sList[sIdx], TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            if (result == TextToSpeech.ERROR) {
                Log.e(TAG, "engine.speak returned ERROR code, attempting voice-agnostic fallback...")
                errorMessage.value = "TTS engine returned playback error. Attempting voice-agnostic fallback..."
                try {
                    // Try to reset voice to default platform default and try speaking again
                    engine.voice = engine.defaultVoice
                    val fallbackResult = engine.speak(sList[sIdx], TextToSpeech.QUEUE_FLUSH, params, utteranceId)
                    if (fallbackResult == TextToSpeech.ERROR) {
                        errorMessage.value = "Playback failed. Please verify your selected voice and device speaker configuration."
                        isPlaying.value = false
                        CrashReporter.recordError(appContext, TAG, Exception("TTS speak call returned ERROR even with default voice"), "TTS_PLAYBACK_SPEAK_ERROR")
                    } else {
                        errorMessage.value = null // clear on successful fallback
                    }
                } catch (ex: Exception) {
                    Log.e(TAG, "Voiced and default engine speak failed", ex)
                    errorMessage.value = "TTS fallback speak failed: ${ex.message ?: "Unknown error"}"
                    isPlaying.value = false
                    CrashReporter.recordError(appContext, TAG, ex, "TTS_PLAYBACK_SPEAK_ERROR")
                }
            } else {
                errorMessage.value = null // successfully playing
            }
        } catch (e: java.lang.NullPointerException) {
            Log.e(TAG, "TTS native nullpointer exception on speak", e)
            errorMessage.value = "TTS engine crashed (NullPointerException). Please try restarting the app."
            isPlaying.value = false
            CrashReporter.recordError(appContext, TAG, e, "TTS_PLAYBACK_CRASH_NPE")
        } catch (e: Exception) {
            Log.e(TAG, "TTS speak failed", e)
            errorMessage.value = "TTS Playback Error: ${e.message ?: "Playback failed"}"
            isPlaying.value = false
            CrashReporter.recordError(appContext, TAG, e, "TTS_PLAYBACK_EXCEPTION")
        }
        
        saveProgress()
    }

    fun pauseSpeaking() {
        isPlaying.value = false
        tts?.stop()
        DiagnosticLogger.logEngineStatus("User requested pause. Speech output stopped, audio focus abandoned.")
        saveProgress()
        forceFlushProgress()
        abandonAudioFocus()
    }

    fun stopSpeaking() {
        isPlaying.value = false
        tts?.stop()
        DiagnosticLogger.logEngineStatus("Speech execution stopped completely.")
        saveProgress()
        forceFlushProgress()
        abandonAudioFocus()
    }

    private fun playNextSentenceAutomatically() {
        if (!isPlaying.value) return
        val sList = filteredSentences.value
        val currentSIdx = activeSentenceIndex.value

        if (currentSIdx < sList.lastIndex) {
            activeSentenceIndex.value = currentSIdx + 1
            startSpeaking()
        } else {
            val nextChIdx = activeChapterIndex.value + 1
            if (nextChIdx < chapters.value.size) {
                selectChapter(nextChIdx, autoPlay = true)
            } else {
                isPlaying.value = false
                activeSentenceIndex.value = 0
                abandonAudioFocus()
            }
        }
    }

    fun skipForward() {
        val currentSIdx = activeSentenceIndex.value
        if (currentSIdx < filteredSentences.value.lastIndex) {
            activeSentenceIndex.value = currentSIdx + 1
            if (isPlaying.value) startSpeaking()
        } else {
            val nextChIdx = activeChapterIndex.value + 1
            if (nextChIdx < chapters.value.size) {
                selectChapter(nextChIdx)
                if (isPlaying.value) startSpeaking()
            }
        }
    }

    fun skipBackward() {
        val currentSIdx = activeSentenceIndex.value
        if (currentSIdx > 0) {
            activeSentenceIndex.value = currentSIdx - 1
            if (isPlaying.value) startSpeaking()
        } else {
            val prevChIdx = activeChapterIndex.value - 1
            if (prevChIdx >= 0) {
                selectChapter(prevChIdx)
                activeSentenceIndex.value = filteredSentences.value.lastIndex.coerceAtLeast(0)
                if (isPlaying.value) startSpeaking()
            }
        }
    }

    fun setSpeechRateAndApply(rate: Float) {
        speechRate.value = rate
        tts?.setSpeechRate(rate)
        DiagnosticLogger.logEngineStatus("Speech rate modified to: $rate x")
    }
    
    fun selectVoiceAndApply(voice: Voice) {
        selectedVoice.value = voice
        tts?.voice = voice
        DiagnosticLogger.logEngineStatus("Selected new engine voice: ${voice.name} (locale: ${voice.locale})")
        if (isPlaying.value) startSpeaking()
    }

    fun setSleepTimer(minutes: Int?) {
        sleepTimerMinutes.value = minutes
        sleepTimerJob?.cancel()
        if (minutes != null) {
            DiagnosticLogger.logEngineStatus("Sleep timer set to: $minutes minutes.")
            sleepTimerJob = managerScope.launch {
                var remaining = minutes
                while (remaining > 0) {
                    delay(60000)
                    remaining--
                    sleepTimerMinutes.value = remaining
                }
                DiagnosticLogger.logEngineStatus("Sleep timer expired. Automatically pausing playback.")
                pauseSpeaking()
                sleepTimerMinutes.value = null
            }
        } else {
            DiagnosticLogger.logEngineStatus("Sleep timer deactivated/cancelled.")
        }
    }

    fun loadRules() {
        val prefs = appContext.getSharedPreferences("reader_text_filters", Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("rules", null)
        if (!jsonStr.isNullOrEmpty()) {
            try {
                val list = mutableListOf<FilterRule>()
                val arr = JSONArray(jsonStr)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        FilterRule(
                            id = obj.getString("id"),
                            original = obj.getString("original"),
                            replacement = obj.getString("replacement"),
                            isVanish = obj.getBoolean("isVanish"),
                            scope = obj.getString("scope"),
                            bookId = obj.getString("bookId")
                        )
                    )
                }
                textFilterRules.value = list
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun saveRules(rules: List<FilterRule>) {
        textFilterRules.value = rules
        val prefs = appContext.getSharedPreferences("reader_text_filters", Context.MODE_PRIVATE)
        try {
            val arr = JSONArray()
            rules.forEach { rule ->
                val obj = JSONObject()
                obj.put("id", rule.id)
                obj.put("original", rule.original)
                obj.put("replacement", rule.replacement)
                obj.put("isVanish", rule.isVanish)
                obj.put("scope", rule.scope)
                obj.put("bookId", rule.bookId)
                arr.put(obj)
            }
            prefs.edit().putString("rules", arr.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Audio Focus listener implementation over AudioManager
    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (wasPlayingBeforeFocusLoss) {
                    startSpeaking()
                }
                wasPlayingBeforeFocusLoss = false
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                if (isPlaying.value) {
                    wasPlayingBeforeFocusLoss = true
                    pauseSpeaking()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (isPlaying.value) {
                    wasPlayingBeforeFocusLoss = true
                    pauseSpeaking()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (isPlaying.value) {
                    wasPlayingBeforeFocusLoss = true
                    pauseSpeaking()
                }
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        val am = audioManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(this)
                .build()
            audioFocusRequest = focusRequest
            am.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                this,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(this)
        }
    }
}
