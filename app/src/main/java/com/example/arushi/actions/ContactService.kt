package com.example.arushi.actions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat

private const val TAG = "ContactService"

data class ContactMatch(
    val name: String,
    val phoneNumber: String
)

sealed interface ContactSearchResult {
    data object PermissionRequired : ContactSearchResult
    data class SingleMatch(val contact: ContactMatch) : ContactSearchResult
    data class MultipleMatches(val query: String, val matches: List<ContactMatch>) : ContactSearchResult
    data class NotFound(val query: String) : ContactSearchResult
    data class Error(val message: String) : ContactSearchResult
}

class ContactService(private val context: Context) {

    fun hasContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun searchByName(rawQuery: String): ContactSearchResult {
        val query = rawQuery.trim()
        if (query.isBlank()) {
            return ContactSearchResult.Error("Contact name cannot be empty.")
        }
        if (!hasContactsPermission()) {
            return ContactSearchResult.PermissionRequired
        }

        return try {
            val matches = queryContacts(query)
            when {
                matches.isEmpty() -> {
                    Log.i(TAG, "No contact found for query='$query'")
                    ContactSearchResult.NotFound(query)
                }
                matches.size == 1 -> {
                    Log.i(TAG, "Single contact matched for query='$query': ${matches.first().name}")
                    ContactSearchResult.SingleMatch(matches.first())
                }
                else -> {
                    val exactMatches = matches.filter {
                        it.name.equals(query, ignoreCase = true)
                    }
                    if (exactMatches.size == 1) {
                        ContactSearchResult.SingleMatch(exactMatches.first())
                    } else {
                        Log.i(TAG, "Multiple contacts (${matches.size}) matched query='$query'")
                        ContactSearchResult.MultipleMatches(query, matches.take(5))
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Contacts permission revoked", e)
            ContactSearchResult.PermissionRequired
        } catch (e: Exception) {
            Log.e(TAG, "Failed searching contacts for '$query'", e)
            ContactSearchResult.Error(e.localizedMessage ?: "Failed to query device contacts.")
        }
    }

    private fun queryContacts(query: String): List<ContactMatch> {
        val results = mutableListOf<ContactMatch>()
        val seenKeys = mutableSetOf<String>()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$query%")
        val sortOrder = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val name = if (nameIdx >= 0) cursor.getString(nameIdx)?.trim().orEmpty() else ""
                val number = if (numberIdx >= 0) cursor.getString(numberIdx)?.trim().orEmpty() else ""
                if (name.isNotEmpty() && number.isNotEmpty()) {
                    val normalizedNumber = normalizeDigits(number)
                    val dedupeKey = "${name.lowercase()}|$normalizedNumber"
                    if (normalizedNumber.isNotEmpty() && seenKeys.add(dedupeKey)) {
                        results.add(ContactMatch(name = name, phoneNumber = number))
                    }
                }
            }
        }
        return results
    }

    private fun normalizeDigits(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return if (digits.length > 10) digits.takeLast(10) else digits
    }
}
