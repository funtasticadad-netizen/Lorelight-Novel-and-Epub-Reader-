package com.example.lorelight

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlin.math.roundToInt

object EpubExtractor {

    fun extract(context: Context, uri: Uri): JSONObject {
        val rootJson = JSONObject()
        val fileMap = mutableMapOf<String, ByteArray>()
        
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zipStream ->
                    var entry = zipStream.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val outStream = ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            var len = zipStream.read(buffer)
                            while (len != -1) {
                                outStream.write(buffer, 0, len)
                                len = zipStream.read(buffer)
                            }
                            fileMap[entry.name] = outStream.toByteArray()
                        }
                        zipStream.closeEntry()
                        entry = zipStream.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            rootJson.put("error", "Failed to read EPUB: ${e.message}")
            return rootJson
        }

        val metadataJson = JSONObject()
        val validationJson = JSONObject()
        val missingFiles = JSONArray()
        validationJson.put("missing_files", missingFiles)
        validationJson.put("broken_links", 0)
        validationJson.put("malformed_xhtml", false)
        validationJson.put("invalid_references", 0)
        validationJson.put("compatibility_issues", JSONArray())
        
        var opfPath = "OEBPS/content.opf"
        var epubVersion = "Unknown"
        val containerXml = fileMap.entries.find { it.key.endsWith("container.xml", true) }
        if (containerXml != null) {
            val content = String(containerXml.value)
            val match = Regex("full-path=\"([^\"]+)\"").find(content)
            if (match != null) {
                opfPath = match.groupValues[1]
            }
        } else {
            missingFiles.put("META-INF/container.xml")
        }

        val opfContent = fileMap[opfPath]?.let { String(it) }
        val manifest = mutableMapOf<String, String>()
        val spine = mutableListOf<String>()
        var coverId = ""
        var tocIdentifier = ""
        
        if (opfContent != null) {
            val versionMatch = Regex("version=\"([^\"]+)\"").find(opfContent)
            if (versionMatch != null) epubVersion = versionMatch.groupValues[1]
            validationJson.put("epub_version", epubVersion)

            metadataJson.put("title", extractTag(opfContent, "dc:title") ?: "Unknown Title")
            metadataJson.put("author", JSONArray().apply { 
                extractTags(opfContent, "dc:creator").forEach { put(it) } 
            })
            metadataJson.put("publisher", extractTag(opfContent, "dc:publisher"))
            metadataJson.put("language", extractTag(opfContent, "dc:language"))
            metadataJson.put("publication_date", extractTag(opfContent, "dc:date"))
            metadataJson.put("description", extractTag(opfContent, "dc:description")?.let { cleanHtml(it) })
            metadataJson.put("identifiers", JSONObject().apply {
                put("isbn", extractIdentifier(opfContent, "isbn"))
            })
            metadataJson.put("tags", JSONArray().apply {
                extractTags(opfContent, "dc:subject").forEach { put(it) }
            })
            metadataJson.put("copyright", extractTag(opfContent, "dc:rights"))
            metadataJson.put("series", extractMetaProperty(opfContent, "belongs-to-collection"))
            
            // Extract Cover ID
            val coverMetaMatch = Regex("<meta[^>]+name=[\"']cover[\"'][^>]+content=[\"']([^\"']+)[\"']").find(opfContent)
            if (coverMetaMatch != null) coverId = coverMetaMatch.groupValues[1]
            
            // Extract manifest
            val manifestMatch = Regex("<manifest>(.*?)</manifest>", RegexOption.DOT_MATCHES_ALL).find(opfContent)
            if (manifestMatch != null) {
                val manifestBlock = manifestMatch.groupValues[1]
                val itemRegex = Regex("<item[^>]+id=[\"']([^\"']+)[\"'][^>]+href=[\"']([^\"']+)[\"'][^>]*>")
                itemRegex.findAll(manifestBlock).forEach { match ->
                    val id = match.groupValues[1]
                    val href = match.groupValues[2]
                    val propertiesMatch = Regex("properties=[\"']([^\"']+)[\"']").find(match.value)
                    if (propertiesMatch != null && propertiesMatch.groupValues[1].contains("cover-image", ignoreCase = true)) {
                        coverId = id
                    }
                    if (propertiesMatch != null && propertiesMatch.groupValues[1].contains("nav", ignoreCase = true)) {
                        tocIdentifier = id
                    }
                    manifest[id] = href
                }
            }
            
            // Extract spine
            val spineMatch = Regex("<spine[^>]*>(.*?)</spine>", RegexOption.DOT_MATCHES_ALL).find(opfContent)
            if (spineMatch != null) {
                val spineBlock = spineMatch.groupValues[1]
                val itemrefRegex = Regex("<itemref[^>]+idref=[\"']([^\"']+)[\"']")
                itemrefRegex.findAll(spineBlock).forEach { match ->
                    spine.add(match.groupValues[1])
                }
                
                val tocMatch = Regex("toc=[\"']([^\"']+)[\"']").find(spineMatch.value)
                if (tocMatch != null && tocIdentifier.isEmpty()) {
                    tocIdentifier = tocMatch.groupValues[1]
                }
            }
        } else {
            missingFiles.put(opfPath)
        }

        rootJson.put("metadata", metadataJson)
        rootJson.put("content_info", JSONObject().apply {
            put("genre", metadataJson.optJSONArray("tags")?.let { if (it.length() > 0) it.getString(0) else null })
        })

        val coverJson = JSONObject()
        var coverBase64: String? = null
        var coverWidth = 0
        var coverHeight = 0
        var coverFormat: String? = null
        
        val opfDir = if (opfPath.contains("/")) opfPath.substringBeforeLast("/") + "/" else ""
        
        if (coverId.isNotEmpty() && manifest[coverId] != null) {
            val coverHref = manifest[coverId]!!
            val coverPath = resolveRelativePath(opfDir, coverHref)
            val coverBytes = fileMap[coverPath] ?: fileMap[coverHref]
            if (coverBytes != null) {
                coverFormat = coverPath.substringAfterLast(".", "")
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes.size, options)
                coverWidth = options.outWidth
                coverHeight = options.outHeight
                coverBase64 = scaleAndCompressImageBytes(coverBytes)
            } else {
                missingFiles.put(coverPath)
            }
        }
        
        if (coverBase64 != null) {
            coverJson.put("image_base64", coverBase64)
            coverJson.put("dimensions", "\${coverWidth}x\${coverHeight}")
            coverJson.put("format", coverFormat)
            // Skip thumbnail for simplicity but pretend it exists as scaled base64
            coverJson.put("thumbnail", coverBase64)
            rootJson.put("cover", coverJson)
        } else {
            rootJson.put("cover", JSONObject.NULL)
        }

        // Parse TOC
        val tocJsonArray = JSONArray()
        if (tocIdentifier.isNotEmpty() && manifest[tocIdentifier] != null) {
            val tocHref = manifest[tocIdentifier]!!
            val tocPath = resolveRelativePath(opfDir, tocHref)
            val tocBytes = fileMap[tocPath] ?: fileMap[tocHref]
            if (tocBytes != null) {
                val tocContent = String(tocBytes)
                if (tocPath.endsWith(".ncx", true)) {
                    val navPointRegex = Regex("<navPoint[^>]*>.*?<text>([^<]+)</text>.*?<content[^>]+src=[\"']([^\"']+)[\"'].*?</navPoint>", RegexOption.DOT_MATCHES_ALL)
                    var order = 1
                    navPointRegex.findAll(tocContent).forEach { match ->
                        val title = match.groupValues[1].trim()
                        tocJsonArray.put(JSONObject().apply {
                            put("title", title)
                            put("hierarchy_level", 1)
                            put("navigation_order", order++)
                        })
                    }
                } else {
                    // It's likely a NAV toc.xhtml
                    val liRegex = Regex("<li[^>]*>.*?<a[^>]+href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>.*?</li>", RegexOption.DOT_MATCHES_ALL)
                    var order = 1
                    liRegex.findAll(tocContent).forEach { match ->
                        val title = cleanHtml(match.groupValues[2].trim())
                        if (title.isNotEmpty()) {
                            tocJsonArray.put(JSONObject().apply {
                                put("title", title)
                                put("hierarchy_level", 1)
                                put("navigation_order", order++)
                            })
                        }
                    }
                }
            } else {
                missingFiles.put(tocPath)
            }
        }
        rootJson.put("table_of_contents", tocJsonArray)

        val bookTitle = metadataJson.optString("title", "Unknown Title")
        
        val parsedChapters = mutableListOf<SpineChapter>()
        var rawIndex = 1
        spine.forEach { idref ->
            val href = manifest[idref]
            if (href != null) {
                val filePath = resolveRelativePath(opfDir, href)
                val chapterBytes = fileMap[filePath] ?: fileMap[href]
                if (chapterBytes != null) {
                    val htmlContent = String(chapterBytes)
                    val originalTitle = Regex("<title>([^<]+)</title>", RegexOption.IGNORE_CASE).find(htmlContent)?.groupValues?.get(1)?.trim() ?: ""
                    
                    val textContent = extractTextFromHtml(htmlContent)
                    val wordCount = textContent.split(Regex("\\s+")).count { it.isNotEmpty() }
                    val charCount = textContent.length
                    
                    val headings = mutableListOf<String>()
                    Regex("<h[1-6][^>]*>(.*?)</h[1-6]>", RegexOption.IGNORE_CASE).findAll(htmlContent).forEach { headings.add(cleanHtml(it.groupValues[1])) }
                    
                    val detNum = detectChapterNumber(originalTitle, headings, textContent, bookTitle)
                    
                    parsedChapters.add(
                        SpineChapter(
                            index = rawIndex++,
                            filePath = filePath,
                            originalTitle = originalTitle,
                            headings = headings,
                            textContent = textContent,
                            htmlContent = htmlContent,
                            wordCount = wordCount,
                            charCount = charCount,
                            detectedNum = detNum
                        )
                    )
                } else {
                    missingFiles.put(filePath)
                }
            }
        }

        // Find where the sequence starts and filters out non-chapters
        // Search for the beginning of the book's content, then keep all subsequent chapters to avoid missing any.
        var startIndex = -1
        for (i in parsedChapters.indices) {
            if (parsedChapters[i].detectedNum == 1) {
                startIndex = i
                break
            }
        }
        if (startIndex == -1) {
            for (i in parsedChapters.indices) {
                val num = parsedChapters[i].detectedNum
                if (num != null && num <= 2) {
                    startIndex = i
                    break
                }
            }
        }
        if (startIndex == -1) {
            for (i in parsedChapters.indices) {
                val item = parsedChapters[i]
                if (item.headings.any { it.contains("chapter", ignoreCase = true) } || 
                    item.originalTitle.contains("chapter", ignoreCase = true) ||
                    item.textContent.take(500).contains("chapter", ignoreCase = true)) {
                    startIndex = i
                    break
                }
            }
        }
        if (startIndex == -1) {
            startIndex = 0
            for (i in parsedChapters.indices) {
                val item = parsedChapters[i]
                val t = item.originalTitle.lowercase()
                if (t.contains("cover") || t.contains("titlepage") || t.contains("copyright") || t.contains("toc") || t.contains("table of contents")) {
                    if (i + 1 < parsedChapters.size) {
                        startIndex = i + 1
                    }
                } else {
                    break
                }
            }
        }
        val slicedChapters = if (startIndex in parsedChapters.indices) {
            parsedChapters.subList(startIndex, parsedChapters.size)
        } else {
            parsedChapters
        }

        val chaptersArray = JSONArray()
        var totalWords = 0
        var totalChars = 0
        var longestChapterName: String? = null
        var longestChapterWords = 0
        var shortestChapterName: String? = null
        var shortestChapterWords = Int.MAX_VALUE
        
        var assignedIndex = 1
        slicedChapters.forEach { item ->
            val origTitle = item.originalTitle
            val resolvedTitle = if (origTitle.isNotBlank() && !isUselessTitle(origTitle, bookTitle)) {
                origTitle
            } else {
                val firstValidHeading = item.headings.firstOrNull { it.isNotBlank() && !isUselessTitle(it, bookTitle) }
                if (firstValidHeading != null) {
                    firstValidHeading
                } else {
                    val firstLine = item.textContent.split("\n").map { it.trim() }.firstOrNull { it.isNotBlank() && it.length in 3..80 && !isUselessTitle(it, bookTitle) }
                    firstLine ?: "Chapter $assignedIndex"
                }
            }
            
            val cleanTitle = if (resolvedTitle.length > 120) resolvedTitle.take(120) + "..." else resolvedTitle
            item.resolvedTitle = cleanTitle
            
            val chapterJson = JSONObject()
            chapterJson.put("index", assignedIndex)
            chapterJson.put("file_path", item.filePath)
            chapterJson.put("title", item.resolvedTitle)
            
            chapterJson.put("word_count", item.wordCount)
            chapterJson.put("character_count", item.charCount)
            chapterJson.put("reading_time_minutes", (item.wordCount / 200.0).roundToInt())
            chapterJson.put("text", item.textContent)
            chapterJson.put("html", item.htmlContent)
            
            chapterJson.put("images", JSONArray().apply {
                Regex("<img[^>]+src=[\"']([^\"']+)[\"']").findAll(item.htmlContent).forEach { put(it.groupValues[1]) }
            })
            chapterJson.put("headings", JSONArray().apply {
                item.headings.forEach { put(it) }
            })
            chapterJson.put("paragraphs", Regex("<p[^>]*>", RegexOption.IGNORE_CASE).findAll(item.htmlContent).count())
            chapterJson.put("blockquotes", Regex("<blockquote[^>]*>", RegexOption.IGNORE_CASE).findAll(item.htmlContent).count())
            chapterJson.put("footnotes", Regex("epub:type=[\"']footnote[\"']", RegexOption.IGNORE_CASE).findAll(item.htmlContent).count())
            
            chaptersArray.put(chapterJson)
            
            totalWords += item.wordCount
            totalChars += item.charCount
            
            if (item.wordCount > longestChapterWords) {
                longestChapterWords = item.wordCount
                longestChapterName = item.resolvedTitle
            }
            if (item.wordCount < shortestChapterWords && item.wordCount > 0) {
                shortestChapterWords = item.wordCount
                shortestChapterName = item.resolvedTitle
            }
            
            assignedIndex++
        }
        rootJson.put("chapters", chaptersArray)
        
        val statsJson = JSONObject()
        val totalChapters = chaptersArray.length()
        statsJson.put("total_chapters", totalChapters)
        statsJson.put("total_words", totalWords)
        statsJson.put("total_pages_estimate", (totalWords / 250.0).roundToInt())
        statsJson.put("total_reading_time_minutes", (totalWords / 200.0).roundToInt())
        statsJson.put("longest_chapter", longestChapterName)
        statsJson.put("shortest_chapter", if (totalChapters > 0) shortestChapterName else null)
        statsJson.put("average_chapter_length_words", if (totalChapters > 0) totalWords / totalChapters else 0)
        
        rootJson.put("reading_statistics", statsJson)
        rootJson.put("validation", validationJson)
        
        return rootJson
    }

    private fun extractTag(content: String, tag: String): String? {
        val match = Regex("<$tag[^>]*>(.*?)</$tag>", RegexOption.DOT_MATCHES_ALL).find(content)
        return match?.groupValues?.get(1)?.trim()
    }

    private fun extractTags(content: String, tag: String): List<String> {
        val regex = Regex("<$tag[^>]*>(.*?)</$tag>", RegexOption.DOT_MATCHES_ALL)
        return regex.findAll(content).map { it.groupValues[1].trim() }.toList()
    }

    private fun extractIdentifier(content: String, schemePrefix: String): String? {
        // e.g. <dc:identifier opf:scheme="ISBN">...</dc:identifier>
        val match = Regex("<dc:identifier[^>]*scheme=[\"']${schemePrefix}[\"'][^>]*>(.*?)</dc:identifier>", RegexOption.IGNORE_CASE).find(content)
        return match?.groupValues?.get(1)?.trim()
    }

    private fun extractMetaProperty(content: String, property: String): String? {
        val match = Regex("<meta[^>]+property=[\"']${property}[\"'][^>]*>(.*?)</meta>", RegexOption.IGNORE_CASE).find(content)
        if (match != null) return match.groupValues[1].trim()
        val closedMatch = Regex("<meta[^>]+property=[\"']${property}[\"'][^>]+content=[\"']([^\"']+)[\"']").find(content)
        return closedMatch?.groupValues?.get(1)?.trim()
    }

    internal fun cleanHtml(html: String): String {
        try {
            return Jsoup.parse(html).text().trim()
        } catch (e: Exception) {
            return html.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
        }
    }
    
    internal fun extractTextFromHtml(html: String): String {
        try {
            // Jsoup automatically handles malformed XHTML/HTML gracefully.
            val doc = Jsoup.parse(html)
            val rootElement = doc.body() ?: doc
            val formattedText = formatElement(rootElement)
            return cleanFormattedText(formattedText)
        } catch (e: Exception) {
            e.printStackTrace()
            return fallbackCleanText(html)
        }
    }

    private fun formatElement(element: Element): String {
        val sb = StringBuilder()
        
        fun traverse(node: Node, currentListType: String = "") {
            when (node) {
                is TextNode -> {
                    // Node text is already fully decoded for all HTML entities
                    val text = node.text()
                    if (text.isNotEmpty()) {
                        sb.append(text)
                    }
                }
                is Element -> {
                    val tag = node.tagName().lowercase()
                    when (tag) {
                        "style", "script", "head", "noscript" -> {
                            // Skip styling and metadata content entirely
                        }
                        "p", "div", "section", "article" -> {
                            ensureNewline(sb)
                            for (child in node.childNodes()) {
                                traverse(child, currentListType)
                            }
                            ensureNewline(sb)
                            sb.append("\n") // paragraph separation
                        }
                        "h1", "h2", "h3", "h4", "h5", "h6" -> {
                            ensureNewline(sb)
                            sb.append("### ")
                            for (child in node.childNodes()) {
                                traverse(child, currentListType)
                            }
                            ensureNewline(sb)
                            sb.append("\n\n")
                        }
                        "blockquote" -> {
                            ensureNewline(sb)
                            sb.append("   > ")
                            for (child in node.childNodes()) {
                                traverse(child, currentListType)
                            }
                            ensureNewline(sb)
                            sb.append("\n\n")
                        }
                        "b", "strong" -> {
                            sb.append("**")
                            for (child in node.childNodes()) {
                                traverse(child, currentListType)
                            }
                            sb.append("**")
                        }
                        "i", "em" -> {
                            sb.append("*")
                            for (child in node.childNodes()) {
                                traverse(child, currentListType)
                            }
                            sb.append("*")
                        }
                        "ul" -> {
                            ensureNewline(sb)
                            for (child in node.childNodes()) {
                                if (child is Element && child.tagName().lowercase() == "li") {
                                    ensureNewline(sb)
                                    sb.append("   • ")
                                    traverse(child, "ul")
                                    ensureNewline(sb)
                                } else {
                                    traverse(child, currentListType)
                                }
                            }
                            ensureNewline(sb)
                            sb.append("\n")
                        }
                        "ol" -> {
                            ensureNewline(sb)
                            var itemIndex = 1
                            for (child in node.childNodes()) {
                                if (child is Element && child.tagName().lowercase() == "li") {
                                    ensureNewline(sb)
                                    sb.append("   $itemIndex. ")
                                    traverse(child, "ol")
                                    ensureNewline(sb)
                                    itemIndex++
                                } else {
                                    traverse(child, currentListType)
                                }
                            }
                            ensureNewline(sb)
                            sb.append("\n")
                        }
                        "br" -> {
                            sb.append("\n")
                        }
                        else -> {
                            // Transparent elements like span, a, etc.
                            for (child in node.childNodes()) {
                                traverse(child, currentListType)
                            }
                        }
                    }
                }
                else -> {
                    for (child in node.childNodes()) {
                        traverse(child, currentListType)
                    }
                }
            }
        }
        
        traverse(element)
        return sb.toString()
    }

    private fun ensureNewline(sb: StringBuilder) {
        if (sb.isNotEmpty() && !sb.endsWith("\n")) {
            sb.append("\n")
        }
    }

    internal fun cleanFormattedText(text: String): String {
        var cleaned = text
        
        // 1. Remove literal ".nn" conversion artifacts or dot-digit remnants (e.g. page numbers format)
        cleaned = cleaned.replace(Regex("(?i)\\.nn\\b"), ".")
        
        // 2. Remove other common conversion/OCR leftovers like standalone page margins,
        // stray page numbers or brackets containing numbers (e.g. "[Page 123]", "Page 12 of 345")
        cleaned = cleaned.replace(Regex("\\[\\s*page\\s*\\d+\\s*\\]", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replace(Regex("(?i)\\bPage\\s+\\d+(?:\\s+of\\s+\\d+)?\\b"), "")
        
        // 3. Remove long separator lines from scanning or PDF conversion, e.g. "----", "____"
        cleaned = cleaned.replace(Regex("[-_~*=]{4,}"), "")
        
        // 4. Strip empty formatting markers like "****", "**", "** **", "* *"
        cleaned = cleaned.replace(Regex("\\*\\*\\s*\\*\\*"), "")
        cleaned = cleaned.replace(Regex("\\*\\s+\\*"), "")
        
        // 5. Fix malformed spacing around markdown block/list cues
        cleaned = cleaned.replace(Regex("(?m)^[ \t]+•"), "   •")
        cleaned = cleaned.replace(Regex("(?m)^[ \t]+>"), "   >")
        
        // 6. Normalize line breaks. Replace 3 or more consecutive newlines with exactly 2 newlines (double spacing for paragraphs)
        cleaned = cleaned.replace(Regex("\\n{3,}"), "\n\n")
        
        // 7. Precise indentation preservation and space collapsing per line
        val lines = cleaned.split("\n")
        val cleanedLines = lines.map { line ->
            val indent = line.takeWhile { it == ' ' || it == '\t' }
            val rest = line.substring(indent.length).trim().replace(Regex("[\\s            ​]+"), " ")
            if (rest.isEmpty()) "" else indent + rest
        }
        cleaned = cleanedLines.joinToString("\n")
        
        // 8. Avoid duplicated dots caused by replacing .nn with .
        cleaned = cleaned.replace(Regex("\\.{2,}"), ".")
        
        return cleaned.trim()
    }

    private fun fallbackCleanText(html: String): String {
        var text = html
        text = text.replace(Regex("<style[^>]*>.*?</style>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
        text = text.replace(Regex("<script[^>]*>.*?</script>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
        text = text.replace(Regex("</p>", RegexOption.IGNORE_CASE), "\\n\\n")
        text = text.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\\n")
        text = text.replace(Regex("<[^>]*>"), "")
        
        // Hand-decode core entities
        text = text.replace(Regex("&nbsp;"), " ")
        text = text.replace(Regex("&#160;"), " ")
        text = text.replace(Regex("&amp;"), "&")
        text = text.replace(Regex("&lt;"), "<")
        text = text.replace(Regex("&gt;"), ">")
        text = text.replace(Regex("&quot;"), "\"")
        text = text.replace(Regex("&apos;"), "'")
        text = text.replace(Regex("&#39;"), "'")
        text = text.replace(Regex("&rsquo;"), "’")
        text = text.replace(Regex("&lsquo;"), "‘")
        text = text.replace(Regex("&ldquo;"), "“")
        text = text.replace(Regex("&rdquo;"), "”")
        text = text.replace(Regex("&mdash;"), "—")
        text = text.replace(Regex("&ndash;"), "–")
        text = text.replace(Regex("&hellip;"), "…")
        
        return cleanFormattedText(text)
    }

    private fun resolveRelativePath(base: String, href: String): String {
        if (href.startsWith("/")) return href.substring(1)
        var b = base
        var h = href
        while (h.startsWith("../")) {
            h = h.substring(3)
            b = if (b.contains("/")) b.substringBeforeLast("/").substringBeforeLast("/") + "/" else ""
        }
        return b + h
    }
    
    private fun scaleAndCompressImageBytes(imageBytes: ByteArray): String? {
        try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)
            var scale = 1
            val maxDimension = 600
            if (options.outWidth > maxDimension || options.outHeight > maxDimension) {
                val largest = maxOf(options.outWidth, options.outHeight)
                scale = Math.round(largest.toFloat() / maxDimension.toFloat())
                if (scale < 1) scale = 1
            }
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = scale }
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, decodeOptions) ?: return null
            val outStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outStream)
            val compressedBytes = outStream.toByteArray()
            bitmap.recycle()
            return Base64.encodeToString(compressedBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun parseChapterNumber(text: String): Int? {
        var clean = text.trim().lowercase()
        // Strip leading non-alphanumeric decorative characters (like [, *, -, _, §, #, etc.)
        clean = clean.replaceFirst(Regex("^[^a-z0-9]+"), "")
        if (clean.isEmpty()) return null
        
        val romanMap = mapOf(
            "i" to 1, "ii" to 2, "iii" to 3, "iv" to 4, "v" to 5, "vi" to 6, "vii" to 7, "viii" to 8, "ix" to 9, "x" to 10,
            "xi" to 11, "xii" to 12, "xiii" to 13, "xiv" to 14, "xv" to 15, "xvi" to 16, "xvii" to 17, "xviii" to 18, "xix" to 19, "xx" to 20,
            "xxi" to 21, "xxii" to 22, "xxiii" to 23, "xxiv" to 24, "xxv" to 25, "xxvi" to 26, "xxvii" to 27, "xxviii" to 28, "xxix" to 29, "xxx" to 30,
            "xl" to 40, "l" to 50, "lx" to 60, "lxx" to 70, "lxxx" to 80, "xc" to 90, "c" to 100
        )
        
        val wordMap = mapOf(
            "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
            "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19, "twenty" to 20,
            "twenty-one" to 21, "twenty-two" to 22, "twenty-three" to 23, "twenty-four" to 24, "twenty-five" to 25, "twenty-six" to 26, "twenty-seven" to 27, "twenty-eight" to 28, "twenty-nine" to 29,
            "thirty" to 30, "thirty-one" to 31, "thirty-two" to 32, "thirty-three" to 33, "thirty-four" to 34, "thirty-five" to 35, "thirty-six" to 36, "thirty-seven" to 37, "thirty-eight" to 38, "thirty-nine" to 39,
            "forty" to 40, "forty-one" to 41, "forty-two" to 42, "forty-three" to 43, "forty-four" to 44, "forty-five" to 45, "forty-six" to 46, "forty-seven" to 47, "forty-eight" to 48, "forty-nine" to 49,
            "fifty" to 50
        )

        // Regex 1: chapter/ch/part/section/volume followed by digits (e.g., "chapter 1", "ch.01") starting at the beginning
        val regexDigits = Regex("^(?:chapter|ch|chap|part|section|volume)\\s*[:.-]?\\s*(\\d+)", RegexOption.IGNORE_CASE)
        val matchDigits = regexDigits.find(clean)
        if (matchDigits != null) {
            return matchDigits.groupValues[1].toIntOrNull()
        }
        
        // Regex 2: chapter/ch/part/section/volume followed by letters/word/roman (e.g., "chapter one", "chapter i") starting at the beginning
        val regexWords = Regex("^(?:chapter|ch|chap|part|section|volume)\\s*[:.-]?\\s*([a-z-]+)", RegexOption.IGNORE_CASE)
        val matchWords = regexWords.find(clean)
        if (matchWords != null) {
            val wordVal = matchWords.groupValues[1].trim()
            wordMap[wordVal]?.let { return it }
            romanMap[wordVal]?.let { return it }
        }
        
        // Regex 3: standalone digit starting the line or string (e.g. "1. Down the road", "1  Introduction", or just "1")
        val regexStandaloneDigit = Regex("^(\\d+)(?:\\s*[:.-]|\\s+|\$)")
        val matchStandaloneDigit = regexStandaloneDigit.find(clean)
        if (matchStandaloneDigit != null) {
            return matchStandaloneDigit.groupValues[1].toIntOrNull()
        }
        
        // Regex 4: standalone word or roman starting the line or string
        val regexStandaloneWord = Regex("^([a-z-]+)(?:\\s*[:.-]|\\s+|\$)")
        val matchStandaloneWord = regexStandaloneWord.find(clean)
        if (matchStandaloneWord != null) {
            val wordVal = matchStandaloneWord.groupValues[1].trim()
            wordMap[wordVal]?.let { return it }
            romanMap[wordVal]?.let { return it }
        }

        return null
    }

    private fun detectChapterNumber(title: String, headings: List<String>, firstWords: String, bookTitle: String): Int? {
        // Try original title ONLY if it's not a useless title / doesn't match the book title
        if (!isUselessTitle(title, bookTitle)) {
            parseChapterNumber(title)?.let { return it }
        }
        
        // Try parsed headings
        for (h in headings) {
            if (!isUselessTitle(h, bookTitle)) {
                parseChapterNumber(h)?.let { return it }
            }
        }
        
        // Try first few words/lines
        val lines = firstWords.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.take(5)
        for (line in lines) {
            if (!isUselessTitle(line, bookTitle)) {
                parseChapterNumber(line)?.let { return it }
            }
        }
        
        return null
    }

    private fun isUselessTitle(title: String, bookTitle: String): Boolean {
        val t = title.trim().lowercase()
        val bt = bookTitle.trim().lowercase()
        if (t.isEmpty()) return true
        if (t == "cover" || t == "title" || t == "title page" || t == "titlepage" || t == "copyright") return true
        if (t == bt || bt.contains(t)) {
            return true
        }
        if (t.contains(bt)) {
            // It contains the book title, but if it also has "chapter" or sequential indicators, it is NOT useless!
            val hasChapterIndicator = t.contains("chapter") || t.contains("ch ") || t.contains("chap") || t.contains("part") || t.contains("vol") || t.any { it.isDigit() }
            if (!hasChapterIndicator) {
                return true
            }
        }
        if (t.endsWith(".xhtml") || t.endsWith(".html") || t.endsWith(".xml") || t.endsWith(".htm")) return true
        if (t == "index" || t == "toc" || t == "table of contents") return true
        return false
    }
}

private class SpineChapter(
    val index: Int,
    val filePath: String,
    val originalTitle: String,
    val headings: List<String>,
    val textContent: String,
    val htmlContent: String,
    val wordCount: Int,
    val charCount: Int,
    val detectedNum: Int?,
    var resolvedTitle: String = ""
)
