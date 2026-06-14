package com.example.lorelight

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI

data class CrawledNovel(
    val title: String,
    val author: String,
    val description: String,
    val coverUrl: String?,
    val sourceUrl: String,
    val chapters: List<CrawledChapter>,
    val genres: List<String> = emptyList(),
    val status: String = "Ongoing"
)

data class CrawledChapter(
    val index: Int,
    val title: String,
    val url: String
)

object CrawlerEngine {

    fun isProbablyHomepageUrl(url: String): Boolean {
        val cleanUrl = url.trim().lowercase().removeSuffix("/")
        try {
            val uri = URI(cleanUrl)
            val path = uri.path ?: ""
            
            // Root domain or empty path
            if (path.isEmpty() || path == "/" || path == "/index.html" || path == "/home" || path == "/index.php" || path == "/index" || path == "/main") {
                return true
            }
            
            // Browse/Catalog directory/listing links
            if (path.contains("/search") || path.contains("/browse") || path.contains("/tag/") || 
                path.contains("/genres") || path.contains("/category") || path.contains("/listings") || 
                path.contains("/all-novels") || path == "/authors" || path == "/popular" || 
                path == "/updates" || path.contains("/latest-releases") || path.contains("/active-popular")
            ) {
                return true
            }
            
            // Base URL regex checks e.g. http://site.com
            val baseRegex = Regex("""^(https?://)?[a-zA-Z0-9.-]+\.[a-zA-Z]{2,4}/?$""")
            if (baseRegex.matches(cleanUrl)) {
                return true
            }
        } catch (e: Exception) {
            // safe fallback
        }
        return false
    }

