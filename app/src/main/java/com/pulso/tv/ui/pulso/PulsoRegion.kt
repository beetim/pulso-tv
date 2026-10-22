package com.pulso.tv.ui.pulso

import com.pulso.tv.graphql.type.Language
import java.util.Locale

/**
 * Which broadcast language is listed first: the language the user chose in the app
 * ([PulsoLang]); if Twitch has no channels in it, the system language (a Brazilian TV
 * sees Portuguese first).
 */
object PulsoRegion {
    /** Twitch broadcaster language code, e.g. "PT" for Portuguese; Android's legacy codes are mapped. */
    val code: String
        get() {
            val app = normalize(PulsoLang.current)
            if (Language.entries.any { it.rawValue == app }) return app
            return normalize(Locale.getDefault().language)
        }

    /** Android still reports old ISO codes for some languages (in = Indonesian, iw = Hebrew). */
    private fun normalize(language: String): String = when (language.lowercase()) {
        "in" -> "ID"
        "iw" -> "HE"
        "ji" -> "YI"
        else -> language.uppercase()
    }

    /** Same filter in the two shapes the data sources take. */
    val queryLanguages: List<Language>? get() = code.let { c -> Language.entries.find { it.rawValue == c } }?.let { listOf(it) }
    val rawLanguages: List<String>? get() = if (queryLanguages != null) listOf(code) else null
}
