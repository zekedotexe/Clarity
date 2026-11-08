package com.example.video_tts_p2.data.repository

import com.example.video_tts_p2.audio.SyllableSplitter
import com.example.video_tts_p2.data.api.DictionaryApiService
import com.example.video_tts_p2.data.model.ValidatedWord
import com.example.video_tts_p2.data.model.WordEntryDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class DictionaryRepository(
    private val apiService: DictionaryApiService = DictionaryApiService.create()
) {
    // In-memory cache for validated words
    private val validatedCache = ConcurrentHashMap<String, ValidatedWord>()
    // Negative cache for strings confirmed not in dictionary (to avoid spamming network)
    private val invalidWordCache = ConcurrentHashMap.newKeySet<String>()

    suspend fun validateAndFetch(candidateWord: String): Result<ValidatedWord> = withContext(Dispatchers.IO) {
        val normalized = candidateWord.trim().lowercase(Locale.ROOT)

        if (normalized.length < 2) {
            return@withContext Result.failure(IllegalArgumentException("Word too short"))
        }

        if (invalidWordCache.contains(normalized)) {
            return@withContext Result.failure(NoSuchElementException("Word '$normalized' previously marked invalid"))
        }

        validatedCache[normalized]?.let { cached ->
            return@withContext Result.success(cached)
        }

        try {
            val response = apiService.getWordEntries(normalized)
            if (response.isSuccessful) {
                val entries = response.body()
                if (!entries.isNullOrEmpty()) {
                    val validated = mapToValidatedWord(normalized, entries)
                    validatedCache[normalized] = validated
                    return@withContext Result.success(validated)
                } else {
                    invalidWordCache.add(normalized)
                    return@withContext Result.failure(NoSuchElementException("Empty response for '$normalized'"))
                }
            } else if (response.code() == 404) {
                invalidWordCache.add(normalized)
                return@withContext Result.failure(NoSuchElementException("Word '$normalized' not found in dictionary"))
            } else {
                return@withContext Result.failure(RuntimeException("Dictionary API HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    private fun mapToValidatedWord(fallbackWord: String, entries: List<WordEntryDto>): ValidatedWord {
        val firstEntry = entries.first()
        val wordName = firstEntry.word ?: fallbackWord

        // Find best audio URL and phonetic string across all phonetics entries
        var rawIpa: String? = null
        var audioUrl: String? = null

        // First check top-level phonetic
        firstEntry.phonetic?.let {
            val clean = it.replace("/", "").replace("[", "").replace("]", "").trim()
            if (clean.isNotEmpty()) rawIpa = clean
        }

        firstEntry.phonetics?.forEach { pDto ->
            if (rawIpa == null && !pDto.text.isNullOrBlank()) {
                val clean = pDto.text.replace("/", "").replace("[", "").replace("]", "").trim()
                if (clean.isNotEmpty()) rawIpa = clean
            }
            if (audioUrl == null && !pDto.audio.isNullOrBlank()) {
                var url = pDto.audio.trim()
                if (url.startsWith("//")) {
                    url = "https:$url"
                }
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    audioUrl = url
                }
            }
        }

        // Extract definitions and part of speech
        var partOfSpeech: String? = null
        var definition: String? = null
        var example: String? = null

        val firstMeaning = firstEntry.meanings?.firstOrNull()
        if (firstMeaning != null) {
            partOfSpeech = firstMeaning.partOfSpeech
            val firstDef = firstMeaning.definitions?.firstOrNull()
            if (firstDef != null) {
                definition = firstDef.definition
                example = firstDef.example
            }
        }

        val phoneticSyllables = SyllableSplitter.getPhoneticSyllables(wordName, rawIpa)
        // Extract phonetic pronunciation strings for each syllable instead of raw spelling
        val syllables = phoneticSyllables.map { it.phonetic }

        // Target visual phonetic formatting: bracketed phonetic respelling, e.g., [kuhn-dahyn]
        val formattedPhonetic = if (!rawIpa.isNullOrBlank()) {
            val respelled = SyllableSplitter.ipaToPhoneticRespelling(rawIpa)
            if (respelled.isNotEmpty()) "[$respelled]" else "[$rawIpa]"
        } else {
            "[${syllables.joinToString("-")}]"
        }

        return ValidatedWord(
            word = wordName,
            rawIpa = rawIpa,
            formattedPhonetic = formattedPhonetic,
            audioUrl = audioUrl,
            partOfSpeech = partOfSpeech,
            definition = definition,
            example = example,
            syllables = syllables,
            phoneticSyllables = phoneticSyllables
        )
    }

    fun getCached(word: String): ValidatedWord? {
        return validatedCache[word.trim().lowercase(Locale.ROOT)]
    }
}
