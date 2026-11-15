package com.example.arushi.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import com.example.arushi.audio.LogSeverity
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val TAG = "DeviceActionBridge"

data class DeviceActionFeedback(
    val actionName: String,
    val summary: String,
    val success: Boolean
)

class DeviceActionBridge(
    private val context: Context,
    private val contactService: ContactService,
    private val onRequestContactsPermission: suspend () -> Boolean,
    private val onActionFeedback: (DeviceActionFeedback) -> Unit,
    private val onDiagnosticLog: (String, String, LogSeverity) -> Unit
) {

    private val appAllowlist: Map<String, List<String>> = mapOf(
        "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
        "whatsapp business" to listOf("com.whatsapp.w4b", "com.whatsapp"),
        "youtube" to listOf("com.google.android.youtube"),
        "instagram" to listOf("com.instagram.android"),
        "spotify" to listOf("com.spotify.music"),
        "maps" to listOf("com.google.android.apps.maps"),
        "google maps" to listOf("com.google.android.apps.maps"),
        "gmail" to listOf("com.google.android.gm"),
        "chrome" to listOf("com.android.chrome"),
        "calculator" to listOf("com.google.android.calculator", "com.android.calculator2"),
        "calendar" to listOf("com.google.android.calendar"),
        "clock" to listOf("com.google.android.deskclock"),
        "alarm" to listOf("com.google.android.deskclock"),
        "messages" to listOf("com.google.android.apps.messaging")
    )

    suspend fun executeTool(name: String, args: Map<String, String>): JsonObject {
        onDiagnosticLog(TAG, "Executing tool '$name' with args=$args", LogSeverity.TOOL)
        return when (name) {
            "openWhatsApp" -> openWhatsApp()
            "openApp" -> openApp(args["appName"].orEmpty())
            "openUrl" -> openUrl(args["url"].orEmpty())
            "makeCall" -> makeCall(args["phoneNumber"].orEmpty())
            "callContact" -> callContact(args["contactName"].orEmpty())
            else -> {
                val msg = "Unsupported tool: $name"
                Log.w(TAG, msg)
                onDiagnosticLog(TAG, msg, LogSeverity.ERROR)
                buildErrorResult(name, msg)
            }
        }
    }

    private fun openWhatsApp(): JsonObject {
        val packages = listOf("com.whatsapp", "com.whatsapp.w4b")
        for (pkg in packages) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                return launchIntentSafely(
                    actionName = "openWhatsApp",
                    intent = launchIntent,
                    successSummary = "Opened WhatsApp"
                )
            }
        }
        val err = "WhatsApp is not installed on this device."
        onActionFeedback(DeviceActionFeedback("openWhatsApp", err, false))
        onDiagnosticLog(TAG, err, LogSeverity.ERROR)
        return buildErrorResult("openWhatsApp", err)
    }

    private fun openApp(rawAppName: String): JsonObject {
        val normalized = rawAppName.trim().lowercase()
        if (normalized.isBlank()) {
            return buildErrorResult("openApp", "No application name was provided.")
        }

        // System-safe intent shortcuts
        when (normalized) {
            "camera" -> {
                return launchIntentSafely(
                    actionName = "openApp",
                    intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE),
                    successSummary = "Opened Camera"
                )
            }
            "settings" -> {
                return launchIntentSafely(
                    actionName = "openApp",
                    intent = Intent(Settings.ACTION_SETTINGS),
                    successSummary = "Opened Settings"
                )
            }
            "phone", "dialer" -> {
                return launchIntentSafely(
                    actionName = "openApp",
                    intent = Intent(Intent.ACTION_DIAL),
                    successSummary = "Opened Phone dialer"
                )
            }
            "alarm", "alarms" -> {
                val alarmIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
                if (alarmIntent.resolveActivity(context.packageManager) != null) {
                    return launchIntentSafely("openApp", alarmIntent, "Opened Clock Alarms")
                }
            }
        }

        val candidates = appAllowlist[normalized]
        if (candidates == null) {
            val err = "App '$rawAppName' is not in the supported safe app list."
            onActionFeedback(DeviceActionFeedback("openApp", err, false))
            onDiagnosticLog(TAG, err, LogSeverity.ERROR)
            return buildErrorResult("openApp", err)
        }

        for (pkg in candidates) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                return launchIntentSafely(
                    actionName = "openApp",
                    intent = launchIntent,
                    successSummary = "Opened $rawAppName"
                )
            }
        }

        val err = "$rawAppName is not installed on this device."
        onActionFeedback(DeviceActionFeedback("openApp", err, false))
        onDiagnosticLog(TAG, err, LogSeverity.ERROR)
        return buildErrorResult("openApp", err)
    }

    private fun openUrl(rawUrl: String): JsonObject {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) {
            return buildErrorResult("openUrl", "URL cannot be empty.")
        }

        val normalized = if (
            trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("http://", ignoreCase = true)
        ) {
            trimmed
        } else if (!trimmed.contains(":")) {
            "https://$trimmed"
        } else {
            val err = "Blocked unsafe URL scheme in '$trimmed'. Only HTTPS/HTTP URLs are allowed."
            onActionFeedback(DeviceActionFeedback("openUrl", err, false))
            onDiagnosticLog(TAG, err, LogSeverity.ERROR)
            return buildErrorResult("openUrl", err)
        }

        val uri = try {
            Uri.parse(normalized)
        } catch (e: Exception) {
            return buildErrorResult("openUrl", "Invalid URL: ${e.message}")
        }

        val scheme = uri.scheme?.lowercase()
        if ((scheme != "https" && scheme != "http") || uri.host.isNullOrBlank()) {
            val err = "Invalid or unsafe URL: '$rawUrl'."
            onActionFeedback(DeviceActionFeedback("openUrl", err, false))
            onDiagnosticLog(TAG, err, LogSeverity.ERROR)
            return buildErrorResult("openUrl", err)
        }

        val intent = Intent(Intent.ACTION_VIEW, uri)
        return launchIntentSafely(
            actionName = "openUrl",
            intent = intent,
            successSummary = "Opened ${uri.host}"
        )
    }

    private fun makeCall(rawPhoneNumber: String): JsonObject {
        val cleaned = sanitizePhoneNumber(rawPhoneNumber)
        val digitCount = cleaned.count { it.isDigit() }
        if (digitCount < 3 || cleaned.length > 20) {
            val err = "Invalid phone number: '$rawPhoneNumber'."
            onActionFeedback(DeviceActionFeedback("makeCall", err, false))
            onDiagnosticLog(TAG, err, LogSeverity.ERROR)
            return buildErrorResult("makeCall", err)
        }

        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleaned"))
        val launched = launchIntentSafely(
            actionName = "makeCall",
            intent = dialIntent,
            successSummary = "Dialer ready: $cleaned"
        )
        return if (launched["success"]?.toString() == "true") {
            buildJsonObject {
                put("success", true)
                put("action", "makeCall")
                put("phoneNumber", cleaned)
                put("status", "dialer_opened")
                put("message", "Opened the phone dialer with $cleaned. The user can tap Call to start the call.")
            }
        } else {
            launched
        }
    }

    private suspend fun callContact(rawContactName: String): JsonObject {
        val contactName = rawContactName.trim()
        if (contactName.isBlank()) {
            return buildErrorResult("callContact", "Contact name cannot be empty.")
        }

        if (!contactService.hasContactsPermission()) {
            onDiagnosticLog(TAG, "Requesting READ_CONTACTS permission for '$contactName'", LogSeverity.TOOL)
            val granted = onRequestContactsPermission()
            if (!granted) {
                val err = "Contacts permission was denied, so I cannot search contacts for '$contactName'."
                onActionFeedback(DeviceActionFeedback("callContact", err, false))
                onDiagnosticLog(TAG, err, LogSeverity.ERROR)
                return buildErrorResult("callContact", err)
            }
        }

        return when (val searchResult = contactService.searchByName(contactName)) {
            is ContactSearchResult.PermissionRequired -> {
                buildErrorResult("callContact", "Contacts permission is required to search for '$contactName'.")
            }
            is ContactSearchResult.NotFound -> {
                val err = "No contact named '${searchResult.query}' was found on this device."
                onActionFeedback(DeviceActionFeedback("callContact", err, false))
                buildJsonObject {
                    put("success", false)
                    put("action", "callContact")
                    put("reason", "not_found")
                    put("error", err)
                }
            }
            is ContactSearchResult.MultipleMatches -> {
                val names = searchResult.matches.map { "${it.name} (${it.phoneNumber})" }
                onActionFeedback(
                    DeviceActionFeedback(
                        "callContact",
                        "Multiple contacts found for '${searchResult.query}'",
                        false
                    )
                )
                buildJsonObject {
                    put("success", false)
                    put("action", "callContact")
                    put("reason", "multiple_matches")
                    put(
                        "matches",
                        JsonArray(searchResult.matches.map { match ->
                            buildJsonObject {
                                put("name", match.name)
                                put("phoneNumber", match.phoneNumber)
                            }
                        })
                    )
                    put(
                        "error",
                        "Found multiple contacts matching '${searchResult.query}': ${names.joinToString(", ")}. Ask the user which one to call; do not guess."
                    )
                }
            }
            is ContactSearchResult.SingleMatch -> {
                val contact = searchResult.contact
                val cleaned = sanitizePhoneNumber(contact.phoneNumber)
                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleaned"))
                val launched = launchIntentSafely(
                    actionName = "callContact",
                    intent = dialIntent,
                    successSummary = "Dialer ready: ${contact.name}"
                )
                if (launched["success"]?.toString() == "true") {
                    buildJsonObject {
                        put("success", true)
                        put("action", "callContact")
                        put("contactName", contact.name)
                        put("phoneNumber", cleaned)
                        put("status", "dialer_opened")
                        put("message", "Opened the phone dialer for ${contact.name} ($cleaned).")
                    }
                } else {
                    launched
                }
            }
            is ContactSearchResult.Error -> {
                onActionFeedback(DeviceActionFeedback("callContact", searchResult.message, false))
                buildErrorResult("callContact", searchResult.message)
            }
        }
    }

    private fun launchIntentSafely(
        actionName: String,
        intent: Intent,
        successSummary: String
    ): JsonObject {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            val resolved = intent.resolveActivity(context.packageManager)
            if (resolved == null) {
                val err = "No supported app found on this device to handle $actionName."
                Log.w(TAG, err)
                onActionFeedback(DeviceActionFeedback(actionName, err, false))
                onDiagnosticLog(TAG, err, LogSeverity.ERROR)
                return buildErrorResult(actionName, err)
            }
            context.startActivity(intent)
            Log.i(TAG, "Successfully launched $actionName -> $successSummary")
            onActionFeedback(DeviceActionFeedback(actionName, successSummary, true))
            onDiagnosticLog(TAG, "Action succeeded: $successSummary", LogSeverity.TOOL)
            buildJsonObject {
                put("success", true)
                put("action", actionName)
                put("message", successSummary)
            }
        } catch (e: Exception) {
            val err = "Could not execute $actionName: ${e.localizedMessage ?: "Activity launch failed"}"
            Log.e(TAG, err, e)
            onActionFeedback(DeviceActionFeedback(actionName, err, false))
            onDiagnosticLog(TAG, err, LogSeverity.ERROR)
            buildErrorResult(actionName, err)
        }
    }

    private fun sanitizePhoneNumber(raw: String): String {
        val trimmed = raw.trim()
        val builder = StringBuilder()
        trimmed.forEachIndexed { index, c ->
            if (c.isDigit()) {
                builder.append(c)
            } else if (c == '+' && index == 0) {
                builder.append(c)
            }
        }
        return builder.toString()
    }

    private fun buildErrorResult(action: String, error: String): JsonObject = buildJsonObject {
        put("success", false)
        put("action", action)
        put("error", error)
    }
}
