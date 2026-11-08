package com.example.video_tts_p2.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.video_tts_p2.audio.AudioPronunciationManager
import com.example.video_tts_p2.audio.PlaybackStatus
import com.example.video_tts_p2.audio.PronunciationMode
import com.example.video_tts_p2.data.model.ValidatedWord
import com.example.video_tts_p2.data.repository.DictionaryRepository
import com.example.video_tts_p2.vision.DetectedSentence
import com.example.video_tts_p2.vision.DetectedWord
import com.example.video_tts_p2.vision.TextDetectionResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val activeSentence: DetectedSentence? = null,
    val activeWord: ValidatedWord? = null,
    val candidateWords: List<DetectedWord> = emptyList(),
    val latestDetection: TextDetectionResult? = null,
    val isLoading: Boolean = false,
    val selectedMode: PronunciationMode = PronunciationMode.MEDIA_PLAYER_SLOW,
    val playbackStatus: PlaybackStatus = PlaybackStatus.Idle,
    val isDetectionFrozen: Boolean = false,
    val activeReadingWordIndex: Int = -1,
    val activeReadingSyllableIndex: Int = -1,
    val isReadingSentence: Boolean = false,
    val errorMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DictionaryRepository()
    val audioManager = AudioPronunciationManager(application)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var lookupJob: Job? = null
    private var sentenceReadJob: Job? = null
    private var lastRequestedWord: String? = null

    init {
        viewModelScope.launch {
            audioManager.playbackStatus.collect { status ->
                _uiState.update { it.copy(playbackStatus = status) }
            }
        }
    }

    /**
     * Ingestion entry point for camera frames from CameraTextAnalyzer
     */
    fun onDetectionResult(result: TextDetectionResult) {
        if (_uiState.value.isDetectionFrozen || _uiState.value.isReadingSentence) {
            _uiState.update { it.copy(latestDetection = result) }
            return
        }

        val targetSentence = result.targetSentence
        val targetWord = result.targetWord?.cleanWord
            ?: targetSentence?.words?.firstOrNull()?.cleanWord

        _uiState.update {
            it.copy(
                latestDetection = result,
                activeSentence = targetSentence ?: it.activeSentence,
                candidateWords = result.candidateWords
            )
        }

        if (targetWord != null && targetWord != lastRequestedWord) {
            debounceAndFetchWord(targetWord)
        }
    }

    private fun debounceAndFetchWord(word: String) {
        lastRequestedWord = word
        lookupJob?.cancel()

        lookupJob = viewModelScope.launch {
            delay(350)
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val fetchResult = repository.validateAndFetch(word)
            fetchResult.fold(
                onSuccess = { validated ->
                    _uiState.update {
                        it.copy(
                            activeWord = validated,
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.localizedMessage
                        )
                    }
                }
            )
        }
    }

    /**
     * Toggles read-a-long sentence audio. Each syllable of each word is spoken
     * and highlighted blue in real time like read-a-long subtitles.
     */
    fun toggleSentenceReadAlong() {
        if (_uiState.value.isReadingSentence) {
            stopSentenceReadAlong()
            return
        }

        val sentence = _uiState.value.activeSentence ?: return
        if (sentence.syllableWords.isEmpty()) return

        sentenceReadJob?.cancel()
        sentenceReadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isReadingSentence = true,
                    activeReadingWordIndex = 0,
                    activeReadingSyllableIndex = 0
                )
            }

            try {
                audioManager.readSentenceSyllables(
                    sentenceText = sentence.rawText,
                    sentenceWords = sentence.syllableWords,
                    repository = repository,
                    speed = if (_uiState.value.selectedMode == PronunciationMode.MEDIA_PLAYER_SLOW) 0.75f else 1.0f,
                    pauseBetweenSyllablesMs = 250L,
                    pauseBetweenWordsMs = 200L,
                    onHighlightUpdate = { wIdx, sIdx ->
                        _uiState.update {
                            it.copy(
                                activeReadingWordIndex = wIdx,
                                activeReadingSyllableIndex = sIdx
                            )
                        }
                    }
                )
            } finally {
                _uiState.update {
                    it.copy(
                        isReadingSentence = false,
                        activeReadingWordIndex = -1,
                        activeReadingSyllableIndex = -1
                    )
                }
            }
        }
    }

    fun stopSentenceReadAlong() {
        sentenceReadJob?.cancel()
        sentenceReadJob = null
        audioManager.stopAllPlayback()
        _uiState.update {
            it.copy(
                isReadingSentence = false,
                activeReadingWordIndex = -1,
                activeReadingSyllableIndex = -1
            )
        }
    }

    /**
     * User can tap on any word in the subtitle banner or chips to validate in dictionary
     */
    fun selectWordManually(word: String) {
        debounceAndFetchWord(word)
    }

    fun setPronunciationMode(mode: PronunciationMode) {
        _uiState.update { it.copy(selectedMode = mode) }
    }

    fun playActiveWord() {
        val word = _uiState.value.activeWord ?: return
        audioManager.playWord(word, _uiState.value.selectedMode)
    }

    fun toggleDetectionFreeze() {
        _uiState.update { it.copy(isDetectionFrozen = !it.isDetectionFrozen) }
    }

    override fun onCleared() {
        super.onCleared()
        audioManager.release()
    }
}
