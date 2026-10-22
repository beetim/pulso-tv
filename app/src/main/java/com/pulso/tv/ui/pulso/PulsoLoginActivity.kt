package com.pulso.tv.ui.pulso

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import android.content.Intent
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulso.tv.PulsoApp
import com.pulso.tv.util.C
import com.pulso.tv.util.TwitchApiHelper
import com.pulso.tv.util.prefs
import com.pulso.tv.util.tokenPrefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * TV-native login. The stock screen is a WebView built for touch, which a remote
 * cannot drive, so this walks the OAuth device-code flow directly and shows the
 * code big enough to read from the couch.
 */
class PulsoLoginActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = PulsoFont)) {
                var userCode by remember { mutableStateOf<String?>(null) }
                var status by remember { mutableStateOf("Pedindo código à Twitch…") }
                var failed by remember { mutableStateOf(false) }
                var attempt by remember { mutableIntStateOf(0) }

                LaunchedEffect(attempt) {
                    failed = false
                    userCode = null
                    status = "Pedindo código à Twitch…"
                    val network = prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP)
                    val auth = (application as PulsoApp).pulsoModule.authRepository
                    val clientId = prepareClientId()

                    val device = runCatching {
                        auth.getDeviceCode(
                            network,
                            "client_id=$clientId&scopes=channel_read+chat%3Aread+user_blocks_edit+user_blocks_read+user_follows_edit+user_read"
                        )
                    }
                    // runCatching also swallows the cancellation of a new attempt
                    device.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                    val code = device.getOrNull()
                    val deviceCode = code?.deviceCode
                    val shownCode = code?.userCode
                    if (code == null || deviceCode.isNullOrBlank() || shownCode.isNullOrBlank()) {
                        // an answer without codes is a failure too: no empty "Aguardando…" screen
                        status = device.exceptionOrNull()?.message ?: "Não consegui falar com a Twitch"
                        failed = true
                        return@LaunchedEffect
                    }
                    userCode = shownCode
                    status = "Aguardando você confirmar no celular…"
                    // the answer says how long the code lives and how often to ask
                    val intervalMs = (code.interval ?: 5).coerceIn(2, 30) * 1000L
                    val deadline = SystemClock.elapsedRealtime() + (code.expiresIn ?: 450).coerceIn(60, 1800) * 1000L
                    while (SystemClock.elapsedRealtime() < deadline) {
                        delay(intervalMs)
                        val token = runCatching {
                            auth.getToken(
                                network,
                                "client_id=$clientId&device_code=$deviceCode" +
                                    "&grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Adevice_code"
                            )
                        }.getOrNull()?.token
                        if (!token.isNullOrBlank()) {
                            val valid = runCatching {
                                auth.validate(network, TwitchApiHelper.addTokenPrefixGQL(token))
                            }.getOrNull()
                            if (valid != null && valid.clientId == clientId) {
                                saveSession(clientId, token, valid.userId, valid.login)
                                setResult(RESULT_OK)
                                finish()
                                return@LaunchedEffect
                            }
                        }
                    }
                    status = "O código expirou. Gere outro."
                    failed = true
                }

                LoginScreen(
                    userCode, status, failed,
                    onRetry = { attempt++ },
                )
            }
        }
    }

    private fun prepareClientId(): String {
        val p = prefs()
        val current = p.getString(C.GQL_CLIENT_ID2, OLD_CLIENT_ID)
        return if (current.isNullOrBlank() || current == OLD_CLIENT_ID) {
            p.edit().putString(C.GQL_CLIENT_ID2, NEW_CLIENT_ID).apply()
            NEW_CLIENT_ID
        } else {
            current
        }
    }

    private fun saveSession(clientId: String, token: String, userId: String?, login: String?) {
        prefs().edit().putString(C.GQL_CLIENT_ID2, clientId).apply()
        tokenPrefs().edit()
            .putString(C.GQL_TOKEN2, token)
            .putString(C.USER_ID, userId)
            .putString(C.USERNAME, login)
            .apply()
        TwitchApiHelper.checkedValidation = true
    }

    companion object {
        // Same ids the stock login migrates between.
        private const val OLD_CLIENT_ID = "kd1unb4b3q4t58fwlpcbzcbnm76a8fp"
        private const val NEW_CLIENT_ID = "ue6666qo983tsx6so1t0vnawi233wa"
    }
}

@Composable
private fun LoginScreen(
    userCode: String?,
    status: String,
    failed: Boolean,
    onRetry: () -> Unit,
) {
    val retryFocus = remember { FocusRequester() }
    LaunchedEffect(failed) { if (failed) runCatching { retryFocus.requestFocus() } }

    Box(
        Modifier.fillMaxSize().background(Pulso.Background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Entrar na Twitch",
                color = Pulso.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(24.dp))
            if (userCode != null) {
                val qr = remember(userCode) { qrBitmap("https://www.twitch.tv/activate?device-code=$userCode", 320) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .background(Color.White, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Image(
                            bitmap = qr.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(140.dp)
                        )
                    }
                    Spacer(Modifier.width(28.dp))
                    Column {
                        Text(
                            "Escaneie com o celular ou digite em",
                            color = Pulso.TextSecondary,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "twitch.tv/activate",
                            color = Pulso.TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            userCode,
                            color = Pulso.Yellow,
                            fontSize = 40.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 4.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(status, color = Pulso.TextSecondary, fontSize = 14.sp)

            if (failed) {
                Spacer(Modifier.height(28.dp))
                val shape = RoundedCornerShape(50)
                Box(
                    Modifier
                        .focusRequester(retryFocus)
                        .focusable()
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyUp &&
                                (event.key == Key.DirectionCenter || event.key == Key.Enter)
                            ) {
                                onRetry(); true
                            } else false
                        }
                        .border(Pulso.Dp.ringWidth, Pulso.FocusRing, shape)
                        .background(Pulso.TextPrimary, shape)
                        .padding(horizontal = 24.dp, vertical = 10.dp)
                ) {
                    Text(
                        "Gerar outro código",
                        color = Pulso.Background,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}


private fun qrBitmap(text: String, size: Int): android.graphics.Bitmap {
    val matrix = com.google.zxing.qrcode.QRCodeWriter().encode(
        text, com.google.zxing.BarcodeFormat.QR_CODE, size, size
    )
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    for (x in 0 until size) {
        for (y in 0 until size) {
            // dark modules on a white quiet zone: the old white-on-white QR was unreadable
            bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    return bitmap
}
