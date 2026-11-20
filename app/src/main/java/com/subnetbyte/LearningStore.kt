package com.subnetbyte

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

class LearningStore(context: Context) {
    private val prefs = context.getSharedPreferences("subnet_learning", Context.MODE_PRIVATE)

    fun addAlias(phrase: String, canonical: String) {
        require(phrase.isNotBlank() && canonical.isNotBlank()) { "Phrase and meaning are required." }
        val map = readAliases()
        map[normalize(phrase)] = canonical.trim()
        prefs.edit().putString("aliases", JSONObject(map as Map<*, *>).toString()).apply()
    }

    fun resolve(text: String): String {
        var resolved = normalize(text)
        readAliases().entries.filter { it.key.isNotBlank() }.sortedByDescending { it.key.length }.forEach { (phrase, meaning) ->
            resolved = resolved.replace(Regex("(?<![\\p{L}\\p{N}])${Regex.escape(phrase)}(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE), meaning)
        }
        return resolved
    }

    fun recordTask(command: String, result: String) {
        if (command.isBlank()) return
        val history = readHistory()
        history.add(0, JSONObject().put("command", command.take(250)).put("result", result.take(100)).put("time", System.currentTimeMillis()))
        while (history.size > 100) history.removeAt(history.lastIndex)
        val array = JSONArray()
        history.forEach(array::put)
        prefs.edit().putString("history", array.toString()).apply()
    }

    fun recentTasks(limit: Int = 5): List<String> = readHistory().take(limit).map { it.optString("command") }
    fun taskCount(): Int = readHistory().size
    fun learnedPhraseCount(): Int = readAliases().size

    fun exportJson(): String {
        val root = JSONObject().put("format", "subnet-learning-v2")
            .put("aliases", JSONObject(readAliases() as Map<*, *>))
            .put("history", JSONArray().also { array -> readHistory().forEach(array::put) })
        return root.toString(2)
    }

    fun importJson(json: String) {
        val root = JSONObject(json)
        require(root.optString("format") in setOf("subnet-learning-v1", "subnet-learning-v2")) { "Unsupported learning file." }
        val incoming = root.optJSONObject("aliases") ?: JSONObject()
        val merged = readAliases()
        incoming.keys().forEach { key -> merged[normalize(key)] = incoming.getString(key) }
        val editor = prefs.edit().putString("aliases", JSONObject(merged as Map<*, *>).toString())
        root.optJSONArray("history")?.let { imported ->
            val combined = readHistory()
            for (i in 0 until imported.length()) imported.optJSONObject(i)?.let(combined::add)
            val bounded = JSONArray()
            combined.take(100).forEach(bounded::put)
            editor.putString("history", bounded.toString())
        }
        editor.apply()
    }

    private fun readAliases(): MutableMap<String, String> {
        val result = mutableMapOf<String, String>()
        val raw = prefs.getString("aliases", null) ?: return result
        val obj = JSONObject(raw)
        obj.keys().forEach { key -> result[key] = obj.getString(key) }
        return result
    }

    private fun readHistory(): MutableList<JSONObject> {
        val raw = prefs.getString("history", null) ?: return mutableListOf()
        val array = JSONArray(raw)
        return MutableList(array.length()) { index -> array.getJSONObject(index) }
    }

    companion object {
        fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT).replace(Regex("[.,!?\\s]+"), " ").trim()
    }
}
