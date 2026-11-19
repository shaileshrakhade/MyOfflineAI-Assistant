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
        if (text.isBlank()) return AssistantCommand.Unknown
        val lower = text.lowercase(Locale.ROOT)

        val teach = Regex("^teach\\s+(.+?)\\s+(?:as|means|to mean)\\s+(.+)$", RegexOption.IGNORE_CASE).find(text)
        if (teach != null) return AssistantCommand.Teach(teach.groupValues[1].trim(), teach.groupValues[2].trim())

        val choice = choiceIndex(text)
        if (choice != null) return AssistantCommand.Choose(choice)
        if (lower in setOf("help", "what can you do", "show commands", "commands")) return AssistantCommand.Help

        val resolved = learning.resolve(text)
        Regex("^(?:call|phone|dial)\\s+(.+)$", RegexOption.IGNORE_CASE).find(resolved)?.let {
            return AssistantCommand.Call(it.groupValues[1].trim())
        }
        Regex("^(?:note|remember|save note|write down)\\s+(.+)$", RegexOption.IGNORE_CASE).find(resolved)?.let {
            return AssistantCommand.Note(it.groupValues[1].trim())
        }
        Regex("^(?:open|launch|start)\\s+(.+)$", RegexOption.IGNORE_CASE).find(resolved)?.let {
            return AssistantCommand.OpenApp(it.groupValues[1].trim())
        }
        Regex("^(?:set\\s+)?alarm(?:\\s+for)?\\s+(.+)$", RegexOption.IGNORE_CASE).find(resolved)?.let {
            return AssistantCommand.Alarm(it.groupValues[1].trim())
        }
        return AssistantCommand.Unknown
    }

    private fun choiceIndex(value: String): Int? = when (value.trim().lowercase(Locale.ROOT)) {
        "one", "first", "number one", "1" -> 0
        "two", "second", "number two", "2" -> 1
        "three", "third", "3" -> 2
        "four", "fourth", "4" -> 3
        else -> value.trim().toIntOrNull()?.minus(1)?.takeIf { it >= 0 }
    }
}