    suspend fun analyzeUrl(url: String): CrawledNovel = withContext(Dispatchers.IO) {
        var currentUrl = url
        val doc = try {
            Jsoup.connect(currentUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(15000)
                .get()
        } catch (e: Exception) {
            if (currentUrl.startsWith("https://")) {
                currentUrl = currentUrl.replaceFirst("https://", "http://")
                Jsoup.connect(currentUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(15000)
                    .get()
            } else {
                throw e
            }
        }
        
        val baseUri = URI(currentUrl)

        val title = extractTitle(doc)
        val author = extractAuthor(doc)
        val description = extractDescription(doc)
        val rawCoverUrl = extractCoverUrl(doc, baseUri)
        val genres = extractGenres(doc)
        val status = extractStatus(doc)
        val chapters = extractChapters(doc, baseUri)

        val base64Cover = downloadImageAsBase64(rawCoverUrl) ?: rawCoverUrl

        CrawledNovel(
            title = title.ifBlank { "Unknown Title" },
            author = author.ifBlank { "Unknown Author" },
            description = description.ifBlank { "No description available." },
            coverUrl = base64Cover,
            sourceUrl = url,
            chapters = chapters,
            genres = genres,
            status = status
        )
    }

    private suspend fun downloadImageAsBase64(imageUrl: String?): String? = withContext(Dispatchers.IO) {
        if (imageUrl.isNullOrBlank()) return@withContext null
        try {
            val response = Jsoup.connect(imageUrl)
                .ignoreContentType(true)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.0.0 Safari/537.36")
                .timeout(10000)
                .execute()
            val bytes = response.bodyAsBytes()
            if (bytes != null && bytes.isNotEmpty()) {
                android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun extractTitle(doc: Document): String {
        return doc.select("h1, .post-title, .novel-title, .title").firstOrNull()?.text()
            ?: doc.title().replace(Regex("(?i)-.*(read|novel).*$"), "").trim()
    }

    private fun extractAuthor(doc: Document): String {
        val cleanDoc = doc.clone()
        val layoutSelectors = listOf(
            "header", "footer", "nav", ".nav", ".navigation", ".footer", ".header", 
            "#header", "#footer", ".sidebar", "#sidebar", ".menu", ".menu-container", 
            ".navbar", "#navbar", ".top-bar", ".footer-links", "#navigation"
        )
        layoutSelectors.forEach { cleanDoc.select(it).remove() }

        val candidates = mutableListOf<String>()

        // 1. Meta tag books:author (Check on original doc, since meta tags are in head which aren't in body layout)
        val metaBooksAuthor = doc.select("meta[property=\"books:author\"], meta[property=\"book:author\"], meta[name=\"author\"], meta[property=\"og:book:author\"], meta[name=\"twitter:creator\"]").attr("content").trim()
        if (metaBooksAuthor.isNotBlank()) candidates.add(metaBooksAuthor)

        // 2. Class names on cleanDoc (removing global layout noise)
        val selectors = listOf(
            "[itemprop=author] [itemprop=name]",
            "[itemprop=author]",
            "span[itemprop=name]",
            ".author",
            ".novel-author",
            ".info-author",
            ".property-author",
            "[itemprop=creator]",
            "a[href*=/author/]",
            "a[class*=author]",
            ".writer",
            ".byline"
        )
        selectors.forEach { selector ->
            val text = cleanDoc.select(selector).text().trim()
            if (text.isNotBlank()) candidates.add(text)
        }

        // 3. Labels containing "Author"
        val labelEl = cleanDoc.select("p:contains(Author), div:contains(Author), span:contains(Author), td:contains(Author)")
        labelEl.forEach { el ->
            val text = el.text().trim()
            if (text.isNotBlank()) candidates.add(text)
        }

        // 4. Info section fallback
        val infoText = cleanDoc.select(".info").text().trim()
        if (infoText.isNotBlank() && infoText.contains("Author", ignoreCase = true)) {
            candidates.add(infoText)
        }

        // Clean extracted text: Remove "Author:", "by", extra spaces, line breaks, site brand links
        val cleanedCandidates = candidates.map { raw ->
            raw.replace(Regex("(?i)^by\\s+"), "")
               .replace(Regex("(?i)^author:?\\s*"), "")
               .replace(Regex("(?i)\\bauthor:?\\s*"), "")
               .replace(Regex("(?i)\\bby\\s+"), "")
               .replace("\n", " ")
               .replace(Regex("\\s+"), " ")
               .trim()
        }.filter {
            it.isNotBlank() && 
            it.length < 60 && 
            !it.contains("chapter", ignoreCase = true) &&
            !it.contains("webnovel", ignoreCase = true) &&
            !it.contains("royalroad", ignoreCase = true) &&
            !it.contains("novelfull", ignoreCase = true) &&
            !it.contains("read", ignoreCase = true) &&
            !it.contains("novel", ignoreCase = true)
        }

        val bestAuthor = cleanedCandidates.firstOrNull() ?: "Unknown Author"

        // Backup to classifier if default is unknown
        if (bestAuthor == "Unknown Author") {
            val classified = ElementClassifier(cleanDoc).classifyAuthor()
            if (classified != null && classified.score > 20.0) {
                return classified.text
            }
        }

        return bestAuthor
    }

    private fun extractDescription(doc: Document): String {
        val classified = ElementClassifier(doc).classifyDescription()
        if (classified != null && classified.score > 20.0) {
            return classified.text
        }

        val metaDesc = doc.select("meta[property=og:description], meta[name=description]").attr("content").trim()
        
        val candidates = doc.select(".description, .summary, [itemprop=description], .manga-info-text, .novel-synopsis, .synopsis, .desc, #info, .content-inner")
        var bestText = ""
        
        candidates.forEach { element ->
             val clone = element.clone()
             clone.select("ul, li, a, h1, h2, h3, .genres, .author, .status, .metadata").remove()

             val text = clone.select("p").joinToString("\n") { it.text().trim() }
             val fallbackText = clone.text()
             
             val finalText = if (text.isNotBlank()) text else fallbackText
             if (finalText.length > bestText.length && finalText.length < 5000) {
                 bestText = finalText
             }
        }
        
        if (bestText.length > 50 && !bestText.contains("Chapter 1:", ignoreCase = true)) {
            return bestText
        }
        return metaDesc
    }

    private fun extractGenres(doc: Document): List<String> {
        val cleanDoc = doc.clone()
        val layoutSelectors = listOf(
            "header", "footer", "nav", ".nav", ".navigation", ".footer", ".header", 
            "#header", "#footer", ".sidebar", "#sidebar", ".menu", ".menu-container", 
            ".navbar", "#navbar", ".top-bar", ".footer-links", "#navigation"
        )
        layoutSelectors.forEach { cleanDoc.select(it).remove() }

        val classified = ElementClassifier(cleanDoc).classifyGenres()
        if (classified.isNotEmpty()) {
            return classified
        }

        val genres = mutableSetOf<String>()
        val genreLinks = cleanDoc.select("a[href*=genre], a[href*=tag], .genres a, .categories a, [itemprop=genre], .property-item a, .novel-tags a, .tags a")
        
        genreLinks.forEach { link ->
            val text = link.text().trim().replace(Regex("^#"), "")
            if (text.isNotBlank() && text.length < 25 && !text.contains(":") && !text.contains("/")) {
                genres.add(text)
            }
        }
        return genres.toList()
    }

    private fun extractStatus(doc: Document): String {
        // 1. Common class names/id/itemprops for status
        val selectors = listOf(
            ".status", ".novel-status", ".info-status", ".property-status",
            "[itemprop=status]", ".property-item", ".metadata", ".info-item"
        )
        for (sel in selectors) {
            val elements = doc.select(sel)
            for (el in elements) {
                val txt = el.text().trim().lowercase()
                if (txt.contains("completed") || txt.contains("complete") || txt.contains("finished")) {
                    return "Completed"
                }
                if (txt.contains("ongoing") || txt.contains("active") || txt.contains("publishing") || txt.contains("serializing")) {
                    return "Ongoing"
                }
                if (txt.contains("dropped") || txt.contains("hiatus") || txt.contains("paused")) {
                    return "Hiatus"
                }
            }
        }
        
        // 2. Search specific text elements with label status
        val elements = doc.select("span, li, div, td, p, h2, h3, h4, h5")
        for (el in elements) {
            val text = el.text().trim()
            if (text.length < 50 && text.contains(Regex("(?i)status"))) {
                val lower = text.lowercase()
                if (lower.contains("completed") || lower.contains("complete") || lower.contains("finished")) {
                    return "Completed"
                }
                if (lower.contains("ongoing") || lower.contains("active") || lower.contains("publishing") || lower.contains("serializing")) {
                    return "Ongoing"
                }
                if (lower.contains("dropped") || lower.contains("hiatus") || lower.contains("paused")) {
                    return "Hiatus"
                }
            }
        }

        // 3. Fallback check on the entire body text
        val allText = doc.text().lowercase()
        if (allText.contains("status: completed") || allText.contains("status : completed") || allText.contains("status: complete")) {
            return "Completed"
        }
        if (allText.contains("status: ongoing") || allText.contains("status : ongoing")) {
            return "Ongoing"
        }

        // Default fallback is Ongoing, but search for Completed if chapters look done
        return "Ongoing"
    }

    private fun extractCoverUrl(doc: Document, baseUri: URI): String? {
        val candidates = mutableListOf<String>()

        // meta[property="og:image"]
        val ogImage = doc.select("meta[property=\"og:image\"], meta[property=\"og:image:secure_url\"]").attr("content").trim()
        if (ogImage.isNotBlank()) candidates.add(ogImage)

        // meta[name="twitter:image"]
        val twitterImage = doc.select("meta[name=\"twitter:image\"], meta[name=\"twitter:image:src\"]").attr("content").trim()
        if (twitterImage.isNotBlank()) candidates.add(twitterImage)

        // .cover img, .book img, .novel-cover img, img[alt*="cover"]
        val selectorImages = doc.select(".cover img, .book img, .novel-cover img, img[alt*=\"cover\"], img[class*=\"cover\"], img[id*=\"cover\"]")
        selectorImages.forEach { img ->
            val src = img.attr("src").trim()
            if (src.isNotBlank()) candidates.add(src)
            val dataSrc = img.attr("data-src").trim()
            if (dataSrc.isNotBlank()) candidates.add(dataSrc)
            val lazySrc = img.attr("data-lazy-src").trim()
            if (lazySrc.isNotBlank()) candidates.add(lazySrc)
        }

        // Clean relative URLs to absolute, resolve HTTP, non-placeholder
        val validUrls = candidates
            .map { resolveUrl(baseUri, it) }
            .filter { url ->
                url.startsWith("http", ignoreCase = true) &&
                !url.contains("placeholder", ignoreCase = true) &&
                !url.contains("default-cover", ignoreCase = true) &&
                !url.contains("loading.gif", ignoreCase = true) &&
                !url.contains("blank.gif", ignoreCase = true)
            }
            .distinct()

        if (validUrls.isEmpty()) {
            val fallbackImg = doc.select("img").firstOrNull()?.attr("src")?.trim()
            if (!fallbackImg.isNullOrBlank()) {
                val resolved = resolveUrl(baseUri, fallbackImg)
                if (resolved.startsWith("http", ignoreCase = true)) {
                    return resolved
                }
            }
            return null
        }

        // Pick largest resolution or metadata preferred cover
        val bestUrl = validUrls.maxByOrNull { url ->
            var score = 0
            if (url == ogImage) score += 200
            if (url == twitterImage) score += 150
            if (url.contains("1200") || url.contains("1000") || url.contains("800") || url.contains("600")) {
                score += 50
            }
            if (url.contains("thumb", ignoreCase = true) || url.contains("150x", ignoreCase = true) || url.contains("100x", ignoreCase = true)) {
                score -= 100
            }
            score
        }

        return bestUrl
    }

    private fun extractChapterNumber(text: String): Double? {
        val cleaned = text.lowercase().trim()
        val pat1 = Regex("""(?:chapter|chpx?|episode|ep|ch\.?|vol|volume|v\.)\s*(\d+(?:\.\d+)?)""")
        val match1 = pat1.find(cleaned)
        if (match1 != null) {
            return match1.groupValues[1].toDoubleOrNull()
        }
        
        val pat2 = Regex("""\b(\d+(?:\.\d+)?)\b""")
        val matches = pat2.findAll(cleaned).toList()
        for (match in matches) {
            val numStr = match.groupValues[1]
            val num = numStr.toDoubleOrNull() ?: continue
            if (num > 0.0 && num < 100000.0) {
                return num
            }
        }
        return null
    }

    private fun extractChapters(doc: Document, baseUri: URI): List<CrawledChapter> {
        val cleanDoc = doc.clone()

        // 5. IMPORTANT SAFETY FILTER: Remove ignored / junk elements from the document cloned
        val junkSelectors = listOf(
            "header", "footer", "nav", ".nav", ".navigation", ".footer", ".header",
            ".sidebar", ".widget", "#sidebar", "#widget", ".right-sidebar", ".left-sidebar", ".ranking-sidebar",
            ".comments", ".comment", "#comments", "#comment", ".comments-area",
            ".ads", ".ad", ".ad-box", "ins", "iframe", "script", "style",
            ".related", ".recommend", ".recommendations", ".related-novels", ".related-post",
            ".latest-releases", ".latest-release", ".popular-novels", ".ranking", ".trend", ".trending",
            "#ranking"
        )
        junkSelectors.forEach { selector ->
            cleanDoc.select(selector).remove()
        }

        // Find all possible containers of anchor tags
        val containers = cleanDoc.select("div, ul, ol, table, tbody, section, main, article")
        
        // Define key rejection filter helper
        fun isRejectedContainer(el: org.jsoup.nodes.Element): Boolean {
            val classId = (el.className() + " " + el.id()).lowercase()
            
            // Reject if sidebar class is detected or any junk keywords in class name / ID
            val badClassKeywords = listOf(
                "sidebar", "widget", "ranking", "latest", "recent", "comment", 
                "popular", "relat", "recommend", "nav", "footer", "header", "menu", "search"
            )
            if (badClassKeywords.any { classId.contains(it) }) {
                return true
            }

            // Reject if title or header inside or directly preceding the container contains bad keywords
            val headersText = el.select("h1, h2, h3, h4, h5, h6, .title, .widget-title, .section-title, .header").text().lowercase()
            if (headersText.contains("latest") || headersText.contains("recent") || headersText.contains("new updates") || headersText.contains("update")) {
                return true
            }

            var sibling = el.previousElementSibling()
            var limit = 3
            while (sibling != null && limit > 0) {
                if (sibling.tagName().matches(Regex("h[1-6]")) || sibling.className().contains("title", ignoreCase = true)) {
                    val sibText = sibling.text().lowercase()
                    if (sibText.contains("latest") || sibText.contains("recent") || sibText.contains("new updates") || sibText.contains("update")) {
                        return true
                    }
                }
                sibling = sibling.previousElementSibling()
                limit--
            }

            return false
        }

        // Helper to check if a link is chapter-like
        fun isChapterLikeLink(link: org.jsoup.nodes.Element): Boolean {
            val href = link.attr("href").trim()
            if (href.isBlank() || href.startsWith("#") || href.contains("javascript:", ignoreCase = true)) return false
            
            // Avoid typical general site links
            val lowerHref = href.lowercase()
            val badHrefKeywords = listOf("comment", "contact", "about", "privacy", "advertise", "login", "register", "signup", "user", "profile", "facebook", "twitter", "reddit", "discord", "category", "genre", "author", "tag")
            if (badHrefKeywords.any { lowerHref.contains(it) }) return false

            val text = link.text().trim()
            val lowerText = text.lowercase()
            if (lowerText.isBlank() || lowerText.contains("home") || lowerText.contains("next") || lowerText.contains("prev") || lowerText.contains("bookmark") || lowerText.contains("search")) return false
            
            // Check if text or href contains chapter patterns
            val hasChapterPattern = lowerText.contains("chapter") || 
                    lowerText.contains("ch.") || 
                    lowerText.contains("ch ") || 
                    lowerText.contains("vol") || 
                    lowerText.matches(Regex(".*(?:ch|vol|ep)[a-zA-Z\\.]*\\s*\\d+.*")) ||
                    lowerHref.contains("chapter") ||
                    lowerHref.contains("/ch-") ||
                    lowerHref.contains("/ch/") ||
                    lowerText.matches(Regex("\\d+(?:\\.\\d+)?"))
            
            return hasChapterPattern
        }

        data class ScoredContainer(
            val element: org.jsoup.nodes.Element,
            val chapterLinks: List<org.jsoup.nodes.Element>,
            val score: Double
        )

        val scoredContainers = mutableListOf<ScoredContainer>()

        for (el in containers) {
            if (isRejectedContainer(el)) continue

            // Find all anchor links directly or indirectly under this container
            val allAnchors = el.select("a[href]")
            val chapterAnchors = allAnchors.filter { isChapterLikeLink(it) }.distinctBy { resolveUrl(baseUri, it.attr("href")) }

            if (chapterAnchors.isEmpty()) continue

            // STEP 3: Reject if Less than 15 chapter links (Strict check during primary phase)
            if (chapterAnchors.size < 15) {
                val classId = (el.className() + " " + el.id()).lowercase()
                val isExplicitChapterList = classId.contains("chapter-list") || classId.contains("chapters") || classId.contains("wp-manga-chapter")
                if (!isExplicitChapterList) {
                    continue
                }
            }

            // Exclude descending-only short list (e.g. less than 15 links that are only descending)
            val parsedNumbers = chapterAnchors.mapNotNull { extractChapterNumber(it.text()) }
            if (chapterAnchors.size < 15 && parsedNumbers.size >= 2) {
                var isDescending = true
                for (i in 0 until parsedNumbers.size - 1) {
                    if (parsedNumbers[i] < parsedNumbers[i + 1]) {
                        isDescending = false
                        break
                    }
                }
                if (isDescending) continue // REJECT descending-only short list
            }

            // STEP 2: Score each container
            var score = 0.0

            // + number of chapter links
            score += chapterAnchors.size * 2.5

            // + sequential chapter numbers
            var sequentialPairsCount = 0
            var prevNum: Double? = null
            for (num in parsedNumbers) {
                if (prevNum != null) {
                    val diff = Math.abs(num - prevNum)
                    if (diff == 1.0 || diff == 0.5) {
                        sequentialPairsCount++
                    }
                }
                prevNum = num
            }
            score += sequentialPairsCount * 8.0

            // + consistent URL pattern
            val patternGroups = chapterAnchors.groupBy { link ->
                val resolved = resolveUrl(baseUri, link.attr("href"))
                resolved.replace(Regex("\\d+"), "{num}")
            }
            val largestPatternGroupSize = patternGroups.values.maxOfOrNull { it.size } ?: 0
            val consistencyRatio = largestPatternGroupSize.toDouble() / chapterAnchors.size
            score += (consistencyRatio * 150.0) + (largestPatternGroupSize * 3.0)

            // + starting from 1 (or close to 1) check & "chapter-list" naming bonus
            val lowestNum = parsedNumbers.minOrNull() ?: 1000.0
            if (lowestNum <= 2.0) {
                score += 350.0 // Huge starting-from-1 bonus
            } else if (lowestNum <= 5.0) {
                score += 150.0
            }

            val classId = (el.className() + " " + el.id()).lowercase()
            if (classId.contains("chapter-list") || classId.contains("chapters") || classId.contains("wp-manga-chapter") || classId.contains("table-of-contents") || classId.contains("contents")) {
                score += 300.0 // Naming match bonus
            }

            scoredContainers.add(ScoredContainer(el, chapterAnchors, score))
        }

        // STEP 4: Select highest valid scored container
        var bestContainer = scoredContainers.maxByOrNull { it.score }

        // 7. FALLBACK SYSTEM: If no valid container of chapters found, fallback to chronological link order of any chapter links (excluding widgets)
        if (bestContainer == null || bestContainer.chapterLinks.isEmpty()) {
            val fallbackAnchors = cleanDoc.select("a[href]").filter { isChapterLikeLink(it) }
                .filter { link ->
                    // Make sure link is not inside a sidebar or widget
                    val parentSidebar = link.parents().any { parent ->
                        val pClassId = (parent.className() + " " + parent.id()).lowercase()
                        pClassId.contains("sidebar") || pClassId.contains("widget") || pClassId.contains("comment") || pClassId.contains("ranking") || pClassId.contains("latest") || pClassId.contains("recent") || pClassId.contains("popular") || pClassId.contains("recommend")
                    }
                    !parentSidebar
                }
                .distinctBy { resolveUrl(baseUri, it.attr("href")) }

            if (fallbackAnchors.isNotEmpty()) {
                val dummyElement = cleanDoc.body()
                bestContainer = ScoredContainer(dummyElement, fallbackAnchors, 10.0)
            }
        }

        val finalChapters = mutableListOf<CrawledChapter>()
        var chapterIndex = 1

        bestContainer?.chapterLinks?.forEach { link ->
            val href = link.attr("href")
            val text = link.text().trim()
            val resolvedUrl = resolveUrl(baseUri, href)
            finalChapters.add(CrawledChapter(chapterIndex++, text.ifBlank { "Chapter ${chapterIndex - 1}" }, resolvedUrl))
        }

        // 2. CHAPTER REVERSAL FIX (Detect if original list was in descending order and reverse if needed, preserving relative positioning of prologue/custom chapters)
        val numbersWithIndices = finalChapters.mapIndexed { idx, ch ->
            Pair(idx, extractChapterNumber(ch.title))
        }.filter { it.second != null }

        var ascendingPairs = 0
        var descendingPairs = 0
        for (i in 0 until numbersWithIndices.size - 1) {
            val num1 = numbersWithIndices[i].second!!
            val num2 = numbersWithIndices[i + 1].second!!
            if (num1 < num2) {
                ascendingPairs++
            } else if (num1 > num2) {
                descendingPairs++
            }
        }

        val sortedList = if (descendingPairs > ascendingPairs) {
            finalChapters.reversed()
        } else {
            finalChapters
        }

        val sortedChapters = sortedList.mapIndexed { idx, ch ->
            ch.copy(index = idx + 1)
        }

        return sortedChapters
    }

    private fun extractNumber(s: String): Float {
        val match = Regex("\\d+(\\.\\d+)?").find(s)
        return match?.value?.toFloatOrNull() ?: 0f
    }

    private fun resolveUrl(baseUri: URI, href: String): String {
        return try {
            baseUri.resolve(href).toString()
        } catch (e: Exception) {
            href
        }
    }

    private fun getCacheFile(context: Context, url: String): File {
        val cacheDir = File(context.filesDir, "chapters_cache")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        val safeName = url.replace(Regex("[^a-zA-Z0-9]"), "_") + ".txt"
        return File(cacheDir, safeName)
    }

    fun isChapterCached(context: Context, url: String): Boolean {
        val file = getCacheFile(context, url)
        return file.exists() && file.length() > 0
    }

    fun cacheChapterText(context: Context, url: String, text: String) {
        try {
            val file = getCacheFile(context, url)
            file.writeText(text)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getCachedChapterText(context: Context, url: String): String? {
        return try {
            val file = getCacheFile(context, url)
            if (file.exists() && file.length() > 0) file.readText() else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun extractChapterText(context: Context, url: String): String = withContext(Dispatchers.IO) {
        val cached = getCachedChapterText(context, url)
        if (cached != null) {
            return@withContext cached
        }
        if (url.startsWith("epub://") || url.startsWith("local://")) {
            return@withContext "Chapter text not found or cleared from cache."
        }
        var currentUrl = url
        val doc = try {
            Jsoup.connect(currentUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(15000)
                .get()
        } catch (e: Exception) {
            if (currentUrl.startsWith("https://")) {
                currentUrl = currentUrl.replaceFirst("https://", "http://")
                Jsoup.connect(currentUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(15000)
                    .get()
            } else {
                throw e
            }
        }

        // Remove junk elements early
        doc.select("script, style, iframe, nav, footer, header, .comments, .ads, ins, .ad-box, .sidebar").remove()

        // Identify best content node
        val candidates = doc.select("div, article, main, section")
        var bestNode: Element = doc.body()
        var maxScore = -1

        candidates.forEach { element ->
             val classId = (element.className() + " " + element.id()).lowercase()
             if (classId.contains("comment") || classId.contains("sidebar") || classId.contains("footer") || classId.contains("nav")) {
                 return@forEach
             }

             val textLength = element.ownText().length + element.select("p").joinToString { it.text() }.length
             var score = textLength
             
             val linkCount = element.select("a").size
             if (linkCount > 0) {
                 score /= (linkCount * 2)
             }

             if (classId.contains("chapter-content") ||
                 classId.contains("entry-content") ||
                 classId.contains("reading-content") ||
                 classId.contains("text-left") ||
                 classId.contains("cha-words")) {
                 score += 10000 
             }

             if (score > maxScore) {
                 maxScore = score
                 bestNode = element
             }
        }

        // Clean up the node again for safety
        bestNode.select("script, style, iframe, nav, footer, .comments, .ads, ins, .ad-box, [class*=\"prev\"], [class*=\"next\"], [id*=\"prev\"], [id*=\"next\"], .chapter-nav, .nav-links, .btn-group, .entry-navigation, .navigation, .chapter-navigation, .page-navigation").remove()
        
        // Preserve block structure using a specific NEWLINE token
        bestNode.apply {
            doc.outputSettings(Document.OutputSettings().prettyPrint(false))
            select("p").prepend("###NEWLINE###")
            select("br").append("###NEWLINE###")
            select("div").prepend("###NEWLINE###")
        }
        
        val rawText = bestNode.text()
        val content = rawText.replace("###NEWLINE###", "\n")
            .replace(Regex(" +"), " ") // remove extra spaces
            .replace(Regex("\n[ \t]+"), "\n") // remove spaces after newlines
            .replace(Regex("\n{3,}"), "\n\n") // compress multiple newlines
            .trim()
            
        val lines = content.split("\n").map { it.trim() }.filter { line ->
            if (line.isEmpty()) return@filter true
            val l = line.lowercase()
            val isNav = (l.contains("next chapter") || l.contains("previous chapter") || l.contains("prev chapter") || l.contains("chapter list") || l.contains("table of contents") || l == "next" || l == "prev" || l == "previous" || l == "index") && line.length < 50
            !isNav
        }
        val filteredContent = lines.joinToString("\n").trim()
            
        val result = filteredContent.ifBlank { "No content extracted." }
        if (result != "No content extracted." && result != "Failed to load chapter content.") {
            cacheChapterText(context, url, result)
        }
        return@withContext result
    }
}

class ElementClassifier(private val doc: Document) {

    data class ClassificationResult(
        val element: Element,
        val text: String,
        val score: Double
    )

    /**
     * Identifies the description/synopsis of the novel.
     * Uses text density patterns, tag ratio, and class/id analysis with confidence scoring.
     */
    fun classifyDescription(): ClassificationResult? {
        val elements = doc.select("div, p, section, article")
        val results = mutableListOf<ClassificationResult>()

        for (el in elements) {
            val classId = (el.className() + " " + el.id()).lowercase()
            
            // Clean out noise containers that shouldn't contain the description
            if (classId.contains("comment") || 
                classId.contains("sidebar") || 
                classId.contains("footer") || 
                classId.contains("nav") || 
                classId.contains("chapter") ||
                classId.contains("header") ||
                classId.contains("menu") ||
                classId.contains("search") ||
                classId.contains("recommend") ||
                classId.contains("relation") ||
                classId.contains("popular")
            ) {
                continue
            }

            val totalText = el.text().trim()
            if (totalText.length < 40 || totalText.length > 4000) continue
            
            // Analyze Text Density: text length as a ratio of total HTML length
            val htmlLength = el.html().length.coerceAtLeast(1)
            val textDensity = totalText.length.toDouble() / htmlLength.toDouble() // range (0.0 to 1.0)

            // Analyze Text-to-Element ratio (average text length per child node)
            val childCount = el.children().size.coerceAtLeast(1)
            val textToElementRatio = totalText.length.toDouble() / childCount.toDouble()

            var confidenceScore = 0.0

            // 1. CSS Selector / Class Name Analysis
            if (classId.contains("description") || classId.contains("synopsis") || classId.contains("summary") || classId.contains("novel-synopsis")) {
                confidenceScore += 100.0
            }
            if (classId.contains("desc") || classId.contains("about") || classId.contains("introduction") || classId.contains("intro")) {
                confidenceScore += 60.0
            }
            if (classId.contains("info-text") || classId.contains("manga-info-text") || classId.contains("story-text")) {
                confidenceScore += 50.0
            }
            if (classId.contains("info") || classId.contains("meta") || classId.contains("content-inner")) {
                confidenceScore += 20.0
            }

            // 2. ARIA labels & Schema Microdata Analysis
            val itemprop = el.attr("itemprop").lowercase()
            if (itemprop == "description" || itemprop == "about" || itemprop == "abstract") {
                confidenceScore += 120.0
            }
            val ariaLabel = (el.attr("aria-label") + " " + el.attr("aria-describedby")).lowercase()
            if (ariaLabel.contains("description") || ariaLabel.contains("synopsis") || ariaLabel.contains("summary")) {
                confidenceScore += 80.0
            }

            // 3. Text Density & Tag Composition Heuristics
            // Descriptions are high density text blocks. Usually high textDensity (little HTML markup relative to text)
            if (textDensity > 0.4) {
                confidenceScore += 40.0
            } else if (textDensity > 0.2) {
                confidenceScore += 20.0
            }

            // Check paragraph density: real descriptions consist mostly of paragraphs or plain text
            val pTags = el.select("p")
            if (pTags.isNotEmpty()) {
                confidenceScore += 30.0
                // High paragraph average length is very characteristic of synopsis/descriptions
                val avgPLength = pTags.map { it.text().trim().length }.average()
                if (avgPLength > 40.0) {
                    confidenceScore += 40.0
                }
            } else {
                // If it's a single long element (e.g. single div or p)
                if (el.tagName() == "p" || el.tagName() == "div") {
                    confidenceScore += 20.0
                }
            }

            // 4. Noise / Link Density Penalty (CRITICAL to avoid picking up metadata grids/menus)
            val anchorCount = el.select("a").size
            if (anchorCount > 0) {
                // Descriptions have very few links. High link count indicates list, menu, tags or metadata blocks.
                confidenceScore -= (anchorCount * 15.0)
            }
            val listCount = el.select("li, ul, ol").size
            if (listCount > 0) {
                confidenceScore -= (listCount * 10.0)
            }
            val formControlsCount = el.select("input, button, select, textarea, form").size
            if (formControlsCount > 0) {
                confidenceScore -= (formControlsCount * 30.0)
            }

            // 5. Structure validation (e.g. does it contain other high-confidence headers or titles?)
            val internalHeaders = el.select("h1, h2, h3")
            if (internalHeaders.isNotEmpty()) {
                confidenceScore -= (internalHeaders.size * 25.0)
            }

            if (confidenceScore > 0) {
                val clone = el.clone()
                // Strip inline noise elements from the final description content
                clone.select("script, style, iframe, nav, footer, .comments, .ads, ins, .ad-box, h1, h2, h3, a, button, input").remove()
                val cleanedText = clone.text().trim()
                
                // Exclude if description is polluted with table of contents/chapter links
                if (cleanedText.length > 40 && 
                    !cleanedText.contains("Chapter 1", ignoreCase = true) && 
                    !cleanedText.contains("Next Chapter", ignoreCase = true) && 
                    !cleanedText.contains("All Chapters", ignoreCase = true)
                ) {
                    results.add(ClassificationResult(el, cleanedText, confidenceScore))
                }
            }
        }

        return results.maxByOrNull { it.score }
    }

    /**
     * Identifies the author of the novel using semantic markers and text analysis.
     */
    fun classifyAuthor(): ClassificationResult? {
        val elements = doc.select("span, a, p, div, td, li")
        val results = mutableListOf<ClassificationResult>()

        for (el in elements) {
            val classId = (el.className() + " " + el.id()).lowercase()
            val text = el.text().trim()

            // Authors are normally short names. Filter outliers.
            if (text.isBlank() || text.length < 2 || text.length > 60) continue
            // Exclude noise phrases
            if (text.contains("Chapter", ignoreCase = true) || text.contains("Page", ignoreCase = true)) continue

            var confidenceScore = 0.0

            // 1. Selector name checks
            if (classId.contains("author") || classId.contains("writer") || classId.contains("creator") || classId.contains("novel-author")) {
                confidenceScore += 100.0
            }
            if (classId.contains("property-value") || classId.contains("meta-value") || classId.contains("info-value")) {
                confidenceScore += 20.0
            } else if (classId.contains("meta") || classId.contains("info") || classId.contains("property")) {
                confidenceScore += 10.0
            }

            // 2. Microdata itemprop checks
            val itemprop = el.attr("itemprop").lowercase()
            if (itemprop == "author" || itemprop == "creator") {
                confidenceScore += 120.0
            }

            // 3. ARIA roles/labels
            val ariaLabel = el.attr("aria-label").lowercase()
            if (ariaLabel.contains("author") || ariaLabel.contains("writer")) {
                confidenceScore += 80.0
            }

            // 4. Label Prefix Checks (e.g. "Author: Shakespeare")
            val parentText = el.parent()?.text()?.trim() ?: ""
            if (text.startsWith("Author:", ignoreCase = true) || text.contains("author:", ignoreCase = true)) {
                confidenceScore += 60.0
            } else if (parentText.startsWith("Author:", ignoreCase = true) || parentText.contains("author:", ignoreCase = true)) {
                confidenceScore += 40.0
            }
            if (text.startsWith("By ", ignoreCase = true)) {
                confidenceScore += 40.0
            }

            // 5. Node simplicity penalty (Authors should be simple leaf nodes, not massive nested hierarchies)
            val childCount = el.children().size
            if (childCount == 0) {
                confidenceScore += 25.0
            } else if (childCount == 1) {
                confidenceScore += 10.0
            } else {
                confidenceScore -= (childCount * 15.0)
            }

            if (confidenceScore > 20.0) {
                val cleanedText = text
                    .replace(Regex("(?i)^(by|author|writer|creator):?\\s*"), "")
                    .replace(Regex("(?i)\\s*(author|writer|creator):?"), "")
                    .trim()
                
                if (cleanedText.isNotBlank() && cleanedText.length < 40) {
                    results.add(ClassificationResult(el, cleanedText, confidenceScore))
                }
            }
        }

        return results.maxByOrNull { it.score }
    }

    /**
     * Extracts a list of confidence-scored genre classification tokens, de-duplicating standard web tags.
     */
    fun classifyGenres(): List<String> {
        val elements = doc.select("a, span, li")
        val candidateGenres = mutableListOf<Pair<String, Double>>()

        val commonGenres = setOf(
            "action", "adventure", "comedy", "drama", "fantasy", "harem", "historical", "horror",
            "isekai", "josei", "martial arts", "mature", "mecha", "mystery", "psychological", "romance",
            "school life", "sci-fi", "seinen", "shoujo", "shoujo ai", "shounen", "shounen ai", "slice of life",
            "sports", "supernatural", "tragedy", "wuxia", "xianxia", "xuanhuan", "cultivation"
        )

        for (el in elements) {
            val classId = (el.className() + " " + el.id() + " " + el.parent()?.className()).lowercase()
            val rel = el.attr("rel").lowercase()
            val href = el.attr("href").lowercase()
            val text = el.text().trim()

            // Genres are short and clean tokens
            if (text.isBlank() || text.length < 2 || text.length > 25 || text.contains(":") || text.contains("/")) continue
            // Ignore generic site UI words
            val lowerText = text.lowercase()
            if (lowerText in setOf("home", "next", "prev", "chapter", "read", "novel", "search", "menu", "login", "register", "comment")) continue

            var confidenceScore = 0.0

            // 1. Selector Clues
            if (classId.contains("genre") || classId.contains("category") || classId.contains("subject") || classId.contains("novel-tag")) {
                confidenceScore += 80.0
            } else if (classId.contains("tag") || classId.contains("label")) {
                confidenceScore += 40.0
            }

            // 2. Intent links in anchor tags (e.g. href="/genre/fantasy")
            if (href.contains("genre") || href.contains("category") || href.contains("tag")) {
                confidenceScore += 60.0
            }
            if (rel == "tag" || rel == "category") {
                confidenceScore += 50.0
            }

            // 3. ARIA attributes
            val ariaLabel = el.attr("aria-label").lowercase()
            if (ariaLabel.contains("genre") || ariaLabel.contains("category") || ariaLabel.contains("tag")) {
                confidenceScore += 70.0
            }

            // 4. Dictionary validation
            if (commonGenres.contains(lowerText)) {
                confidenceScore += 50.0
            }

            if (confidenceScore > 35.0) {
                val cleanedGenre = text.replace(Regex("^#"), "").trim()
                if (cleanedGenre.isNotBlank()) {
                    candidateGenres.add(Pair(cleanedGenre, confidenceScore))
                }
            }
        }

        // Aggregate and fetch highest confidence scores grouped by normalized genre labels
        return candidateGenres
            .groupBy { it.first.lowercase() }
            .map { entry -> entry.value.maxBy { it.second }.first }
            .distinct()
    }
}
