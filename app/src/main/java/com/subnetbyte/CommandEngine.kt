package com.subnetbyte

import java.util.Locale

sealed class AssistantCommand {
    data class Call(val contact: String) : AssistantCommand()
    data class OpenApp(val app: String) : AssistantCommand()
    data class Note(val text: String) : AssistantCommand()
    data class Alarm(val time: String) : AssistantCommand()
    data class Teach(val phrase: String, val meaning: String) : AssistantCommand()
    data class Choose(val index: Int) : AssistantCommand()
    data object Help : AssistantCommand()
    data object Unknown : AssistantCommand()
}

class CommandEngine(private val learning: LearningStore) {
    fun parse(raw: String): AssistantCommand {
        val text = raw.trim()
        val lower = text.lowercase(Locale.ROOT)
        if (text.isBlank()) return AssistantCommand.Unknown

        val teachEnglish = Regex("^teach\\s+(.+?)\\s+(?:as|means)\\s+(.+)$", RegexOption.IGNORE_CASE).find(text)
        if (teachEnglish != null) return AssistantCommand.Teach(teachEnglish.groupValues[1], teachEnglish.groupValues[2])

        val teachMarathi = Regex("^शिकव\\s+(.+?)\\s+(?:म्हणजे|असे)\\s+(.+)$").find(text)
        if (teachMarathi != null) return AssistantCommand.Teach(teachMarathi.groupValues[1], teachMarathi.groupValues[2])

        val choice = choiceIndex(text)
        if (choice != null) return AssistantCommand.Choose(choice)

        if (lower in listOf("help", "मदत", "काय करू शकतो")) return AssistantCommand.Help

        val resolved = learning.resolve(text)
        val callPattern = Regex("^(?:call|फोन कर|कॉल कर|कॉल लाव|लाव फोन|डायल कर|फोन लाव)\\s*(.*)$", RegexOption.IGNORE_CASE)
        val marathiCall = Regex("(.+?)(?:ला|ला फोन|ला कॉल)\\s*(?:call कर|फोन कर|कॉल कर)?$", RegexOption.IGNORE_CASE)
        val callMatch = callPattern.find(resolved)
        if (callMatch != null && callMatch.groupValues[1].isNotBlank()) {
            return AssistantCommand.Call(callMatch.groupValues[1].trim())
        }
        val marathiMatch = marathiCall.find(resolved)
        if (marathiMatch != null && marathiMatch.groupValues[1].isNotBlank()) {
            return AssistantCommand.Call(marathiMatch.groupValues[1].trim())
        }

        val noteMatch = Regex("^(?:note|remember|नोंद कर|लक्षात ठेव)\\s*(.*)$", RegexOption.IGNORE_CASE).find(text)
        if (noteMatch != null && noteMatch.groupValues[1].isNotBlank()) return AssistantCommand.Note(noteMatch.groupValues[1].trim())

        val openMatch = Regex("^(?:open|उघड|चालू कर)\\s+(.+)$", RegexOption.IGNORE_CASE).find(text)
        if (openMatch != null) return AssistantCommand.OpenApp(openMatch.groupValues[1].trim())

        val alarmMatch = Regex("^(?:set alarm for|alarm|अलार्म लाव|गजर लाव)\\s*(.*)$", RegexOption.IGNORE_CASE).find(text)
        if (alarmMatch != null && alarmMatch.groupValues[1].isNotBlank()) return AssistantCommand.Alarm(alarmMatch.groupValues[1].trim())

        return AssistantCommand.Unknown
    }

    private fun choiceIndex(value: String): Int? {
        val v = value.trim().lowercase(Locale.ROOT)
        return when (v) {
            "पहिला", "पहिली", "पहिले", "पहिला नंबर", "एक", "१", "1", "one", "first", "number one" -> 0
            "दुसरा", "दुसरी", "दुसरे", "दुसरा नंबर", "दोन", "२", "2", "two", "second", "number two" -> 1
            "तिसरा", "तिसरी", "तीन", "३", "3", "three", "third" -> 2
            "चौथा", "चार", "४", "4", "four", "fourth" -> 3
            else -> v.toIntOrNull()?.minus(1)?.takeIf { it >= 0 }
        }
    }
}
