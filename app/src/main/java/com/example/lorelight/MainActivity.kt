package com.example.lorelight

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.MoreVert
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar
import androidx.compose.animation.core.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.provider.OpenableColumns
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import java.util.zip.ZipInputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import coil.compose.AsyncImage
import coil.request.ImageRequest

private val base64ImageCache = java.util.concurrent.ConcurrentHashMap<String, androidx.compose.ui.graphics.ImageBitmap>()

data class Novel(
    val title: String,
    val currentChapter: String,
    val lastReadTimestamp: Long,
    val author: String = "Unknown Author",
    val status: String = "Ongoing",
    val totalChapters: Int = 100,
    val language: String = "English",
    val genres: List<String> = listOf("Fantasy", "Adventure"),
    val description: String = "No description available.",
    val chapters: List<String> = (1..100).map { "Chapter $it" },
    val coverImageBase64: String? = null,
    val isOnline: Boolean = false,
    val sourceUrl: String? = null,
    val chapterUrls: List<String> = emptyList(),
    val currentParagraphIndex: Int = 0,
    val completedChapters: List<String> = emptyList(),
    val currentScrollOffset: Int = 0,
    val currentWordIndex: Int = 0
)

data class Website(
    val url: String,
    val title: String,
    val logoUrl: String? = null
)

fun Novel.toJSONObject(): JSONObject {
    val obj = JSONObject()
    obj.put("title", title)
    obj.put("currentChapter", currentChapter)
    obj.put("lastReadTimestamp", lastReadTimestamp)
    obj.put("author", author)
    obj.put("status", status)
    obj.put("totalChapters", totalChapters)
    obj.put("language", language)
    
    val genresArr = JSONArray()
    genres.forEach { genresArr.put(it) }
    obj.put("genres", genresArr)
    
    obj.put("description", description)
    
    val chaptersArr = JSONArray()
    chapters.forEach { chaptersArr.put(it) }
    obj.put("chapters", chaptersArr)
    
    obj.put("coverImageBase64", coverImageBase64 ?: JSONObject.NULL)
    obj.put("isOnline", isOnline)
    obj.put("sourceUrl", sourceUrl ?: JSONObject.NULL)
    val chapterUrlsArr = JSONArray()
    chapterUrls.forEach { chapterUrlsArr.put(it) }
    obj.put("chapterUrls", chapterUrlsArr)
    
    obj.put("currentParagraphIndex", currentParagraphIndex)
    val completedChaptersArr = JSONArray()
    completedChapters.forEach { completedChaptersArr.put(it) }
    obj.put("completedChapters", completedChaptersArr)
    
    obj.put("currentScrollOffset", currentScrollOffset)
    obj.put("currentWordIndex", currentWordIndex)
    
    return obj
}

fun JSONObject.toNovel(): Novel {
    val genresList = mutableListOf<String>()
    val genresArr = optJSONArray("genres")
    if (genresArr != null) {
        for (i in 0 until genresArr.length()) {
            genresList.add(genresArr.optString(i, ""))
        }
    } else {
        genresList.addAll(listOf("Fantasy", "Adventure"))
    }
    
    val chaptersList = mutableListOf<String>()
    val chaptersArr = optJSONArray("chapters")
    if (chaptersArr != null) {
        for (i in 0 until chaptersArr.length()) {
            val valChapter = chaptersArr.optString(i)
            if (!valChapter.isNullOrEmpty()) {
                chaptersList.add(valChapter)
            }
        }
    } else {
        chaptersList.addAll((1..100).map { "Chapter $it" })
    }
    
    val cover = optString("coverImageBase64", null)
    val coverVal = if (cover == "null" || cover.isNullOrEmpty()) null else cover
    
    val chapterUrlsList = mutableListOf<String>()
    val chapterUrlsArr = optJSONArray("chapterUrls")
    if (chapterUrlsArr != null) {
        for (i in 0 until chapterUrlsArr.length()) {
            val valUrl = chapterUrlsArr.optString(i)
            if (!valUrl.isNullOrEmpty()) {
                chapterUrlsList.add(valUrl)
            }
        }
    }

    val completedChaptersList = mutableListOf<String>()
    val completedChaptersArr = optJSONArray("completedChapters")
    if (completedChaptersArr != null) {
        for (i in 0 until completedChaptersArr.length()) {
            val valCh = completedChaptersArr.optString(i)
            if (!valCh.isNullOrEmpty()) {
                completedChaptersList.add(valCh)
            }
        }
    }

    val currentScrollOffset = optInt("currentScrollOffset", 0)
    val currentWordIndex = optInt("currentWordIndex", 0)

    return Novel(
        title = optString("title", "Unknown"),
        currentChapter = optString("currentChapter", "Chapter 1"),
        lastReadTimestamp = optLong("lastReadTimestamp", 0L),
        author = optString("author", "Unknown Author"),
        status = optString("status", "Ongoing"),
        totalChapters = optInt("totalChapters", 100),
        language = optString("language", "English"),
        genres = genresList,
        description = optString("description", "No description available."),
        chapters = chaptersList,
        coverImageBase64 = coverVal,
        isOnline = optBoolean("isOnline", false),
        sourceUrl = optString("sourceUrl", null).takeIf { it != "null" },
        chapterUrls = chapterUrlsList,
        currentParagraphIndex = optInt("currentParagraphIndex", 0),
        completedChapters = completedChaptersList,
        currentScrollOffset = currentScrollOffset,
        currentWordIndex = currentWordIndex
    )
}

fun Website.toJSONObject(): JSONObject {
    val obj = JSONObject()
    obj.put("url", url)
    obj.put("title", title)
    obj.put("logoUrl", logoUrl ?: JSONObject.NULL)
    return obj
}

fun JSONObject.toWebsite(): Website {
    val logo = optString("logoUrl", null)
    val logoVal = if (logo == "null" || logo.isNullOrEmpty()) null else logo
    return Website(
        url = optString("url", ""),
        title = optString("title", ""),
        logoUrl = logoVal
    )
}

