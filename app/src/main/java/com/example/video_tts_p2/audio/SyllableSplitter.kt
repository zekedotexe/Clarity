package com.example.video_tts_p2.audio

import java.util.Locale

/**
 * Represents a single syllable with its written orthography, phonetic respelling pronunciation,
 * and optional IPA representation.
 */
data class PhoneticSyllable(
    val written: String,
    val phonetic: String,
    val ipa: String? = null
)

/**
 * Utility to segment English words and IPA strings into phonetic syllables
 * for accurate phonetic pronunciation and read-a-long UI.
 */
object SyllableSplitter {

    // Common manual dictionary overrides mapping words to written syllables and phonetic pronunciations
    private val phoneticDictionary = mapOf(
        "condign" to listOf(
            PhoneticSyllable("con", "kuhn", "kən"),
            PhoneticSyllable("dign", "dahyn", "daɪn")
        ),
        "dictionary" to listOf(
            PhoneticSyllable("dic", "dik", "dɪk"),
            PhoneticSyllable("tion", "shuh", "ʃən"),
            PhoneticSyllable("ar", "ner", "ɛr"),
            PhoneticSyllable("y", "ee", "i")
        ),
        "pronunciation" to listOf(
            PhoneticSyllable("pro", "pruh", "prə"),
            PhoneticSyllable("nun", "nuhn", "nʌn"),
            PhoneticSyllable("ci", "see", "si"),
            PhoneticSyllable("a", "ay", "eɪ"),
            PhoneticSyllable("tion", "shuhn", "ʃən")
        ),
        "phonetic" to listOf(
            PhoneticSyllable("pho", "fuh", "fə"),
            PhoneticSyllable("net", "net", "nɛt"),
            PhoneticSyllable("ic", "ik", "ɪk")
        ),
        "syllable" to listOf(
            PhoneticSyllable("syl", "sil", "sɪl"),
            PhoneticSyllable("la", "uh", "ə"),
            PhoneticSyllable("ble", "buhl", "bəl")
        ),
        "camera" to listOf(
            PhoneticSyllable("cam", "kam", "kæm"),
            PhoneticSyllable("er", "er", "ər"),
            PhoneticSyllable("a", "uh", "ə")
        ),
        "computer" to listOf(
            PhoneticSyllable("com", "kuhm", "kəm"),
            PhoneticSyllable("pu", "pyoo", "pjuː"),
            PhoneticSyllable("ter", "ter", "tər")
        ),
        "punishment" to listOf(
            PhoneticSyllable("pun", "puhn", "pʌn"),
            PhoneticSyllable("ish", "ish", "ɪʃ"),
            PhoneticSyllable("ment", "muhnt", "mənt")
        ),
        "swiftly" to listOf(
            PhoneticSyllable("swift", "swift", "swɪft"),
            PhoneticSyllable("ly", "lee", "li")
        ),
        "administered" to listOf(
            PhoneticSyllable("ad", "ad", "æd"),
            PhoneticSyllable("min", "min", "mɪn"),
            PhoneticSyllable("is", "ih", "ɪ"),
            PhoneticSyllable("tered", "sterd", "stərd")
        ),
        "knight" to listOf(
            PhoneticSyllable("knight", "nahyt", "naɪt")
        ),
        "thought" to listOf(
            PhoneticSyllable("thought", "thawt", "θɔːt")
        ),
        "enough" to listOf(
            PhoneticSyllable("e", "ih", "ɪ"),
            PhoneticSyllable("nough", "nuhf", "nʌf")
        ),
        "colonel" to listOf(
            PhoneticSyllable("col", "kur", "kɜːr"),
            PhoneticSyllable("onel", "nuhl", "nəl")
        ),
        "answer" to listOf(
            PhoneticSyllable("an", "an", "æn"),
            PhoneticSyllable("swer", "ser", "sər")
        )
    )

