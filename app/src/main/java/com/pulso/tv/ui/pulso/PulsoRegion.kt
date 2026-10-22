package com.pulso.tv.ui.pulso

import com.pulso.tv.graphql.type.Language
import java.util.Locale

/**
 * Where the user is, as far as the TV tells us: the system language picks the
 * broadcast language that is listed first (a Brazilian TV sees Portuguese first).
 */
object PulsoRegion {
    /** Twitch broadcaster language code, e.g. "PT" for pt-BR. */
    val code: String get() = Locale.getDefault().language.uppercase()

    /** Same filter in the two shapes the data sources take. */
    val queryLanguages: List<Language>? get() = Language.entries.find { it.rawValue == code }?.let { listOf(it) }
    val rawLanguages: List<String>? get() = if (queryLanguages != null) listOf(code) else null
}
