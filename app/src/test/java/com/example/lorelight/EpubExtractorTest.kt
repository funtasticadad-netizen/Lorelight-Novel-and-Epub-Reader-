package com.example.lorelight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubExtractorTest {

    @Test
    fun testHtmlEntityDecoding() {
        // Standard entities
        val html1 = "Hello &amp; Welcome &quot;World&quot;, &lt;Reader&gt; &apos;s &amp; more!"
        val text1 = EpubExtractor.extractTextFromHtml(html1)
        println("testHtmlEntityDecoding 1: [\n$text1\n]")
        assertEquals("Hello & Welcome \"World\", <Reader> 's & more!", text1)

        // Rich unicode entities (curly quotes, dashes, ellipses)
        val html2 = "He said, &ldquo;This &mdash; is exciting&rdquo;&hellip; right&apos;s &rsquo;s"
        val text2 = EpubExtractor.extractTextFromHtml(html2)
        println("testHtmlEntityDecoding 2: [\n$text2\n]")
        assertEquals("He said, “This — is exciting”… right's ’s", text2)

        // Numeric entities
        val html3 = "Test &#8212; emdash, &#8195; emspace, &#160; nbsp"
        val text3 = EpubExtractor.extractTextFromHtml(html3)
        println("testHtmlEntityDecoding 3: [\n$text3\n]")
        assertEquals("Test — emdash, emspace, nbsp", text3.replace(Regex("\\s+"), " "))
    }

    @Test
    fun testTextCleanupAndArtifactRemoval() {
        // 1. Literal ".nn" conversion artifacts
        val raw1 = "He decided to look back.nn"
        val cleaned1 = EpubExtractor.cleanFormattedText(raw1)
        assertEquals("He decided to look back.", cleaned1)

        // 2. OCR remnants and stray headers
        val raw2 = "Stray scanner line | here or Page 12 of 345 or [page 34] block."
        val cleaned2 = EpubExtractor.cleanFormattedText(raw2)
        println("cleaned2 actual: [\n$cleaned2\n]")
        for (i in cleaned2.indices) {
            println("cleaned2[$i] = '${cleaned2[i]}' (code: ${cleaned2[i].code})")
        }
        assertEquals("Stray scanner line | here or or block.", cleaned2)

        // 3. Duplicate whitespace normalization
        val raw3 = "This    has \t   very   broken      spacing."
        val cleaned3 = EpubExtractor.cleanFormattedText(raw3)
        assertEquals("This has very broken spacing.", cleaned3)

        // 4. Multi line limit - 3 or more consecutive newlines collapsed to exactly 2 newlines (double vertical space)
        val raw4 = "Line 1\n\n\n\nLine 2\n\n\nLine 3"
        val cleaned4 = EpubExtractor.cleanFormattedText(raw4)
        assertEquals("Line 1\n\nLine 2\n\nLine 3", cleaned4)
    }

    @Test
    fun testMalformedXhtmlGracefulHandling() {
        // Missing body & malformed tags (nested/unclosed tags, scripts, styles inside)
        val malformedHtml = """
            <html>
            <head>
                <style>body { color: red; }</style>
                <script>console.log("alert");</script>
            </head>
            <div>
                <h1>Malformed Title
                <p>This is a nested <b>paragraph with <i>styles still open.
            </div>
            </html>
        """.trimIndent()
        
        val text = EpubExtractor.extractTextFromHtml(malformedHtml)
        println("testMalformedXhtmlGracefulHandling Output: [\n$text\n]")
        
        // Assert styles, scripts are removed
        assertTrue(!text.contains("body { color"))
        assertTrue(!text.contains("console.log"))
        
        // Assert unclosed tag got parsed and extracted
        assertTrue(text.contains("### Malformed Title"))
        assertTrue(text.contains("This is a nested **paragraph with *styles still open. ***"))
    }

    @Test
    fun testEPUBFormattingPreservation() {
        val structuredHtml = """
            <html>
            <body>
                <h1>Chapter One: The Great Adventure</h1>
                <p>This is the first paragraph. It is <b>bold</b> and <i>italic</i>.</p>
                <blockquote>
                    This is a quote from a wise man.
                </blockquote>
                <p>Features included:</p>
                <ul>
                    <li>First bullet element</li>
                    <li>Second bullet element</li>
                </ul>
                <ol>
                    <li>First numbered item</li>
                    <li>Second numbered item</li>
                </ol>
            </body>
            </html>
        """.trimIndent()

        val text = EpubExtractor.extractTextFromHtml(structuredHtml)
        println("testEPUBFormattingPreservation Output: [\n$text\n]")

        // Assert Heading
        assertTrue(text.contains("### Chapter One: The Great Adventure"))

        // Assert Bold / Italic
        assertTrue(text.contains("**bold**"))
        assertTrue(text.contains("*italic*"))

        // Assert Blockquote prefix
        assertTrue(text.contains("   > This is a quote from a wise man."))

        // Assert Unordered list item prefix
        assertTrue(text.contains("   • First bullet element"))
        assertTrue(text.contains("   • Second bullet element"))

        // Assert Ordered list item indexing
        assertTrue(text.contains("   1. First numbered item"))
        assertTrue(text.contains("   2. Second numbered item"))
    }
}