    /**
     * Splits an English word into phonetic syllables.
     * If an official IPA string is supplied, it decomposes the IPA transcription into phonetic syllables.
     * Otherwise, it applies phonetic transcription rules.
     */
    fun getPhoneticSyllables(word: String, rawIpa: String? = null): List<PhoneticSyllable> {
        val cleanWord = word.trim().lowercase(Locale.ROOT)
        if (cleanWord.isEmpty()) return emptyList()

        phoneticDictionary[cleanWord]?.let { return it }

        // If official IPA is available, split the IPA into phonetic syllables
        if (!rawIpa.isNullOrBlank()) {
            val ipaSyllables = splitIpa(rawIpa)
            val writtenSyllables = splitWord(cleanWord)

            if (ipaSyllables.isNotEmpty()) {
                return ipaSyllables.mapIndexed { index, ipaPart ->
                    val written = writtenSyllables.getOrNull(index) ?: cleanWord
                    val respelled = ipaToPhoneticRespelling(ipaPart)
                    PhoneticSyllable(
                        written = written,
                        phonetic = respelled,
                        ipa = ipaPart
                    )
                }
            }
        }

        // Rule-based phonetic syllable breakdown from orthography
        val writtenSyllables = splitWord(cleanWord)
        return writtenSyllables.map { syl ->
            val phoneticPronunciation = convertOrthographyToPhonetic(syl, cleanWord)
            PhoneticSyllable(
                written = syl,
                phonetic = phoneticPronunciation,
                ipa = null
            )
        }
    }

    /**
     * Splits an English word into orthographic syllables using sonority rules.
     */
    fun splitWord(word: String): List<String> {
        val clean = word.trim().lowercase(Locale.ROOT)
        if (clean.length <= 3) return listOf(clean)

        phoneticDictionary[clean]?.let { return it.map { syl -> syl.written } }

        val vowels = setOf('a', 'e', 'i', 'o', 'u', 'y')
        val syllables = mutableListOf<String>()
        val currentSyllable = StringBuilder()

        var i = 0
        while (i < clean.length) {
            val char = clean[i]
            currentSyllable.append(char)

            val isVowel = char in vowels
            val isNextConsonant = (i + 1 < clean.length) && (clean[i + 1] !in vowels)
            val isNextNextVowel = (i + 2 < clean.length) && (clean[i + 2] in vowels)
            val isDoubleConsonant = (i + 2 < clean.length) &&
                    (clean[i + 1] !in vowels) && (clean[i + 2] !in vowels)

            if (isVowel && isNextConsonant && isNextNextVowel && i > 0 && i < clean.length - 2) {
                syllables.add(currentSyllable.toString())
                currentSyllable.clear()
            } else if (isVowel && isDoubleConsonant && i < clean.length - 3) {
                currentSyllable.append(clean[i + 1])
                i++
                syllables.add(currentSyllable.toString())
                currentSyllable.clear()
            }
            i++
        }

        if (currentSyllable.isNotEmpty()) {
            if (syllables.isNotEmpty() && currentSyllable.length == 1 && currentSyllable[0] !in vowels) {
                val lastIdx = syllables.size - 1
                syllables[lastIdx] = syllables[lastIdx] + currentSyllable.toString()
            } else {
                syllables.add(currentSyllable.toString())
            }
        }

        return if (syllables.isEmpty()) listOf(clean) else syllables
    }

    /**
     * Splits IPA transcription into individual syllables respecting stress markers (ˈ, ˌ, .)
     */
    fun splitIpa(ipa: String): List<String> {
        val cleanIpa = ipa.replace("/", "").replace("[", "").replace("]", "").trim()
        if (cleanIpa.isEmpty()) return emptyList()

        val delimiterRegex = Regex("(?=[.ˈˌ])")
        val parts = cleanIpa.split(delimiterRegex)
            .map { it.trim().removePrefix(".").removePrefix("ˈ").removePrefix("ˌ").trim() }
            .filter { it.isNotEmpty() }

        return if (parts.isNotEmpty()) parts else listOf(cleanIpa)
    }

