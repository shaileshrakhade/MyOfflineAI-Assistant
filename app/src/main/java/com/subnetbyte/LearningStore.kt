package com.subnetbyte

import android.content.Context
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

class LearningStore(context: Context) {
    private val prefs = context.getSharedPreferences("subnet_learning", Context.MODE_PRIVATE)

    fun addAlias(phrase: String, canonical: String) {
        val map = readAliases()
        map[normalize(phrase)] = canonical.trim()
        prefs.edit().putString("aliases", JSONObject(map as Map<*, *>).toString()).apply()
    }

    fun resolve(text: String): String {
        var resolved = normalize(text)
        val aliases = readAliases().entries
            .filter { it.key.isNotBlank() }
            .sortedByDescending { it.key.length }
        for ((phrase, meaning) in aliases) {
            resolved = resolved.replace(phrase, meaning, ignoreCase = true)
        }
        return resolved
    }

    fun exportJson(): String {
        val root = JSONObject()
        root.put("format", "subnet-learning-v1")
        root.put("aliases", JSONObject(readAliases() as Map<*, *>))
        return root.toString(2)
    }

    fun importJson(json: String) {
        val root = JSONObject(json)
        require(root.optString("format") == "subnet-learning-v1") { "Unsupported learning file" }
        val incoming = root.optJSONObject("aliases") ?: JSONObject()
        val merged = readAliases()
        incoming.keys().forEach { key -> merged[normalize(key)] = incoming.getString(key) }
        prefs.edit().putString("aliases", JSONObject(merged as Map<*, *>).toString()).apply()
    }

    private fun readAliases(): MutableMap<String, String> {
        val result = mutableMapOf<String, String>()
        val raw = prefs.getString("aliases", null) ?: return result
        val obj = JSONObject(raw)
        obj.keys().forEach { key -> result[key] = obj.getString(key) }
        return result
    }

    companion object {
        fun normalize(value: String): String {
            return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .lowercase(Locale.ROOT)
                .replace(Regex("[.,!?।\\s]+"), " ")
                .trim()
        }
    }
}
