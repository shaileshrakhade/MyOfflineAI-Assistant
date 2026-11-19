package com.subnetbyte

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var commandInput: EditText
    private lateinit var setupStatus: TextView
    private lateinit var learning: LearningStore
    private lateinit var engine: CommandEngine

    private var speechRecognizer: SpeechRecognizer? = null
    private var pendingContacts: List<PhoneMatch> = emptyList()
    private var pendingContactName: String = ""

    companion object {
        private const val REQ_AUDIO = 100
        private const val REQ_CONTACTS = 101
        private const val REQ_EXPORT = 102
        private const val REQ_IMPORT = 103
        private const val PREFS = "subnet_owner_setup"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.status)
        commandInput = findViewById(R.id.commandInput)
        setupStatus = findViewById(R.id.setupStatus)
        learning = LearningStore(this)
        engine = CommandEngine(learning)

        findViewById<Button>(R.id.listenButton).setOnClickListener { listen() }
        findViewById<Button>(R.id.runButton).setOnClickListener {
            runCommand(commandInput.text.toString())
        }
        findViewById<Button>(R.id.setupButton).setOnClickListener { ownerSetup() }
        findViewById<Button>(R.id.exportButton).setOnClickListener { exportLearning() }
        findViewById<Button>(R.id.importButton).setOnClickListener { importLearning() }
        findViewById<Button>(R.id.suggestButton).setOnClickListener { suggestFeature() }

        refreshSetupStatus()
        if (!getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("complete", false)) {
            status.text = "पहिल्यांदा Owner Setup करा. / Please complete owner setup first."
        }
    }

    private fun refreshSetupStatus() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val owner = prefs.getString("owner_name", null)
        setupStatus.text = if (owner.isNullOrBlank()) {
            "Setup required / सेटअप आवश्यक"
        } else {
            "Owner: $owner"
        }
    }

    private fun ownerSetup() {
        val input = EditText(this).apply { hint = "Your name / तुमचे नाव" }
        AlertDialog.Builder(this)
            .setTitle("Subnet owner setup")
            .setMessage("Set the owner profile for this device. This profile is local to this phone.")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isBlank()) {
                    speakStatus("Please enter your name.")
                    return@setPositiveButton
                }
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString("owner_name", name)
                    .putBoolean("complete", true)
                    .apply()
                refreshSetupStatus()
                speakStatus("Setup complete. नमस्कार $name!")
            }
            .show()
    }

    /**
     * Uses Android's SpeechRecognizer directly instead of launching the
     * separate Google Voice Search activity. Offline recognition is used
     * when this device exposes an installed on-device recognition service.
     */
    private fun listen() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_AUDIO)
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            speakStatus("Speech recognition is not available on this device.")
            return
        }

        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null

            speechRecognizer = if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
            ) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            } else {
                SpeechRecognizer.createSpeechRecognizer(this)
            }

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    status.text = "Listening... बोला, Subnet ऐकत आहे."
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    status.text = "Processing your command..."
                }

                override fun onError(error: Int) {
                    val message = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Microphone audio error."
                        SpeechRecognizer.ERROR_CLIENT -> "Speech recognition was interrupted. Try again."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                            "Microphone permission is required."
                        SpeechRecognizer.ERROR_NETWORK ->
                            "Recognition network error. Check your connection."
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            "Recognition network timeout."
                        SpeechRecognizer.ERROR_NO_MATCH ->
                            "Could not understand. Please try again."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                            "Speech recognizer is busy. Try again."
                        SpeechRecognizer.ERROR_SERVER ->
                            "Speech recognition service error."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                            "No speech detected. Please try again."
                        else -> "Speech recognition error: $error"
                    }
                    speakStatus(message)
                }

                override fun onResults(results: Bundle?) {
                    val spoken = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()

                    if (spoken.isNotBlank()) {
                        commandInput.setText(spoken)
                        status.text = "You said: $spoken"
                        runCommand(spoken)
                    } else {
                        speakStatus("Please try again.")
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "mr-IN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "mr-IN")
                putExtra(
                    RecognizerIntent.EXTRA_PROMPT,
                    "Speak to Subnet / Subnet ला बोला"
                )
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }

            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            speakStatus("Unable to start microphone: ${e.localizedMessage ?: "unknown error"}")
        }
    }

    @Deprecated("Legacy activity result API retained for compatibility with this starter")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return

        when (requestCode) {
            REQ_EXPORT -> {
                data.data?.let { uri ->
                    contentResolver.openOutputStream(uri)?.use {
                        it.write(learning.exportJson().toByteArray())
                    }
                }
                speakStatus("Learned commands exported.")
            }

            REQ_IMPORT -> {
                val uri = data.data ?: return
                val json = contentResolver.openInputStream(uri)
                    ?.bufferedReader()
                    ?.use { it.readText() } ?: return
                try {
                    learning.importJson(json)
                    speakStatus("Learned commands imported.")
                } catch (e: Exception) {
                    speakStatus("Import failed: ${e.message}")
                }
            }
        }
    }

    private fun runCommand(raw: String) {
        val setupDone = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getBoolean("complete", false)

        if (!setupDone) {
            speakStatus("Please complete owner setup first.")
            return
        }

        when (val command = engine.parse(raw)) {
            is AssistantCommand.Call -> findContact(command.contact)
            is AssistantCommand.OpenApp -> openApp(command.app)
            is AssistantCommand.Note -> saveNote(command.text)
            is AssistantCommand.Alarm -> setAlarm(command.time)
            is AssistantCommand.Teach -> {
                learning.addAlias(command.phrase, command.meaning)
                speakStatus("शिकलो. ${command.phrase} means ${command.meaning}.")
            }
            is AssistantCommand.Choose -> choosePending(command.index)
            AssistantCommand.Help ->
                speakStatus("तुम्ही Call, Open, Note, Alarm आणि Teach commands वापरू शकता.")
            AssistantCommand.Unknown ->
                speakStatus("माफ करा, समजले नाही. कृपया पुन्हा सांगा.")
        }
    }

    private fun findContact(query: String) {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pendingContactName = query
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), REQ_CONTACTS)
            return
        }

        val matches = ContactRepository(this).findMatches(query)
        if (matches.isEmpty()) {
            speakStatus("Contact सापडला नाही: $query")
            return
        }

        pendingContacts = matches
        if (matches.size == 1) {
            confirmNumber(matches.first())
        } else {
            val choices = matches.mapIndexed { index, item ->
                "${index + 1}. ${item.contactName} — ${item.number}"
            }.joinToString("\n")
            speakStatus("एकापेक्षा जास्त पर्याय सापडले. पहिला, दुसरा किंवा क्रमांक सांगा.\n$choices")
        }
    }

    private fun choosePending(index: Int) {
        if (pendingContacts.isEmpty()) {
            speakStatus("आत्ता निवडण्यासाठी पर्याय नाहीत.")
            return
        }
        if (index !in pendingContacts.indices) {
            speakStatus("तो पर्याय उपलब्ध नाही. पुन्हा निवडा.")
            return
        }
        confirmNumber(pendingContacts[index])
    }

    private fun confirmNumber(match: PhoneMatch) {
        AlertDialog.Builder(this)
            .setTitle("Call ${match.contactName}?")
            .setMessage(
                "Number: ${match.number}\nThe dialer will open so you can confirm the call."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Open dialer") { _, _ ->
                startActivity(
                    Intent(
                        Intent.ACTION_DIAL,
                        Uri.parse("tel:${Uri.encode(match.number)}")
                    )
                )
                speakStatus("Dialer opened for ${match.contactName}.")
                pendingContacts = emptyList()
            }
            .show()
    }

    private fun openApp(name: String) {
        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val matches = apps.filter {
            pm.getApplicationLabel(it).toString().contains(name, ignoreCase = true)
        }

        if (matches.isEmpty()) {
            speakStatus("App not found: $name")
            return
        }

        if (matches.size == 1) {
            launchApp(matches.first().packageName)
            return
        }

        val labels = matches.map { pm.getApplicationLabel(it).toString() }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Which app?")
            .setItems(labels) { _, which ->
                launchApp(matches[which].packageName)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun launchApp(packageName: String) {
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        if (launch != null) {
            startActivity(launch)
        } else {
            speakStatus("Cannot open this app.")
        }
    }

    private fun saveNote(note: String) {
        openFileOutput("notes.txt", MODE_APPEND).bufferedWriter().use {
            it.append("${System.currentTimeMillis()} | $note\n")
        }
        speakStatus("नोंद सेव्ह केली.")
    }

    private fun setAlarm(raw: String) {
        val text = raw.lowercase(Locale.ROOT)
        val regex = Regex("(\\d{1,2})(?::(\\d{1,2}))?\\s*(am|pm)?")
        val match = regex.find(text)

        if (match == null) {
            speakStatus("वेळ समजली नाही. उदाहरण: 7:30 AM")
            return
        }

        var hour = match.groupValues[1].toIntOrNull() ?: return
        val minute = match.groupValues[2].ifBlank { "0" }.toIntOrNull() ?: 0
        val meridiem = match.groupValues[3]

        if (minute !in 0..59 || hour !in 0..23) {
            speakStatus("Invalid time.")
            return
        }

        if (meridiem == "pm" && hour in 1..11) hour += 12
        if (meridiem == "am" && hour == 12) hour = 0

        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, "Subnet")
        }

        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            speakStatus("Alarm app not available.")
        }
    }

    private fun exportLearning() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "subnet-learning.json")
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQ_EXPORT)
    }

    private fun importLearning() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQ_IMPORT)
    }

    private fun suggestFeature() {
        val titleInput = EditText(this).apply {
            hint = "Feature title / काय हवे आहे?"
            setSingleLine(true)
        }

        val detailsInput = EditText(this).apply {
            hint = "Describe your idea in Marathi or English..."
            minLines = 3
            maxLines = 6
            setPadding(24, 18, 24, 18)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }

        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(24, 8, 24, 0)
            addView(titleInput)
            addView(detailsInput)
        }

        AlertDialog.Builder(this)
            .setTitle("Suggest a feature 💡")
            .setMessage("Tell us what you want Subnet to do next.")
            .setView(container)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save & share") { _, _ ->
                val title = titleInput.text.toString().trim()
                val details = detailsInput.text.toString().trim()

                if (title.isBlank() && details.isBlank()) {
                    speakStatus("Please enter your suggestion first.")
                    return@setPositiveButton
                }

                val suggestion = buildString {
                    appendLine("Subnet Assistant feature suggestion")
                    appendLine("Title: ${title.ifBlank { "New idea" }}")
                    appendLine("Details: $details")
                    appendLine("App version: 2.1")
                    appendLine(
                        "Date: ${
                            java.text.SimpleDateFormat(
                                "yyyy-MM-dd HH:mm",
                                Locale.getDefault()
                            ).format(java.util.Date())
                        }"
                    )
                    appendLine("---")
                }

                openFileOutput("feature_suggestions.txt", MODE_APPEND).bufferedWriter().use {
                    it.write(suggestion)
                }

                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Subnet Assistant feature suggestion")
                    putExtra(Intent.EXTRA_TEXT, suggestion)
                }

                speakStatus("Suggestion saved on this phone. Choose an app to share it.")
                startActivity(Intent.createChooser(share, "Share your suggestion"))
            }
            .show()
    }

    private fun speakStatus(message: String) {
        status.text = message
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            REQ_AUDIO -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                    listen()
                } else {
                    speakStatus("Microphone permission is required.")
                }
            }

            REQ_CONTACTS -> {
                if (
                    grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED &&
                    pendingContactName.isNotBlank()
                ) {
                    val name = pendingContactName
                    pendingContactName = ""
                    findContact(name)
                } else {
                    speakStatus("Contacts permission is required for calling.")
                }
            }
        }
    }

    override fun onDestroy() {
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        super.onDestroy()
    }
}
