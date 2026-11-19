package com.subnetbyte

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.telecom.TelecomManager
import java.util.Locale

/**
 * User-started microphone foreground service. It can keep listening while the
 * activity is backgrounded / the screen is locked. Android still controls when
 * microphone foreground services may start and force-stop always stops the app.
 */
class WakeWordService : Service(), TextToSpeech.OnInitListener {
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var running = false
    private var awaitingCommand = false
    private var awaitingContactChoice = false
    private var retryCount = 0
    private var pendingContacts: List<PhoneMatch> = emptyList()
    private lateinit var learning: LearningStore
    private lateinit var engine: CommandEngine

    companion object {
        const val ACTION_START = "com.subnetbyte.action.START_WAKE"
        const val ACTION_STOP = "com.subnetbyte.action.STOP_WAKE"
        const val ACTION_STATUS = "com.subnetbyte.action.STATUS"
        const val EXTRA_STATUS = "status"
        private const val CHANNEL_ID = "subnet_voice_assistant"
        private const val NOTIFICATION_ID = 4107
        private const val STATE_PREFS = "subnet_service_state"
    }

    override fun onCreate() {
        super.onCreate()
        learning = LearningStore(this)
        engine = CommandEngine(learning)
        tts = TextToSpeech(this, this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopListening()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    publishStatus("Microphone permission is missing. Open Subnet and grant it.")
                    stopSelf()
                    return START_NOT_STICKY
                }
                try {
                    if (Build.VERSION.SDK_INT >= 29) {
                        startForeground(NOTIFICATION_ID, buildNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                    } else {
                        startForeground(NOTIFICATION_ID, buildNotification())
                    }
                    running = true
                    getSharedPreferences(STATE_PREFS, MODE_PRIVATE).edit().putBoolean("running", true).apply()
                    publishStatus("Subnet is listening for Hey Subnet or Hey Bro. Screen may be locked.")
                    startWakeRecognition()
                } catch (e: Exception) {
                    publishStatus("Could not start background listening: ${e.localizedMessage ?: "Android blocked microphone access"}")
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val languageResult = tts?.setLanguage(Locale.US) ?: TextToSpeech.LANG_NOT_SUPPORTED
            ttsReady = languageResult >= TextToSpeech.LANG_AVAILABLE
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 2, Intent(this, WakeWordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        builder.setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Subnet voice assistant is active")
            .setContentText("Listening for Hey Subnet / Hey Bro")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_media_pause), "Stop listening", stopIntent).build())
        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "Subnet voice assistant", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun newRecognizer(): SpeechRecognizer? {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            publishStatus("No speech recognition provider is installed or enabled on this phone.")
            return null
        }
        return try {
            if (Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            } else {
                SpeechRecognizer.createSpeechRecognizer(this)
            }
        } catch (e: Exception) {
            publishStatus("Speech recognition could not start: ${e.localizedMessage ?: "unknown error"}")
            null
        }
    }

    private fun listenForWakeOrReply() {
        if (!running) return
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = newRecognizer() ?: return
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                publishStatus(if (awaitingContactChoice) "Say the contact name or option number." else if (awaitingCommand) "Subnet is listening for your command." else "Listening for Hey Subnet or Hey Bro...")
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { publishStatus("Processing your voice...") }
            override fun onError(error: Int) {
                if (!running) return
                val msg = when (error) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission was denied. Open Subnet to enable it."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech service network error. Checking again."
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> null
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech service is busy. Retrying."
                    SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "Speech service disconnected. Retrying."
                    else -> "Speech recognition interrupted. Retrying."
                }
                if (msg != null) publishStatus(msg)
                retryCount = (retryCount + 1).coerceAtMost(5)
                val delayMs = if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1800L else (700L shl retryCount).coerceAtMost(12000L)
                android.os.Handler(mainLooper).postDelayed({ listenForWakeOrReply() }, delayMs)
            }
            override fun onResults(results: Bundle?) {
                if (!running) return
                retryCount = 0
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().trim()
                if (text.isBlank()) {
                    listenForWakeOrReply()
                    return
                }
                if (awaitingContactChoice) {
                    handleContactChoice(text)
                } else if (awaitingCommand) {
                    awaitingCommand = false
                    handleCommand(text)
                } else {
                    val remainder = stripWakePhrase(text)
                    if (remainder != null) {
                        awaitingCommand = true
                        if (remainder.isNotBlank()) {
                            awaitingCommand = false
                            handleCommand(remainder)
                        } else {
                            say("Yes? I'm listening.") { listenForWakeOrReply() }
                        }
                    } else {
                        listenForWakeOrReply()
                    }
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-US")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
        try {
            recognizer?.startListening(intent)
        } catch (_: Exception) {
            publishStatus("Could not open the microphone. Retrying.")
            recognizer?.destroy()
            recognizer = null
            android.os.Handler(mainLooper).postDelayed({ listenForWakeOrReply() }, 1200)
        }
    }

    private fun stripWakePhrase(text: String): String? {
        val pattern = Regex("\\b(?:hey\\s+sub\\s*net(?:\\s+ai)?|sub\\s*net|hey\\s+bro)\\b", RegexOption.IGNORE_CASE)
        val match = pattern.find(text) ?: return null
        return text.removeRange(match.range).trim().trim(',', '.', '!', '?')
    }

    private fun handleCommand(text: String) {
        publishStatus("You: $text")
        learning.recordTask(text, "voice command")
        when (val command = engine.parse(text)) {
            is AssistantCommand.Call -> findContact(command.contact)
            is AssistantCommand.Choose -> handleContactChoice(text)
            is AssistantCommand.Note -> {
                openFileOutput("notes.txt", MODE_APPEND).bufferedWriter().use { it.append("${System.currentTimeMillis()} | ${command.text}\n") }
                learning.recordTask(text, "note saved locally")
                say("Saved your note on this phone.")
            }
            is AssistantCommand.Teach -> {
                learning.addAlias(command.phrase, command.meaning)
                say("I learned that ${command.phrase} means ${command.meaning}. This is saved locally.")
            }
            is AssistantCommand.OpenApp -> say("To open ${command.app}, please unlock your phone or open Subnet. Android may block apps opening from the background.")
            is AssistantCommand.Alarm -> say("Please open Subnet to set this alarm safely.")
            AssistantCommand.Help -> say("I can call contacts, save notes, learn phrases, and help with app commands. Say call followed by a contact name.")
            AssistantCommand.Unknown -> say("I didn't understand that. Please say it another way.")
        }
    }

    private fun findContact(query: String) {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            say("Please open Subnet and grant contacts permission before using voice calls.")
            return
        }
        val matches = try { ContactRepository(this).findMatches(query) } catch (e: Exception) { emptyList() }
        if (matches.isEmpty()) {
            say("I couldn't find a phone number for $query.")
            return
        }
        if (matches.size == 1) {
            pendingContacts = matches
            placeCall(matches.first())
        } else {
            pendingContacts = matches.take(8)
            awaitingContactChoice = true
            val spoken = pendingContacts.mapIndexed { i, item ->
                "Option ${i + 1}: ${item.contactName}, number ending ${item.number.filter { it.isDigit() }.takeLast(4)}"
            }.joinToString(". ")
            say("I found ${matches.size} matching phone numbers. Here are the first ${pendingContacts.size} choices. $spoken. Say the person's full name or option number.") {
                listenForWakeOrReply()
            }
            publishStatus("Multiple matches for $query. Waiting for a name or option number.")
        }
    }