fun saveNovelsToPrefs(context: Context, novels: List<Novel>) {
    try {
        val sharedPrefs = context.getSharedPreferences("lorelight_settings", Context.MODE_PRIVATE)
        val arr = JSONArray()
        novels.forEach { arr.put(it.toJSONObject()) }
        sharedPrefs.edit().putString("saved_novels", arr.toString()).apply()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun saveNovelsToPrefsSync(context: Context, novels: List<Novel>) {
    try {
        val sharedPrefs = context.getSharedPreferences("lorelight_settings", Context.MODE_PRIVATE)
        val arr = JSONArray()
        novels.forEach { arr.put(it.toJSONObject()) }
        sharedPrefs.edit().putString("saved_novels", arr.toString()).commit()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun loadNovelsFromPrefs(context: Context): List<Novel> {
    val list = mutableListOf<Novel>()
    try {
        val sharedPrefs = context.getSharedPreferences("lorelight_settings", Context.MODE_PRIVATE)
        val jsonStr = sharedPrefs.getString("saved_novels", null)
        if (!jsonStr.isNullOrEmpty()) {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                try {
                    val obj = arr.getJSONObject(i)
                    list.add(obj.toNovel())
                } catch (ce: Exception) {
                    ce.printStackTrace()
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}

fun saveWebsitesToPrefs(context: Context, websites: List<Website>) {
    try {
        val sharedPrefs = context.getSharedPreferences("lorelight_settings", Context.MODE_PRIVATE)
        val arr = JSONArray()
        websites.forEach { arr.put(it.toJSONObject()) }
        sharedPrefs.edit().putString("saved_websites", arr.toString()).apply()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun saveWebsitesToPrefsSync(context: Context, websites: List<Website>) {
    try {
        val sharedPrefs = context.getSharedPreferences("lorelight_settings", Context.MODE_PRIVATE)
        val arr = JSONArray()
        websites.forEach { arr.put(it.toJSONObject()) }
        sharedPrefs.edit().putString("saved_websites", arr.toString()).commit()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun loadWebsitesFromPrefs(context: Context): List<Website> {
    val list = mutableListOf<Website>()
    try {
        val sharedPrefs = context.getSharedPreferences("lorelight_settings", Context.MODE_PRIVATE)
        val jsonStr = sharedPrefs.getString("saved_websites", null)
        if (!jsonStr.isNullOrEmpty()) {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                try {
                    val obj = arr.getJSONObject(i)
                    list.add(obj.toWebsite())
                } catch (ce: Exception) {
                    ce.printStackTrace()
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    
    // Filter out deprecated Royal Road, WuxiaWorld, and LightNovelPub, and insert the new presets requested
    val deprecatedUrls = listOf("wuxiaworld.com", "royalroad.com", "lightnovelpub.com", "lightnovelpub", "novelonlinefull.com", "novelfull.com", "novelfire.net")
    val hasOld = list.any { item -> deprecatedUrls.any { item.url.contains(it) } }
    
    val defaults = listOf(
        Website("https://freewebnovel.com", "FreeWebNovel", "https://www.google.com/s2/favicons?sz=256&domain=freewebnovel.com")
    )
    
    if (list.isEmpty() || hasOld) {
        val filtered = list.filterNot { item -> deprecatedUrls.any { item.url.contains(it) } }.toMutableList()
        defaults.forEach { defaultWeb ->
            val normDef = defaultWeb.url.replace("https://", "").replace("http://", "").replace("www.", "").removeSuffix("/")
            if (filtered.none { it.url.replace("https://", "").replace("http://", "").replace("www.", "").removeSuffix("/") == normDef }) {
                filtered.add(defaultWeb)
            }
        }
        saveWebsitesToPrefs(context, filtered)
        return filtered
    }
    return list
}

fun formatLastReadTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val sdfTime = SimpleDateFormat("h:mm a", Locale.US)
    val sdfFull = SimpleDateFormat("d MMMM h:mm a", Locale.US)
    
    val cal1 = Calendar.getInstance()
    cal1.timeInMillis = now
    val cal2 = Calendar.getInstance()
    cal2.timeInMillis = timestamp
    
    val isSameDay = cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                    cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
                    
    cal1.add(Calendar.DAY_OF_YEAR, -1)
    val isYesterday = cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                      cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
                      
    return when {
        isSameDay -> "Today, ${sdfTime.format(Date(timestamp))}"
        isYesterday -> "Yesterday, ${sdfTime.format(Date(timestamp))}"
        else -> sdfFull.format(Date(timestamp))
    }
}

fun getCompactChapterText(chapter: String): String {
    val regex = Regex("""(?i)^(chapter\s+\d+)\b""", RegexOption.IGNORE_CASE)
    val match = regex.find(chapter)
    return if (match != null) {
        match.groupValues[1]
    } else {
        if (chapter.length > 15) {
            chapter.take(12) + "..."
        } else {
            chapter
        }
    }
}

fun getFileName(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/')
        if (cut != null && cut != -1) {
            result = result.substring(cut + 1)
        }
    }
    return result ?: "Unknown Novel"
}

fun countHtmlFiles(context: Context, uri: Uri): Int {
    var count = 0
    try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zipStream ->
                var entry = zipStream.nextEntry
                while (entry != null) {
                    val name = entry.name.lowercase()
                    if (name.endsWith(".html") || name.endsWith(".xhtml")) {
                        count++
                    }
                    zipStream.closeEntry()
                    entry = zipStream.nextEntry
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return count
}



val ForestBackground = Color(0xFF050907)
val ForestSurface = Color(0xFF0A1410)
val ForestSurfaceVariant = Color(0xFF111E17)
val ForestPrimary = Color(0xFF75974D)
val ForestOnBackground = Color(0xFFDEEBE2)
val ForestOnSurfaceVariant = Color(0xFFACC2B6)
val ForestBorder = Color(0xFF233A2D)

val SophisticatedDarkScheme = darkColorScheme(
    primary = ForestPrimary,
    onPrimary = Color(0xFF0D1A11),
    primaryContainer = Color(0xFF1B3224),
    onPrimaryContainer = ForestPrimary,
    secondary = Color(0xFF6DA17F),
    onSecondary = Color(0xFF0B190E),
    secondaryContainer = Color(0xFF142918),
    onSecondaryContainer = Color(0xFF6DA17F),
    tertiary = Color(0xFF5D8C7E),
    onTertiary = Color(0xFF081815),
    tertiaryContainer = Color(0xFF112D26),
    onTertiaryContainer = Color(0xFF5D8C7E),
    background = ForestBackground,
    onBackground = ForestOnBackground,
    surface = ForestSurface,
    onSurface = ForestOnBackground,
    surfaceVariant = ForestSurfaceVariant,
    onSurfaceVariant = ForestOnSurfaceVariant,
    outline = ForestBorder,
    outlineVariant = Color(0xFF1E3227)
)

val GoldenBackground = Color(0xFF0C0A07)
val GoldenSurface = Color(0xFF17130F)
val GoldenSurfaceVariant = Color(0xFF261F17)
val GoldenPrimary = Color(0xFFFFD580) // Brighter golden
val GoldenOnBackground = Color(0xFFFFF7EB) // Brighter background text
val GoldenOnSurfaceVariant = Color(0xFFDECBB5)
val GoldenBorder = Color(0xFF5C472E)

val GoldenOakScheme = darkColorScheme(
    primary = GoldenPrimary,
    onPrimary = Color(0xFF2C1F05),
    primaryContainer = Color(0xFF3D2E1A),
    onPrimaryContainer = Color(0xFFFBE4BD),
    secondary = Color(0xFFE2C89E),
    onSecondary = Color(0xFF2B1F0D),
    secondaryContainer = Color(0xFF352B1E),
    onSecondaryContainer = Color(0xFFE2C89E),
    tertiary = Color(0xFFECE1CD),
    onTertiary = Color(0xFF2A2318),
    tertiaryContainer = Color(0xFF3F3627),
    onTertiaryContainer = Color(0xFFECE1CD),
    background = GoldenBackground,
    onBackground = GoldenOnBackground,
    surface = GoldenSurface,
    onSurface = GoldenOnBackground,
    surfaceVariant = GoldenSurfaceVariant,
    onSurfaceVariant = GoldenOnSurfaceVariant,
    outline = GoldenBorder,
    outlineVariant = Color(0xFF4A3E30)
)

val PinkBackground = Color(0xFF140D12) // Deep velvet plum/rose dark background
val PinkSurface = Color(0xFF1E141B) // Deep sweet rose-dark surface
val PinkSurfaceVariant = Color(0xFF2C1D27) // Slightly lighter elegant rose-wood variant
val PinkPrimary = Color(0xFFFFB0D0) // Sweet bright pastelle pink
val PinkOnBackground = Color(0xFFFCE9F1) // Soft dreamy pinkish white for readability
val PinkOnSurfaceVariant = Color(0xFFFFD1E3) // Highly readable soft pink text
val PinkBorder = Color(0xFF503243) // Elegant matching border line

val SatinPinkScheme = darkColorScheme(
    primary = PinkPrimary,
    onPrimary = Color(0xFF4A122E),
    primaryContainer = Color(0xFF671D44),
    onPrimaryContainer = Color(0xFFFFD8E6),
    secondary = Color(0xFFFFB0CF),
    onSecondary = Color(0xFF431B2A),
    secondaryContainer = Color(0xFF5A2A3E),
    onSecondaryContainer = Color(0xFFFFD0E0),
    tertiary = Color(0xFFF2D1DC),
    onTertiary = Color(0xFF38232A),
    tertiaryContainer = Color(0xFF4F3940),
    onTertiaryContainer = Color(0xFFF2D1DC),
    background = PinkBackground,
    onBackground = PinkOnBackground,
    surface = PinkSurface,
    onSurface = PinkOnBackground,
    surfaceVariant = PinkSurfaceVariant,
    onSurfaceVariant = PinkOnSurfaceVariant,
    outline = PinkBorder,
    outlineVariant = Color(0xFF614354)
)

val MidnightBackground = Color(0xFF050505)
val MidnightSurface = Color(0xFF111111)
val MidnightSurfaceVariant = Color(0xFF1B1B1B)
val MidnightPrimary = Color(0xFFE5E5E5)
val MidnightOnBackground = Color(0xFFF5F5F5)
val MidnightOnSurfaceVariant = Color(0xFFD4D4D4)
val MidnightBorder = Color(0xFF333333)

val MidnightBlackScheme = darkColorScheme(
    primary = MidnightPrimary,
    onPrimary = Color(0xFF121212),
    primaryContainer = Color(0xFF262626),
    onPrimaryContainer = MidnightPrimary,
    secondary = Color(0xFFB5B5B5),
    onSecondary = Color(0xFF1B1B1B),
    secondaryContainer = Color(0xFF2E2E2E),
    onSecondaryContainer = Color(0xFFD4D4D4),
    tertiary = Color(0xFFCECECE),
    onTertiary = Color(0xFF202020),
    tertiaryContainer = Color(0xFF333333),
    onTertiaryContainer = Color(0xFFCECECE),
    background = MidnightBackground,
    onBackground = MidnightOnBackground,
    surface = MidnightSurface,
    onSurface = MidnightOnBackground,
    surfaceVariant = MidnightSurfaceVariant,
    onSurfaceVariant = MidnightOnSurfaceVariant,
    outline = MidnightBorder,
    outlineVariant = Color(0xFF3D3D3D)
)

val OceanBackground = Color(0xFF000B14)
val OceanSurface = Color(0xFF051726)
val OceanSurfaceVariant = Color(0xFF0C243A)
val OceanPrimary = Color(0xFF6ABFFF)
val OceanOnBackground = Color(0xFFE6F3FF)
val OceanOnSurfaceVariant = Color(0xFFADD6FF)
val OceanBorder = Color(0xFF1B3D5C)

val OceanBlueScheme = darkColorScheme(
    primary = OceanPrimary,
    onPrimary = Color(0xFF003355),
    primaryContainer = Color(0xFF004977),
    onPrimaryContainer = Color(0xFFCBE5FF),
    secondary = Color(0xFF94CDFF),
    onSecondary = Color(0xFF00325B),
    secondaryContainer = Color(0xFF004880),
    onSecondaryContainer = Color(0xFFD1E4FF),
    tertiary = Color(0xFFA6C8FF),
    onTertiary = Color(0xFF003067),
    tertiaryContainer = Color(0xFF004693),
    onTertiaryContainer = Color(0xFFA6C8FF),
    background = OceanBackground,
    onBackground = OceanOnBackground,
    surface = OceanSurface,
    onSurface = OceanOnBackground,
    surfaceVariant = OceanSurfaceVariant,
    onSurfaceVariant = OceanOnSurfaceVariant,
    outline = OceanBorder,
    outlineVariant = Color(0xFF23517A)
)

val RefinedGoldGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFFC59832), // Warm burnished gold
        Color(0xFFE5B942), // Satin gold shimmer
        Color(0xFFF5D68F)  // Delicate pale amber
    )
)
val GoldOnGradient = Color(0xFF1E1405)

val RefinedPinkGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFFDC829F), // Rich velvet pink
        Color(0xFFEFA5C0), // Satin rose
        Color(0xFFFFCBDC)  // Pale marshmallow pink
    )
)
val PinkOnGradient = Color(0xFF3B0B24)

val RefinedMidnightGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFFBCBCC0), // Sleek metallic platinum
        Color(0xFFD8D8DC), // Brushing chrome satin
        Color(0xFFF2F2F7)  // Pure light titanium
    )
)
val MidnightOnGradient = Color(0xFF000000)

val RefinedOceanGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFF4DA6FF), // Soft sky blue
        Color(0xFF7AD3FF), // Sparkling lagoon blue
        Color(0xFFA1ECFF)  // Delicate sea foam cyan
    )
)
val OceanOnGradient = Color(0xFF001F3F)

val RefinedForestGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFF5B7A3E), // Soft forest shadow
        Color(0xFF75974D), // Darker forest leaf/meadow
        Color(0xFF9EBA7B)  // Tender sage green
    )
)
val ForestOnGradient = Color(0xFF0C1B0E)

fun getThemedButtonProps(primary: Color, fallbackContent: Color = Color.Black): Triple<Boolean, Brush, Color> {
    val isPremium = primary == Color(0xFFF3C77D) || primary == Color(0xFFFFD580) ||
                    primary == Color(0xFFFFB0D0) || 
                    primary == Color(0xFFE5E5E5) ||
                    primary == Color(0xFF6ABFFF) ||
                    primary == ForestPrimary
    val gradient = when (primary) {
        Color(0xFFF3C77D), Color(0xFFFFD580) -> RefinedGoldGradient
        Color(0xFFFFB0D0) -> RefinedPinkGradient
        Color(0xFFE5E5E5) -> RefinedMidnightGradient
        Color(0xFF6ABFFF) -> RefinedOceanGradient
        ForestPrimary -> RefinedForestGradient
        else -> Brush.linearGradient(colors = listOf(primary, primary))
    }
    val contentColor = when (primary) {
        Color(0xFFF3C77D), Color(0xFFFFD580) -> GoldOnGradient
        Color(0xFFFFB0D0) -> PinkOnGradient
        Color(0xFFE5E5E5) -> MidnightOnGradient
        Color(0xFF6ABFFF) -> OceanOnGradient
        ForestPrimary -> ForestOnGradient
        else -> fallbackContent
    }
    return Triple(isPremium, gradient, contentColor)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        SelfHealingEngine.onAppStart(this)
        CrashReporter.init(this)
        super.onCreate(savedInstanceState)
        TtsPlaybackManager.init(this)
        BackupManager.init(this)
        enableEdgeToEdge()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        setContent {
            val readerViewModel: ReaderViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            val themeState by readerViewModel.appTheme.collectAsState()
            val activeScheme = when (themeState) {
                "Golden Oak" -> GoldenOakScheme
                "Satin Pink" -> SatinPinkScheme
                "Midnight Black" -> MidnightBlackScheme
                "Ocean Blue" -> OceanBlueScheme
                else -> SophisticatedDarkScheme
            }

            MaterialTheme(
                colorScheme = activeScheme
            ) {
                LorelightApp(readerViewModel)
            }
        }
    }
}

