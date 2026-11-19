
package com.subnetbyte

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.speech.RecognizerIntent
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import java.io.File
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var commandInput: EditText

    private val audioRequest = 100
    private val callRequest = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        status = findViewById(R.id.status)
        commandInput = findViewById(R.id.commandInput)

        findViewById<Button>(R.id.listenButton)
            .setOnClickListener { listen() }

        findViewById<Button>(R.id.runButton)
            .setOnClickListener {
                runCommand(commandInput.text.toString())
            }
    }

    private fun listen() {

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                audioRequest
            )
            return
        }

        val intent = Intent(
            RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        ).apply {

            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )

            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                Locale.getDefault()
            )

            putExtra(
                RecognizerIntent.EXTRA_PREFER_OFFLINE,
                true
            )

            putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                "Speak a command"
            )
        }

        try {
            startActivityForResult(intent, audioRequest)
        } catch (e: Exception) {
            status.text =
                "No speech recognition app is available."
        }
    }

    @Deprecated("Legacy activity result API")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == audioRequest && resultCode == RESULT_OK) {

            val spoken = data
                ?.getStringArrayListExtra(
                    RecognizerIntent.EXTRA_RESULTS
                )
                ?.firstOrNull()
                .orEmpty()

            commandInput.setText(spoken)
            runCommand(spoken)
        }
    }

    private fun runCommand(raw: String) {

        val command = raw.trim()
        val lower = command.lowercase(Locale.ROOT)

        when {

            lower.startsWith("note ") ||
            lower.startsWith("remember ") -> {

                val note = command.substringAfter(" ").trim()

                if (note.isNotBlank()) {

                    File(filesDir, "notes.txt")
                        .appendText(
                            "${System.currentTimeMillis()} | $note\n"
                        )

                    status.text = "Saved locally: $note"
                }
            }

            lower.startsWith("open ") -> {
                openApp(command.substringAfter(" ").trim())
            }

            lower.startsWith("set alarm for ") -> {
                setAlarm(command.drop("set alarm for ".length))
            }

            lower.startsWith("call ") -> {
                callContact(command.substringAfter(" ").trim())
            }

            else -> {
                status.text =
                    "Try: open Chrome, note hello, set alarm for 7:00 AM, or call contact."
            }
        }
    }

    private fun openApp(name: String) {

        val pm = packageManager

        val apps = pm.getInstalledApplications(
            PackageManager.GET_META_DATA
        )

        val match = apps.firstOrNull {
            pm.getApplicationLabel(it)
                .toString()
                .equals(name, ignoreCase = true)
        }

        val launchIntent = match?.let {
            pm.getLaunchIntentForPackage(it.packageName)
        }

        if (launchIntent != null) {
            startActivity(launchIntent)
        } else {
            status.text = "Couldn't find app: $name"
        }
    }

    private fun setAlarm(timeText: String) {

        val regex = Regex(
            "([0-9]{1,2}):([0-9]{2})[ ]*(am|pm)?",
            RegexOption.IGNORE_CASE
        )

        val match = regex.find(timeText)

        if (match == null) {
            status.text = "Enter time like 7:30 AM."
            return
        }

        var hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()

        val meridiem = match.groupValues[3].lowercase()

        if (hour !in 1..12 || minute !in 0..59) {
            status.text = "Invalid alarm time."
            return
        }

        if (meridiem == "pm" && hour < 12) {
            hour += 12
        }

        if (meridiem == "am" && hour == 12) {
            hour = 0
        }

        val intent = Intent(
            AlarmClock.ACTION_SET_ALARM
        ).apply {

            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)

            putExtra(
                AlarmClock.EXTRA_MESSAGE,
                "Subnet Assistant"
            )
        }

        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            status.text = "No alarm app available."
        }
    }

    private fun callContact(name: String) {

        if (checkSelfPermission(Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {

            requestPermissions(
                arrayOf(Manifest.permission.READ_CONTACTS),
                callRequest
            )

            status.text = "Allow Contacts permission and try again."
            return
        }

        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$name%"),
            null
        )

        val number = cursor?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }

        if (number.isNullOrBlank()) {
            status.text = "Contact not found: $name"
            return
        }

        val intent = Intent(
            Intent.ACTION_DIAL,
            Uri.parse("tel:$number")
        )

        startActivity(intent)

        status.text =
            "Dialer opened. Review and confirm the call."
    }
}
