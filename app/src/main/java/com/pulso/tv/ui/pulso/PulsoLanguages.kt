package com.pulso.tv.ui.pulso

/** Um idioma: o código e o nome escrito no próprio idioma. */
data class PulsoLanguage(val code: String, val endonym: String)

/** Ordem em que o card de escolha mostra os idiomas. */
internal val PULSO_LANGUAGES: List<PulsoLanguage> = listOf(
    PulsoLanguage("pt", "Português"),
    PulsoLanguage("en", "English"),
    PulsoLanguage("es", "Español"),
    PulsoLanguage("fr", "Français"),
    PulsoLanguage("de", "Deutsch"),
    PulsoLanguage("it", "Italiano"),
    PulsoLanguage("pl", "Polski"),
    PulsoLanguage("tr", "Türkçe"),
    PulsoLanguage("ru", "Русский"),
    PulsoLanguage("ja", "日本語"),
    PulsoLanguage("ko", "한국어"),
    PulsoLanguage("zh", "中文"),
)
