package com.sovereignvoice.granna

import android.content.Context
import android.provider.ContactsContract

/**
 * Reads contacts from the phone and fuzzy-matches spoken names.
 */
class ContactHelper(private val context: Context) {

    data class Contact(val name: String, val number: String)

    private val contacts: List<Contact> by lazy { loadContacts() }

    private fun loadContacts(): List<Contact> {
        val list = mutableListOf<Contact>()
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        ) ?: return list

        cursor.use {
            val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx  = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val name = it.getString(nameIdx)?.trim() ?: continue
                val num  = it.getString(numIdx)?.trim()?.replace("[^0-9+]".toRegex(), "") ?: continue
                if (name.isNotEmpty() && num.isNotEmpty()) {
                    list.add(Contact(name, num))
                }
            }
        }
        return list
    }

    /**
     * Find the best matching contact for what was heard.
     * Uses a simple scoring system:
     *   3 pts — exact name match (case insensitive)
     *   2 pts — all heard words appear in contact name
     *   1 pt  — any heard word appears in contact name
     */
    fun findBestMatch(heard: String): Contact? {
        if (heard.isBlank()) return null
        val heardClean = heard.lowercase().trim()
        val heardWords = heardClean.split("\\s+".toRegex()).filter { it.length > 1 }

        data class Scored(val contact: Contact, val score: Int)

        val scored = contacts.mapNotNull { contact ->
            val nameLower = contact.name.lowercase()
            val nameWords = nameLower.split("\\s+".toRegex())

            val score = when {
                nameLower == heardClean -> 3
                heardWords.all { hw -> nameWords.any { nw -> nw == hw } } -> 2
                heardWords.any { hw -> nameWords.any { nw -> nw.startsWith(hw) || hw.startsWith(nw) } } -> 1
                else -> 0
            }
            if (score > 0) Scored(contact, score) else null
        }

        return scored.maxByOrNull { it.score }?.contact
    }

    fun getAllContacts(): List<Contact> = contacts
}
