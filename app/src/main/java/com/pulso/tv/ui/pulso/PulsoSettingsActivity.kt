package com.pulso.tv.ui.pulso

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulso.tv.util.C
import com.pulso.tv.util.prefs

/**
 * Settings: grouped rows (name on the left, current value on the right); OK on a row changes its value.
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
        var chatOnOpen by remember { mutableStateOf(prefs.getBoolean(PREF_CHAT_ON_OPEN, true)) }
        var preview by remember { mutableStateOf(prefs.getBoolean(PREF_PREVIEW, true)) }
        var adblock by remember { mutableStateOf(prefs.getBoolean(PREF_ADBLOCK, true)) }

        val qualityLabel = qualities.firstOrNull { it.value == quality }?.label ?: quality
        val sizes = listOf(12 to "Pequeno", 14 to "Médio", 18 to "Grande")
        fun onOff(v: Boolean) = if (v) "Ligado" else "Desligado"

        Box(Modifier.fillMaxSize().background(Pulso.Background)) {
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxHeight()
                    .width(640.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(top = Pulso.Dp.safeV, bottom = Pulso.Dp.safeV),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Configurações", color = Pulso.TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "OK muda o valor da opção.", color = Pulso.TextSecondary, fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 4.dp),
                )

                SectionTitle("Player")
                SettingRow("Qualidade padrão", qualityLabel) {
                    val i = qualities.indexOfFirst { it.value == quality }
                    val next = qualities[(i + 1).mod(qualities.size)]
                    quality = next.value
                    prefs.edit().putString(C.PLAYER_DEFAULT_QUALITY, next.value).apply()
                }
                SettingRow("Bloquear anúncios", onOff(adblock), on = adblock) {
                    adblock = !adblock
                    prefs.edit()
                        .putBoolean(PREF_ADBLOCK, adblock)
                        .putBoolean(C.PLAYER_USE_VIDEO_SWAP, adblock)
                        .putBoolean(C.PLAYER_HIDE_ADS, adblock)
                        .apply()
                }

                SectionTitle("Chat")
                SettingRow("Chat ao abrir o player", onOff(chatOnOpen), on = chatOnOpen) {
                    chatOnOpen = !chatOnOpen
                    prefs.edit().putBoolean(PREF_CHAT_ON_OPEN, chatOnOpen).apply()
                }
                SettingRow("Tamanho do texto", sizes.firstOrNull { it.first == chatText }?.second ?: "Médio") {
                    val i = sizes.indexOfFirst { it.first == chatText }
                    val next = sizes[(i + 1).mod(sizes.size)].first
                    chatText = next
                    prefs.edit().putInt(PREF_CHAT_TEXT, next).apply()
                }
                SettingRow("Emotes", onOff(emotes), on = emotes) {
                    emotes = !emotes
                    prefs.edit()
                        .putBoolean(C.CHAT_ENABLE_STV, emotes)
                        .putBoolean(C.CHAT_ENABLE_BTTV, emotes)
                        .putBoolean(C.CHAT_ENABLE_FFZ, emotes)
                        .apply()
                }

                SectionTitle("Geral")
                SettingRow("Prévia nos cards", onOff(preview), on = preview) {
                    preview = !preview
                    prefs.edit().putBoolean(PREF_PREVIEW, preview).apply()
                }

                Spacer(Modifier.height(8.dp))
                PulsoPill("Voltar", primary = true, height = 44.dp, modifier = Modifier.fillMaxWidth()) { finish() }
            }
        }
    }

    @Composable
    private fun SectionTitle(text: String) {
        Text(
            text.uppercase(), color = Pulso.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp, modifier = Modifier.padding(top = 10.dp, start = 4.dp),
        )
    }

    /** One setting: name on the left, value on the right (yellow when the option is on). */
    @Composable
    private fun SettingRow(label: String, value: String, on: Boolean? = null, onClick: () -> Unit) {
        var focused by remember { mutableStateOf(false) }
        val latestClick by rememberUpdatedState(onClick)
        val shape = RoundedCornerShape(12.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .onFocusChanged { focused = it.isFocused }
                .focusable()
                .onKeyEvent { e ->
                    if (e.type == KeyEventType.KeyUp && (e.key == Key.DirectionCenter || e.key == Key.Enter)) {
                        latestClick(); true
                    } else false
                }
                .pointerInput(Unit) { detectTapGestures { latestClick() } }
                .then(if (focused) Modifier.border(Pulso.Dp.ringWidth, Pulso.FocusRing, shape) else Modifier)
                .background(if (focused) Pulso.SurfaceRaised else Pulso.Surface, shape)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label, color = Pulso.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f), maxLines = 1,
            )
            Text(
                value,
                color = if (on == true) Pulso.Yellow else Pulso.TextSecondary,
                fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            )
        }
    }

    companion object {
        const val PREF_CHAT_TEXT = "pulso_chat_text"
        const val PREF_CHAT_ON_OPEN = "pulso_chat_on_open"   // player opens with the chat visible (and connected)
        const val PREF_PREVIEW = "pulso_preview"             // live preview while a stream card has the focus
        const val PREF_ADBLOCK = "pulso_adblock"             // anonymous token first + video swap + hide ads
    }
}