    /**
     * Converts an IPA syllable transcription into readable English phonetic respelling.
     * e.g., "daɪn" -> "dahyn", "kən" -> "kuhn", "ʃən" -> "shuhn"
     */
    fun ipaToPhoneticRespelling(ipa: String): String {
        var res = ipa.replace("ˈ", "").replace("ˌ", "").replace(".", "").trim()

        // Vowel mappings
        res = res.replace("eɪ", "ay")
        res = res.replace("aɪ", "ahy")
        res = res.replace("oʊ", "oh")
        res = res.replace("əʊ", "oh")
        res = res.replace("aʊ", "ow")
        res = res.replace("ɔɪ", "oy")
        res = res.replace("iː", "ee")
        res = res.replace("uː", "oo")
        res = res.replace("ɔː", "aw")
        res = res.replace("ɜːr", "ur")
        res = res.replace("ər", "er")
        res = res.replace("ə", "uh")
        res = res.replace("ʌ", "uh")
        res = res.replace("æ", "a")
        res = res.replace("ɛ", "eh")
        res = res.replace("ɪ", "ih")
        res = res.replace("ɒ", "o")
        res = res.replace("ɑː", "ah")
        res = res.replace("ɑ", "ah")

        // Consonant mappings
        res = res.replace("ʃ", "sh")
        res = res.replace("tʃ", "ch")
        res = res.replace("ʒ", "zh")
        res = res.replace("dʒ", "j")
        res = res.replace("θ", "th")
        res = res.replace("ð", "th")
        res = res.replace("ŋ", "ng")
        res = res.replace("j", "y")

        return if (res.isNotEmpty()) res else ipa
    }

    /**
     * Converts an isolated orthographic syllable into its phonetic pronunciation string.
     * Fixes silent letters and irregular graphemes (e.g. "dign" -> "dahyn", "tion" -> "shuhn", "pho" -> "fuh").
     */
    private fun convertOrthographyToPhonetic(syllable: String, fullWord: String): String {
        var s = syllable.lowercase(Locale.ROOT)

        // Silent 'g' before 'n' (as in condign, sign, align, design)
        if (s.endsWith("ign")) {
            return s.removeSuffix("ign") + "ahyn"
        }
        if (s == "dign") return "dahyn"
        if (s == "sign") return "sahyn"

        // Common suffixes
        if (s == "tion" || s == "sion") return "shuhn"
        if (s == "tious" || s == "cious") return "shuhs"
        if (s == "ture") return "cher"
        if (s.endsWith("ly") && s.length > 2) return s.removeSuffix("ly") + "lee"
        if (s.endsWith("ment")) return s.removeSuffix("ment") + "muhnt"

        // Common consonant digraphs
        if (s.startsWith("kn")) s = s.removePrefix("k")
        if (s.startsWith("wr")) s = s.removePrefix("w")
        if (s.startsWith("ps")) s = s.removePrefix("p")
        if (s.startsWith("wh")) s = "w" + s.removePrefix("wh")
        if (s.startsWith("ph")) s = "f" + s.removePrefix("ph")
        s = s.replace("ph", "f")

        // 'c' rules
        if (s.startsWith("ce") || s.startsWith("ci") || s.startsWith("cy")) {
            s = "s" + s.substring(1)
        } else if (s.startsWith("c") && !s.startsWith("ch")) {
            s = "k" + s.substring(1)
        }

        // 'ight' -> 'ahyt'
        if (s.contains("ight")) {
            s = s.replace("ight", "ahyt")
        }

        // 'igh' -> 'ahy'
        if (s.contains("igh")) {
            s = s.replace("igh", "ahy")
        }

        // 'oo' stays 'oo'
        // 'ee' or 'ea' -> 'ee'
        s = s.replace("ea", "ee")

        // Final solitary 'e' silent or open vowel adjustments
        if (s.length > 2 && s.endsWith("e") && !s.endsWith("ee")) {
            val stem = s.dropLast(1)
            if (stem.endsWith("a")) s = stem.dropLast(1) + "ay"
            else if (stem.endsWith("i")) s = stem.dropLast(1) + "ahy"
            else if (stem.endsWith("o")) s = stem.dropLast(1) + "oh"
            else if (stem.endsWith("u")) s = stem.dropLast(1) + "yoo"
        }

        return s
    }
}