@Composable
fun LorelightApp(readerViewModel: ReaderViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Library", "History", "Website", "Settings")
    
    // Global state
    var currentNovel by remember {
        val list = loadNovelsFromPrefs(context)
        val lastRead = list.filter { it.lastReadTimestamp > 0 }.maxByOrNull { it.lastReadTimestamp }
        mutableStateOf<Novel?>(lastRead ?: list.firstOrNull())
    }
    var selectedNovelForDetails by remember { mutableStateOf<Novel?>(null) }
    var importedNovels by remember { mutableStateOf(loadNovelsFromPrefs(context)) }
    var websites by remember { mutableStateOf(loadWebsitesFromPrefs(context)) }
    
    var showReader by remember { mutableStateOf(false) }
    var showCrawler by remember { mutableStateOf(false) }
    
    val activeNovel by readerViewModel.activeNovel.collectAsState()
    val themeState by readerViewModel.appTheme.collectAsState()
    val heroImageRes = when (themeState) {
        "Golden Oak" -> R.drawable.librarygolden
        "Satin Pink" -> R.drawable.librarypink
        "Midnight Black" -> R.drawable.libraryblack
        "Ocean Blue" -> R.drawable.libraryblue
        else -> R.drawable.librarygreen
    }

    LaunchedEffect(activeNovel) {
        val updatedNov = activeNovel
        if (updatedNov != null) {
            importedNovels = importedNovels.map {
                if (it.title == updatedNov.title) updatedNov else it
            }
            if (currentNovel?.title == updatedNov.title) {
                currentNovel = updatedNov
            }
            if (selectedNovelForDetails?.title == updatedNov.title) {
                selectedNovelForDetails = updatedNov
            }
        }
    }
    
    LaunchedEffect(importedNovels) {
        kotlinx.coroutines.delay(3000) // Debounce writes to disk to prevent piling up of I/O operations and CPU lag during TTS
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            saveNovelsToPrefs(context, importedNovels)
        }
    }
    
    LaunchedEffect(websites) {
        kotlinx.coroutines.delay(3000) // Debounce writes to disk to prevent piling up of I/O operations
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            saveWebsitesToPrefs(context, websites)
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                com.example.lorelight.TtsPlaybackManager.isAppInBackground.value = false
                DiagnosticLogger.logEngineStatus("App transition to FOREGROUND. Normal TTS state polling active.")
            }
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE || event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                com.example.lorelight.TtsPlaybackManager.isAppInBackground.value = true
                DiagnosticLogger.logEngineStatus("App transition to BACKGROUND. Battery-saving check enabled.")
                
                // Immediately write current state to disk on backgrounding
                val valActive = readerViewModel.activeNovel.value
                val finalList = if (valActive != null) {
                    importedNovels.map {
                        if (it.title == valActive.title) valActive else it
                    }
                } else {
                    importedNovels
                }
                saveNovelsToPrefsSync(context, finalList)
                saveWebsitesToPrefsSync(context, websites)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val icons = when (themeState) {
        "Golden Oak" -> listOf(
            R.drawable.ic_golden_library,
            R.drawable.ic_golden_history,
            R.drawable.ic_golden_website,
            R.drawable.ic_golden_settings
        )
        "Satin Pink" -> listOf(
            R.drawable.ic_pink_library,
            R.drawable.ic_pink_history,
            R.drawable.ic_pink_website,
            R.drawable.ic_pink_settings
        )
        "Midnight Black" -> listOf(
            R.drawable.ic_midnight_library,
            R.drawable.ic_midnight_history,
            R.drawable.ic_midnight_website,
            R.drawable.ic_midnight_settings
        )
        "Ocean Blue" -> listOf(
            R.drawable.ic_ocean_library,
            R.drawable.ic_ocean_history,
            R.drawable.ic_ocean_website,
            R.drawable.ic_ocean_settings
        )
        else -> listOf(
            R.drawable.ic_forest_library,
            R.drawable.ic_forest_history,
            R.drawable.ic_forest_website,
            R.drawable.ic_forest_settings
        )
    }

    if (selectedNovelForDetails != null) {
        DetailsScreen(
            novel = selectedNovelForDetails,
            onBack = { selectedNovelForDetails = null },
            heroImageRes = heroImageRes,
            onUpdateCurrentChapter = { novel, chapter ->
                val updatedNovel = novel.copy(
                    currentChapter = chapter,
                    lastReadTimestamp = System.currentTimeMillis()
                )
                importedNovels = importedNovels.map {
                    if (it.title == novel.title) updatedNovel else it
                }
                selectedNovelForDetails = updatedNovel
                currentNovel = updatedNovel
                readerViewModel.startReading(updatedNovel, chapter)
                showReader = true
            },
            onReadClick = {
                selectedNovelForDetails?.let { novel ->
                    readerViewModel.startReading(novel)
                    showReader = true
                }
            },
            onDownloadWhole = { novel ->
                readerViewModel.startDownloadWhole(novel)
            }
        )
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            tabs.forEachIndexed { index, title ->
                                val isSelected = selectedTab == index
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { selectedTab = index }
                                        .padding(vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp, 28.dp)
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .background(
                                                        when (themeState) {
                                                            "Golden Oak" -> RefinedGoldGradient
                                                            "Satin Pink" -> RefinedPinkGradient
                                                            "Midnight Black" -> RefinedMidnightGradient
                                                            "Ocean Blue" -> RefinedOceanGradient
                                                            else -> RefinedForestGradient
                                                        }
                                                    )
                                            )
                                        }
                                        Icon(
                                            painter = painterResource(id = icons[index]),
                                            contentDescription = title,
                                            tint = if (isSelected) {
                                                when (themeState) {
                                                    "Golden Oak" -> GoldOnGradient
                                                    "Satin Pink" -> PinkOnGradient
                                                    "Midnight Black" -> MidnightOnGradient
                                                    "Ocean Blue" -> OceanOnGradient
                                                    else -> ForestOnGradient
                                                }
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                            },
                                            modifier = Modifier.size(20.dp)
                                        )

                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        title.uppercase(),
                                        fontSize = 9.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        letterSpacing = 0.5.sp,
                                        color = if (isSelected) {
                                            when (themeState) {
                                                "Golden Oak" -> GoldenPrimary
                                                "Satin Pink" -> PinkPrimary
                                                "Midnight Black" -> MidnightPrimary
                                                "Ocean Blue" -> OceanPrimary
                                                else -> MaterialTheme.colorScheme.primary
                                            }
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (selectedTab) {
                    0 -> LibraryScreen(
                        currentNovel = currentNovel,
                        importedNovels = importedNovels,
                        onCurrentNovelChange = { updated ->
                            currentNovel = updated
                            if (updated != null) {
                                importedNovels = importedNovels.map {
                                    if (it.title == updated.title) updated else it
                                }
                            }
                        },
                        onImportedNovelsChange = { importedNovels = it },
                        onSelectNovelForDetails = { novel ->
                            selectedNovelForDetails = novel
                        },
                        onReadCurrent = {
                            currentNovel?.let { 
                                readerViewModel.startReading(it)
                                showReader = true 
                            }
                        },
                        onCrawlerClick = { showCrawler = true },
                        readerViewModel = readerViewModel,
                        heroImageRes = heroImageRes
                    )
                    1 -> HistoryScreen(
                        history = importedNovels,
                        heroImageRes = heroImageRes,
                        onPlayClick = { novel ->
                            val updatedNovel = novel.copy(
                                lastReadTimestamp = System.currentTimeMillis()
                            )
                            importedNovels = importedNovels.map {
                                if (it.title == novel.title) updatedNovel else it
                            }
                            currentNovel = updatedNovel
                            selectedNovelForDetails = updatedNovel
                            readerViewModel.startReading(updatedNovel)
                            showReader = true
                        }
                    )
                    2 -> WebsiteScreen(
                        websites = websites,
                        onUpdateWebsites = { websites = it },
                        onAddNovel = { novel ->
                            if (!importedNovels.any { it.title == novel.title }) {
                                importedNovels = importedNovels + novel
                            }
                        },
                        heroImageRes = heroImageRes,
                        readerViewModel = readerViewModel
                    )
                    3 -> SettingsScreen(
                        importedNovels = importedNovels,
                        websites = websites,
                        readerViewModel = readerViewModel,
                        onRestoreSuccess = {
                            importedNovels = loadNovelsFromPrefs(context)
                            websites = loadWebsitesFromPrefs(context)
                            val lastRead = importedNovels.filter { it.lastReadTimestamp > 0 }.maxByOrNull { it.lastReadTimestamp }
                            currentNovel = lastRead ?: importedNovels.firstOrNull()
                        }
                    )
                    else -> ComingSoonScreen(tabs[selectedTab])
                }
            }
        }
    }
    
    if (showReader) {
        ReaderScreen(
            viewModel = readerViewModel,
            onClose = { showReader = false }
        )
    }
    
    if (showCrawler) {
        CrawlerScreen(
            onClose = { showCrawler = false },
            onAddNovel = { novel ->
                if (!importedNovels.any { it.title == novel.title }) {
                    importedNovels = importedNovels + novel
                }
                showCrawler = false
            }
        )
    }
}

