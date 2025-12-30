package com.aitextassistant

enum class AIAction(
    val displayName: String,
    val prompt: String
) {

    FIX_GRAMMAR(
        "Fix Grammar",
        "Correct grammar, spelling, and clarity. Preserve meaning and tone. Do not add new ideas."
    ),

    REWRITE(
        "Rewrite",
        "Rewrite this text to improve clarity and flow while preserving the core message."
    ),

    SIMPLIFY(
        "Simplify",
        "Simplify this text to make it easier to understand. Use simpler words and shorter sentences."
    ),

    TRANSLATE(
        "Translate",
        "If this text is in Hindi, translate it to English. If it's in English, translate it to Hindi."
    ),

    SUMMARIZE(
        "Summarize",
        "Summarize this text in exactly 3 lines or less. Be concise and capture the main points."
    ),

    EXPAND(
        "Expand with examples",
        "Expand this text with relevant examples and additional explanation to make it more comprehensive."
    ),

    PROFESSIONAL_EMAIL(
        "Professional Email",
        "Rewrite this text as a professional email with proper greeting and sign-off."
    ),

    CREATE_TWEET(
        "Create Tweet",
        "Transform this into an engaging tweet. Maximum 280 characters. Include relevant hashtags if appropriate."
    ),

    CUSTOM(
        "Custom Instruction",
        "" // User-provided instruction will be injected dynamically
    )
}

enum class ToneModifier(
    val displayName: String,
    val modifier: String
) {

    NONE("None", ""),

    FORMAL("Formal", "Use a formal tone."),

    CASUAL("Casual", "Use a casual, friendly tone."),

    EMOTIONAL("Emotional", "Make it more emotional and expressive."),

    NEUTRAL("Neutral", "Keep the tone neutral and objective."),

    PROFESSIONAL("Professional", "Use a professional business tone."),

    PERSUASIVE("Persuasive", "Make it more persuasive and convincing.")
}
