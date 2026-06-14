package com.example.lorelight

import android.content.SharedPreferences
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

class ReaderViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext

    // Delegate TTS & Player State to TtsPlaybackManager
    val isTtsReady = TtsPlaybackManager.isTtsReady
    val availableVoices = TtsPlaybackManager.availableVoices
    val selectedVoice = TtsPlaybackManager.selectedVoice
    
    val speechRate = TtsPlaybackManager.speechRate
    val speechPitch = TtsPlaybackManager.speechPitch

    // Sleep Timer
    val sleepTimerMinutes = TtsPlaybackManager.sleepTimerMinutes

    // Browser Force Dark Mode
    val browserForceDarkMode = MutableStateFlow(false)

    // Reader Mode Settings (Read vs Listen)
    val readerMode = MutableStateFlow("Listen") // "Read" or "Listen"
    val readerFontFamily = MutableStateFlow("System Default")

    val readerBrightness = MutableStateFlow(0.8f)
    val isDimmerEnabled = MutableStateFlow(false)
    val isNavLineAnimEnabled = MutableStateFlow(false)
    val isDefaultHighlight = MutableStateFlow(true)
    val customHighlightColor = MutableStateFlow(0xFF39FF14L) // Glow Green default

    // TTS Voice filtering by Accents
    val allVoices = TtsPlaybackManager.allVoices
    val availableAccents = TtsPlaybackManager.availableAccents
    val selectedAccent = TtsPlaybackManager.selectedAccent
    val filteredVoices = TtsPlaybackManager.filteredVoices

    // Typography
    val readerFontSize = MutableStateFlow(18f)
    val readerLineSpacing = MutableStateFlow(1.3f)
    val readerParagraphSpacing = MutableStateFlow(16f)
    val readerTextAlignment = MutableStateFlow("Left")

    val readerBgColor = MutableStateFlow(0xFF1C1B1FL)
    val readerTextColor = MutableStateFlow(0xFFE6E1E5L)
    val readerHighlightColor = MutableStateFlow(0xFF2E7D32L)
    val readerBackgroundType = MutableStateFlow("Theme")
    val appTheme = MutableStateFlow("Forest Green")

    // Playback State
    val activeNovel = TtsPlaybackManager.activeNovel
    val chapters = TtsPlaybackManager.chapters
    val activeChapterIndex = TtsPlaybackManager.activeChapterIndex
    val activeSentenceIndex = TtsPlaybackManager.activeSentenceIndex
    val sentences = TtsPlaybackManager.sentences
    val isPlaying = TtsPlaybackManager.isPlaying
    val isFetchingChapter = TtsPlaybackManager.isFetchingChapter

    // Text Filter Rules
    val textFilterRules = TtsPlaybackManager.textFilterRules

    val activeNovelTitle = TtsPlaybackManager.activeNovelTitle

    val filteredSentences = TtsPlaybackManager.filteredSentences

    // TTS error message state flow
    val errorMessage = TtsPlaybackManager.errorMessage

    val tokensBySentence = filteredSentences.map { list ->
        var globalWordIdx = 0
        list.map { sentence ->
            val (rawTokens, nextIdx) = TextFilter.tokenize(sentence, globalWordIdx)
            globalWordIdx = nextIdx
            rawTokens
        }
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    fun loadRules() {
        TtsPlaybackManager.loadRules()
    }

    fun saveRules(rules: List<FilterRule>) {
        TtsPlaybackManager.saveRules(rules)
    }

    init {
        loadRules()
        // Load saved settings
        val prefs = appContext.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
        readerFontSize.value = prefs.getSafeFloat("reader_font_size", 18f)
        readerLineSpacing.value = prefs.getSafeFloat("reader_line_spacing", 1.3f)
        readerParagraphSpacing.value = prefs.getSafeFloat("reader_paragraph_spacing", 16f)
        readerTextAlignment.value = prefs.getSafeString("reader_text_alignment", "Left") ?: "Left"
        readerBgColor.value = prefs.getSafeLong("reader_bg_color", 0xFF1C1B1FL)
        readerTextColor.value = prefs.getSafeLong("reader_text_color", 0xFFE6E1E5L)
        readerHighlightColor.value = prefs.getSafeLong("reader_highlight_color", 0xFF2E7D32L)

        // Load new settings
        readerMode.value = prefs.getSafeString("reader_mode", "Listen") ?: "Listen"
        readerFontFamily.value = prefs.getSafeString("reader_font_family", "System Default") ?: "System Default"
        readerBrightness.value = prefs.getSafeFloat("reader_brightness", 0.8f)
        isDimmerEnabled.value = prefs.getSafeBoolean("is_dimmer_enabled", false)
        isNavLineAnimEnabled.value = prefs.getSafeBoolean("is_nav_line_anim_enabled", false)
        isDefaultHighlight.value = prefs.getSafeBoolean("is_default_highlight", true)
        customHighlightColor.value = prefs.getSafeLong("custom_highlight_color", 0xFF39FF14L)
        readerBackgroundType.value = prefs.getSafeString("reader_background_type", "Theme") ?: "Theme"
        appTheme.value = prefs.getSafeString("app_theme", "Forest Green") ?: "Forest Green"
        browserForceDarkMode.value = prefs.getSafeBoolean("browser_force_dark_mode", false)

        // Setup observers to save preferences
        viewModelScope.launch { readerFontSize.collect { prefs.edit().putFloat("reader_font_size", it).apply() } }
        viewModelScope.launch { readerLineSpacing.collect { prefs.edit().putFloat("reader_line_spacing", it).apply() } }
        viewModelScope.launch { readerParagraphSpacing.collect { prefs.edit().putFloat("reader_paragraph_spacing", it).apply() } }
        viewModelScope.launch { readerTextAlignment.collect { prefs.edit().putString("reader_text_alignment", it).apply() } }
        viewModelScope.launch { readerBgColor.collect { prefs.edit().putLong("reader_bg_color", it).apply() } }
        viewModelScope.launch { readerTextColor.collect { prefs.edit().putLong("reader_text_color", it).apply() } }
        viewModelScope.launch { readerHighlightColor.collect { prefs.edit().putLong("reader_highlight_color", it).apply() } }

        // Setup new observers to save preferences
        viewModelScope.launch { readerMode.collect { prefs.edit().putString("reader_mode", it).apply() } }
        viewModelScope.launch { readerFontFamily.collect { prefs.edit().putString("reader_font_family", it).apply() } }
        viewModelScope.launch { readerBrightness.collect { prefs.edit().putFloat("reader_brightness", it).apply() } }
        viewModelScope.launch { isDimmerEnabled.collect { prefs.edit().putBoolean("is_dimmer_enabled", it).apply() } }
        viewModelScope.launch { isNavLineAnimEnabled.collect { prefs.edit().putBoolean("is_nav_line_anim_enabled", it).apply() } }
        viewModelScope.launch { isDefaultHighlight.collect { prefs.edit().putBoolean("is_default_highlight", it).apply() } }
        viewModelScope.launch { customHighlightColor.collect { prefs.edit().putLong("custom_highlight_color", it).apply() } }
        viewModelScope.launch { readerBackgroundType.collect { prefs.edit().putString("reader_background_type", it).apply() } }
        viewModelScope.launch { appTheme.collect { prefs.edit().putString("app_theme", it).apply() } }
        viewModelScope.launch { browserForceDarkMode.collect { prefs.edit().putBoolean("browser_force_dark_mode", it).apply() } }
    }

    fun updateFilteredVoicesAndSelectDefault() {
        TtsPlaybackManager.updateFilteredVoicesAndSelectDefault()
    }

    fun selectAccentAndApply(accent: String) {
        TtsPlaybackManager.selectAccentAndApply(accent)
    }

    fun setBrowserForceDarkMode(enabled: Boolean) {
        browserForceDarkMode.value = enabled
    }

    fun startReading(novel: Novel, startChapterName: String? = null) {
        TtsPlaybackManager.startReading(novel, startChapterName)
    }

    fun saveProgress() {
        TtsPlaybackManager.saveProgress()
    }

    fun saveDetailedProgress(paragraphIndex: Int, scrollOffset: Int, wordIndex: Int) {
        TtsPlaybackManager.saveDetailedProgress(paragraphIndex, scrollOffset, wordIndex)
    }

    fun selectChapter(index: Int, initialParagraphIndex: Int = 0, autoPlay: Boolean = false) {
        TtsPlaybackManager.selectChapter(index, initialParagraphIndex, autoPlay)
    }

    fun startDownloadWhole(novel: Novel) {
        viewModelScope.launch(Dispatchers.IO) {
            val total = novel.chapters.size
            launch(Dispatchers.Main) {
                Toast.makeText(appContext, "Starting background download of all chapters for ${novel.title}...", Toast.LENGTH_SHORT).show()
            }
            var downloadedCount = 0
            for (i in 0 until total) {
                val url = novel.chapterUrls.getOrNull(i)
                if (url.isNullOrEmpty()) continue
                if (!CrawlerEngine.isChapterCached(appContext, url)) {
                    try {
                        CrawlerEngine.extractChapterText(appContext, url)
                        downloadedCount++
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    if (i < total - 1) {
                        delay(2000L)
                    }
                }
            }
            launch(Dispatchers.Main) {
                Toast.makeText(appContext, "Successfully downloaded all chapters for ${novel.title}!", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun togglePlayPause() {
        TtsPlaybackManager.togglePlayPause()
    }

    fun startSpeaking() {
        TtsPlaybackManager.startSpeaking()
    }

    fun pauseSpeaking() {
        TtsPlaybackManager.pauseSpeaking()
    }

    fun stopSpeaking() {
        TtsPlaybackManager.stopSpeaking()
    }

    fun skipForward() {
        TtsPlaybackManager.skipForward()
    }

    fun skipBackward() {
        TtsPlaybackManager.skipBackward()
    }

    fun setSpeechRateAndApply(rate: Float) {
        TtsPlaybackManager.setSpeechRateAndApply(rate)
    }
    
    fun selectVoiceAndApply(voice: Voice) {
        TtsPlaybackManager.selectVoiceAndApply(voice)
    }

    fun setSleepTimer(minutes: Int?) {
        TtsPlaybackManager.setSleepTimer(minutes)
    }

    fun clearErrorMessage() {
        TtsPlaybackManager.errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        // Playback continues safely completely decoupled from Activity!
    }
}
