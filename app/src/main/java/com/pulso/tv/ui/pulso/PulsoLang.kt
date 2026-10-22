package com.pulso.tv.ui.pulso

import android.content.Context
import androidx.compose.material3.Text as M3Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import java.util.Locale

/** Which "há quanto tempo" bucket a date falls into. */
enum class PulsoAgoUnit { MINUTE, HOUR, DAY, WEEK, MONTH, YEAR }

/**
 * Pulso speaks twelve languages, but the screens are written in Portuguese — the source
 * language. Every string drawn through [Text] passes through [tr], so a language switch
 * never touches a screen: it just changes one observable value and everything redraws.
 *
 * Texts that mix numbers with words (viewer counts, "3 days ago") are not built by hand
 * in the screens; they come from [compact] and [ago], which know the rules of each
 * language. "pt" is the source and has no table.
 */
object PulsoLang {

    /** Every language, each named in its own language. */
    val all get() = PULSO_LANGUAGES

    /** "pt", "en", "ja"... Read inside composables, so changing it redraws the screen. */
    var current by mutableStateOf(defaultCode())
        private set

    private const val FILE = "pulso_prefs"
    private const val KEY = "lang"

    /** The active language's name, written in that language: "Português", "日本語". */
    val label: String get() = all.firstOrNull { it.code == current }?.endonym ?: current

    internal val table: PulsoLangTable? get() = LANG_TABLES[current]

    fun init(ctx: Context) {
        val saved = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null)
        current = saved?.takeIf { saved -> all.any { it.code == saved } } ?: defaultCode()
    }

    fun set(ctx: Context, code: String) {
        if (all.none { it.code == code }) return
        current = code
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, code).apply()
    }

    /** 48200 -> "48,2 mil" (pt) · "48.2K" (en) · "4.8万" (ja) · "48,2 тыс." (ru). */
    fun compact(n: Int): String {
        val v = n.coerceAtLeast(0)
        val style = table?.number ?: return ptCompact(v)
        if (v < style.divisor) return v.toString()
        val scaled = v.toDouble() / style.divisor
        val small = scaled < style.divisor
        val value = if (small) scaled else scaled / style.divisor
        val unit = if (small) style.small else style.big
        return String.format(Locale.US, "%.1f", value).replace('.', style.decimalSep.first()) + unit
    }

    fun justNow(): String = table?.ago?.justNow ?: "agora há pouco"

    /** "3 dias" in the active language, with the agreement that language needs. */
    fun ago(value: Long, unit: PulsoAgoUnit): String {
        val ago = table?.ago ?: return ptAgo(value, unit)
        return when (unit) {
            PulsoAgoUnit.MINUTE -> ago.minute(value)
            PulsoAgoUnit.HOUR -> ago.hour(value)
            PulsoAgoUnit.DAY -> ago.day(value)
            PulsoAgoUnit.WEEK -> ago.week(value)
            PulsoAgoUnit.MONTH -> ago.month(value)
            PulsoAgoUnit.YEAR -> ago.year(value)
        }
    }

    private fun defaultCode(): String {
        val code = Locale.getDefault().language
        return if (all.any { it.code == code }) code else "en"
    }
}

// ---- Portuguese, the source language (no table needed) -------------------------

private fun ptCompact(n: Int): String = when {
    n >= 1_000_000 -> String.format(Locale("pt", "BR"), "%.1f mi", n / 1_000_000f)
    n >= 1_000 -> String.format(Locale("pt", "BR"), "%.1f mil", n / 1_000f)
    else -> n.toString()
}

private fun ptAgo(value: Long, unit: PulsoAgoUnit): String = when (unit) {
    PulsoAgoUnit.MINUTE -> "há $value min"
    PulsoAgoUnit.HOUR -> "há $value " + if (value == 1L) "hora" else "horas"
    PulsoAgoUnit.DAY -> "há $value " + if (value == 1L) "dia" else "dias"
    PulsoAgoUnit.WEEK -> "há $value " + if (value == 1L) "semana" else "semanas"
    PulsoAgoUnit.MONTH -> "há $value " + if (value == 1L) "mês" else "meses"
    PulsoAgoUnit.YEAR -> "há $value " + if (value == 1L) "ano" else "anos"
}

/**
 * Russian counts in three forms — 1 час, 2 часа, 5 часов — and the generated tables
 * call this instead of spelling the rule out for every unit.
 */
@Suppress("unused")
internal fun pluralRu(n: Long, one: String, few: String, many: String): String = when {
    n % 10 == 1L && n % 100 != 11L -> one
    n % 10 in 2L..4L && (n % 100 < 10L || n % 100 >= 20L) -> few
    else -> many
}

/** Translates a Portuguese string into the active language; unknown text passes through. */
fun tr(s: String): String {
    val table = PulsoLang.table ?: return s
    table.exact[s]?.let { return it }
    var out = s
    for ((regex, replace) in table.rules) out = regex.replace(out, replace)
    return out
}

/** Drop-in for material3 Text that translates plain strings. */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
) {
    M3Text(
        text = tr(text),
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        letterSpacing = letterSpacing,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
    )
}