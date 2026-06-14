package com.example.lorelight

import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

data class Token(
    val raw: String,
    val isWord: Boolean,
    val wordIdx: Int
)

data class FilterRule(
    val id: String,
    val original: String,
    val replacement: String,
    val isVanish: Boolean,
    val scope: String, // "This Book" or "All Books"
    val bookId: String
)

object TextFilter {
    fun applyRawStringRules(text: String, rules: List<FilterRule>, currentBookId: String): String {
        val activeRules = rules.filter {
            it.scope == "All Books" || (
                it.scope == "This Book" && (
                    it.bookId.isBlank() || 
                    currentBookId.isBlank() ||
                    it.bookId.trim().lowercase(Locale.ROOT) == currentBookId.trim().lowercase(Locale.ROOT) ||
                    currentBookId.trim().lowercase(Locale.ROOT).contains(it.bookId.trim().lowercase(Locale.ROOT)) ||
                    it.bookId.trim().lowercase(Locale.ROOT).contains(currentBookId.trim().lowercase(Locale.ROOT))
                )
            )
        }
        if (activeRules.isEmpty()) return text

        // Sort rules by original text length descending so we replace longer phrases first!
        val sortedRules = activeRules.sortedByDescending { it.original.length }

        var result = text
        for (rule in sortedRules) {
            val originalNeedle = rule.original
            if (originalNeedle.isEmpty()) continue
            
            val replacement = if (rule.isVanish) "" else rule.replacement
            result = replaceCaseInsensitive(result, originalNeedle, replacement)
        }
        return result
    }

    private fun replaceCaseInsensitive(source: String, target: String, replacement: String): String {
        if (source.isEmpty() || target.isEmpty()) return source
        val sb = StringBuilder()
        var lastIdx = 0
        val targetLower = target.lowercase(Locale.ROOT)
        val sourceLower = source.lowercase(Locale.ROOT)
        
        while (true) {
            val idx = sourceLower.indexOf(targetLower, lastIdx)
            if (idx == -1) {
                sb.append(source.substring(lastIdx))
                break
            }
            sb.append(source.substring(lastIdx, idx))
            sb.append(replacement)
            lastIdx = idx + target.length
        }
        return sb.toString()
    }

    fun tokenize(text: String, startWordIdx: Int): Pair<List<Token>, Int> {
        val tokens = mutableListOf<Token>()
        var currentWordIdx = startWordIdx
        var i = 0
        val len = text.length
        while (i < len) {
            val char = text[i]
            if (char.isLetterOrDigit()) {
                val start = i
                while (i < len && text[i].isLetterOrDigit()) {
                    i++
                }
                val word = text.substring(start, i)
                tokens.add(Token(raw = word, isWord = true, wordIdx = currentWordIdx))
                currentWordIdx++
            } else {
                val start = i
                while (i < len && !text[i].isLetterOrDigit()) {
                    i++
                }
                val nonWord = text.substring(start, i)
                tokens.add(Token(raw = nonWord, isWord = false, wordIdx = -1))
            }
        }
        return Pair(tokens, currentWordIdx)
    }

    fun applyRules(tokens: List<Token>, rules: List<FilterRule>, currentBookId: String): List<Token> {
        val activeRules = rules.filter {
            it.scope == "All Books" || (it.scope == "This Book" && it.bookId.trim().lowercase() == currentBookId.trim().lowercase())
        }
        if (activeRules.isEmpty()) return tokens

        // Helper to strip non-alphanumeric and lowercase
        fun standardize(word: String): String {
            return word.filter { it.isLetterOrDigit() }.lowercase(Locale.ROOT)
        }

        data class RulePhrase(
            val rule: FilterRule,
            val words: List<String>
        )

        val parsedRules = activeRules.map { rule ->
            // Tokenize rule's original string to support multi-word phrase matching!
            val ruleWords = rule.original.split(Regex("\\s+"))
                .map { standardize(it) }
                .filter { it.isNotEmpty() }
            RulePhrase(rule, ruleWords)
        }.filter { it.words.isNotEmpty() }

        // Sort by longest match phrase first for greedy correctness
        val sortedRules = parsedRules.sortedByDescending { it.words.size }

        val overriddenText = Array<String?>(tokens.size) { null }
        val hidden = BooleanArray(tokens.size) { false }

        var tIdx = 0
        while (tIdx < tokens.size) {
            var matchedRule: RulePhrase? = null
            var matchedTokenCount = 0

            for (rp in sortedRules) {
                val ruleWords = rp.words
                var wMatchCount = 0
                var tempTIdx = tIdx
                var ruleWordsIdx = 0

                while (tempTIdx < tokens.size && ruleWordsIdx < ruleWords.size) {
                    val tok = tokens[tempTIdx]
                    if (tok.isWord) {
                        val standTok = standardize(tok.raw)
                        if (standTok == ruleWords[ruleWordsIdx]) {
                            ruleWordsIdx++
                            wMatchCount++
                        } else {
                            break
                        }
                    }
                    tempTIdx++
                }

                if (ruleWordsIdx == ruleWords.size && wMatchCount == ruleWords.size) {
                    matchedRule = rp
                    matchedTokenCount = tempTIdx - tIdx
                    break
                }
            }

            if (matchedRule != null && matchedTokenCount > 0) {
                val rule = matchedRule.rule
                if (rule.isVanish) {
                    for (i in tIdx until (tIdx + matchedTokenCount)) {
                        hidden[i] = true
                    }
                } else {
                    var firstWordTokLoc = -1
                    for (i in tIdx until (tIdx + matchedTokenCount)) {
                        if (tokens[i].isWord) {
                            if (firstWordTokLoc == -1) {
                                firstWordTokLoc = i
                            } else {
                                hidden[i] = true
                            }
                        } else {
                            if (firstWordTokLoc != -1) {
                                hidden[i] = true
                            }
                        }
                    }
                    if (firstWordTokLoc != -1) {
                        overriddenText[firstWordTokLoc] = rule.replacement
                    }
                }
                tIdx += matchedTokenCount
            } else {
                tIdx++
            }
        }

        val result = mutableListOf<Token>()
        for (i in tokens.indices) {
            val tok = tokens[i]
            if (hidden[i]) {
                result.add(tok.copy(raw = "", isWord = false, wordIdx = -1))
            } else {
                val over = overriddenText[i]
                if (over != null) {
                    result.add(tok.copy(raw = over))
                } else {
                    result.add(tok)
                }
            }
        }
        return result
    }
}