@Composable
fun NovelCoverImage(coverBase64: String?, title: String = "Novel", modifier: Modifier = Modifier) {
    if (coverBase64 == null) {
        Box(
            modifier = modifier.background(Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = title.split(" ").firstOrNull() ?: "Novel",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        return
    }

    if (coverBase64.startsWith("http://") || coverBase64.startsWith("https://")) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(coverBase64)
                .crossfade(true)
                .build(),
            contentDescription = "Novel Cover",
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
        return
    }

    // Check high-speed cache first!
    val cachedBitmap = base64ImageCache[coverBase64]
    if (cachedBitmap != null) {
        Image(
            bitmap = cachedBitmap,
            contentDescription = "Novel Cover",
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
        return
    }

    var imageBitmap by remember(coverBase64) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var isLoading by remember(coverBase64) { mutableStateOf(true) }

    LaunchedEffect(coverBase64) {
        withContext(Dispatchers.IO) {
            try {
                val decodedString = Base64.decode(coverBase64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
                val imgBmp = bitmap?.asImageBitmap()
                if (imgBmp != null) {
                    base64ImageCache[coverBase64] = imgBmp
                }
                withContext(Dispatchers.Main) {
                    imageBitmap = imgBmp
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }

    if (isLoading) {
        Box(
            modifier = modifier.background(Color.White.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                modifier = Modifier.size(24.dp)
            )
        }
    } else if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap!!,
            contentDescription = "Novel Cover",
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = title.split(" ").firstOrNull() ?: "Novel",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun BookListLineupItem(
    novel: Novel,
    isSelected: Boolean,
    isManageMode: Boolean = false,
    modifier: Modifier = Modifier,
    coverScale: Float = 1.0f,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Transparent)
            .border(
                width = if (isSelected) 1.5.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size((48 * coverScale).dp, (68 * coverScale).dp)
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
        ) {
            NovelCoverImage(
                coverBase64 = novel.coverImageBase64,
                title = novel.title,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = novel.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "By ${novel.author}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${novel.totalChapters} Chapters",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isManageMode) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .border(
                        1.5.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.background,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun LibraryScreen(
    currentNovel: Novel?,
    importedNovels: List<Novel>,
    onCurrentNovelChange: (Novel?) -> Unit,
    onImportedNovelsChange: (List<Novel>) -> Unit,
    onSelectNovelForDetails: (Novel) -> Unit,
    onReadCurrent: () -> Unit = {},
    onCrawlerClick: () -> Unit = {},
    readerViewModel: ReaderViewModel,
    heroImageRes: Int = R.drawable.librarygreen
) {
    val context = LocalContext.current
    val isPlaying by readerViewModel.isPlaying.collectAsState()
    val activeNovel by readerViewModel.activeNovel.collectAsState()
    val activeChapterIndex by readerViewModel.activeChapterIndex.collectAsState()
    val chapters by readerViewModel.chapters.collectAsState()
    val sharedPrefs = remember(context) {
        context.getSharedPreferences("lorelight_settings", Context.MODE_PRIVATE)
    }
    
    var isManageMode by remember { mutableStateOf(false) }
    var selectedNovelTitles by remember { mutableStateOf(setOf<String>()) }
    var showEditDialog by remember { mutableStateOf(false) }
    
    val bookshelfStyle = "Cover only"
    var bookshelfCoverScale by remember {
        val scale = sharedPrefs.getSafeFloat("bookshelf_cover_scale", 1.0f)
        mutableStateOf(scale)
    }
    var isEpubLoading by remember { mutableStateOf(false) }
    
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    
    LaunchedEffect(currentNovel) {
        if (currentNovel != null && importedNovels.isNotEmpty()) {
            val index = importedNovels.indexOfFirst { it.title == currentNovel.title }
            if (index >= 0) {
                // Smooth scroll to the target book to enhance reading fluidity and user location awareness
                try {
                    gridState.animateScrollToItem(index)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
    
    LaunchedEffect(bookshelfCoverScale) {
        sharedPrefs.edit().putFloat("bookshelf_cover_scale", bookshelfCoverScale).apply()
    }
    
    // Background cover image downloader to convert remote cover URLs into local offline Base64 strings automatically
    LaunchedEffect(importedNovels) {
        val novelsWithWebCovers = importedNovels.filter { novel ->
            val cover = novel.coverImageBase64
            !cover.isNullOrEmpty() && (cover.startsWith("http://") || cover.startsWith("https://"))
        }
        if (novelsWithWebCovers.isNotEmpty()) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                var listModified = false
                val updatedNovels = importedNovels.map { novel ->
                    val cover = novel.coverImageBase64
                    if (!cover.isNullOrEmpty() && (cover.startsWith("http://") || cover.startsWith("https://"))) {
                        try {
                            val conn = org.jsoup.Jsoup.connect(cover)
                                .ignoreContentType(true)
                                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                                .timeout(12000)
                                .execute()
                            val bytes = conn.bodyAsBytes()
                            if (bytes != null && bytes.isNotEmpty()) {
                                val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                                listModified = true
                                novel.copy(coverImageBase64 = b64)
                            } else {
                                novel
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            novel
                        }
                    } else {
                        novel
                    }
                }
                if (listModified) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onImportedNovelsChange(updatedNovels)
                        updatedNovels.forEach { novel ->
                            if (currentNovel?.title == novel.title) {
                                onCurrentNovelChange(novel)
                            }
                        }
                    }
                }
            }
        }
    }
    
    val coroutineScope = rememberCoroutineScope()

    var editedTitle by remember(selectedNovelTitles.firstOrNull()) {
        mutableStateOf("")
    }
    var editedCoverBase64 by remember(selectedNovelTitles.firstOrNull()) {
        mutableStateOf<String?>(null)
    }
    
    val targetNovel = remember(showEditDialog, selectedNovelTitles.firstOrNull()) {
        importedNovels.find { it.title == selectedNovelTitles.firstOrNull() }
    }
    
    LaunchedEffect(targetNovel) {
        if (targetNovel != null) {
            editedTitle = targetNovel.title
            editedCoverBase64 = targetNovel.coverImageBase64
        }
    }
    
    val coverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bytes = inputStream?.readBytes()
                    inputStream?.close()
                    if (bytes != null) {
                        val base64Str = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        editedCoverBase64 = base64Str
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(context, "Failed to load cover image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    val epubLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                isEpubLoading = true
                try {
                    val fileName = getFileName(context, uri)
                    if (!fileName.endsWith(".epub", ignoreCase = true)) {
                        Toast.makeText(context, "Please select an EPUB file (ending with .epub)", Toast.LENGTH_LONG).show()
                        isEpubLoading = false
                        return@launch
                    }
                    
                    val parsedJson = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        EpubExtractor.extract(context, uri)
                    }
                    
                    val metadata = parsedJson.optJSONObject("metadata")
                    val cover = parsedJson.optJSONObject("cover")
                    val chaptersArray = parsedJson.optJSONArray("chapters")

                    val extractedChapters = mutableListOf<String>()
                    val virtualUrls = mutableListOf<String>()
                    if (chaptersArray != null && chaptersArray.length() > 0) {
                        for (i in 0 until chaptersArray.length()) {
                            val chObj = chaptersArray.optJSONObject(i)
                            if (chObj != null) {
                                val chTitle = chObj.optString("title", "Chapter ${i+1}")
                                val chText = chObj.optString("text", "")
                                val vUrl = "epub://${(metadata?.optString("title") ?: "Unknown").hashCode()}/chapter_${i}"
                                extractedChapters.add(chTitle)
                                virtualUrls.add(vUrl)
                                if (chText.isNotEmpty()) {
                                    CrawlerEngine.cacheChapterText(context, vUrl, chText)
                                }
                            }
                        }
                    } else {
                        extractedChapters.add("Chapter 1")
                        virtualUrls.add("epub://unknown/chapter_0")
                    }

                    val newNovel = Novel(
                        title = metadata?.optString("title") ?: "Unknown Title",
                        currentChapter = extractedChapters.first(),
                        lastReadTimestamp = System.currentTimeMillis(),
                        author = metadata?.optJSONArray("author")?.let { if (it.length() > 0) it.optString(0) else "Unknown" } ?: "Unknown Author",
                        status = "Imported",
                        totalChapters = extractedChapters.size,
                        language = metadata?.optString("language") ?: "English",
                        genres = listOf("EPUB", "Imported"),
                        description = metadata?.optString("description") ?: "EPUB imported.",
                        chapters = extractedChapters,
                        chapterUrls = virtualUrls,
                        coverImageBase64 = cover?.optString("image_base64")
                    )
                    
                    if (!importedNovels.any { it.title == newNovel.title }) {
                        onImportedNovelsChange(importedNovels + newNovel)
                        Toast.makeText(context, "Successfully imported \"${newNovel.title}\"", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "\"${newNovel.title}\" is already in your library", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to import EPUB: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                } finally {
                    isEpubLoading = false
                }
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val isTablet = configuration.screenWidthDp >= 600
        val heroHeight = if (isTablet) 240.dp else 280.dp

        // Hero Banner Layer (Constrained on wide screens and centered)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 680.dp)
                .height(heroHeight)
        ) {
            Image(
                painter = painterResource(id = heroImageRes),
                contentDescription = "Library Hero Banner",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            // Fading effect starting below the girl's shoulder
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                0.45f to Color.Transparent,
                                0.85f to MaterialTheme.colorScheme.background,
                                1.0f to MaterialTheme.colorScheme.background
                            )
                        )
                    )
            )
        }

        // Content Layer (Constrained on wide screens to align with Hero Banner)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
        ) {
            // App Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 32.dp, bottom = 32.dp)
                    .height(48.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Library",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Serif,
                    color = Color.White
                )
                
                // Import button on top right to replace the old state testing switch
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                            .clickable {
                                try {
                                    epubLauncher.launch("application/epub+zip")
                                } catch (e: Exception) {
                                    epubLauncher.launch("*/*")
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MenuBook,
                            contentDescription = "Import",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Import",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(68.dp))

            // Continue Reading / Playback Card (Fixed, not scrollable)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                if (isPlaying && activeNovel != null) {
                    AudioControllerCard(
                        novel = activeNovel!!,
                        isPlaying = isPlaying,
                        onPlayPause = { readerViewModel.togglePlayPause() },
                        onSkipBackward = { readerViewModel.skipBackward() },
                        onSkipForward = { readerViewModel.skipForward() },
                        onPrevChapter = {
                            if (activeChapterIndex > 0) {
                                readerViewModel.selectChapter(activeChapterIndex - 1)
                            }
                        },
                        onNextChapter = {
                            if (activeChapterIndex < chapters.size - 1) {
                                readerViewModel.selectChapter(activeChapterIndex + 1)
                            }
                        },
                        hasPrevChapter = activeChapterIndex > 0,
                        hasNextChapter = activeChapterIndex < chapters.size - 1,
                        onStop = { readerViewModel.stopSpeaking() },
                        onCardClick = onReadCurrent
                    )
                } else {
                    ContinueReadingCard(
                        novel = currentNovel,
                        onReadClick = onReadCurrent
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bookshelf Title Header with custom layout settings
            if (isManageMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(onClick = { 
                            isManageMode = false
                            selectedNovelTitles = emptySet()
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Cancel",
                                tint = Color.White
                            )
                        }
                        Text(
                            text = "${selectedNovelTitles.size} Selected",
                            fontSize = 15.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (importedNovels.isNotEmpty()) {
                            val allSel = selectedNovelTitles.size == importedNovels.size
                            TextButton(
                                onClick = {
                                    selectedNovelTitles = if (allSel) emptySet() else importedNovels.map { it.title }.toSet()
                                }
                            ) {
                                Text(
                                    text = if (allSel) "Deselect All" else "Select All",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        if (selectedNovelTitles.size == 1) {
                            IconButton(onClick = { showEditDialog = true }) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = "Edit Novel",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        
                        if (selectedNovelTitles.isNotEmpty()) {
                            IconButton(onClick = {
                                val remainingNovels = importedNovels.filter { it.title !in selectedNovelTitles }
                                onImportedNovelsChange(remainingNovels)
                                if (currentNovel != null && currentNovel.title in selectedNovelTitles) {
                                    onCurrentNovelChange(remainingNovels.firstOrNull())
                                }
                                selectedNovelTitles = emptySet()
                                isManageMode = false
                            }) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Delete Novels",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "My Bookshelf",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                        color = Color.White
                    )
                    
                    Box {
                        var showThreeDotMenu by remember { mutableStateOf(false) }
                        var showDisplayDialog by remember { mutableStateOf(false) }
                        
                        IconButton(onClick = { showThreeDotMenu = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "Bookshelf Options",
                                tint = Color.White
                            )
                        }
                        
                        DropdownMenu(
                            expanded = showThreeDotMenu,
                            onDismissRequest = { showThreeDotMenu = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Display Settings", color = Color.White, fontSize = 14.sp) },
                                onClick = {
                                    showThreeDotMenu = false
                                    showDisplayDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Edit / Manage Novels", color = Color.White, fontSize = 14.sp) },
                                onClick = {
                                    showThreeDotMenu = false
                                    isManageMode = true
                                    selectedNovelTitles = emptySet()
                                }
                            )
                        }
                        
                        if (showDisplayDialog) {
                            Dialog(
                                onDismissRequest = { showDisplayDialog = false }
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp)
                                    ) {
                                        Text(
                                            text = "Bookshelf Display Settings",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            fontFamily = FontFamily.Serif,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.height(20.dp))
                                        
                                        Text(
                                            text = "Cover Image Size",
                                            fontSize = 13.sp,
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Slider(
                                                value = bookshelfCoverScale,
                                                onValueChange = { bookshelfCoverScale = it },
                                                valueRange = 0.5f..1.2f,
                                                colors = SliderDefaults.colors(
                                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                                ),
                                                modifier = Modifier.weight(1f)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = "${(bookshelfCoverScale * 100).toInt()}%",
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.width(42.dp),
                                                textAlign = androidx.compose.ui.text.style.TextAlign.End
                                            )
                                        }
                                        
                                        Spacer(modifier = Modifier.height(16.dp))
                                        val primaryColor = MaterialTheme.colorScheme.primary
                                        val (isPremiumBtn, btnGrad, btnContent) = getThemedButtonProps(primaryColor, MaterialTheme.colorScheme.background)
                                        Button(
                                            onClick = { showDisplayDialog = false },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isPremiumBtn) Color.Transparent else primaryColor,
                                                contentColor = btnContent
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .then(
                                                    if (isPremiumBtn) {
                                                        Modifier.background(btnGrad, RoundedCornerShape(10.dp))
                                                    } else {
                                                        Modifier
                                                    }
                                                ),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("Apply Layout", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Bookshelf Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Bookshelf Grid or Empty state
                if (importedNovels.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Transparent)
                            .padding(vertical = 32.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.MenuBook,
                                contentDescription = "Empty Shelf",
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Your shelf is empty",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Click 'Import' on top to add a novel.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyHorizontalGrid(
                            rows = GridCells.Fixed(2),
                            state = gridState,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(importedNovels) { novel ->
                                Box(
                                    modifier = Modifier.fillMaxHeight(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    BookCoverItem(
                                        novel = novel,
                                        isSelected = if (isManageMode) novel.title in selectedNovelTitles else currentNovel?.title == novel.title,
                                        isManageMode = isManageMode,
                                        modifier = Modifier.fillMaxHeight(bookshelfCoverScale),
                                        onClick = {
                                            if (isManageMode) {
                                                selectedNovelTitles = if (novel.title in selectedNovelTitles) {
                                                    selectedNovelTitles - novel.title
                                                } else {
                                                    selectedNovelTitles + novel.title
                                                }
                                            } else {
                                                onCurrentNovelChange(novel)
                                                onSelectNovelForDetails(novel)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Small spacer at bottom matching edge to edge rules perfectly
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    // Edit / Delete novel dialog
    if (showEditDialog && targetNovel != null) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = {
                Text(
                    text = "Edit Novel Details",
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    color = Color.White
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Customize the title and cover image of this novel.",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    
                    OutlinedTextField(
                        value = editedTitle,
                        onValueChange = { editedTitle = it },
                        label = { Text("Novel Title") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Cover Image",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Box(
                            modifier = Modifier
                                .size(96.dp, 136.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .clickable {
                                    coverPickerLauncher.launch("image/*")
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            NovelCoverImage(
                                coverBase64 = editedCoverBase64,
                                title = editedTitle,
                                modifier = Modifier.fillMaxSize()
                            )
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.4f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = "Change Cover",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Tap to Change",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            val remainingNovels = importedNovels.filter { it.title != targetNovel.title }
                            onImportedNovelsChange(remainingNovels)
                            if (currentNovel?.title == targetNovel.title) {
                                onCurrentNovelChange(remainingNovels.firstOrNull())
                            }
                            selectedNovelTitles = emptySet()
                            showEditDialog = false
                            isManageMode = false
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(onClick = { showEditDialog = false }) {
                            Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                        }
                        Button(
                            onClick = {
                                if (editedTitle.isNotBlank()) {
                                    val updatedNovels = importedNovels.map {
                                        if (it.title == targetNovel.title) {
                                            it.copy(title = editedTitle, coverImageBase64 = editedCoverBase64)
                                        } else {
                                            it
                                        }
                                    }
                                    onImportedNovelsChange(updatedNovels)
                                    if (currentNovel?.title == targetNovel.title) {
                                        onCurrentNovelChange(updatedNovels.find { it.title == editedTitle })
                                    }
                                    selectedNovelTitles = emptySet()
                                    showEditDialog = false
                                    isManageMode = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (getThemedButtonProps(MaterialTheme.colorScheme.primary).first) Color.Transparent else MaterialTheme.colorScheme.primary,
                                contentColor = getThemedButtonProps(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.background).third
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.then(
                                if (getThemedButtonProps(MaterialTheme.colorScheme.primary).first) {
                                    Modifier.background(getThemedButtonProps(MaterialTheme.colorScheme.primary).second, RoundedCornerShape(10.dp))
                                } else {
                                    Modifier
                                }
                            )
                        ) {
                            Text("Save", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(18.dp)
        )
    }
}

@Composable
fun BookCoverItem(
    novel: Novel,
    isSelected: Boolean,
    isManageMode: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .aspectRatio(0.68f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Transparent)
            .border(
                width = if (isSelected) 2.5.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        NovelCoverImage(
            coverBase64 = novel.coverImageBase64,
            title = novel.title,
            modifier = Modifier.fillMaxSize()
        )

        if (isManageMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isSelected) Color.Black.copy(alpha = 0.3f) else Color.Transparent)
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.5f))
                    .border(
                        1.5.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.background,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ContinueReadingCard(
    novel: Novel?,
    onReadClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface) // dark green surface
            .border(
                1.dp,
                if (novel != null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                RoundedCornerShape(16.dp)
            )
            .clickable { if(novel != null) onReadClick() }
            .padding(vertical = 8.dp, horizontal = 12.dp)
    ) {
        if (novel == null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp, horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.MenuBook,
                    contentDescription = "No Novel Active",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    modifier = Modifier.size(30.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "No Novel Active",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cover thumbnail
                Box(
                    modifier = Modifier
                        .size(44.dp, 62.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.outline),
                    contentAlignment = Alignment.Center
                ) {
                    NovelCoverImage(
                        coverBase64 = novel.coverImageBase64,
                        title = novel.title,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                Spacer(modifier = Modifier.width(10.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = novel.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = novel.currentChapter,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(2.dp))
                    
                    // Play action row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatLastReadTime(novel.lastReadTimestamp),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        
                        // Play Button
                        val primaryColor = MaterialTheme.colorScheme.primary
                        val isPremiumTheme = primaryColor == GoldenPrimary || primaryColor == PinkPrimary || primaryColor == MidnightPrimary || primaryColor == OceanPrimary || primaryColor == ForestPrimary
                        val gradientBrush = when (primaryColor) {
                            GoldenPrimary -> RefinedGoldGradient
                            PinkPrimary -> RefinedPinkGradient
                            MidnightPrimary -> RefinedMidnightGradient
                            OceanPrimary -> RefinedOceanGradient
                            ForestPrimary -> RefinedForestGradient
                            else -> Brush.linearGradient(colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary))
                        }
                        val onGradientText = when (primaryColor) {
                            GoldenPrimary -> GoldOnGradient
                            PinkPrimary -> PinkOnGradient
                            MidnightPrimary -> MidnightOnGradient
                            OceanPrimary -> OceanOnGradient
                            ForestPrimary -> ForestOnGradient
                            else -> MaterialTheme.colorScheme.background
                        }
                        Box(
                            modifier = Modifier
                                .height(28.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(gradientBrush)
                                .clickable { onReadClick() }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.MenuBook,
                                    contentDescription = "Continue",
                                    tint = onGradientText,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Continue",
                                    color = onGradientText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AudioControllerCard(
    novel: Novel,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSkipBackward: () -> Unit,
    onSkipForward: () -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    hasPrevChapter: Boolean,
    hasNextChapter: Boolean,
    onStop: () -> Unit,
    onCardClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                RoundedCornerShape(20.dp)
            )
            .clickable { onCardClick() }
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp, 80.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.outline),
                contentAlignment = Alignment.Center
            ) {
                NovelCoverImage(
                    coverBase64 = novel.coverImageBase64,
                    title = novel.title,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.VolumeUp,
                        contentDescription = "Playing audio",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "NOW PLAYING",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                    
                    IconButton(
                        onClick = onStop,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Stop TTS",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                
                Text(
                    text = novel.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = novel.currentChapter,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onPrevChapter,
                        enabled = hasPrevChapter,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FastRewind,
                            contentDescription = "Prev Chapter",
                            tint = if (hasPrevChapter) Color.White else Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    
                    IconButton(
                        onClick = onSkipBackward,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipPrevious,
                            contentDescription = "Prev Sentence",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { onPlayPause() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    
                    IconButton(
                        onClick = onSkipForward,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = "Next Sentence",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    
                    IconButton(
                        onClick = onNextChapter,
                        enabled = hasNextChapter,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FastForward,
                            contentDescription = "Next Chapter",
                            tint = if (hasNextChapter) Color.White else Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ComingSoonScreen(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$title Coming Soon",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun HistoryScreen(
    history: List<Novel>,
    onPlayClick: (Novel) -> Unit,
    heroImageRes: Int = R.drawable.librarygreen
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Hero Banner Layer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
        ) {
            Image(
                painter = painterResource(id = heroImageRes), // the user will upload it later, reuse library for now
                contentDescription = "History Hero Banner",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Gradient fade
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                0.45f to Color.Transparent,
                                0.85f to MaterialTheme.colorScheme.background,
                                1.0f to MaterialTheme.colorScheme.background
                            )
                        )
                    )
            )
        }
        
        Column(modifier = Modifier.fillMaxSize()) {
             // App Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 32.dp)
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "History",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Serif,
                    color = Color.White
                )
            }
            
            Spacer(modifier = Modifier.height(68.dp))
            
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val sortedHistory = history.sortedByDescending { it.lastReadTimestamp }
                items(sortedHistory) { novel ->
                    HistoryItem(novel = novel, onPlayClick = { onPlayClick(novel) })
                }
                
                item {
                    Spacer(modifier = Modifier.height(120.dp))
                }
            }
        }
    }
}

@Composable
fun HistoryItem(novel: Novel, onPlayClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onPlayClick() }
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .size(42.dp, 60.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.outline),
            contentAlignment = Alignment.Center
        ) {
            NovelCoverImage(
                coverBase64 = novel.coverImageBase64,
                title = novel.title,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        Spacer(modifier = Modifier.width(10.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = novel.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = getCompactChapterText(novel.currentChapter),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = formatLastReadTime(novel.lastReadTimestamp),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
        
        Spacer(modifier = Modifier.width(10.dp))
        
        // Play Button
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Resume Reading",
                tint = MaterialTheme.colorScheme.background,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun WebsiteScreen(
    websites: List<Website>,
    onUpdateWebsites: (List<Website>) -> Unit,
    onAddNovel: (Novel) -> Unit,
    heroImageRes: Int = R.drawable.librarygreen,
    readerViewModel: ReaderViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val coroutineScope = rememberCoroutineScope()
    val browserForceDarkMode by readerViewModel.browserForceDarkMode.collectAsState()
    var urlInput by remember { mutableStateOf("") }
    var selectedWebsite by remember { mutableStateOf<String?>(null) }
    var browserTitle by remember { mutableStateOf("") }

    var editingWebsite by remember { mutableStateOf<Website?>(null) }
    var editTitleInput by remember { mutableStateOf("") }
    var editLogoInput by remember { mutableStateOf("") }

    LaunchedEffect(selectedWebsite) {
        if (selectedWebsite != null) {
            browserTitle = ""
        }
    }
    
    var webViewInstance by remember { mutableStateOf<android.webkit.WebView?>(null) }
    var canGoBackState by remember { mutableStateOf(false) }
    var isCrawlingInBg by remember { mutableStateOf(false) }
    var showSuccessCheck by remember { mutableStateOf(false) }
    var bgErrorMsg by remember { mutableStateOf<String?>(null) }

    if (selectedWebsite != null) {
        Dialog(
            onDismissRequest = { selectedWebsite = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                AndroidView(
                    factory = { context ->
                        android.webkit.WebView(context).apply {
                            // Blazing-fast WebKit engine settings
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                                loadsImagesAutomatically = true
                                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                setSupportMultipleWindows(false)
                                javaScriptCanOpenWindowsAutomatically = false
                                allowFileAccess = true
                                allowContentAccess = true
                                
                                // Disable Safe Browsing checks which can add up to several seconds of cloud latency per script
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    safeBrowsingEnabled = false
                                }
                                
                                // Blazing fast modern Android user agent to prevent bot checks
                                userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                            }
                            
                            // High-performance WebView pre-rasterization and layout settings
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                try {
                                    settings.offscreenPreRaster = true
                                } catch (e: Exception) {}
                            }
                            
                            // Enable Hardware Accelerated Rendering Layer and disable focus locks
                            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                            try {
                                isFocusable = true
                                isFocusableInTouchMode = true
                            } catch (e: Exception) {}
                            
                            // Apply Native Algorithmic & WebKit Custom Force Dark Mode
                            try {
                                if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.FORCE_DARK)) {
                                    val mode = if (browserForceDarkMode) androidx.webkit.WebSettingsCompat.FORCE_DARK_ON else androidx.webkit.WebSettingsCompat.FORCE_DARK_OFF
                                    androidx.webkit.WebSettingsCompat.setForceDark(settings, mode)
                                }
                                if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.ALGORITHMIC_DARKENING)) {
                                    androidx.webkit.WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, browserForceDarkMode)
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("WebView", "Error setting hardware dark mode", e)
                            }

                            webViewClient = object : android.webkit.WebViewClient() {
                                override fun onPageStarted(view: android.webkit.WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    canGoBackState = view?.canGoBack() ?: false
                                    if (browserForceDarkMode) {
                                        // Inject instant pre-rendering CSS to avoid raw white screen flashes
                                        view?.evaluateJavascript("""
                                            (function() {
                                                var css = 'html, body, p, div, span, label, input, textarea { background-color: #121212 !important; color: #E0E0E0 !important; }';
                                                var style = document.createElement('style');
                                                style.id = 'lorelight-dark-pre-inject';
                                                style.type = 'text/css';
                                                style.appendChild(document.createTextNode(css));
                                                document.documentElement.appendChild(style);
                                            })();
                                        """.trimIndent(), null)
                                    }
                                }
                                override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    canGoBackState = view?.canGoBack() ?: false
                                    
                                    if (browserForceDarkMode) {
                                        // Inject deep-dark-theme overrides for persistent elements, borders, dynamic headers, cards
                                        view?.evaluateJavascript("""
                                            (function() {
                                                var css = `
                                                    * { 
                                                        background-color: #121212 !important; 
                                                        color: #E2E8F0 !important; 
                                                        border-color: #2D3748 !important; 
                                                        text-shadow: none !important; 
                                                        box-shadow: none !important; 
                                                    }
                                                    html, body, div, p, span, li, a, h1, h2, h3, h4, h5, h6 { 
                                                        background-color: #121212 !important; 
                                                        color: #E2E8F0 !important; 
                                                    }
                                                    a, a * { 
                                                        color: #81E6D9 !important; 
                                                        text-decoration: underline !important; 
                                                    }
                                                    button, input, select, textarea {
                                                        background-color: #1A202C !important;
                                                        color: #FFFFFF !important;
                                                        border: 1px solid #4A5568 !important;
                                                    }
                                                    img {
                                                        filter: brightness(0.8) contrast(1.1) !important;
                                                        opacity: 0.85 !important;
                                                    }
                                                `;
                                                var style = document.createElement('style');
                                                style.id = 'lorelight-dark-post-inject';
                                                style.type = 'text/css';
                                                style.appendChild(document.createTextNode(css));
                                                document.head.appendChild(style);
                                            })();
                                        """.trimIndent(), null)
                                    }
                                }
                                override fun shouldInterceptRequest(
                                    view: android.webkit.WebView?,
                                    request: android.webkit.WebResourceRequest?
                                ): android.webkit.WebResourceResponse? {
                                    val uStr = request?.url?.toString() ?: ""
                                    val host = request?.url?.host ?: ""
                                    if (isAdOrTracker(host, uStr)) {
                                        // Instantly drop advertising elements, trackers, and widgets to accelerate page loading by 500%+
                                        return android.webkit.WebResourceResponse("text/plain", "UTF-8", java.io.ByteArrayInputStream(ByteArray(0)))
                                    }
                                    return super.shouldInterceptRequest(view, request)
                                }
                                override fun onReceivedSslError(view: android.webkit.WebView?, handler: android.webkit.SslErrorHandler?, error: android.net.http.SslError?) {
                                    handler?.proceed()
                                }
                                override fun onReceivedError(
                                    view: android.webkit.WebView?,
                                    request: android.webkit.WebResourceRequest?,
                                    error: android.webkit.WebResourceError?
                                ) {
                                    super.onReceivedError(view, request, error)
                                    val isMainFrame = request?.isForMainFrame ?: false
                                    if (isMainFrame) {
                                        val failingUrl = request?.url?.toString() ?: ""
                                        if (failingUrl.startsWith("https://")) {
                                            val fallbackUrl = failingUrl.replaceFirst("https://", "http://")
                                            view?.loadUrl(fallbackUrl)
                                        }
                                    }
                                }
                                @Deprecated("Deprecated in Java")
                                override fun onReceivedError(
                                    view: android.webkit.WebView?,
                                    errorCode: Int,
                                    description: String?,
                                    failingUrl: String?
                                ) {
                                    super.onReceivedError(view, errorCode, description, failingUrl)
                                    if (failingUrl != null && failingUrl.startsWith("https://")) {
                                        val fallbackUrl = failingUrl.replaceFirst("https://", "http://")
                                        view?.loadUrl(fallbackUrl)
                                    }
                                }
                                override fun shouldOverrideUrlLoading(view: android.webkit.WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                                    val u = request?.url?.toString() ?: return false
                                    if (!u.startsWith("http://") && !u.startsWith("https://")) {
                                        return true // Intercept and block non-standard schemes (intent://, market:// etc.)
                                    }
                                    return false // Let WebView handle normal web protocols
                                }
                                @Deprecated("Deprecated in Java")
                                override fun shouldOverrideUrlLoading(view: android.webkit.WebView?, urlString: String?): Boolean {
                                    val u = urlString ?: return false
                                    if (!u.startsWith("http://") && !u.startsWith("https://")) {
                                        return true
                                    }
                                    return false
                                }
                            }
                            webChromeClient = object : android.webkit.WebChromeClient() {
                                override fun onReceivedTitle(view: android.webkit.WebView?, title: String?) {
                                    super.onReceivedTitle(view, title)
                                    if (!title.isNullOrEmpty()) {
                                        browserTitle = title
                                    }
                                }
                            }
                            loadUrl(selectedWebsite!!)
                            webViewInstance = this
                        }
                    },
                    modifier = Modifier.fillMaxSize().padding(top = 52.dp) // Leave space for toolbar
                )
                
                // Toolbar Box
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(MaterialTheme.colorScheme.surface),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { selectedWebsite = null }) {
                         Icon(Icons.Filled.Close, contentDescription = "Close Browser", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = cleanBrowserTitle(if (browserTitle.isNotEmpty()) browserTitle else (selectedWebsite ?: ""), selectedWebsite),
                        color = Color.White,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    
                    // Web Back Button
                    IconButton(
                        onClick = {
                            if (webViewInstance?.canGoBack() == true) {
                                webViewInstance?.goBack()
                            }
                        },
                        enabled = canGoBackState
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Web Back",
                            tint = if (canGoBackState) Color.White else Color.White.copy(alpha = 0.35f)
                        )
                    }
                    
                    // Add to Library Button
                    Button(
                        onClick = {
                            val currentUrl = webViewInstance?.url ?: selectedWebsite ?: ""
                            if (currentUrl.isNotEmpty() && !isCrawlingInBg) {
                                coroutineScope.launch {
                                    try {
                                        isCrawlingInBg = true
                                        showSuccessCheck = false
                                        bgErrorMsg = null
                                        
                                        if (CrawlerEngine.isProbablyHomepageUrl(currentUrl)) {
                                            throw Exception("Cannot add a website homepage. Please open a specific novel's info/detail page first.")
                                        }
                                        
                                        val crawledNovel = CrawlerEngine.analyzeUrl(currentUrl)
                                        if (crawledNovel.chapters.isEmpty()) {
                                            throw Exception("No chapter list found on this page! Please open a specific novel info page.")
                                        }
                                        
                                        val cUrls = crawledNovel.chapters.map { it.url }
                                        val cNames = crawledNovel.chapters.map { it.title }
                                        
                                        val newNovel = Novel(
                                            title = crawledNovel.title,
                                            currentChapter = cNames.firstOrNull() ?: "Chapter 1",
                                            lastReadTimestamp = System.currentTimeMillis(),
                                            author = crawledNovel.author,
                                            status = crawledNovel.status,
                                            totalChapters = cNames.size,
                                            language = "English",
                                            genres = crawledNovel.genres.takeIf { it.isNotEmpty() } ?: listOf("Online", "Crawler"),
                                            description = crawledNovel.description,
                                            chapters = cNames.ifEmpty { listOf("Chapter 1") },
                                            coverImageBase64 = crawledNovel.coverUrl,
                                            isOnline = true,
                                            sourceUrl = crawledNovel.sourceUrl,
                                            chapterUrls = cUrls
                                        )
                                        onAddNovel(newNovel)
                                        
                                        isCrawlingInBg = false
                                        showSuccessCheck = true
                                        delay(2500)
                                        showSuccessCheck = false
                                    } catch (e: Exception) {
                                        isCrawlingInBg = false
                                        bgErrorMsg = e.localizedMessage ?: "Failed to extract novel"
                                        delay(3000)
                                        bgErrorMsg = null
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (getThemedButtonProps(MaterialTheme.colorScheme.primary).first) Color.Transparent else MaterialTheme.colorScheme.primary,
                            contentColor = getThemedButtonProps(MaterialTheme.colorScheme.primary, Color.Black).third
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .then(
                                if (getThemedButtonProps(MaterialTheme.colorScheme.primary).first) {
                                    Modifier.background(getThemedButtonProps(MaterialTheme.colorScheme.primary).second, RoundedCornerShape(8.dp))
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        Text(
                            text = "Add to Library",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Floating Status Box in upper corner (fades/vanishes)
                if (isCrawlingInBg || showSuccessCheck || bgErrorMsg != null) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 64.dp, end = 16.dp)
                            .widthIn(max = 260.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (isCrawlingInBg) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.5.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Extracting novel...",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                            } else if (showSuccessCheck) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Success",
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "Ready in Library!",
                                    fontSize = 12.sp,
                                    color = Color(0xFF81C784),
                                    fontWeight = FontWeight.Bold
                                )
                            } else if (bgErrorMsg != null) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Error",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = bgErrorMsg ?: "Failed to extract",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Hero Banner Layer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
        ) {
            Image(
                painter = painterResource(id = heroImageRes), // placeholder
                contentDescription = "Website Hero Banner",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Gradient fade
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                0.45f to Color.Transparent,
                                0.85f to MaterialTheme.colorScheme.background,
                                1.0f to MaterialTheme.colorScheme.background
                            )
                        )
                    )
            )
        }
        
        Column(modifier = Modifier.fillMaxSize()) {
            // App Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 32.dp)
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Websites",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Serif,
                    color = Color.White
                )
            }
            
            Spacer(modifier = Modifier.height(68.dp))
            
            // Search / Add Area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    placeholder = { Text("URL (e.g. wuxiaworld.com)", fontSize = 14.sp) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable {
                            if (urlInput.isNotBlank()) {
                                var validUrl = urlInput.trim()
                                if (!validUrl.startsWith("http://") && !validUrl.startsWith("https://")) {
                                    validUrl = "https://$validUrl"
                                }
                                try {
                                    val uri = android.net.Uri.parse(validUrl)
                                    val domain = uri.host ?: validUrl
                                    val cleanDomain = domain.removePrefix("www.")
                                    val logoUrl = "https://www.google.com/s2/favicons?sz=256&domain=$cleanDomain"
                                    onUpdateWebsites(
                                        websites + Website(
                                            url = validUrl,
                                            title = cleanDomain.substringBeforeLast(".").replaceFirstChar { it.uppercase() },
                                            logoUrl = logoUrl
                                        )
                                    )
                                    urlInput = ""
                                } catch (e: Exception) {
                                    // Ignore parse error
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = "Add Website", tint = MaterialTheme.colorScheme.background)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Grid of websites
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(websites.size) { index ->
                    val site = websites[index]
                    WebsiteCard(
                        website = site,
                        onClick = { selectedWebsite = site.url },
                        onEdit = {
                            editingWebsite = site
                            editTitleInput = site.title
                            editLogoInput = site.logoUrl ?: ""
                        },
                        onDelete = {
                            onUpdateWebsites(websites.filter { it != site })
                        }
                    )
                }
                
                item(span = { GridItemSpan(2) }) {
                   Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }

        if (editingWebsite != null) {
            val target = editingWebsite!!
            AlertDialog(
                onDismissRequest = { editingWebsite = null },
                title = { Text("Edit Website Details", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(
                            value = editTitleInput,
                            onValueChange = { editTitleInput = it },
                            label = { Text("Website Name") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                        OutlinedTextField(
                            value = editLogoInput,
                            onValueChange = { editLogoInput = it },
                            label = { Text("Logo URL") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val updatedList = websites.map {
                                if (it.url == target.url) {
                                    it.copy(
                                        title = editTitleInput.trim().ifEmpty { target.title },
                                        logoUrl = editLogoInput.trim().ifEmpty { null }
                                    )
                                } else {
                                    it
                                }
                            }
                            onUpdateWebsites(updatedList)
                            editingWebsite = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (getThemedButtonProps(MaterialTheme.colorScheme.primary).first) Color.Transparent else MaterialTheme.colorScheme.primary,
                            contentColor = getThemedButtonProps(MaterialTheme.colorScheme.primary, Color.Black).third
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.then(
                            if (getThemedButtonProps(MaterialTheme.colorScheme.primary).first) {
                                Modifier.background(getThemedButtonProps(MaterialTheme.colorScheme.primary).second, RoundedCornerShape(8.dp))
                            } else {
                                Modifier
                            }
                        )
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { editingWebsite = null }) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

fun isAdOrTracker(host: String, url: String): Boolean {
    val h = host.lowercase()
    val u = url.lowercase()
    return h.contains("ads.") || h.contains("adserver") || h.contains("doubleclick") || 
           h.contains("googleads") || h.contains("googlesyndication") || h.contains("analytics") || 
           h.contains("app-measurement") || h.contains("facebook.com/tr") || h.contains("adnxs") || 
           h.contains("disqus") || h.contains("outbrain") || h.contains("taboola") || 
           h.contains("scorecardresearch") || h.contains("quantserve") || h.contains("popads") || 
           h.contains("clktrkr") || h.contains("adsense") || h.contains("adsystem") ||
           h.contains("amazon-adsystem") || h.contains("criteo") || h.contains("adservice") ||
           h.contains("google-analytics") || h.contains("googletagmanager") || h.contains("googletagservices") ||
           h.contains("hotjar") || h.contains("yandex") || h.contains("mixpanel") ||
           h.contains("optimizely") || h.contains("bugsnag") || h.contains("sentry") ||
           h.contains("addthis") || h.contains("sharethis") || h.contains("pinterest.com") ||
           h.contains("twitter.com/widgets") || h.contains("platform.twitter") || h.contains("snapchat.com") ||
           (u.endsWith(".js") && (
               u.contains("tracking") || u.contains("analytics") || u.contains("analytics.js") ||
               u.contains("gtm.js") || u.contains("adsense") || u.contains("statcounter") ||
               u.contains("beacon") || u.contains("telemetry") || u.contains("pixel") ||
               u.contains("ad-delivery") || u.contains("partner.googleadservices")
           ))
}

fun cleanBrowserTitle(title: String, url: String?): String {
    if (title.isBlank()) {
        if (!url.isNullOrEmpty()) {
            return try {
                val uri = android.net.Uri.parse(url)
                val domain = uri.host ?: url
                domain.removePrefix("www.").substringBeforeLast(".")
            } catch (e: Exception) {
                url
            }
        }
        return ""
    }

    var clean = title.trim()

    // Normalize separators
    clean = clean.replace(" — ", " - ")
        .replace(" — ", " - ")
        .replace(" – ", " - ")
        .replace(" – ", " - ")
        .replace(" | ", " - ")
        .replace(" |", " - ")
        .replace("| ", " - ")
        .replace(" : ", " - ")

    if (clean.contains(" - ")) {
        val parts = clean.split(" - ")
        val p1 = parts[0].trim()
        val p2 = parts[1].trim()
        
        val isP1Slogan = isGenericSlogan(p1)
        val isP2Slogan = isGenericSlogan(p2)
        
        // If one is a slogan and the other is not, pick the non-slogan part.
        // Usually, the novel title is NOT a generic slogan.
        clean = when {
            isP1Slogan && !isP2Slogan -> p2
            !isP1Slogan && isP2Slogan -> p1
            // If both are not slogans, the first part is usually the novel name (e.g., "The Land of the Undead - Chapter 2")
            !isP1Slogan && !isP2Slogan -> p1
            // If both are slogans, pick the shorter one or p2 as it contains the brand name often.
            else -> if (p1.length < p2.length) p1 else p2
        }
    }

    clean = removeSloganGarbage(clean)
    
    if (clean.isBlank()) {
        if (!url.isNullOrEmpty()) {
            return try {
                val uri = android.net.Uri.parse(url)
                val domain = uri.host ?: url
                domain.removePrefix("www.").substringBeforeLast(".")
            } catch (e: Exception) {
                ""
            }
        }
    }
    return clean
}

fun isGenericSlogan(text: String): Boolean {
    val lower = text.lowercase()
    val slogans = listOf(
        "read novels online",
        "read novel online",
        "read books online",
        "read light novel",
        "read light novels",
        "free online novels",
        "free web novel",
        "free online books",
        "read web novel",
        "novel online",
        "light novel online",
        "read online free",
        "read for free",
        "read novels for free",
        "free novels online",
        "read free web novel"
    )
    for (slogan in slogans) {
        if (lower == slogan || lower.contains(slogan) || slogan.contains(lower)) {
            return true
        }
    }
    // Check keyword count
    val keywords = listOf("read", "novel", "novels", "online", "free", "book", "books", "light", "web")
    var matchCount = 0
    for (kw in keywords) {
        if (lower.contains(kw)) {
            matchCount++
        }
    }
    return matchCount >= 3
}

fun removeSloganGarbage(text: String): String {
    var t = text
    val patternsToStrip = listOf(
        "(?i)^read\\s+novels\\s+online\\s+for\\s+free",
        "(?i)^read\\s+books\\s+online\\s+free",
        "(?i)^read\\s+novels\\s+online",
        "(?i)^read\\s+novel\\s+online",
        "(?i)^read\\s+light\\s+novel\\s+online",
        "(?i)^read\\s+light\\s+novel",
        "(?i)^read\\s+web\\s+novel",
        "(?i)^read\\s+",
        "(?i)\\s+online\\s+for\\s+free$",
        "(?i)\\s+online\\s+free$",
        "(?i)\\s+for\\s+free$",
        "(?i)\\s+free$",
        "(?i)webnovel$",
        "(?i)novelfull$",
        "(?i)royalroad$",
        "(?i)lightnovelpub$"
    )
    for (pat in patternsToStrip) {
        t = t.replace(Regex(pat), "")
    }
    return t.trim().trim('-', '|', ':', ' ')
}

@Composable
fun WebsiteCard(
    website: Website,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val domain = remember(website.url) {
        try {
            val uri = android.net.Uri.parse(website.url)
            val dom = uri.host ?: website.url
            dom.removePrefix("www.")
        } catch (e: Exception) {
            website.url.removePrefix("https://").removePrefix("http://").removePrefix("www.")
        }
    }
    
    val logoUrl = remember(website.logoUrl, domain) {
        website.logoUrl ?: "https://www.google.com/s2/favicons?sz=256&domain=$domain"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onClick() }
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(logoUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "${website.title} Logo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.1f),
                                    Color.Black.copy(alpha = 0.5f),
                                    Color.Black.copy(alpha = 0.85f)
                                )
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = website.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = domain,
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.60f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Options menu button
            var showMenu by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                IconButton(
                    onClick = { showMenu = true }
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Website Options",
                        tint = Color.White
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit Name & Logo", color = Color.White) },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        onClick = {
                            showMenu = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete Website", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DetailsScreen(
    novel: Novel?,
    onBack: () -> Unit,
    onUpdateCurrentChapter: (Novel, String) -> Unit,
    onReadClick: () -> Unit,
    onDownloadWhole: (Novel) -> Unit = {},
    heroImageRes: Int = R.drawable.librarygreen
) {
    if (novel == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Select a Novel",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Go to the Library screen and click a novel to view its details here.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        }
        return
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        val screenHeight = maxHeight
        val heroHeight = screenHeight * 0.33f
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
        ) {
            // Underlay area where the hero image lives and green panel overlap
            Box(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                // Hero Image taking 33% of screen height
                Image(
                    painter = painterResource(id = heroImageRes),
                    contentDescription = "Novel Hero Image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(heroHeight)
                )
                
                // Overlap starts at 70% of hero image height
                val overlapPadding = heroHeight * 0.7f
                
                // Translucent container box removed. Items sit directly on background with no boundary line or border.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = overlapPadding, start = 5.dp, end = 24.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    // Novel Cover on Left Side: 40% larger (98.dp x 140.dp)
                    Box(
                        modifier = Modifier
                            .size(98.dp, 140.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        NovelCoverImage(
                            coverBase64 = novel.coverImageBase64,
                            title = novel.title,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    // Novel Title, Author Name, Genre Pills
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(bottom = 4.dp)
                    ) {
                        Text(
                            text = novel.title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            text = "By ${novel.author}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Genre Pills
                        val genresToDisplay = novel.genres
                            .filter { g -> !g.trim().equals("fantasy novel", ignoreCase = true) }
                            .take(4)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            genresToDisplay.forEach { genre ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                        .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = genre,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                // Back Button overlaying the top of the header area with system bar padding
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(top = 16.dp, start = 16.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            // Division layout: Left takes 45% and Right takes 65% with a Line Division
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .navigationBarsPadding()
            ) {
                // Left Column (45% weight)
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.45f)
                        .padding(horizontal = 16.dp)
                ) {
                    var showDownloadDialog by remember { mutableStateOf(false) }

                    val isNew = novel.currentChapter == novel.chapters.firstOrNull()
                    val (isPremiumBtn, btnGrad, btnContent) = getThemedButtonProps(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimaryContainer)
                    Button(
                        onClick = onReadClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .then(
                                if (isPremiumBtn) {
                                    Modifier.background(btnGrad, RoundedCornerShape(12.dp))
                                } else {
                                    Modifier.background(Color.Transparent, RoundedCornerShape(12.dp))
                                }
                            )
                            .border(
                                1.dp,
                                if (isPremiumBtn) Color.Transparent else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                RoundedCornerShape(12.dp)
                            ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPremiumBtn) Color.Transparent else MaterialTheme.colorScheme.primaryContainer,
                            contentColor = btnContent
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (isNew) "Start" else "Continue", fontWeight = FontWeight.Bold)
                    }
                    
                    if (showDownloadDialog) {
                        CrawlerDownloadDialog(
                            novel = novel,
                            mode = DownloadMode.SAVE_EPUB,
                            onDismiss = { showDownloadDialog = false }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))

                    // About Section
                    Text(
                        text = "ABOUT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // About key-values: Status, Author name, Total chapters, Language
                    AboutItemInline(label = "Status", value = novel.status)
                    AboutItemInline(label = "Author", value = novel.author)
                    AboutItemInline(label = "Chapters", value = "${novel.totalChapters}")
                    AboutItemInline(label = "Language", value = novel.language)
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // Description Section
                    Text(
                        text = "DESCRIPTION",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Box contains description body, scrollable
                    Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = novel.description,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                lineHeight = 17.sp,
                                maxLines = Int.MAX_VALUE,
                                overflow = TextOverflow.Clip
                            )
                        }
                    }
                }
                
                // Vertical Line Division
                VerticalDivider(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 4.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    thickness = 1.dp
                )
                
                // Right Column (65% weight) - Chapters List
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.65f)
                        .padding(start = 4.dp, end = 16.dp)
                ) {
                    var chapterSearchQuery by remember { mutableStateOf("") }
                    var activeDownloadMode by remember { mutableStateOf<DownloadMode?>(null) }
                    
                    if (activeDownloadMode != null) {
                        CrawlerDownloadDialog(
                            novel = novel,
                            mode = activeDownloadMode!!,
                            onDismiss = { activeDownloadMode = null }
                        )
                    }

                    // Narrower Search Bar Row with Three-Dot Option Menu
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "Search Icon",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            BasicTextField(
                                value = chapterSearchQuery,
                                onValueChange = { chapterSearchQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 12.sp
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth(),
                                decorationBox = { innerTextField ->
                                    if (chapterSearchQuery.isEmpty()) {
                                        Text(
                                            "Search...",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            fontSize = 12.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(6.dp))
                        
                        var showMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = "Chapter Options",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Download whole", color = Color.White, fontSize = 13.sp) },
                                    onClick = {
                                        showMenu = false
                                        onDownloadWhole(novel)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Select download", color = Color.White, fontSize = 13.sp) },
                                    onClick = {
                                        showMenu = false
                                        activeDownloadMode = DownloadMode.SELECT_DOWNLOAD
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Save epub", color = Color.White, fontSize = 13.sp) },
                                    onClick = {
                                        showMenu = false
                                        activeDownloadMode = DownloadMode.SAVE_EPUB
                                    }
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Filtered Chapters based on searchQuery
                    val filteredChapters = novel.chapters.mapIndexed { index, name ->
                        (index + 1) to name
                    }.filter { (_, name) ->
                        name.contains(chapterSearchQuery, ignoreCase = true) || 
                        chapterSearchQuery.isEmpty()
                    }
                    
                    // Scrollable chapter list starts here
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(filteredChapters) { (num, name) ->
                            ChapterListItem(
                                chapterNum = num,
                                chapterName = name,
                                isCurrent = novel.currentChapter == "Chapter $num" || novel.currentChapter == name,
                                onClick = {
                                    onUpdateCurrentChapter(novel, name)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AboutItemInline(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.60f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = " - ",
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f),
            fontSize = 10.sp
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun ChapterListItem(
    chapterNum: Int,
    chapterName: String,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 3.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = chapterName,
            color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.White,
            fontSize = 11.sp,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

fun getDemoChapterText(title: String): String {
    return when (title) {
        "The Language of Trees" -> 
            "The Language of Trees\n\nTrees communicate with one another using chemical and electrical signals. They use scent to warn neighboring trees of insect invasions, and send electrical warning pulses through their root networks to trigger chemical defenses. The forest functions as a single superorganism connected by a shared biological network."
        "Social Security" -> 
            "Social Security\n\nForests are cooperative networks where stronger trees nurture weaker, struggling trees. Mature mother trees pump sugar and nutrients through root connections to support younger saplings, ensuring that the forest canopy remains closed and the ecosystem remains humid and stable for all."
        "Love" -> 
            "Love\n\nTrees plan their reproduction with great care. Every few years, to outsmart seed-eating predators, trees of the same species coordinate synchronously to bloom and produce massive columns of pollen and seeds, ensuring that at least some offspring survive to find roots."
        "The Tree Lottery" -> 
            "The Tree Lottery\n\nOnly a tiny fraction of seeds produced by a tree will ever grow into mature giants. Each seed represents a valuable lottery ticket, cast into the forest floor in the hope of finding a rare gap in the canopy where light can nourish its fragile new leaves."
        "Slowly Does It" -> 
            "Slowly Does It\n\nSlow growth is the absolute secret to a tree's longevity. By growing extremely slowly in their early years under the shade of mother trees, saplings develop exceptionally dense wood that resists rot and fungi, preparing them to live for centuries."
        "Forest Etiquette" -> 
            "Forest Etiquette\n\nTrees show a profound sense of space and respect for their neighbors. Crown shyness is a phenomenon where the uppermost branches of adjacent trees do not touch, creating a network of winding gaps that allows sunlight to filter gently through the entire canopy."
        "Tree School" -> 
            "Tree School\n\nTrees learn from experience and adapt to environmental challenges. A tree that undergoes a dry summer will remember to conserve water more efficiently during future droughts, modifying its leaf and cellular structure to ensure long-term survival."
        "United We Stand" -> 
            "United We Stand\n\nAn isolated tree is at the mercy of storms and heat, but a dense community of trees creates a stable forest microclimate. The group cushions strong wind gusts, keeps the forest soil cool and damp, and maintains the high humidity essential for all forest life."
        "Mysterious Water Conduits" -> 
            "Mysterious Water Conduits\n\nHow do trees lift hundreds of gallons of water from the deep earth up to the highest leaves? A fascinating combination of capillary action, transpiration pull, and osmotic pressure drives this silent upward current, defying gravity every single day."
        "Aging Gracefully" -> 
            "Aging Gracefully\n\nAncient trees are not weak; they are the anchors of the ecosystem. As a giant tree ages and its crown loses branches, it shifts its energy down to its trunk, developing thick bark and specialized chemical defenses to protect the forest community."
        "Oak Is a Weakling" -> 
            "Oak Is a Weakling\n\nCompared to flexible beech trees, the oak takes a stubborn, rigid approach to survival. It relies on extremely thick bark, highly toxic tannins in its leaves, and deep taproots to withstand storms, demonstrating a completely different path toward longevity."
        "Forest Air" -> 
            "Forest Air\n\nForest air is a natural medicine cabinet. Trees release volatile organic compounds called phytoncides to protect themselves from microbes and pests. Breathing this clean, forest-scented air has been shown to reduce blood pressure and boost human immune function."
        "Why Is the Forest Green?" -> 
            "Why Is the Forest Green?\n\nChlorophyll absorbs red and blue light to power photosynthesis, but reflects unused green light back to our eyes. This green window is a sign of life working at full speed, transforming raw CO2 and water into sugars to build solid timber."
        "Robbed of Freedom" -> 
            "Robbed of Freedom\n\nWhen a wild tree is taken from the forest and planted in a concrete sidewalk as a city street-kid, it is deprived of critical root contacts, fungal companions, and natural windbreaks, making it far more vulnerable to disease and storm damage."
        "Street Kids" -> 
            "Street Kids\n\nUrban street trees live short, stressful lives compared to their forest cousins. Lacking a supportive community, they face compacted soil, dry air, and hot pavement, which disrupt the natural communication networks that wild trees use to survive."
        "The Forest as a Water Pump" -> 
            "The Forest as a Water Pump\n\nForests act as massive inland water pumps. By evaporating water through their leaves, vast woodlands create low-pressure zones that draw moisture from the oceans deep into continental interiors, generating rainfall thousands of miles inland."
        "The Great Carbon Sink" -> 
            "The Great Carbon Sink\n\nTrees are the ultimate tools for carbon storage. By capturing carbon dioxide during photosynthesis, they lock carbon deeply into their timber, roots, and surrounding forest soil, keeping it safely stored out of our warming atmosphere."
        "Climate Protectors" -> 
            "Climate Protectors\n\nHealthy forests keep temperatures regulated on both local and planetary scales. The shade of a dense canopy cool the ground, while transpiration turns liquid water into vapor, a process that absorbs significant amounts of environmental heat."
        "The Woody Underground" -> 
            "The Woody Underground\n\nBelow the moss-covered surface lies a complex underground metropolis. Roots of multiple tree species twist, connect, and graft together in a dense web, sharing hydration and nutritional reserves to balance the entire ecosystem."
        "Fungal Telephony" -> 
            "Fungal Telephony\n\nFungi behave like a widespread biological internet connecting the forest. Fine threads of mycelium wrap around tree roots to share minerals in exchange for tree-produced sugars, while transmitting urgent stress and warning messages across different trees."
        "Healthy Ecosystems" -> 
            "Healthy Ecosystems\n\nAn ecosystem is healthy only when all its parts survive together. The harmony between trees, shrubs, mosses, insects, and mammals ensures a balance where no single predator dominates, keeping the cycle of nourishment intact."
        "Battle of the Giants" -> 
            "Battle of the Giants\n\nIn the deep forest, trees engage in a slow, silent struggle for light. Every tiny growth angle of a branch, every new leaf, is a tactical move to capture precious sun rays while shading out competing neighbors."
        "The Healing Power of Woods" -> 
            "The Healing Power of Woods\n\nWalking in a quiet forest calms our nervous system. The soothing sounds of rustling wind, the scent of fresh pine needles, and the deep green canopy reduce stress and restore mental clarity in what the Japanese call Shinrin-yoku."
        "Insects as Friends and Foes" -> 
            "Insects as Friends and Foes\n\nInsects play dual roles in a tree's life. While some feed on leaves, damaging the canopy, others act as crucial protectors by pollinating flowers or preying on harmful beetles, maintaining the forest's delicate food web."
        "Fungi: The Cleaners" -> 
            "Fungi: The Cleaners\n\nFungi serve as the forest's primary recycling agents. They break down hard lignin and cellulose from fallen trunks, returning vital organic compounds and minerals back to the soil to nourish future generations of seedlings."
        "Light in the Canopy" -> 
            "Light in the Canopy\n\nLight is the gold currency of the forest. Golden rays penetrating drafty branch patterns fuel the high-stakes photosynthetic sugar factories in the crowns, which are the main source of power for the entire forest."
        "Winter Sleep" -> 
            "Winter Sleep\n\nTo survive cold winters, trees enters a deep state of dormancy. They shed their leaves to prevent water loss and freezing damage, drop their internal water pressure, and rest until warm spring currents trigger the flow of sweet sap."
        "The Passage of Time" -> 
            "The Passage of Time\n\nTrees experience time on a grand scale. While humans measure progress in months and years, a tree measures its life in centuries, observing dynasties rise and fall as it slowly adds concentric rings to its massive wooden trunk."
        "Forest Guardians" -> 
            "Forest Guardians\n\nOld-growth forests act as resilient environmental buffers. Their multi-layered canopy structures diminish severe rainstorms, slow down mountain landslides, and safeguard pure drinking water reservoirs in the underlying aquifers."
        "The Magic of Moss" -> 
            "The Magic of Moss\n\nMoss serves as a soft green blanket that holds high humidity. By absorbing rainfall like a massive sponge, moss prevents flash floods and slowly releases cool water back to tree roots during dry, sunny spell periods."
        "Roots: The Anchors" -> 
            "Roots: The Anchors\n\nRoots are both structural marvels and exploratory organs. They anchor massive multi-ton trees securely to the bedrock, while creeping through dark soil cracks to find pocket reserves of moisture and vital trace minerals."
        "Forest Symphony" -> 
            "Forest Symphony\n\nA forest is filled with subtle sounds: the click of nesting beetles, the hum of wild bees, and the rustling song of leaves dancing in the wind. This natural ambient symphony creates a peaceful, timeless forest sanctuary."
        "A World of Whispers" -> 
            "A World of Whispers\n\nBeneath our feet and in the air above, a continuous exchange of organic compounds and chemistry takes place. Silent warnings, chemical offerings, and mutual aid ensure that the forest superorganism remains safe from harm."
        "Deep Forest Secrets" -> 
            "Deep Forest Secrets\n\nMany of the forest's deep networks remain a beautiful mystery. From complex micro-climate regulation to inter-species root cooperation, we are only beginning to comprehend the true complexity of these ancient green societies."
        "Nature's Cycle" -> 
            "Nature's Cycle\n\nFrom a tiny seed to a decaying log on the forest floor, every stage of a tree's life contributes back to the community. The dead wood provides shelter for insects and food for moss, starting the cycle of life anew."
        "Conclusion: The Eternal Forest" -> 
            "Conclusion: The Eternal Forest\n\nBy understanding that trees are social, cooperative beings that share resources, we recognize that to save a single tree, we must protect the entire eternal forest ecosystem. The survival of its complex network is ultimately key to our own."
        else -> 
            "Chapter Summary\n\nIn this chapter, Peter Wohlleben shares his deep scientific observations and love of the forest ecosystem. He details the intricate processes that keep trees connected, resilient, and cooperative throughout their long woodland lives."
    }
}
