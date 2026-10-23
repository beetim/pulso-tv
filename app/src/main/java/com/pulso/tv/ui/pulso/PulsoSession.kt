package com.pulso.tv.ui.pulso

import android.content.Context
import com.pulso.tv.PulsoApp
import com.pulso.tv.util.C
import com.pulso.tv.util.TwitchApiHelper
import com.pulso.tv.util.prefs
import com.pulso.tv.util.tokenPrefs
import kotlinx.coroutines.CancellationException
import java.net.URLEncoder

/**
 * Keeps the Twitch sign-in alive: checks the saved token and, when Twitch says it expired,
 * trades the refresh token for a new one. Without this the app kept showing a signed-in
 * user whose requests silently failed until the next manual login.
 */
object PulsoSession {

    private const val REFRESH_KEY = "pulso_refresh_token"
    private const val CHECK_INTERVAL_MS = 60 * 60 * 1000L
    private var lastCheck = 0L

    fun saveRefreshToken(context: Context, token: String?) {
        context.tokenPrefs().edit().putString(REFRESH_KEY, token).apply()
    }

    fun clear(context: Context) = saveRefreshToken(context, null)

    /**
     * True while the session is usable (or could not be checked, e.g. no network);
     * false only when the token is dead and could not be renewed: the person must sign in again.
     */
    suspend fun ensureValid(app: PulsoApp, force: Boolean = false): Boolean {
        val token = app.tokenPrefs().getString(C.GQL_TOKEN2, null)
        if (token.isNullOrBlank()) return true
        val now = System.currentTimeMillis()
        if (!force && now - lastCheck < CHECK_INTERVAL_MS) return true
        val auth = app.pulsoModule.authRepository
        val network = app.prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP)

        val check = runCatching { auth.validate(network, TwitchApiHelper.addTokenPrefixGQL(token)) }
        check.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        if (check.isSuccess) {
            lastCheck = now
            return true
        }
        // only an explicit 401 means "expired"; anything else is a network hiccup
        if (check.exceptionOrNull()?.message != "401") return true

        val refresh = app.tokenPrefs().getString(REFRESH_KEY, null)
        if (refresh.isNullOrBlank()) return false
        val clientId = app.prefs().getString(C.GQL_CLIENT_ID2, null)
        if (clientId.isNullOrBlank()) return false
        val renewed = runCatching {
            auth.getToken(
                network,
                "client_id=$clientId&grant_type=refresh_token&refresh_token=" + URLEncoder.encode(refresh, "UTF-8")
            )
        }
        renewed.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        val response = renewed.getOrNull() ?: return true // could not reach Twitch: try again later
        val newToken = response.token
        if (newToken.isNullOrBlank()) return false
        app.tokenPrefs().edit().putString(C.GQL_TOKEN2, newToken).apply()
        saveRefreshToken(app, response.refreshToken ?: refresh)
        lastCheck = now
        return true
    }
}
