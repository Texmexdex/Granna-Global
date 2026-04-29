package com.sovereignvoice.granna

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.sovereignvoice.granna.databinding.ActivityDialerBinding

import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

/**
 * Full-screen dialer activity.
 * Shows when the button is pressed — big text, high contrast.
 * Uses Android's native SpeechRecognizer (Google engine).
 */
class DialerActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityDialerBinding
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var tts: TextToSpeech
    private lateinit var contactHelper: ContactHelper
    private var mediaPlayer: MediaPlayer? = null

    private var pendingContact: ContactHelper.Contact? = null
    private var isListeningForConfirmation = false

    private val handler = Handler(Looper.getMainLooper())
    private var isActive = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isActive = true

        // Configure activity to show over lock screen and wake up
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityDialerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup TTS first
        tts = TextToSpeech(this, this)
        
        contactHelper = ContactHelper(this)
        setupSpeech()

        // Cancel button
        binding.btnCancel.setOnClickListener { finish() }

        // Don't call startListening() here - wait for TTS onInit to play the greeting
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
            tts.setSpeechRate(0.9f) // Slightly faster for less gap
            
            // Simplified voice search to avoid startup lag
            val maleVoice = tts.voices?.find { it.name.lowercase().contains("male") || it.name.lowercase().contains("guy") }
            if (maleVoice != null) {
                tts.voice = maleVoice
            }
            
            playCustomAudioOrSpeak(R.raw.who_to_call, "Who would you like to call?")
        }
    }

    private fun setupSpeech() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                binding.tvStatus.text = "Listening..."
                if (isListeningForConfirmation) {
                    binding.tvInstruction.text = "Say Yes or No"
                } else {
                    binding.tvInstruction.text = "Who would you like to call?"
                }
            }

            override fun onResults(results: Bundle?) {
                if (!isActive) return
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    handleSpeechResult(matches)
                } else {
                    showError("Didn't catch that.")
                    binding.btnRetry.visibility = android.view.View.VISIBLE
                    binding.btnRetry.setOnClickListener { startListening() }
                }
            }

            override fun onError(error: Int) {
                if (!isActive) return
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    else -> "Error $error"
                }
                showError(msg)
                binding.btnRetry.visibility = android.view.View.VISIBLE
                binding.btnRetry.setOnClickListener { startListening() }
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { binding.tvStatus.text = "Processing..." }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            showError("Microphone permission needed")
            return
        }

        binding.tvStatus.text = "Ready..."
        if (!isListeningForConfirmation) {
            pendingContact = null
            binding.tvInstruction.text = "Who would you like to call?"
            binding.btnConfirm.visibility = android.view.View.GONE
            binding.btnRetry.visibility = android.view.View.GONE
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            
            // Balanced settings: allow for slower speech without the "hang"
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L) 
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000L)
        }
        speechRecognizer.startListening(intent)
    }

    private fun handleSpeechResult(matches: List<String>) {
        if (isListeningForConfirmation) {
            handleConfirmationResult(matches)
            return
        }

        // Try each result against contacts until we find a match
        for (heard in matches) {
            val contact = contactHelper.findBestMatch(heard)
            if (contact != null) {
                pendingContact = contact
                showConfirmation(contact, heard)
                return
            }
        }
        // No match found
        showError("Didn't recognise \"${matches.first()}\"")
        binding.btnRetry.visibility = android.view.View.VISIBLE
        binding.btnRetry.setOnClickListener { startListening() }
    }

    private fun handleConfirmationResult(matches: List<String>) {
        val positive = listOf("yes", "yeah", "yep", "correct", "call", "ok")
        val negative = listOf("no", "nope", "cancel", "stop", "wrong")

        for (heard in matches) {
            val word = heard.lowercase()
            if (positive.any { word.contains(it) }) {
                isListeningForConfirmation = false
                pendingContact?.let { dialContact(it) }
                return
            }
            if (negative.any { word.contains(it) }) {
                isListeningForConfirmation = false
                // Play "Who to call" audio instead of plain TTS
                playCustomAudioOrSpeak(R.raw.who_to_call, "Who would you like to call?")
                return
            }
        }
        
        // If nothing matched, ask again
        speakChainedConfirmation(pendingContact?.name ?: "this contact", isRetry = true)
    }

    private fun playCustomAudioOrSpeak(rawResId: Int, fallbackText: String, onComplete: (() -> Unit)? = null) {
        try {
            // Log for debugging
            val resName = resources.getResourceEntryName(rawResId)
            Log.d("DialerActivity", "Attempting to play audio: $resName")
            
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(this, rawResId)
            
            if (mediaPlayer != null) {
                mediaPlayer?.setOnCompletionListener {
                    it.release()
                    mediaPlayer = null
                    if (onComplete != null) onComplete() else startListening()
                }
                mediaPlayer?.start()
            } else {
                Log.w("DialerActivity", "MediaPlayer.create returned null for $resName, using fallback")
                speak(fallbackText, onComplete)
            }
        } catch (e: Exception) {
            Log.e("DialerActivity", "Error in playCustomAudioOrSpeak", e)
            speak(fallbackText, onComplete)
        }
    }

    private fun speak(text: String, onComplete: (() -> Unit)? = null) {
        if (!::tts.isInitialized) return
        
        tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                handler.post { if (isActive) onComplete?.invoke() ?: startListening() }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                handler.post { if (isActive) startListening() }
            }
        })

        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "message")
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 0.5f) 
        
        // Use QUEUE_ADD to minimize transition gaps
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "message")
    }

    private fun speakChainedConfirmation(name: String, isRetry: Boolean = false) {
        val prefixText = if (isRetry) "I didn't catch that. You want to call" else "You want to call"
        
        // Step 1: Play "You want to call..." (Custom Audio or Fallback)
        playCustomAudioOrSpeak(R.raw.call_prefix, prefixText) {
            // Step 2: Speak the Name (Male TTS)
            speak(name) {
                // Step 3: Play "...is that correct?" (Custom Audio or Fallback)
                playCustomAudioOrSpeak(R.raw.is_that_correct, "is that correct?") {
                    // Step 4: Final Listening
                    startListening()
                }
            }
        }
    }

    private fun showConfirmation(contact: ContactHelper.Contact, heard: String) {
        if (!isActive) return
        binding.tvInstruction.text = "Call ${contact.name}?"
        binding.tvStatus.text = "I heard: \"$heard\""
        binding.btnConfirm.visibility = android.view.View.VISIBLE
        binding.btnRetry.visibility = android.view.View.VISIBLE

        binding.btnConfirm.setOnClickListener { 
            isListeningForConfirmation = false
            dialContact(contact) 
        }
        binding.btnRetry.setOnClickListener { 
            isListeningForConfirmation = false
            startListening() 
        }

        isListeningForConfirmation = true
        speakChainedConfirmation(contact.name)
    }

    private fun dialContact(contact: ContactHelper.Contact) {
        if (!isActive) return
        binding.tvInstruction.text = "Calling ${contact.name}..."
        binding.tvStatus.text = contact.number
        binding.btnConfirm.visibility = android.view.View.GONE
        binding.btnRetry.visibility = android.view.View.GONE

        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${contact.number}"))
        startActivity(intent)

        // Close dialer after a moment
        handler.postDelayed({ if (isActive) finish() }, 2000)
    }

    private fun showError(msg: String) {
        binding.tvStatus.text = msg
    }

    override fun onPause() {
        super.onPause()
        isActive = false
        handler.removeCallbacksAndMessages(null)
        speechRecognizer.stopListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        isActive = false
        handler.removeCallbacksAndMessages(null)
        mediaPlayer?.release()
        speechRecognizer.destroy()
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
    }
}
