package com.pulso.tv.ui.pulso

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulso.tv.util.C
import com.pulso.tv.util.prefs

/**
 * Settings: a short list of rows, each one cycles through its values on OK.
 * Only options the engine really reads are offered.
 */
class PulsoSettingsActivity : ComponentActivity() {

    private class Choice(val label: String, val value: String)

    // stored in the engine's own preference so the player picks it up
    private val qualities = listOf(
        Choice("Automático", "auto"),
        Choice("Última usada", "saved"),
        Choice("Fonte", "Source"),
        Choice("1080p", "1080p60"),
        Choice("720p", "720p60"),
        Choice("480p", "480p"),
        Choice("360p", "360p"),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = PulsoFont)) {
                SettingsScreen()
            }
        }
    }

    @Composable
    private fun SettingsScreen() {
        val prefs = prefs()
        var quality by remember {
            mutableStateOf((prefs.getString(C.PLAYER_DEFAULT_QUALITY, "auto") ?: "auto").substringBefore(" "))
        }
        var chatText by remember { mutableStateOf(prefs.getInt(PREF_CHAT_TEXT, 14)) }
        var emotes by remember { mutableStateOf(prefs.getBoolean(C.CHAT_ENABLE_STV, true)) }

        val qualityLabel = qualities.firstOrNull { it.value == quality }?.label ?: quality
        val sizes = listOf(12 to "Pequeno", 14 to "Médio", 18 to "Grande")

        Box(Modifier.fillMaxSize().background(Pulso.Background)) {
            Column(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = Pulso.Dp.safeH, top = Pulso.Dp.safeV)
                    .width(520.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Configurações", color = Pulso.TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "OK muda o valor da opção.", color = Pulso.TextSecondary, fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                PulsoPill("Qualidade padrão: $qualityLabel", height = 44.dp, modifier = Modifier.fillMaxWidth()) {
                    val i = qualities.indexOfFirst { it.value == quality }
                    val next = qualities[(i + 1).mod(qualities.size)]
                    quality = next.value
                    prefs.edit().putString(C.PLAYER_DEFAULT_QUALITY, next.value).apply()
                }
                PulsoPill(
                    "Texto do chat: " + (sizes.firstOrNull { it.first == chatText }?.second ?: "Médio"),
                    height = 44.dp, modifier = Modifier.fillMaxWidth(),
                ) {
                    val i = sizes.indexOfFirst { it.first == chatText }
                    val next = sizes[(i + 1).mod(sizes.size)].first
                    chatText = next
                    prefs.edit().putInt(PREF_CHAT_TEXT, next).apply()
                }
                PulsoPill(
                    "Emotes 7TV, BTTV e FFZ: " + if (emotes) "Ligado" else "Desligado",
                    height = 44.dp, modifier = Modifier.fillMaxWidth(),
                ) {
                    emotes = !emotes
                    prefs.edit()
                        .putBoolean(C.CHAT_ENABLE_STV, emotes)
                        .putBoolean(C.CHAT_ENABLE_BTTV, emotes)
                        .putBoolean(C.CHAT_ENABLE_FFZ, emotes)
                        .apply()
                }
                PulsoPill("Voltar", primary = true, height = 44.dp, modifier = Modifier.fillMaxWidth()) { finish() }
            }
        }
    }

    companion object {
        const val PREF_CHAT_TEXT = "pulso_chat_text"
    }
}
