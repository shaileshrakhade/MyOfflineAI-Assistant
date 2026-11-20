package com.subnetbyte

import android.content.Context
import android.provider.ContactsContract

data class PhoneMatch(val contactName: String, val number: String)

class ContactRepository(private val context: Context) {
    fun findMatches(query: String): List<PhoneMatch> {
        val results = linkedMapOf<String, PhoneMatch>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val args = arrayOf("%${query.trim()}%")
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection, selection, args,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE ASC"
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIndex) ?: continue
                val number = cursor.getString(numberIndex) ?: continue
                results["${name.lowercase()}|${number.filter { it.isDigit() }}"] = PhoneMatch(name, number)
            }
        }
        return results.values.toList()
    }
}
