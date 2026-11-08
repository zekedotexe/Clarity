package com.example.video_tts_p2.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.util.Log
import com.example.video_tts_p2.data.model.ValidatedWord
import com.example.video_tts_p2.data.repository.DictionaryRepository
import com.example.video_tts_p2.vision.WordSyllableItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class PronunciationMode {
    MEDIA_PLAYER_SLOW,   // Streams Free Dictionary API .mp3 at 0.75x speed
    MEDIA_PLAYER_NORMAL  // Streams Free Dictionary API .mp3 at 1.0x speed
}

sealed class PlaybackStatus {
    object Idle : PlaybackStatus()
    data class Playing(val mode: PronunciationMode, val word: String) : PlaybackStatus()
    data class ReadingSentence(val sentence: String, val wordIndex: Int, val syllableIndex: Int) : PlaybackStatus()
    data class Error(val message: String) : PlaybackStatus()
}

/**
 * Manages audio pronunciation using pre-recorded audio from the Free Dictionary API (.mp3)
 * streamed via Android MediaPlayer, with synchronized real-time syllable read-a-long highlighting.
 */
class AudioPronunciationManager(private val context: Context) {

    private val TAG = "AudioPronunciationMgr"

    private var mediaPlayer: MediaPlayer? = null

    private val _playbackStatus = MutableStateFlow<PlaybackStatus>(PlaybackStatus.Idle)
    val playbackStatus: StateFlow<PlaybackStatus> = _playbackStatus.asStateFlow()

    private var sentenceJob: Job? = null

    /**
     * Reads an entire sentence syllable-by-syllable using Free Dictionary API audio,
     * updating the highlight callback in real time.
     */
    suspend fun readSentenceSyllables(
        sentenceText: String,
        sentenceWords: List<WordSyllableItem>,
        repository: DictionaryRepository,
        speed: Float = 0.75f,
        pauseBetweenSyllablesMs: Long = 250L,
        pauseBetweenWordsMs: Long = 200L,
        onHighlightUpdate: (wordIndex: Int, syllableIndex: Int) -> Unit
    ) = withContext(Dispatchers.Default) {
        stopAllPlayback()

        try {
            for ((wIdx, wordItem) in sentenceWords.withIndex()) {
                // Fetch dictionary entry to get Free Dictionary API audioUrl
                val fetchResult = repository.validateAndFetch(wordItem.cleanWord)
                val validatedWord = fetchResult.getOrNull()
                val audioUrl = validatedWord?.audioUrl

                val syllables = wordItem.phoneticSyllables
                val numSyllables = syllables.size.coerceAtLeast(1)

                for (sIdx in 0 until numSyllables) {
                    _playbackStatus.value = PlaybackStatus.ReadingSentence(sentenceText, wIdx, sIdx)
                    onHighlightUpdate(wIdx, sIdx)

                    // If first syllable and audioUrl is available, play Free Dictionary audio
                    if (sIdx == 0 && !audioUrl.isNullOrBlank()) {
                        val audioPlayed = CompletableDeferred<Unit>()
                        withContext(Dispatchers.Main) {
                            playMediaPlayerAudioInternal(
                                word = wordItem.cleanWord,
                                audioUrl = audioUrl,
                                speed = speed,
                                onComplete = { audioPlayed.complete(Unit) },
                                onError = { audioPlayed.complete(Unit) }
                            )
                        }
                        withTimeoutOrNull(2000L) {
                            audioPlayed.await()
                        }
                    } else {
                        delay(pauseBetweenSyllablesMs)
                    }
                }
                delay(pauseBetweenWordsMs)
            }
        } finally {
            onHighlightUpdate(-1, -1)
            _playbackStatus.value = PlaybackStatus.Idle
        }
    }

    /**
     * Streams Free Dictionary API audio via Android MediaPlayer.
     */
    fun playMediaPlayerAudio(
        word: String,
        audioUrl: String,
        speed: Float = 0.75f,
        onComplete: () -> Unit = {},
        onError: () -> Unit = {}
    ) {
        stopAllPlayback()

        if (audioUrl.isBlank()) {
            _playbackStatus.value = PlaybackStatus.Error("No audio URL available for '$word'")
            onError()
            return
        }

        val mode = if (speed < 1.0f) PronunciationMode.MEDIA_PLAYER_SLOW else PronunciationMode.MEDIA_PLAYER_NORMAL
        _playbackStatus.value = PlaybackStatus.Playing(mode, word)

        try {
            val mp = MediaPlayer()
            mediaPlayer = mp

            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )

            mp.setDataSource(audioUrl)

            mp.setOnPreparedListener { player ->
                try {
                    val params = PlaybackParams().setSpeed(speed)
                    player.playbackParams = params
                    player.start()
                } catch (e: Exception) {
                    Log.w(TAG, "Could not set PlaybackParams speed, playing at normal speed", e)
                    player.start()
                }
            }

            mp.setOnCompletionListener { player ->
                _playbackStatus.value = PlaybackStatus.Idle
                player.release()
                mediaPlayer = null
                onComplete()
            }

            mp.setOnErrorListener { player, what, extra ->
                Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                _playbackStatus.value = PlaybackStatus.Error("Audio stream error ($what)")
                player.release()
                mediaPlayer = null
                onError()
                true
            }

            mp.prepareAsync()
        } catch (e: Exception) {
            Log.e(TAG, "MediaPlayer setup failed", e)
            _playbackStatus.value = PlaybackStatus.Error("Failed to load audio stream")
            onError()
        }
    }

    private fun playMediaPlayerAudioInternal(
        word: String,
        audioUrl: String,
        speed: Float,
        onComplete: () -> Unit,
        onError: () -> Unit
    ) {
        playMediaPlayerAudio(word, audioUrl, speed, onComplete, onError)
    }

    fun playWord(validatedWord: ValidatedWord, mode: PronunciationMode = PronunciationMode.MEDIA_PLAYER_SLOW) {
        val speed = if (mode == PronunciationMode.MEDIA_PLAYER_SLOW) 0.75f else 1.0f
        if (!validatedWord.audioUrl.isNullOrBlank()) {
            playMediaPlayerAudio(
                word = validatedWord.word,
                audioUrl = validatedWord.audioUrl,
                speed = speed
            )
        } else {
            _playbackStatus.value = PlaybackStatus.Error("No audio available from Free Dictionary API for '${validatedWord.word}'")
        }
    }

    fun stopAllPlayback() {
        sentenceJob?.cancel()
        sentenceJob = null

        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (_: Exception) {}
        mediaPlayer = null

        _playbackStatus.value = PlaybackStatus.Idle
    }

    fun release() {
        stopAllPlayback()
    }
}
