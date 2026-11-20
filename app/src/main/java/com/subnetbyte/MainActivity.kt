package com.subnetbyte

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var commandInput: EditText
    private lateinit var setupStatus: TextView
    private lateinit var learningStatus: TextView
    private lateinit var learning: LearningStore
    private lateinit var engine: CommandEngine
    private var receiverRegistered = false

    companion object {
        private const val REQ_PERMISSIONS = 201
        private const val PREFS = "subnet_owner_setup"
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            status.text = intent?.getStringExtra(WakeWordService.EXTRA_STATUS) ?: return
            val active = getSharedPreferences("subnet_service_state", MODE_PRIVATE).getBoolean("running", false)
            findViewById<Button>(R.id.listenButton).text = if (active) "Stop wake listening" else "Start background wake listening"
            refreshLearningStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        commandInput = findViewById(R.id.commandInput)
        setupStatus = findViewById(R.id.setupStatus)
        learningStatus = findViewById(R.id.learningStatus)
        learning = LearningStore(this)
        engine = CommandEngine(learning)

        findViewById<Button>(R.id.listenButton).setOnClickListener { toggleWakeService() }
        findViewById<Button>(R.id.runButton).setOnClickListener { runTypedCommand(commandInput.text.toString()) }
        findViewById<Button>(R.id.setupButton).setOnClickListener { ownerSetup() }
        findViewById<Button>(R.id.exportButton).setOnClickListener { exportLearning() }
        findViewById<Button>(R.id.importButton).setOnClickListener { importLearning() }
        findViewById<Button>(R.id.suggestButton).setOnClickListener { suggestFeature() }
        refreshSetupStatus()
        if (getSharedPreferences("subnet_service_state", MODE_PRIVATE).getBoolean("running", false)) {
            findViewById<Button>(R.id.listenButton).text = "Stop wake listening"
            status.text = "Subnet background listening is enabled. Say Hey Subnet or Hey Bro."
        }
        if (!getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("complete", false)) {
            status.text = "Please complete owner setup first."
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(WakeWordService.ACTION_STATUS)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(statusReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(statusReceiver, filter)
        }
        receiverRegistered = true
    }

    override fun onStop() {
        if (receiverRegistered) {
            unregisterReceiver(statusReceiver)
            receiverRegistered = false
        }
        super.onStop()
    }

    private fun refreshSetupStatus() {
        val owner = getSharedPreferences(PREFS, MODE_PRIVATE).getString("owner_name", null)
        setupStatus.text = if (owner.isNullOrBlank()) "Setup required" else "Owner: $owner"
        refreshLearningStatus()
    }

    private fun refreshLearningStatus() {
        learningStatus.text = "Local memory: ${learning.learnedPhraseCount()} learned phrases • ${learning.taskCount()} saved tasks"
    }

    private fun toggleWakeService() {
        if (getSharedPreferences("subnet_service_state", MODE_PRIVATE).getBoolean("running", false)) {
            stopWakeService()
            return
        }
        if (!getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("complete", false)) {
            status.text = "Complete owner setup before enabling Subnet."
            ownerSetup()
            return
        }
        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE)
        if (Build.VERSION.SDK_INT >= 33) needed.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = needed.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), REQ_PERMISSIONS)
            return
        }
        startWakeService()
    }

    private fun startWakeService() {
        try {
            val intent = Intent(this, WakeWordService::class.java).setAction(WakeWordService.ACTION_START)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
            findViewById<Button>(R.id.listenButton).text = "Stop wake listening"
            status.text = "Starting Subnet voice service..."
        } catch (e: Exception) {
            status.text = "Could not start voice service: ${e.localizedMessage ?: "Android restriction"}"
        }
    }

    private fun stopWakeService() {
        startService(Intent(this, WakeWordService::class.java).setAction(WakeWordService.ACTION_STOP))
        getSharedPreferences("subnet_service_state", MODE_PRIVATE).edit().putBoolean("running", false).apply()
        findViewById<Button>(R.id.listenButton).text = "Start wake listening"
        status.text = "Subnet wake listening stopped."
    }

    private fun runTypedCommand(raw: String) {
        val text = raw.trim()
        if (text.isBlank()) return
        val command = engine.parse(text)
        when (command) {
            is AssistantCommand.Teach -> {
                learning.addAlias(command.phrase, command.meaning)
                status.text = "Learned locally: ${command.phrase} means ${command.meaning}."
            }
            is AssistantCommand.Note -> {
                openFileOutput("notes.txt", MODE_APPEND).bufferedWriter().use { it.append("${System.currentTimeMillis()} | ${command.text}\n") }
                status.text = "Note saved in this phone's private app storage."
            }
            else -> status.text = "For hands-free commands, start wake listening and say Hey Subnet or Hey Bro."
        }
        refreshLearningStatus()
    }

    private fun ownerSetup() {
        val input = EditText(this).apply { hint = "Your name" }
        android.app.AlertDialog.Builder(this)
            .setTitle("Subnet owner setup")
            .setMessage("Your profile and learned phrases are stored locally on this phone.")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isBlank()) {
                    status.text = "Please enter your name."
                } else {
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("owner_name", name).putBoolean("complete", true).apply()
                    refreshSetupStatus()
                    status.text = "Setup complete. Hello $name!"
                }
            }.show()
    }

    private fun exportLearning() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "subnet-learning.json")
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, 102)
    }

    private fun importLearning() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, 103)
    }

    @Deprecated("Legacy document picker result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return
        val uri = data.data!!
        try {
            when (requestCode) {
                102 -> contentResolver.openOutputStream(uri)?.use { it.write(learning.exportJson().toByteArray()) }
                103 -> contentResolver.openInputStream(uri)?.bufferedReader()?.use { learning.importJson(it.readText()) }
            }
            refreshLearningStatus()
            status.text = "Local learning data imported or exported."
        } catch (e: Exception) {
            status.text = "File operation failed: ${e.localizedMessage}"
        }
    }

    private fun suggestFeature() {
        val title = EditText(this).apply { hint = "Feature title" }
        val details = EditText(this).apply { hint = "Describe your idea"; minLines = 3 }
        val layout = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.VERTICAL; setPadding(24, 8, 24, 0); addView(title); addView(details) }
        android.app.AlertDialog.Builder(this).setTitle("Suggest a feature").setView(layout)
            .setNegativeButton("Cancel", null).setPositiveButton("Save") { _, _ ->
                openFileOutput("feature_suggestions.txt", MODE_APPEND).bufferedWriter().use { it.append("${title.text}\n${details.text}\n---\n") }
                status.text = "Suggestion saved locally."
            }.show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMISSIONS) {
            val requiredGranted = listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE)
                .all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
            if (requiredGranted) startWakeService() else status.text = "Grant microphone, contacts, and phone permissions to use voice calling."
        }
    }
}
