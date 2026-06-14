package com.example.lorelight

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object EpubGenerator {

    private fun String.escapeXml(): String {
        return this.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private suspend fun fetchCoverBytes(coverUrl: String?): ByteArray? = withContext(Dispatchers.IO) {
        if (coverUrl.isNullOrBlank()) return@withContext null
        try {
            if (coverUrl.startsWith("data:image", ignoreCase = true) && coverUrl.contains("base64,")) {
                val base64Data = coverUrl.substringAfter("base64,")
                return@withContext android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
            }
            if (coverUrl.startsWith("http", ignoreCase = true)) {
                val connection = java.net.URL(coverUrl).openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.inputStream.use { it.readBytes() }
            } else {
                // Raw base64 string
                android.util.Base64.decode(coverUrl, android.util.Base64.DEFAULT)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun generateAndSave(
        context: Context,
        novelTitle: String,
        author: String,
        coverUrl: String?,
        description: String,
        sourceUrl: String?,
        chapters: List<CrawledChapter>,
        progressCallback: (Int, Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        val safeTitle = novelTitle.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        
        val startChapter = chapters.firstOrNull()?.index ?: 1
        val endChapter = chapters.lastOrNull()?.index ?: 1
        val fileName = "${safeTitle}_Ch${startChapter}-Ch${endChapter}.epub"
        
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, fileName)
        
        try {
            val fos = FileOutputStream(file)
            val zos = ZipOutputStream(fos)
            
            // 1. Mimetype (must be uncompressed)
            val mimeEntry = ZipEntry("mimetype")
            mimeEntry.method = ZipEntry.STORED
            mimeEntry.size = 20
            val crc = java.util.zip.CRC32().apply { update("application/epub+zip".toByteArray()) }.value
            mimeEntry.crc = crc
            zos.putNextEntry(mimeEntry)
            zos.write("application/epub+zip".toByteArray())
            zos.closeEntry()
            
            // 2. META-INF/container.xml
            zos.putNextEntry(ZipEntry("META-INF/container.xml"))
            zos.write(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                    <rootfiles>
                        <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                    </rootfiles>
                </container>
                """.trimIndent().toByteArray()
            )
            zos.closeEntry()
            
            val manifestItems = StringBuilder()
            val spineItems = StringBuilder()
            val navItems = StringBuilder()
            val tocItems = StringBuilder()
            
            // Try fetching cover image before we proceed
            val coverBytes = fetchCoverBytes(coverUrl)
            if (coverBytes != null) {
                zos.putNextEntry(ZipEntry("OEBPS/images/cover.jpg"))
                zos.write(coverBytes)
                zos.closeEntry()
                manifestItems.append("""<item id="cover-image" href="images/cover.jpg" media-type="image/jpeg" properties="cover-image"/>${"\n"}""")
            }

            // Download and write chapters
            for ((i, chapter) in chapters.withIndex()) {
                val index = i + 1
                progressCallback(index, chapters.size)
                
                var text = ""
                try {
                    text = CrawlerEngine.extractChapterText(context, chapter.url)
                } catch(e: Exception) {
                    text = "Failed to load chapter content."
                }
                
                val html = """
                    <?xml version="1.0" encoding="utf-8"?>
                    <!DOCTYPE html>
                    <html xmlns="http://www.w3.org/1999/xhtml">
                    <head>
                        <title>${chapter.title.escapeXml()}</title>
                    </head>
                    <body>
                        <h2>${chapter.title.escapeXml()}</h2>
                        ${text.split("\n\n").joinToString("\n") { "<p>${it.escapeXml()}</p>" }}
                    </body>
                    </html>
                """.trimIndent()
                
                val chId = "chapter_$index"
                val chFile = "Text/$chId.xhtml"
                
                zos.putNextEntry(ZipEntry("OEBPS/$chFile"))
                zos.write(html.toByteArray())
                zos.closeEntry()
                
                manifestItems.append("""<item id="$chId" href="$chFile" media-type="application/xhtml+xml"/>${"\n"}""")
                spineItems.append("""<itemref idref="$chId"/>${"\n"}""")
                
                navItems.append("""<li><a href="$chFile">${chapter.title.escapeXml()}</a></li>${"\n"}""")
                
                tocItems.append("""
                    <navPoint id="navPoint-$index" playOrder="$index">
                        <navLabel>
                            <text>${chapter.title.escapeXml()}</text>
                        </navLabel>
                        <content src="$chFile"/>
                    </navPoint>
                """.trimIndent() + "\n")
            }
            
            // OEBPS/toc.ncx
            zos.putNextEntry(ZipEntry("OEBPS/toc.ncx"))
            zos.write(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
                    <head>
                        <meta name="dtb:uid" content="urn:uuid:00000000-0000-0000-0000-000000000000"/>
                        <meta name="dtb:depth" content="1"/>
                        <meta name="dtb:totalPageCount" content="0"/>
                        <meta name="dtb:maxPageNumber" content="0"/>
                    </head>
                    <docTitle><text>${novelTitle.escapeXml()}</text></docTitle>
                    <navMap>
                        $tocItems
                    </navMap>
                </ncx>
                """.trimIndent().toByteArray()
            )
            zos.closeEntry()
            
            // OEBPS/nav.xhtml
            zos.putNextEntry(ZipEntry("OEBPS/nav.xhtml"))
            zos.write(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <!DOCTYPE html>
                <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
                <head>
                    <title>Table of Contents</title>
                </head>
                <body>
                    <nav epub:type="toc" id="toc">
                        <h1>Table of Contents</h1>
                        <ol>
                            $navItems
                        </ol>
                    </nav>
                </body>
                </html>
                """.trimIndent().toByteArray()
            )
            zos.closeEntry()
            
            // OEBPS/content.opf
            manifestItems.append("""<item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>${"\n"}""")
            manifestItems.append("""<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>${"\n"}""")
            
            val coverMeta = if (coverBytes != null) """<meta name="cover" content="cover-image"/>""" else ""

            zos.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zos.write(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="pub-id">
                    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>${novelTitle.escapeXml()}</dc:title>
                        <dc:creator>${author.escapeXml()}</dc:creator>
                        <dc:language>en</dc:language>
                        <dc:identifier id="pub-id">urn:uuid:00000000-0000-0000-0000-000000000000</dc:identifier>
                        <dc:description>${description.escapeXml()}</dc:description>
                        <dc:source>${(sourceUrl ?: "").escapeXml()}</dc:source>
                        $coverMeta
                    </metadata>
                    <manifest>
                        $manifestItems
                    </manifest>
                    <spine toc="ncx">
                        $spineItems
                    </spine>
                </package>
                """.trimIndent().toByteArray()
            )
            zos.closeEntry()
            
            zos.close()
            fos.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
