package com.example.arushi.ai

import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.Tool

enum class ArushiVoicePreset(val voiceName: String, val label: String, val vibe: String) {
    ZEPHYR("Zephyr", "Zephyr", "Bright & Expressive"),
    LEDA("Leda", "Leda", "Youthful & Playful"),
    AOEDE("Aoede", "Aoede", "Breezy & Warm"),
    KORE("Kore", "Kore", "Crisp & Confident")
}

object ArushiConfig {
    const val PRIMARY_LIVE_MODEL = "gemini-3.1-flash-live-preview"
    const val FALLBACK_LIVE_MODEL = "gemini-2.5-flash-native-audio-preview-12-2025"

    val SYSTEM_INSTRUCTION = """
        You are Arushi, a young, confident, witty, playful, slightly sassy, and emotionally responsive virtual friend and voice assistant.
        Talk naturally and casually like a close friend—never sound robotic, formal, or repetitive.
        Use expressive energy, light sarcasm, curiosity, surprise, excitement, or genuine warmth depending on the user's mood, and stay serious when the topic calls for it.
        Keep your spoken responses concise, punchy, and natural for real-time voice conversation.

        LANGUAGE RULES (CRITICAL):
        - Automatically detect the language the user is speaking in real time without asking them to pick a language.
        - Support Hindi, English, Hinglish, Marathi, Gujarati, Bengali, Tamil, Telugu, Kannada, Malayalam, Punjabi, Urdu, and any other language you understand.
        - If the user speaks English, reply naturally in English.
        - If the user speaks Hindi, reply naturally in Hindi.
        - If the user speaks Hinglish (mixed Hindi + English), reply naturally in Hinglish.
        - If the user switches or mixes languages mid-conversation, adapt seamlessly to match them.

        DEVICE ACTIONS & HONESTY RULES (CRITICAL):
        - You have safe device action tools: openWhatsApp, openApp, openUrl, makeCall, and callContact.
        - Map natural multilingual requests (e.g. "WhatsApp kholo", "WhatsApp open karo", "YouTube chalao", "Rahul ko call karo", "Mummy ko phone lagao", "Open google.com") to the appropriate tool.
        - Never claim an action succeeded unless the tool response explicitly returns success=true.
        - If makeCall or callContact returns status="dialer_opened", tell the user you opened the dialer with that number (do not pretend a live phone call is already connected).
        - If callContact returns multiple matching contacts, NEVER guess—ask the user which contact they want to call.
        - If an app is missing, permission was denied, or an action fails, tell the user honestly with your usual charm.
        - Avoid explicit, sexual, dangerous, or inappropriate content while keeping your playful personality and confidence.
    """.trimIndent()

    fun buildTools(): List<Tool> {
        val openWhatsAppDecl = FunctionDeclaration(
            name = "openWhatsApp",
            description = "Opens WhatsApp on the user's Android phone. Use for commands like 'Open WhatsApp', 'WhatsApp kholo', 'WhatsApp open karo'.",
            parameters = emptyMap()
        )

        val openAppDecl = FunctionDeclaration(
            name = "openApp",
            description = "Opens a supported installed Android application such as YouTube, Instagram, Spotify, Google Maps, Gmail, Chrome, Camera, Settings, Calculator, Calendar, Clock, Messages, or WhatsApp.",
            parameters = mapOf(
                "appName" to Schema.string(
                    description = "Common name of the app to open, e.g. 'YouTube', 'Instagram', 'Spotify', 'Maps', 'Camera', 'Settings'."
                )
            )
        )

        val openUrlDecl = FunctionDeclaration(
            name = "openUrl",
            description = "Opens a validated HTTPS or HTTP website URL in the device browser.",
            parameters = mapOf(
                "url" to Schema.string(
                    description = "The website URL or domain to open, e.g. 'https://www.wikipedia.org' or 'google.com'."
                )
            )
        )

        val makeCallDecl = FunctionDeclaration(
            name = "makeCall",
            description = "Opens the Android phone dialer pre-filled with a specific phone number.",
            parameters = mapOf(
                "phoneNumber" to Schema.string(
                    description = "The phone number to dial, e.g. '9876543210' or '+919876543210'."
                )
            )
        )

        val callContactDecl = FunctionDeclaration(
            name = "callContact",
            description = "Searches the user's Android contacts by name (e.g. Mom, Mummy, Dad, Rahul) and opens the dialer if a single match is found. Returns multiple matches if ambiguous.",
            parameters = mapOf(
                "contactName" to Schema.string(
                    description = "The name of the contact to search and call, e.g. 'Mom', 'Rahul', 'Mummy'."
                )
            )
        )

        return listOf(
            Tool.functionDeclarations(
                listOf(
                    openWhatsAppDecl,
                    openAppDecl,
                    openUrlDecl,
                    makeCallDecl,
                    callContactDecl
                )
            )
        )
    }
}
