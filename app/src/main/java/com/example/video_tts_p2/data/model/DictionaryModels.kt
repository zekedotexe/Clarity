package com.example.video_tts_p2.data.model

import com.example.video_tts_p2.audio.PhoneticSyllable
import com.google.gson.annotations.SerializedName

/**
 * Raw response model from https://api.dictionaryapi.dev/api/v2/entries/en/{word}
 */
data class WordEntryDto(
    @SerializedName("word") val word: String?,
    @SerializedName("phonetic") val phonetic: String?,
    @SerializedName("phonetics") val phonetics: List<PhoneticDto>?,
    @SerializedName("meanings") val meanings: List<MeaningDto>?,
    @SerializedName("sourceUrls") val sourceUrls: List<String>?
)

data class PhoneticDto(
    @SerializedName("text") val text: String?,
    @SerializedName("audio") val audio: String?,
    @SerializedName("sourceUrl") val sourceUrl: String?
)

data class MeaningDto(
    @SerializedName("partOfSpeech") val partOfSpeech: String?,
    @SerializedName("definitions") val definitions: List<DefinitionDto>?,
    @SerializedName("synonyms") val synonyms: List<String>?,
    @SerializedName("antonyms") val antonyms: List<String>?
)

data class DefinitionDto(
    @SerializedName("definition") val definition: String?,
    @SerializedName("example") val example: String?,
    @SerializedName("synonyms") val synonyms: List<String>?,
    @SerializedName("antonyms") val antonyms: List<String>?
)

/**
 * Clean domain model for validated dictionary words
 */
data class ValidatedWord(
    val word: String,
    val rawIpa: String?,
    val formattedPhonetic: String,
    val audioUrl: String?,
    val partOfSpeech: String?,
    val definition: String?,
    val example: String?,
    val syllables: List<String>,
    val phoneticSyllables: List<PhoneticSyllable> = emptyList()
)