    private fun handleContactChoice(answer: String) {
        if (pendingContacts.isEmpty()) {
            awaitingContactChoice = false
            awaitingCommand = false
            say("There is no pending contact choice. Say Hey Subnet and ask me to call again.")
            return
        }
        val normalized = answer.lowercase(Locale.ROOT).trim()
        val ordinal = Regex("(?:option|number)\\s*(\\d+)").find(normalized)?.groupValues?.get(1)?.toIntOrNull()
            ?: normalized.toIntOrNull()
        val numberWord = when (normalized) {
            "one", "first", "the first one", "option one", "number one" -> 1
            "two", "second", "the second one", "option two", "number two" -> 2
            "three", "third", "the third one", "option three", "number three" -> 3
            "four", "fourth", "the fourth one", "option four", "number four" -> 4
            "five", "fifth", "the fifth one", "option five", "number five" -> 5
            "six", "sixth", "the sixth one", "option six", "number six" -> 6
            "seven", "seventh", "the seventh one", "option seven", "number seven" -> 7
            "eight", "eighth", "the eighth one", "option eight", "number eight" -> 8
            else -> null
        }
        val selectedIndex = ((ordinal ?: numberWord)?.minus(1))
        val selected = if (selectedIndex != null) pendingContacts.getOrNull(selectedIndex) else {
            val named = pendingContacts.filter { it.contactName.contains(answer, ignoreCase = true) }
            named.singleOrNull()
        }
        if (selected == null) {
            say("I couldn't match that choice. Please say the full contact name or option number.") { listenForWakeOrReply() }
            return
        }
        awaitingContactChoice = false
        pendingContacts = emptyList()
        placeCall(selected)
    }

    private fun placeCall(match: PhoneMatch) {
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            say("Calling permission is not enabled. Open Subnet and grant phone permission to place calls directly.")
            return
        }
        try {
            val telecom = getSystemService(TELECOM_SERVICE) as TelecomManager
            telecom.placeCall(Uri.fromParts("tel", match.number, null), Bundle())
            publishStatus("Calling ${match.contactName}...")
            say("Calling ${match.contactName} now.")
            learning.recordTask("Call ${match.contactName}", "call placed")
        } catch (e: Exception) {
            publishStatus("Call failed: ${e.localizedMessage ?: "phone service unavailable"}")
            say("I couldn't place that call. Please check phone permission and try again.")
        }
    }

    private fun say(message: String, after: (() -> Unit)? = null) {
        publishStatus(message)
        if (!ttsReady || tts == null) {
            after?.invoke() ?: listenForWakeOrReply()
            return
        }
        val utteranceId = "subnet-${System.currentTimeMillis()}"
        tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { android.os.Handler(mainLooper).post { if (running) (after ?: { listenForWakeOrReply() }).invoke() } }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { android.os.Handler(mainLooper).post { if (running) (after ?: { listenForWakeOrReply() }).invoke() } }
        })
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    private fun publishStatus(message: String) {
        sendBroadcast(Intent(ACTION_STATUS).setPackage(packageName).putExtra(EXTRA_STATUS, message))
    }

    private fun startWakeRecognition() {
        awaitingCommand = false
        awaitingContactChoice = false
        listenForWakeOrReply()
    }

    private fun stopListening() {
        running = false
        getSharedPreferences(STATE_PREFS, MODE_PRIVATE).edit().putBoolean("running", false).apply()
        publishStatus("Subnet wake listening stopped.")
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    override fun onDestroy() {
        stopListening()
        super.onDestroy()
    }
}
