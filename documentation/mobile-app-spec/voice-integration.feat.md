# Voice Integration Feature Specification
**For Android Mobile Application**

> **⚠️ FUTURE RELEASE FEATURE**  
> This feature has been deferred from the initial release due to complexity.  
> Voice integration will be considered for a future version after core functionality is stable.  
> This specification is maintained for future reference.

## Table of Contents
1. [Overview](#overview)
2. [Architecture](#architecture)
   - [Technology Stack](#technology-stack-android-specific)
   - [Key Components](#key-components)
3. [Components Architecture](#components-architecture)
   - [Speech Recognition Manager](#speech-recognition-manager)
   - [Text-to-Speech Manager](#text-to-speech-manager)
   - [Voice Command Processor](#voice-command-processor)
   - [Audio Permission Handler](#audio-permission-handler)
   - [Voice UI Components](#voice-ui-components)
   - [Voice Feedback System](#voice-feedback-system)
   - [Voice Settings Manager](#voice-settings-manager)
   - [Error Handling](#error-handling)
   - [Integration Points](#integration-points)
4. [Testing](#testing)
   - [Testing Checklist](#testing-checklist)
   - [Unit Tests](#unit-tests)
   - [Integration Tests](#integration-tests)
5. [Implementation Notes](#implementation-notes-android-mobile)
   - [Critical Implementation Details](#critical-implementation-details)
   - [Performance Considerations](#performance-considerations-android-specific)
   - [Package Structure](#package-structure)
   - [Future Extensions](#future-extensions-android-mobile-focus)

## Overview

The Voice Integration feature provides comprehensive voice interaction capabilities for **Pocket Agent - a remote coding agent mobile interface**. This feature implements speech-to-text for voice commands and dictation, text-to-speech for audio feedback and Claude response narration, and a complete voice UI system. It enables hands-free interaction with Claude Code sessions, making the app accessible while driving, walking, or in other situations where touch interaction is impractical.

**Target Platform**: Native Android Application (API 26+)
**Development Environment**: Android Studio with Kotlin
**Architecture**: MVVM with reactive voice processing pipeline
**Primary Specification**: [Frontend Technical Specification](./frontend.spec.md#voice-integration)

This feature is designed to be implemented in Phase 3 as an advanced capability that enhances all existing features with voice interaction. It integrates deeply with the chat interface, quick actions, and notification systems to provide a seamless voice-driven experience.

## Architecture

### Technology Stack (Android-Specific)

- **Speech Recognition**: Android SpeechRecognizer API - On-device and cloud speech recognition
- **Text-to-Speech**: Android TextToSpeech API - Natural voice synthesis
- **Audio Processing**: AudioRecord & MediaRecorder - Raw audio capture and processing
- **Noise Cancellation**: Android NoiseSuppressor API - Background noise reduction
- **Wake Word Detection**: Porcupine Wake Word - Offline "Hey Claude" detection
- **Permission Management**: Android Runtime Permissions - RECORD_AUDIO permission handling
- **Voice Activity Detection**: WebRTC VAD - Detect when user is speaking
- **Dependency Injection**: Hilt - Voice component injection
- **Testing**: Mockito + Robolectric - Voice API mocking

### Key Components

- **SpeechRecognitionManager**: Manages speech-to-text conversion with multiple recognition modes
- **TextToSpeechManager**: Handles TTS initialization, voice selection, and audio playback
- **VoiceCommandProcessor**: Interprets voice commands and routes to appropriate actions
- **AudioPermissionHandler**: Manages audio recording permissions with rationale UI
- **VoiceInputButton**: Composable UI component for voice input with visual feedback
- **VoiceFeedbackController**: Coordinates audio responses and haptic feedback
- **VoiceSettingsManager**: User preferences for voice features and language settings
- **WakeWordDetector**: Always-listening service for hands-free activation
- **VoiceSessionManager**: Manages voice interaction sessions and context

## Components Architecture

### Speech Recognition Manager

**Purpose**: Implements robust speech-to-text functionality with support for continuous recognition, command mode, and dictation mode. Handles multiple languages, offline recognition fallback, and real-time transcription updates.

```kotlin
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeechRecognitionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioPermissionHandler: AudioPermissionHandler,
    private val voiceSettingsManager: VoiceSettingsManager
) {
    
    companion object {
        const val PARTIAL_RESULT_DELAY = 100L
        const val SILENCE_TIMEOUT = 2000L
        const val MAX_RECOGNITION_TIME = 60000L // 1 minute max
        const val CONFIDENCE_THRESHOLD = 0.7f
    }
    
    sealed class RecognitionState {
        object Idle : RecognitionState()
        object Listening : RecognitionState()
        data class Recognizing(val partialResult: String) : RecognitionState()
        data class Result(val text: String, val confidence: Float) : RecognitionState()
        data class Error(val error: RecognitionError) : RecognitionState()
    }
    
    sealed class RecognitionMode {
        object Command : RecognitionMode() // Short commands, quick timeout
        object Dictation : RecognitionMode() // Long-form input, extended timeout
        object Continuous : RecognitionMode() // Continuous listening for conversation
    }
    
    sealed class RecognitionError {
        object NoPermission : RecognitionError()
        object NoInternet : RecognitionError()
        object NoMatch : RecognitionError()
        object AudioCapture : RecognitionError()
        data class Unknown(val message: String) : RecognitionError()
    }
    
    private var speechRecognizer: SpeechRecognizer? = null
    private val _recognitionState = MutableStateFlow<RecognitionState>(RecognitionState.Idle)
    val recognitionState: StateFlow<RecognitionState> = _recognitionState.asStateFlow()
    
    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()
    
    init {
        checkAvailability()
    }
    
    private fun checkAvailability() {
        _isAvailable.value = SpeechRecognizer.isRecognitionAvailable(context)
    }
    
    suspend fun startRecognition(
        mode: RecognitionMode = RecognitionMode.Command,
        language: String? = null
    ): Flow<RecognitionState> = callbackFlow {
        // Check permissions first
        if (!audioPermissionHandler.hasAudioPermission()) {
            trySend(RecognitionState.Error(RecognitionError.NoPermission))
            close()
            return@callbackFlow
        }
        
        // Initialize recognizer
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    trySend(RecognitionState.Listening)
                }
                
                override fun onBeginningOfSpeech() {
                    // User started speaking
                }
                
                override fun onRmsChanged(rmsdB: Float) {
                    // Audio level changed - can be used for UI feedback
                }
                
                override fun onBufferReceived(buffer: ByteArray?) {
                    // Audio buffer received
                }
                
                override fun onEndOfSpeech() {
                    // User stopped speaking
                }
                
                override fun onError(error: Int) {
                    val recognitionError = when (error) {
                        SpeechRecognizer.ERROR_NETWORK -> RecognitionError.NoInternet
                        SpeechRecognizer.ERROR_AUDIO -> RecognitionError.AudioCapture
                        SpeechRecognizer.ERROR_NO_MATCH -> RecognitionError.NoMatch
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> RecognitionError.NoPermission
                        else -> RecognitionError.Unknown("Error code: $error")
                    }
                    trySend(RecognitionState.Error(recognitionError))
                    close()
                }
                
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val confidence = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                    
                    if (!matches.isNullOrEmpty()) {
                        val bestMatch = matches[0]
                        val bestConfidence = confidence?.getOrNull(0) ?: 1.0f
                        
                        trySend(RecognitionState.Result(bestMatch, bestConfidence))
                    } else {
                        trySend(RecognitionState.Error(RecognitionError.NoMatch))
                    }
                    close()
                }
                
                override fun onPartialResults(partialResults: Bundle?) {
                    val partialMatches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    partialMatches?.firstOrNull()?.let { partial ->
                        trySend(RecognitionState.Recognizing(partial))
                    }
                }
                
                override fun onEvent(eventType: Int, params: Bundle?) {
                    // Handle recognition events
                }
            })
        }
        
        // Configure recognition intent
        val intent = createRecognitionIntent(mode, language)
        speechRecognizer?.startListening(intent)
        
        _recognitionState.value = RecognitionState.Listening
        
        awaitClose {
            stopRecognition()
        }
    }
    
    private fun createRecognitionIntent(mode: RecognitionMode, language: String?): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, when (mode) {
                RecognitionMode.Command -> RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH
                RecognitionMode.Dictation -> RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                RecognitionMode.Continuous -> RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            })
            
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language ?: Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            
            when (mode) {
                RecognitionMode.Command -> {
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)
                }
                RecognitionMode.Dictation -> {
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                }
                RecognitionMode.Continuous -> {
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
                }
            }
        }
    }
    
    fun stopRecognition() {
        speechRecognizer?.apply {
            stopListening()
            cancel()
            destroy()
        }
        speechRecognizer = null
        _recognitionState.value = RecognitionState.Idle
    }
    
    fun isListening(): Boolean {
        return _recognitionState.value is RecognitionState.Listening ||
               _recognitionState.value is RecognitionState.Recognizing
    }
    
    suspend fun recognizeOnce(mode: RecognitionMode = RecognitionMode.Command): String? {
        return startRecognition(mode)
            .filterIsInstance<RecognitionState.Result>()
            .firstOrNull()
            ?.takeIf { it.confidence >= CONFIDENCE_THRESHOLD }
            ?.text
    }
}
```

### Text-to-Speech Manager

**Purpose**: Provides natural text-to-speech functionality with voice selection, speed control, and audio focus management. Supports reading Claude responses, notifications, and UI feedback with appropriate voice characteristics.

```kotlin
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TextToSpeechManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val voiceSettingsManager: VoiceSettingsManager,
    private val scope: CoroutineScope
) {
    
    companion object {
        const val UTTERANCE_ID_PREFIX = "pocket_agent_"
        const val DEFAULT_SPEECH_RATE = 1.0f
        const val DEFAULT_PITCH = 1.0f
        const val MIN_SPEECH_RATE = 0.5f
        const val MAX_SPEECH_RATE = 2.0f
    }
    
    sealed class TtsState {
        object Uninitialized : TtsState()
        object Initializing : TtsState()
        object Ready : TtsState()
        data class Speaking(val utteranceId: String, val text: String) : TtsState()
        data class Error(val message: String) : TtsState()
    }
    
    data class TtsVoice(
        val id: String,
        val name: String,
        val language: String,
        val country: String,
        val isNetworkRequired: Boolean,
        val features: Set<String>
    )
    
    private var tts: TextToSpeech? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    
    private val _ttsState = MutableStateFlow<TtsState>(TtsState.Uninitialized)
    val ttsState: StateFlow<TtsState> = _ttsState.asStateFlow()
    
    private val _availableVoices = MutableStateFlow<List<TtsVoice>>(emptyList())
    val availableVoices: StateFlow<List<TtsVoice>> = _availableVoices.asStateFlow()
    
    private var currentUtteranceId = 0
    
    init {
        initializeTts()
    }
    
    private fun initializeTts() {
        _ttsState.value = TtsState.Initializing
        
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                configureTts()
                _ttsState.value = TtsState.Ready
                loadAvailableVoices()
            } else {
                _ttsState.value = TtsState.Error("TTS initialization failed")
            }
        }
    }
    
    private fun configureTts() {
        tts?.apply {
            // Set default language
            val preferredLocale = voiceSettingsManager.getPreferredLocale()
            val result = setLanguage(preferredLocale)
            
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Fallback to English
                setLanguage(Locale.US)
            }
            
            // Set speech parameters
            // Speech rate and pitch will be set during speak operations
            
            // Set audio attributes for proper routing
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            
            setAudioAttributes(audioAttributes)
            
            // Set utterance listener
            setOnUtteranceProgressListener(utteranceListener)
        }
    }
    
    private val utteranceListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            utteranceId?.let { id ->
                val text = utteranceTexts[id] ?: ""
                _ttsState.value = TtsState.Speaking(id, text)
            }
        }
        
        override fun onDone(utteranceId: String?) {
            utteranceId?.let { id ->
                utteranceTexts.remove(id)
                if (_ttsState.value is TtsState.Speaking && 
                    (_ttsState.value as TtsState.Speaking).utteranceId == id) {
                    _ttsState.value = TtsState.Ready
                }
            }
            releaseAudioFocus()
        }
        
        override fun onError(utteranceId: String?) {
            utteranceId?.let { id ->
                utteranceTexts.remove(id)
            }
            _ttsState.value = TtsState.Error("Speech synthesis error")
            releaseAudioFocus()
        }
        
        @Deprecated("Deprecated in API level 21")
        override fun onError(utteranceId: String?, errorCode: Int) {
            onError(utteranceId)
        }
    }
    
    private val utteranceTexts = mutableMapOf<String, String>()
    
    private fun loadAvailableVoices() {
        tts?.let { engine ->
            val voices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                engine.voices?.map { voice ->
                    TtsVoice(
                        id = voice.name,
                        name = voice.name,
                        language = voice.locale.language,
                        country = voice.locale.country,
                        isNetworkRequired = voice.isNetworkConnectionRequired,
                        features = voice.features ?: emptySet()
                    )
                } ?: emptyList()
            } else {
                // Fallback for older API levels
                emptyList()
            }
            _availableVoices.value = voices
        }
    }
    
    suspend fun speak(
        text: String,
        priority: SpeechPriority = SpeechPriority.NORMAL,
        voiceId: String? = null
    ): Flow<TtsState> = callbackFlow {
        if (_ttsState.value !is TtsState.Ready && _ttsState.value !is TtsState.Speaking) {
            trySend(TtsState.Error("TTS not ready"))
            close()
            return@callbackFlow
        }
        
        // Request audio focus
        if (!requestAudioFocus()) {
            trySend(TtsState.Error("Could not acquire audio focus"))
            close()
            return@callbackFlow
        }
        
        // Generate utterance ID
        val utteranceId = "$UTTERANCE_ID_PREFIX${currentUtteranceId++}"
        utteranceTexts[utteranceId] = text
        
        // Set voice if specified
        voiceId?.let { id ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val voice = tts?.voices?.find { it.name == id }
                voice?.let { tts?.voice = it }
            }
        }
        
        // Queue mode based on priority
        val queueMode = when (priority) {
            SpeechPriority.HIGH -> TextToSpeech.QUEUE_FLUSH
            SpeechPriority.NORMAL -> TextToSpeech.QUEUE_ADD
            SpeechPriority.LOW -> TextToSpeech.QUEUE_ADD
        }
        
        // Speak
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts?.speak(text, queueMode, null, utteranceId)
        } else {
            @Suppress("DEPRECATION")
            tts?.speak(text, queueMode, hashMapOf(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID to utteranceId))
        }
        
        if (result == TextToSpeech.SUCCESS) {
            // Flow will emit states through the utterance listener
            ttsState
                .takeWhile { state ->
                    when (state) {
                        is TtsState.Speaking -> state.utteranceId != utteranceId
                        is TtsState.Ready -> false
                        is TtsState.Error -> false
                        else -> true
                    }
                }
                .collect { trySend(it) }
        } else {
            trySend(TtsState.Error("Failed to queue speech"))
        }
        
        awaitClose {
            // Cleanup if cancelled
            if ((_ttsState.value as? TtsState.Speaking)?.utteranceId == utteranceId) {
                stop()
            }
        }
    }
    
    fun stop() {
        tts?.stop()
        _ttsState.value = TtsState.Ready
        releaseAudioFocus()
    }
    
    fun pause() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts?.stop() // No pause API, so we stop
        }
    }
    
    fun setSpeechRate(rate: Float) {
        val clampedRate = rate.coerceIn(MIN_SPEECH_RATE, MAX_SPEECH_RATE)
        tts?.setSpeechRate(clampedRate)
        // Save preference asynchronously
        scope.launch {
            voiceSettingsManager.setSpeechRate(clampedRate)
        }
    }
    
    fun setPitch(pitch: Float) {
        val clampedPitch = pitch.coerceIn(0.5f, 2.0f)
        tts?.setPitch(clampedPitch)
        // Save preference asynchronously
        scope.launch {
            voiceSettingsManager.setPitch(clampedPitch)
        }
    }
    
    fun setVoice(voiceId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val voice = tts?.voices?.find { it.name == voiceId }
            voice?.let { 
                tts?.voice = it
                // Save preference asynchronously
                scope.launch {
                    voiceSettingsManager.setPreferredVoice(voiceId)
                }
            }
        }
    }
    
    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener { }
                .build()
            
            audioFocusRequest = focusRequest
            audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                { },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }
    
    private fun releaseAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus { }
        }
    }
    
    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        _ttsState.value = TtsState.Uninitialized
    }
    
    enum class SpeechPriority {
        HIGH,    // Interrupts current speech
        NORMAL,  // Queued after current
        LOW      // Only spoken if queue is empty
    }
}
```

### Voice Command Processor

**Purpose**: Interprets voice commands and routes them to appropriate actions. Supports natural language understanding for common commands, contextual interpretation based on current screen, and command shortcuts.

```kotlin
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

// Note: Project class should be imported from the data layer
// import com.pocketagent.domain.model.Project

@Singleton
class VoiceCommandProcessor @Inject constructor(
    private val navigationManager: NavigationManager,
    private val secureDataRepository: SecureDataRepository,
    private val connectionManager: ConnectionManager,
    private val chatManager: ChatManager
) {
    
    sealed class VoiceCommand {
        // Navigation commands
        object ShowProjects : VoiceCommand()
        data class OpenProject(val projectName: String) : VoiceCommand()
        object GoBack : VoiceCommand()
        object GoHome : VoiceCommand()
        
        // Chat commands
        data class SendMessage(val message: String) : VoiceCommand()
        object ReadLastMessage : VoiceCommand()
        object ClearChat : VoiceCommand()
        
        // Connection commands
        object Connect : VoiceCommand()
        object Disconnect : VoiceCommand()
        object CheckStatus : VoiceCommand()
        
        // Quick actions
        data class RunCommand(val command: String) : VoiceCommand()
        object ShowQuickActions : VoiceCommand()
        
        // Help
        object ShowHelp : VoiceCommand()
        object ListCommands : VoiceCommand()
        
        // Unknown
        data class Unknown(val text: String) : VoiceCommand()
    }
    
    data class CommandContext(
        val currentScreen: String,
        val activeProjectId: String?,
        val isConnected: Boolean
    )
    
    private val commandPatterns = mapOf(
        // Navigation patterns
        Regex("(open|go to|show) projects?", RegexOption.IGNORE_CASE) to VoiceCommand.ShowProjects,
        Regex("(go|navigate) back", RegexOption.IGNORE_CASE) to VoiceCommand.GoBack,
        Regex("(go|navigate) home", RegexOption.IGNORE_CASE) to VoiceCommand.GoHome,
        
        // Connection patterns
        Regex("connect", RegexOption.IGNORE_CASE) to VoiceCommand.Connect,
        Regex("disconnect", RegexOption.IGNORE_CASE) to VoiceCommand.Disconnect,
        Regex("(check|show) status", RegexOption.IGNORE_CASE) to VoiceCommand.CheckStatus,
        
        // Chat patterns
        Regex("read last (message|response)", RegexOption.IGNORE_CASE) to VoiceCommand.ReadLastMessage,
        Regex("clear chat", RegexOption.IGNORE_CASE) to VoiceCommand.ClearChat,
        
        // Quick action patterns
        Regex("show (quick )?actions", RegexOption.IGNORE_CASE) to VoiceCommand.ShowQuickActions,
        
        // Help patterns
        Regex("(show )?help", RegexOption.IGNORE_CASE) to VoiceCommand.ShowHelp,
        Regex("(list|show) commands", RegexOption.IGNORE_CASE) to VoiceCommand.ListCommands
    )
    
    private val projectNamePattern = Regex("open (?:project )?(.+)", RegexOption.IGNORE_CASE)
    private val runCommandPattern = Regex("run (?:command )?(.+)", RegexOption.IGNORE_CASE)
    private val sendMessagePattern = Regex("(?:send|tell claude|ask) (.+)", RegexOption.IGNORE_CASE)
    
    suspend fun processCommand(
        text: String,
        context: CommandContext
    ): VoiceCommand {
        val normalizedText = text.trim().lowercase()
        
        // Check simple pattern matches first
        for ((pattern, command) in commandPatterns) {
            if (pattern.matches(normalizedText)) {
                return command
            }
        }
        
        // Check complex patterns
        projectNamePattern.find(text)?.let { match ->
            val projectName = match.groupValues[1]
            return VoiceCommand.OpenProject(projectName)
        }
        
        runCommandPattern.find(text)?.let { match ->
            val command = match.groupValues[1]
            return VoiceCommand.RunCommand(command)
        }
        
        sendMessagePattern.find(text)?.let { match ->
            val message = match.groupValues[1]
            return VoiceCommand.SendMessage(message)
        }
        
        // Context-aware interpretation
        return interpretWithContext(text, context)
    }
    
    private suspend fun interpretWithContext(
        text: String,
        context: CommandContext
    ): VoiceCommand {
        // If in chat screen and no command matched, treat as message
        if (context.currentScreen == "chat" && context.isConnected) {
            return VoiceCommand.SendMessage(text)
        }
        
        // Try fuzzy matching for project names
        if (text.contains("open") || text.contains("project")) {
            val projects = projectRepository.getAllProjects().first()
            val bestMatch = findBestProjectMatch(text, projects)
            bestMatch?.let {
                return VoiceCommand.OpenProject(it.name)
            }
        }
        
        return VoiceCommand.Unknown(text)
    }
    
    private fun findBestProjectMatch(text: String, projects: List<Project>): Project? {
        val normalizedText = text.lowercase()
        
        // Exact match
        projects.find { it.name.lowercase() in normalizedText }?.let { return it }
        
        // Partial match
        projects.find { project ->
            val words = project.name.lowercase().split(" ")
            words.all { word -> word in normalizedText }
        }?.let { return it }
        
        // Fuzzy match with Levenshtein distance
        val scoredProjects = projects.map { project ->
            val distance = levenshteinDistance(normalizedText, project.name.lowercase())
            project to distance
        }.sortedBy { it.second }
        
        // Return best match if distance is reasonable
        return scoredProjects.firstOrNull()?.takeIf { it.second < 5 }?.first
    }
    
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        
        return dp[s1.length][s2.length]
    }
    
    suspend fun executeCommand(
        command: VoiceCommand,
        context: CommandContext
    ): Result<String> {
        return try {
            val response = when (command) {
                is VoiceCommand.ShowProjects -> {
                    navigationManager.navigateToProjects()
                    "Showing projects"
                }
                
                is VoiceCommand.OpenProject -> {
                    val projects = projectRepository.getAllProjects().first()
                    val project = projects.find { it.name.equals(command.projectName, ignoreCase = true) }
                    
                    if (project != null) {
                        navigationManager.navigateToProject(project.id)
                        "Opening ${project.name}"
                    } else {
                        "Project ${command.projectName} not found"
                    }
                }
                
                is VoiceCommand.GoBack -> {
                    navigationManager.navigateBack()
                    "Going back"
                }
                
                is VoiceCommand.GoHome -> {
                    navigationManager.navigateToHome()
                    "Going home"
                }
                
                is VoiceCommand.SendMessage -> {
                    if (context.activeProjectId != null && context.isConnected) {
                        chatManager.sendMessage(context.activeProjectId, command.message)
                        "Message sent"
                    } else {
                        "Not connected to a project"
                    }
                }
                
                is VoiceCommand.Connect -> {
                    if (context.activeProjectId != null) {
                        connectionManager.connect(context.activeProjectId)
                        "Connecting"
                    } else {
                        "No active project"
                    }
                }
                
                is VoiceCommand.Disconnect -> {
                    if (context.activeProjectId != null) {
                        connectionManager.disconnect(context.activeProjectId)
                        "Disconnected"
                    } else {
                        "No active project"
                    }
                }
                
                is VoiceCommand.CheckStatus -> {
                    val status = if (context.isConnected) "Connected" else "Disconnected"
                    "Status: $status"
                }
                
                is VoiceCommand.ShowHelp -> {
                    navigationManager.navigateToHelp()
                    "Showing help"
                }
                
                is VoiceCommand.Unknown -> {
                    "Sorry, I didn't understand: ${command.text}"
                }
                
                else -> "Command not implemented yet"
            }
            
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun getAvailableCommands(context: CommandContext): List<String> {
        val commands = mutableListOf(
            "Show projects",
            "Go back",
            "Show help"
        )
        
        if (context.activeProjectId != null) {
            commands.addAll(listOf(
                "Connect",
                "Disconnect",
                "Check status"
            ))
            
            if (context.isConnected) {
                commands.addAll(listOf(
                    "Send [message]",
                    "Read last message",
                    "Clear chat",
                    "Run [command]",
                    "Show quick actions"
                ))
            }
        }
        
        return commands
    }
}
```

### Audio Permission Handler

**Purpose**: Manages Android runtime permissions for audio recording with proper rationale display, permission request flow, and settings redirection for denied permissions.

```kotlin
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioPermissionHandler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    sealed class PermissionState {
        object Granted : PermissionState()
        object Denied : PermissionState()
        object ShowRationale : PermissionState()
        object PermanentlyDenied : PermissionState()
    }
    
    private val _permissionState = MutableStateFlow(checkCurrentPermissionState())
    val permissionState: StateFlow<PermissionState> = _permissionState.asStateFlow()
    
    private val permissionResultChannel = Channel<Boolean>()
    
    fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun checkCurrentPermissionState(): PermissionState {
        return if (hasAudioPermission()) {
            PermissionState.Granted
        } else {
            PermissionState.Denied
        }
    }
    
    fun registerForPermissionResult(activity: ComponentActivity): ActivityResultLauncher<String> {
        return activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            _permissionState.value = if (isGranted) {
                PermissionState.Granted
            } else {
                if (activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                    PermissionState.ShowRationale
                } else {
                    PermissionState.PermanentlyDenied
                }
            }
            permissionResultChannel.trySend(isGranted)
        }
    }
    
    suspend fun requestPermission(
        activity: ComponentActivity,
        launcher: ActivityResultLauncher<String>
    ): Boolean {
        if (hasAudioPermission()) {
            return true
        }
        
        // Check if we should show rationale
        if (activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            _permissionState.value = PermissionState.ShowRationale
        }
        
        // Launch permission request
        launcher.launch(Manifest.permission.RECORD_AUDIO)
        
        // Wait for result
        return permissionResultChannel.receive()
    }
    
    fun openAppSettings(activity: ComponentActivity) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", activity.packageName, null)
        }
        activity.startActivity(intent)
    }
    
    fun updatePermissionState() {
        _permissionState.value = checkCurrentPermissionState()
    }
}
```

### Voice UI Components

**Purpose**: Provides reusable Compose UI components for voice interaction including animated voice input button, voice level visualizer, and transcription display with real-time updates.

```kotlin
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.sin

@Composable
fun VoiceInputButton(
    modifier: Modifier = Modifier,
    isListening: Boolean,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    enabled: Boolean = true,
    size: VoiceButtonSize = VoiceButtonSize.LARGE
) {
    val haptic = LocalHapticFeedback.current
    
    // Animation for listening state
    val infiniteTransition = rememberInfiniteTransition(label = "voice_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    val buttonScale = if (isListening) scale else 1f
    val buttonColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant
        isListening -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    
    val icon = when {
        !enabled -> Icons.Default.MicOff
        isListening -> Icons.Default.Stop
        else -> Icons.Default.Mic
    }
    
    Box(
        modifier = modifier
            .size(size.dp)
            .scale(buttonScale)
            .clip(CircleShape)
            .background(buttonColor)
            .clickable(enabled = enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                if (isListening) {
                    onStopListening()
                } else {
                    onStartListening()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = if (isListening) "Stop listening" else "Start listening",
            tint = Color.White,
            modifier = Modifier.size(size.iconSize)
        )
    }
}

@Composable
fun VoiceLevelVisualizer(
    modifier: Modifier = Modifier,
    audioLevel: Float, // 0.0 to 1.0
    isActive: Boolean
) {
    val animatedLevel by animateFloatAsState(
        targetValue = if (isActive) audioLevel else 0f,
        animationSpec = tween(100),
        label = "audio_level"
    )
    
    Canvas(modifier = modifier.fillMaxWidth().height(60.dp)) {
        val barCount = 20
        val barWidth = size.width / (barCount * 2)
        val maxBarHeight = size.height * 0.8f
        
        for (i in 0 until barCount) {
            val x = i * (barWidth * 2) + barWidth / 2
            val frequency = (i + 1) * 0.5f
            val amplitude = sin(animatedLevel * frequency * Math.PI).toFloat()
            val barHeight = maxBarHeight * amplitude * animatedLevel
            
            drawBar(
                x = x,
                barHeight = barHeight,
                barWidth = barWidth,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun DrawScope.drawBar(
    x: Float,
    barHeight: Float,
    barWidth: Float,
    color: Color
) {
    val y = (size.height - barHeight) / 2
    drawRect(
        color = color,
        topLeft = Offset(x, y),
        size = androidx.compose.ui.geometry.Size(barWidth, barHeight)
    )
}

@Composable
fun TranscriptionDisplay(
    modifier: Modifier = Modifier,
    recognitionState: SpeechRecognitionManager.RecognitionState,
    onRetry: () -> Unit = {}
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        AnimatedContent(
            targetState = recognitionState,
            transitionSpec = {
                fadeIn() with fadeOut()
            },
            label = "transcription_state"
        ) { state ->
            when (state) {
                is SpeechRecognitionManager.RecognitionState.Idle -> {
                    EmptyTranscription()
                }
                
                is SpeechRecognitionManager.RecognitionState.Listening -> {
                    ListeningIndicator()
                }
                
                is SpeechRecognitionManager.RecognitionState.Recognizing -> {
                    PartialTranscription(text = state.partialResult)
                }
                
                is SpeechRecognitionManager.RecognitionState.Result -> {
                    FinalTranscription(
                        text = state.text,
                        confidence = state.confidence
                    )
                }
                
                is SpeechRecognitionManager.RecognitionState.Error -> {
                    ErrorDisplay(
                        error = state.error,
                        onRetry = onRetry
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyTranscription() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Tap the microphone to start",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ListeningIndicator() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "Listening...",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun PartialTranscription(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun FinalTranscription(text: String, confidence: Float) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        if (confidence < 0.8f) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Low confidence",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun ErrorDisplay(
    error: SpeechRecognitionManager.RecognitionError,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val errorMessage = when (error) {
            is SpeechRecognitionManager.RecognitionError.NoPermission -> 
                "Microphone permission required"
            is SpeechRecognitionManager.RecognitionError.NoInternet -> 
                "No internet connection"
            is SpeechRecognitionManager.RecognitionError.NoMatch -> 
                "Couldn't understand. Please try again."
            is SpeechRecognitionManager.RecognitionError.AudioCapture -> 
                "Microphone error"
            is SpeechRecognitionManager.RecognitionError.Unknown -> 
                error.message
        }
        
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        TextButton(onClick = onRetry) {
            Text("Try Again")
        }
    }
}

enum class VoiceButtonSize(val dp: Int, val iconSize: androidx.compose.ui.unit.Dp) {
    SMALL(48, 24.dp),
    MEDIUM(56, 28.dp),
    LARGE(64, 32.dp)
}

// Voice command help dialog
@Composable
fun VoiceCommandHelpDialog(
    commands: List<String>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Voice Commands")
        },
        text = {
            Column {
                Text(
                    text = "Say any of these commands:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                commands.forEach { command ->
                    Text(
                        text = "• $command",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it")
            }
        }
    )
}

// Floating voice input FAB
@Composable
fun VoiceInputFab(
    modifier: Modifier = Modifier,
    voiceEnabled: Boolean,
    isListening: Boolean,
    onVoiceInput: () -> Unit
) {
    AnimatedVisibility(
        visible = voiceEnabled,
        enter = scaleIn() + fadeIn(),
        exit = scaleOut() + fadeOut(),
        modifier = modifier
    ) {
        FloatingActionButton(
            onClick = onVoiceInput,
            containerColor = if (isListening) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            }
        ) {
            Icon(
                imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = "Voice input"
            )
        }
    }
}
```

### Voice Feedback System

**Purpose**: Provides audio and haptic feedback for voice interactions, manages TTS responses for Claude messages, and coordinates with notification system for background audio announcements.

```kotlin
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceFeedbackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ttsManager: TextToSpeechManager,
    private val voiceSettingsManager: VoiceSettingsManager
) {
    
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    
    sealed class FeedbackType {
        object CommandRecognized : FeedbackType()
        object CommandExecuted : FeedbackType()
        object Error : FeedbackType()
        object MessageReceived : FeedbackType()
        data class Announcement(val text: String, val priority: TextToSpeechManager.SpeechPriority) : FeedbackType()
    }
    
    suspend fun provideFeedback(type: FeedbackType) {
        when (type) {
            is FeedbackType.CommandRecognized -> {
                provideHapticFeedback(HapticPattern.LIGHT_TICK)
                if (voiceSettingsManager.isAudioFeedbackEnabled()) {
                    playSound(SoundEffect.COMMAND_RECOGNIZED)
                }
            }
            
            is FeedbackType.CommandExecuted -> {
                provideHapticFeedback(HapticPattern.SUCCESS)
                if (voiceSettingsManager.isAudioFeedbackEnabled()) {
                    playSound(SoundEffect.SUCCESS)
                }
            }
            
            is FeedbackType.Error -> {
                provideHapticFeedback(HapticPattern.ERROR)
                if (voiceSettingsManager.isAudioFeedbackEnabled()) {
                    playSound(SoundEffect.ERROR)
                }
            }
            
            is FeedbackType.MessageReceived -> {
                provideHapticFeedback(HapticPattern.NOTIFICATION)
                if (voiceSettingsManager.isAudioFeedbackEnabled()) {
                    playSound(SoundEffect.MESSAGE)
                }
            }
            
            is FeedbackType.Announcement -> {
                if (voiceSettingsManager.isVoiceAnnouncementsEnabled()) {
                    ttsManager.speak(type.text, type.priority).collect()
                }
            }
        }
    }
    
    fun speakClaudeResponse(
        response: String,
        projectName: String,
        isTaskComplete: Boolean = false
    ): Flow<TextToSpeechManager.TtsState> = flow {
        if (!voiceSettingsManager.shouldReadClaudeResponses()) {
            return@flow
        }
        
        // Check if response contains <speak> tags for smart summarization
        val speakTagPattern = Regex("<speak>(.*?)</speak>", RegexOption.DOT_MATCHES_ALL)
        val speakMatch = speakTagPattern.find(response)
        
        val textToSpeak = if (speakMatch != null) {
            // Use the content within <speak> tags
            speakMatch.groupValues[1].trim()
        } else if (isTaskComplete && voiceSettingsManager.isSmartSummarizationEnabled()) {
            // For task completion without speak tags, use a short summary
            summarizeTaskCompletion(response)
        } else {
            // Process the full text for speech
            processTextForSpeech(response)
        }
        
        val introduction = if (isTaskComplete) "Task complete. Claude says:" else "Claude says:"
        
        // Speak introduction
        ttsManager.speak(introduction, TextToSpeechManager.SpeechPriority.HIGH)
            .collect { emit(it) }
        
        // Speak response
        ttsManager.speak(textToSpeak, TextToSpeechManager.SpeechPriority.NORMAL)
            .collect { emit(it) }
    }
    
    private fun summarizeTaskCompletion(response: String): String {
        // Extract key completion indicators
        val completionPatterns = listOf(
            Regex("(?:created|added|updated|fixed|implemented|completed)\\s+(.+?)(?:\\.|,|$)", RegexOption.IGNORE_CASE),
            Regex("(?:successfully)\\s+(.+?)(?:\\.|,|$)", RegexOption.IGNORE_CASE),
            Regex("✅\\s*(.+?)(?:\\.|,|$)"),
            Regex("Done[:\\s]+(.+?)(?:\\.|,|$)", RegexOption.IGNORE_CASE)
        )
        
        val summaryParts = mutableListOf<String>()
        
        for (pattern in completionPatterns) {
            pattern.findAll(response).forEach { match ->
                val summary = match.groupValues[1].trim()
                if (summary.length < 100) {
                    summaryParts.add(summary)
                }
            }
        }
        
        return if (summaryParts.isNotEmpty()) {
            summaryParts.take(3).joinToString(". ") + "."
        } else {
            // Fallback to first sentence or first 100 chars
            response.split('.').firstOrNull()?.trim()?.plus(".") 
                ?: response.take(100) + "..."
        }
    }
    
    private fun processTextForSpeech(text: String): String {
        return text
            // Remove code blocks
            .replace(Regex("```[\\s\\S]*?```"), "code block")
            // Remove inline code
            .replace(Regex("`[^`]+`"), "code")
            // Remove URLs
            .replace(Regex("https?://[^\\s]+"), "link")
            // Remove excessive punctuation
            .replace(Regex("[.]{2,}"), ".")
            // Limit length
            .take(500)
    }
    
    private suspend fun provideHapticFeedback(pattern: HapticPattern) {
        if (!voiceSettingsManager.isHapticFeedbackEnabled()) return
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = when (pattern) {
                HapticPattern.LIGHT_TICK -> VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE)
                HapticPattern.SUCCESS -> VibrationEffect.createWaveform(longArrayOf(0, 50, 50, 50), -1)
                HapticPattern.ERROR -> VibrationEffect.createWaveform(longArrayOf(0, 100, 100, 100), -1)
                HapticPattern.NOTIFICATION -> VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
            }
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            when (pattern) {
                HapticPattern.LIGHT_TICK -> vibrator.vibrate(10)
                HapticPattern.SUCCESS -> vibrator.vibrate(longArrayOf(0, 50, 50, 50), -1)
                HapticPattern.ERROR -> vibrator.vibrate(longArrayOf(0, 100, 100, 100), -1)
                HapticPattern.NOTIFICATION -> vibrator.vibrate(50)
            }
        }
    }
    
    private fun playSound(effect: SoundEffect) {
        // Sound effects would be implemented using MediaPlayer or SoundPool
        // This is a placeholder for the actual implementation
    }
    
    enum class HapticPattern {
        LIGHT_TICK,
        SUCCESS,
        ERROR,
        NOTIFICATION
    }
    
    enum class SoundEffect {
        COMMAND_RECOGNIZED,
        SUCCESS,
        ERROR,
        MESSAGE
    }
}
```

### Voice Settings Manager

**Purpose**: Manages user preferences for voice features including language selection, speech rate, voice selection, and feature toggles. Persists settings and provides reactive updates.

```kotlin
import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

val Context.voiceDataStore by preferencesDataStore(name = "voice_settings")

@Singleton
class VoiceSettingsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    private object PreferenceKeys {
        val VOICE_ENABLED = booleanPreferencesKey("voice_enabled")
        val PREFERRED_LANGUAGE = stringPreferencesKey("preferred_language")
        val SPEECH_RATE = floatPreferencesKey("speech_rate")
        val PITCH = floatPreferencesKey("pitch")
        val PREFERRED_VOICE = stringPreferencesKey("preferred_voice")
        val READ_CLAUDE_RESPONSES = booleanPreferencesKey("read_claude_responses")
        val VOICE_ANNOUNCEMENTS = booleanPreferencesKey("voice_announcements")
        val AUDIO_FEEDBACK = booleanPreferencesKey("audio_feedback")
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        val WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
        val CONTINUOUS_CONVERSATION = booleanPreferencesKey("continuous_conversation")
        val SMART_SUMMARIZATION = booleanPreferencesKey("smart_summarization")
    }
    
    data class VoiceSettings(
        val voiceEnabled: Boolean = true,
        val preferredLanguage: String = Locale.getDefault().toLanguageTag(),
        val speechRate: Float = 1.0f,
        val pitch: Float = 1.0f,
        val preferredVoice: String? = null,
        val readClaudeResponses: Boolean = false,
        val voiceAnnouncements: Boolean = true,
        val audioFeedback: Boolean = true,
        val hapticFeedback: Boolean = true,
        val wakeWordEnabled: Boolean = false,
        val continuousConversation: Boolean = false,
        val smartSummarization: Boolean = true
    )
    
    val settings: Flow<VoiceSettings> = context.voiceDataStore.data
        .map { preferences ->
            VoiceSettings(
                voiceEnabled = preferences[PreferenceKeys.VOICE_ENABLED] ?: true,
                preferredLanguage = preferences[PreferenceKeys.PREFERRED_LANGUAGE] ?: Locale.getDefault().toLanguageTag(),
                speechRate = preferences[PreferenceKeys.SPEECH_RATE] ?: 1.0f,
                pitch = preferences[PreferenceKeys.PITCH] ?: 1.0f,
                preferredVoice = preferences[PreferenceKeys.PREFERRED_VOICE],
                readClaudeResponses = preferences[PreferenceKeys.READ_CLAUDE_RESPONSES] ?: false,
                voiceAnnouncements = preferences[PreferenceKeys.VOICE_ANNOUNCEMENTS] ?: true,
                audioFeedback = preferences[PreferenceKeys.AUDIO_FEEDBACK] ?: true,
                hapticFeedback = preferences[PreferenceKeys.HAPTIC_FEEDBACK] ?: true,
                wakeWordEnabled = preferences[PreferenceKeys.WAKE_WORD_ENABLED] ?: false,
                continuousConversation = preferences[PreferenceKeys.CONTINUOUS_CONVERSATION] ?: false,
                smartSummarization = preferences[PreferenceKeys.SMART_SUMMARIZATION] ?: true
            )
        }
    
    suspend fun setVoiceEnabled(enabled: Boolean) {
        context.voiceDataStore.edit { preferences ->
            preferences[PreferenceKeys.VOICE_ENABLED] = enabled
        }
    }
    
    suspend fun setPreferredLanguage(language: String) {
        context.voiceDataStore.edit { preferences ->
            preferences[PreferenceKeys.PREFERRED_LANGUAGE] = language
        }
    }
    
    suspend fun getPreferredLanguage(): String {
        return settings.map { it.preferredLanguage }.first()
    }
    
    suspend fun getPreferredLocale(): Locale {
        val languageTag = getPreferredLanguage()
        return Locale.forLanguageTag(languageTag)
    }
    
    suspend fun setSpeechRate(rate: Float) {
        context.voiceDataStore.edit { preferences ->
            preferences[PreferenceKeys.SPEECH_RATE] = rate.coerceIn(0.5f, 2.0f)
        }
    }
    
    suspend fun getSpeechRate(): Float {
        return settings.map { it.speechRate }.first()
    }
    
    suspend fun setPitch(pitch: Float) {
        context.voiceDataStore.edit { preferences ->
            preferences[PreferenceKeys.PITCH] = pitch.coerceIn(0.5f, 2.0f)
        }
    }
    
    suspend fun getPitch(): Float {
        return settings.map { it.pitch }.first()
    }
    
    suspend fun setPreferredVoice(voiceId: String) {
        context.voiceDataStore.edit { preferences ->
            preferences[PreferenceKeys.PREFERRED_VOICE] = voiceId
        }
    }
    
    suspend fun setReadClaudeResponses(enabled: Boolean) {
        context.voiceDataStore.edit { preferences ->
            preferences[PreferenceKeys.READ_CLAUDE_RESPONSES] = enabled
        }
    }
    
    suspend fun shouldReadClaudeResponses(): Boolean {
        return settings.map { it.readClaudeResponses }.first()
    }
    
    suspend fun setVoiceAnnouncementsEnabled(enabled: Boolean) {
        context.voiceDataStore.edit { preferences ->
            preferences[PreferenceKeys.VOICE_ANNOUNCEMENTS] = enabled
        }
    }
    
    suspend fun isVoiceAnnouncementsEnabled(): Boolean {
        return settings.map { it.voiceAnnouncements }.first()
    }
    
    suspend fun setAudioFeedbackEnabled(enabled: Boolean) {
        context.voiceDataStore.edit { preferences ->
            preferences[PreferenceKeys.AUDIO_FEEDBACK] = enabled
        }
    }
    
    suspend fun isAudioFeedbackEnabled(): Boolean {
        return settings.map { it.audioFeedback }.first()
    }
    
    suspend fun setHapticFeedbackEnabled(enabled: Boolean) {
        context.voiceDataStore.edit { preferences ->
            preferences[PreferenceKeys.HAPTIC_FEEDBACK] = enabled
        }
    }
    
    suspend fun isHapticFeedbackEnabled(): Boolean {
        return settings.map { it.hapticFeedback }.first()
    }
    
    suspend fun setWakeWordEnabled(enabled: Boolean) {
        context.voiceDataStore.edit { preferences ->
            preferences[PreferenceKeys.WAKE_WORD_ENABLED] = enabled
        }
    }
    
    suspend fun setContinuousConversation(enabled: Boolean) {
        context.voiceDataStore.edit { preferences ->
            preferences[PreferenceKeys.CONTINUOUS_CONVERSATION] = enabled
        }
    }
    
    suspend fun setSmartSummarizationEnabled(enabled: Boolean) {
        context.voiceDataStore.edit { preferences ->
            preferences[PreferenceKeys.SMART_SUMMARIZATION] = enabled
        }
    }
    
    suspend fun isSmartSummarizationEnabled(): Boolean {
        return settings.map { it.smartSummarization }.first()
    }
    
    suspend fun resetToDefaults() {
        context.voiceDataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
```

### Error Handling

**Purpose**: Provides comprehensive error handling for voice operations including permission errors, network issues, recognition failures, and TTS errors with appropriate user feedback and recovery options.

```kotlin
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceErrorHandler @Inject constructor(
    private val analyticsTracker: AnalyticsTracker,
    private val crashReporter: CrashReporter
) {
    
    sealed class VoiceError : Exception() {
        // Permission errors
        object MicrophonePermissionDenied : VoiceError()
        object MicrophonePermissionPermanentlyDenied : VoiceError()
        
        // Recognition errors
        data class RecognitionFailed(val reason: String) : VoiceError()
        object RecognitionTimeout : VoiceError()
        object NoSpeechDetected : VoiceError()
        object RecognitionServiceUnavailable : VoiceError()
        
        // TTS errors
        data class TtsInitializationFailed(val reason: String) : VoiceError()
        object TtsLanguageNotSupported : VoiceError()
        object TtsVoiceNotAvailable : VoiceError()
        
        // Network errors
        object OfflineRecognitionUnavailable : VoiceError()
        object NetworkRequired : VoiceError()
        
        // Processing errors
        data class CommandProcessingFailed(val command: String, val reason: String) : VoiceError()
        object ContextUnavailable : VoiceError()
        
        // Wake word errors
        object WakeWordServiceFailed : VoiceError()
        
        // Unknown errors
        data class Unknown(override val message: String) : VoiceError()
    }
    
    fun handleError(error: VoiceError): ErrorRecovery {
        // Log error
        logError(error)
        
        // Track analytics
        trackError(error)
        
        // Determine recovery strategy
        return when (error) {
            is VoiceError.MicrophonePermissionDenied -> {
                ErrorRecovery.RequestPermission(
                    message = "Microphone access is required for voice features"
                )
            }
            
            is VoiceError.MicrophonePermissionPermanentlyDenied -> {
                ErrorRecovery.OpenSettings(
                    message = "Please enable microphone access in settings"
                )
            }
            
            is VoiceError.RecognitionTimeout,
            is VoiceError.NoSpeechDetected -> {
                ErrorRecovery.Retry(
                    message = "No speech detected. Please try again."
                )
            }
            
            is VoiceError.RecognitionServiceUnavailable -> {
                ErrorRecovery.Unavailable(
                    message = "Speech recognition is not available on this device"
                )
            }
            
            is VoiceError.NetworkRequired,
            is VoiceError.OfflineRecognitionUnavailable -> {
                ErrorRecovery.CheckNetwork(
                    message = "Internet connection required for voice features"
                )
            }
            
            is VoiceError.TtsLanguageNotSupported -> {
                ErrorRecovery.ChangeSettings(
                    message = "Selected language is not supported. Please choose another."
                )
            }
            
            is VoiceError.CommandProcessingFailed -> {
                ErrorRecovery.Retry(
                    message = "Couldn't process command: ${error.reason}"
                )
            }
            
            is VoiceError.Unknown -> {
                crashReporter.reportCrash(error)
                ErrorRecovery.ShowError(
                    message = "An unexpected error occurred"
                )
            }
            
            else -> {
                ErrorRecovery.ShowError(
                    message = "Voice feature error: ${error.javaClass.simpleName}"
                )
            }
        }
    }
    
    private fun logError(error: VoiceError) {
        when (error) {
            is VoiceError.Unknown -> {
                // Log stack trace for unknown errors
                error.printStackTrace()
            }
            else -> {
                // Log error type and details
                println("Voice error: $error")
            }
        }
    }
    
    private fun trackError(error: VoiceError) {
        val eventName = "voice_error"
        val parameters = mapOf(
            "error_type" to error.javaClass.simpleName,
            "error_details" to when (error) {
                is VoiceError.RecognitionFailed -> error.reason
                is VoiceError.CommandProcessingFailed -> error.command
                is VoiceError.Unknown -> error.message
                else -> ""
            }
        )
        
        analyticsTracker.trackEvent(eventName, parameters)
    }
    
    sealed class ErrorRecovery {
        abstract val message: String
        
        data class Retry(override val message: String) : ErrorRecovery()
        data class RequestPermission(override val message: String) : ErrorRecovery()
        data class OpenSettings(override val message: String) : ErrorRecovery()
        data class CheckNetwork(override val message: String) : ErrorRecovery()
        data class ChangeSettings(override val message: String) : ErrorRecovery()
        data class Unavailable(override val message: String) : ErrorRecovery()
        data class ShowError(override val message: String) : ErrorRecovery()
    }
    
    // Resilient voice operation wrapper
    suspend fun <T> withVoiceErrorHandling(
        operation: suspend () -> T,
        onError: (ErrorRecovery) -> Unit = {}
    ): Result<T> {
        return try {
            Result.success(operation())
        } catch (e: VoiceError) {
            val recovery = handleError(e)
            onError(recovery)
            Result.failure(e)
        } catch (e: Exception) {
            val voiceError = VoiceError.Unknown(e.message ?: "Unknown error")
            val recovery = handleError(voiceError)
            onError(recovery)
            Result.failure(voiceError)
        }
    }
}
```

### Integration Points

**Purpose**: Defines how voice features integrate with existing app components and features to provide a seamless voice-enabled experience.

```kotlin
// Integration with Chat feature
interface VoiceChatIntegration {
    fun enableVoiceInput(projectId: String)
    fun readLastMessage(projectId: String)
    fun dictateMessage(): Flow<String>
    suspend fun sendVoiceMessage(projectId: String, audioData: ByteArray)
    
    /**
     * Smart Summarization Protocol:
     * When the user has smart summarization enabled and Claude completes a task,
     * the app will automatically append the following to the user's prompt:
     * 
     * "Also, please provide a brief voice summary of what you did (under 50 words) 
     * wrapped in <speak></speak> tags at the end of your response."
     * 
     * Claude should then include something like:
     * <speak>
     * I successfully created the login screen component with email and password fields, 
     * implemented form validation, and added the authentication logic. The component 
     * is now ready to use in your app.
     * </speak>
     * 
     * The TTS system will automatically detect and read only the content within 
     * the <speak> tags when smart summarization is enabled.
     */
    suspend fun enhancePromptForVoiceSummary(prompt: String, isTaskOriented: Boolean): String
}

// Integration with Quick Actions
interface VoiceQuickActionsIntegration {
    suspend fun executeVoiceCommand(command: String, projectId: String): Result<String>
    fun getAvailableVoiceCommands(projectId: String): List<VoiceCommand>
}

// Integration with Navigation
interface VoiceNavigationIntegration {
    suspend fun navigateByVoice(command: String): Result<Unit>
    fun announceScreenChange(screenName: String)
}

// Integration with Background Services
interface VoiceBackgroundIntegration {
    fun announceNotification(notification: NotificationType, content: String)
    fun handleVoiceCommandFromNotification(command: String)
    suspend fun startWakeWordDetection()
    suspend fun stopWakeWordDetection()
}

// Integration with Settings
interface VoiceSettingsIntegration {
    fun openVoiceSettings()
    fun getVoiceSettingsState(): Flow<VoiceSettings>
}
```

## Testing

### Testing Checklist

```kotlin
/**
 * Voice Integration Testing Checklist
 * 
 * Permission Handling:
 * 1. [ ] Request microphone permission on first use
 * 2. [ ] Handle permission denial gracefully
 * 3. [ ] Show rationale when needed
 * 4. [ ] Redirect to settings for permanently denied
 * 5. [ ] Update UI based on permission state
 * 
 * Speech Recognition:
 * 6. [ ] Start/stop recognition correctly
 * 7. [ ] Show partial results during recognition
 * 8. [ ] Handle recognition errors
 * 9. [ ] Support multiple languages
 * 10. [ ] Work offline when possible
 * 11. [ ] Timeout appropriately
 * 12. [ ] Cancel recognition on navigation
 * 
 * Text-to-Speech:
 * 13. [ ] Initialize TTS engine
 * 14. [ ] Speak text with correct parameters
 * 15. [ ] Handle TTS errors
 * 16. [ ] Respect audio focus
 * 17. [ ] Support voice selection
 * 18. [ ] Adjust speech rate and pitch
 * 19. [ ] Stop speech on user action
 * 
 * Voice Commands:
 * 20. [ ] Recognize navigation commands
 * 21. [ ] Execute project commands
 * 22. [ ] Process chat commands
 * 23. [ ] Handle ambiguous commands
 * 24. [ ] Provide command help
 * 25. [ ] Context-aware interpretation
 * 
 * UI Components:
 * 26. [ ] Voice button animations
 * 27. [ ] Audio level visualization
 * 28. [ ] Transcription display updates
 * 29. [ ] Error state handling
 * 30. [ ] Accessibility compliance
 * 
 * Feedback:
 * 31. [ ] Haptic feedback on actions
 * 32. [ ] Audio feedback when enabled
 * 33. [ ] Visual feedback for states
 * 34. [ ] Read Claude responses
 * 35. [ ] Announce notifications
 * 
 * Settings:
 * 36. [ ] Save/load preferences
 * 37. [ ] Apply settings immediately
 * 38. [ ] Reset to defaults
 * 39. [ ] Language selection works
 * 40. [ ] Voice selection works
 * 
 * Integration:
 * 41. [ ] Chat integration works
 * 42. [ ] Quick actions integration
 * 43. [ ] Navigation integration
 * 44. [ ] Background service integration
 * 45. [ ] Settings integration
 * 
 * Performance:
 * 46. [ ] Fast recognition response
 * 47. [ ] Smooth UI updates
 * 48. [ ] Memory efficiency
 * 49. [ ] Battery efficiency
 * 50. [ ] Background efficiency
 */
```

### Unit Tests

**Purpose**: Example unit tests for voice components demonstrating proper testing patterns for recognition, TTS, and command processing.

```kotlin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class VoiceCommandProcessorTest {
    
    @Mock private lateinit var mockNavigationManager: NavigationManager
    @Mock private lateinit var mockSecureDataRepository: SecureDataRepository
    @Mock private lateinit var mockConnectionManager: ConnectionManager
    @Mock private lateinit var mockChatManager: ChatManager
    
    private lateinit var processor: VoiceCommandProcessor
    private val testScope = TestScope()
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        processor = VoiceCommandProcessor(
            mockNavigationManager,
            mockSecureDataRepository,
            mockConnectionManager,
            mockChatManager
        )
    }
    
    @Test
    fun testNavigationCommands() = testScope.runTest {
        val context = VoiceCommandProcessor.CommandContext(
            currentScreen = "home",
            activeProjectId = null,
            isConnected = false
        )
        
        // Test show projects command
        val showProjectsCommand = processor.processCommand("show projects", context)
        assertEquals(VoiceCommandProcessor.VoiceCommand.ShowProjects, showProjectsCommand)
        
        // Test go back command
        val goBackCommand = processor.processCommand("go back", context)
        assertEquals(VoiceCommandProcessor.VoiceCommand.GoBack, goBackCommand)
        
        // Test go home command
        val goHomeCommand = processor.processCommand("navigate home", context)
        assertEquals(VoiceCommandProcessor.VoiceCommand.GoHome, goHomeCommand)
    }
    
    @Test
    fun testProjectCommands() = testScope.runTest {
        val context = VoiceCommandProcessor.CommandContext(
            currentScreen = "projects",
            activeProjectId = null,
            isConnected = false
        )
        
        // Test open project command
        val openCommand = processor.processCommand("open project My App", context)
        assertTrue(openCommand is VoiceCommandProcessor.VoiceCommand.OpenProject)
        assertEquals("My App", (openCommand as VoiceCommandProcessor.VoiceCommand.OpenProject).projectName)
    }
    
    @Test
    fun testChatCommands() = testScope.runTest {
        val context = VoiceCommandProcessor.CommandContext(
            currentScreen = "chat",
            activeProjectId = "project123",
            isConnected = true
        )
        
        // Test send message command
        val sendCommand = processor.processCommand("tell claude to implement a login screen", context)
        assertTrue(sendCommand is VoiceCommandProcessor.VoiceCommand.SendMessage)
        assertEquals("to implement a login screen", 
            (sendCommand as VoiceCommandProcessor.VoiceCommand.SendMessage).message)
        
        // Test read last message
        val readCommand = processor.processCommand("read last message", context)
        assertEquals(VoiceCommandProcessor.VoiceCommand.ReadLastMessage, readCommand)
    }
    
    @Test
    fun testContextAwareInterpretation() = testScope.runTest {
        val chatContext = VoiceCommandProcessor.CommandContext(
            currentScreen = "chat",
            activeProjectId = "project123",
            isConnected = true
        )
        
        // Plain text in chat screen should be interpreted as message
        val command = processor.processCommand("implement user authentication", chatContext)
        assertTrue(command is VoiceCommandProcessor.VoiceCommand.SendMessage)
        assertEquals("implement user authentication", 
            (command as VoiceCommandProcessor.VoiceCommand.SendMessage).message)
    }
    
    @Test
    fun testCommandExecution() = testScope.runTest {
        val context = VoiceCommandProcessor.CommandContext(
            currentScreen = "home",
            activeProjectId = null,
            isConnected = false
        )
        
        // Test navigation execution
        val result = processor.executeCommand(
            VoiceCommandProcessor.VoiceCommand.ShowProjects,
            context
        )
        
        assertTrue(result.isSuccess)
        assertEquals("Showing projects", result.getOrNull())
        verify(mockNavigationManager).navigateToProjects()
    }
}

@ExperimentalCoroutinesApi
class SpeechRecognitionManagerTest {
    
    @Mock private lateinit var mockContext: Context
    @Mock private lateinit var mockAudioPermissionHandler: AudioPermissionHandler
    @Mock private lateinit var mockVoiceSettingsManager: VoiceSettingsManager
    
    private lateinit var recognitionManager: SpeechRecognitionManager
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        
        whenever(mockAudioPermissionHandler.hasAudioPermission()).thenReturn(true)
        whenever(mockVoiceSettingsManager.getPreferredLanguage()).thenReturn("en-US")
        
        recognitionManager = SpeechRecognitionManager(
            mockContext,
            mockAudioPermissionHandler,
            mockVoiceSettingsManager
        )
    }
    
    @Test
    fun testPermissionCheck() = runTest {
        whenever(mockAudioPermissionHandler.hasAudioPermission()).thenReturn(false)
        
        val result = recognitionManager.startRecognition().first()
        
        assertTrue(result is SpeechRecognitionManager.RecognitionState.Error)
        assertEquals(
            SpeechRecognitionManager.RecognitionError.NoPermission,
            (result as SpeechRecognitionManager.RecognitionState.Error).error
        )
    }
}
```

### Integration Tests

**Purpose**: Integration tests demonstrating full voice flow with UI interactions, background services, and end-to-end voice command execution.

```kotlin
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VoiceIntegrationTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun testVoiceInputFlow() {
        composeTestRule.setContent {
            // Set up test UI with voice components
            VoiceEnabledChatScreen()
        }
        
        // Find and click voice button
        composeTestRule
            .onNodeWithContentDescription("Start listening")
            .performClick()
        
        // Verify listening state
        composeTestRule
            .onNodeWithText("Listening...")
            .assertIsDisplayed()
        
        // Simulate recognition result
        // This would require mocking the recognition service
        
        // Verify transcription display
        composeTestRule
            .onNodeWithText("open project my app")
            .assertIsDisplayed()
    }
    
    @Test
    fun testVoiceCommandExecution() = runTest {
        // Test end-to-end voice command flow
        // This would involve:
        // 1. Setting up the full app state
        // 2. Triggering voice recognition
        // 3. Processing the command
        // 4. Verifying navigation/action occurred
    }
}
```

## Implementation Notes

### Critical Implementation Details

1. **Permission Flow**: Always check and request RECORD_AUDIO permission before any voice operation
2. **Audio Focus**: Properly manage audio focus for TTS to respect other apps
3. **Recognition Lifecycle**: Cancel recognition on screen changes to prevent memory leaks
4. **TTS Initialization**: Initialize TTS engine early but lazily to avoid startup delay
5. **Background Restrictions**: Wake word detection requires foreground service on Android 9+
6. **Language Support**: Always provide fallback to device default language
7. **Offline Support**: Implement offline recognition fallback where available
8. **Battery Impact**: Disable continuous features when battery is low

### Performance Considerations (Android-Specific)

1. **Recognition Efficiency**:
   - Use command mode for short inputs (faster, less battery)
   - Limit continuous recognition to active conversation
   - Cancel recognition immediately when not needed

2. **TTS Optimization**:
   - Pre-warm TTS engine when voice features enabled
   - Cache frequently spoken phrases
   - Use lower quality voices on low-end devices

3. **Memory Management**:
   - Release recognition/TTS resources on low memory
   - Limit audio buffer sizes
   - Clear transcription history periodically

4. **Battery Optimization**:
   - Disable wake word detection on battery saver
   - Reduce recognition frequency in background
   - Use efficient audio processing libraries

### Package Structure

```
com.pocketagent.voice/
├── recognition/
│   ├── SpeechRecognitionManager.kt
│   ├── RecognitionModels.kt
│   └── OfflineRecognitionProvider.kt
├── synthesis/
│   ├── TextToSpeechManager.kt
│   ├── TtsModels.kt
│   └── VoiceDownloader.kt
├── commands/
│   ├── VoiceCommandProcessor.kt
│   ├── CommandPatterns.kt
│   └── CommandExecutor.kt
├── ui/
│   ├── VoiceInputButton.kt
│   ├── TranscriptionDisplay.kt
│   ├── VoiceLevelVisualizer.kt
│   └── VoiceSettingsScreen.kt
├── feedback/
│   ├── VoiceFeedbackController.kt
│   ├── HapticManager.kt
│   └── AudioEffects.kt
├── permissions/
│   ├── AudioPermissionHandler.kt
│   └── PermissionUI.kt
├── settings/
│   ├── VoiceSettingsManager.kt
│   └── VoicePreferences.kt
├── wakeword/
│   ├── WakeWordDetector.kt
│   └── WakeWordService.kt
├── integration/
│   ├── VoiceChatIntegration.kt
│   ├── VoiceNavigationIntegration.kt
│   └── VoiceBackgroundIntegration.kt
└── di/
    └── VoiceModule.kt
```

### Future Extensions (Android Mobile Focus)

1. **Advanced Voice Features**:
   - Custom wake words
   - Voice biometrics for security
   - Multi-language conversation
   - Emotion detection
   - Background voice memos

2. **Enhanced Commands**:
   - Complex multi-step commands
   - Conditional commands
   - Command macros
   - Voice shortcuts
   - Custom command training

3. **Accessibility**:
   - Full screen reader integration
   - Voice-only navigation mode
   - Audio descriptions
   - Voice tutorials
   - Gesture alternatives

4. **Platform Integration**:
   - Google Assistant integration
   - Android Auto support
   - Wear OS companion
   - Quick Settings tile
   - Widget voice actions

5. **AI Enhancements**:
   - Natural language understanding
   - Context prediction
   - Personalized responses
   - Voice style matching
   - Conversation summarization