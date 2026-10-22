package com.pulso.tv.ui.pulso

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.pulso.tv.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Self-updater: asks the GitHub Releases of [BuildConfig.GITHUB_REPO] for the newest
 * version and, when there is one, downloads the APK and hands it to the system installer.
 * Android never lets a regular app install silently, so the person always confirms the
 * installation on the system screen. Does nothing while GITHUB_REPO is empty.
 */
object PulsoUpdater {

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val version: String, val url: String, val size: Long, val notes: String) : State
        data class Downloading(val percent: Int) : State
        data class Failed(val reason: String) : State
    }

    var state by mutableStateOf<State>(State.Idle)
        private set

    /** The person tapped "Depois": hide the Home shortcut until the next version. */
    var dismissed by mutableStateOf(false)

    val enabled: Boolean get() = BuildConfig.GITHUB_REPO.isNotBlank()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastCheck = 0L

    /** Cheap automatic check: at most once every 6 hours per process. */
    fun checkIfDue(context: Context) {
        if (!enabled) return
        if (System.currentTimeMillis() - lastCheck < CHECK_INTERVAL_MS) return
        check(context, quiet = true)
    }

    /** Manual check ("Verificar atualizações"): always asks and shows the progress. */
    fun checkNow(context: Context) = check(context, quiet = false)

    private fun check(context: Context, quiet: Boolean) {
        if (!enabled || state is State.Checking || state is State.Downloading) return
        lastCheck = System.currentTimeMillis()
        if (!quiet) state = State.Checking
        scope.launch {
            val next = runCatching { fetchLatest() }.getOrElse {
                if (quiet) state else State.Failed("Sem conexão com o GitHub")
            }
            if (next is State.Available && next.version != (state as? State.Available)?.version) dismissed = false
            state = next
        }
    }

    private fun fetchLatest(): State {
        val conn = open("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest")
        try {
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            if (conn.responseCode == 404) return State.UpToDate // no release published yet
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = json.optString("tag_name").removePrefix("v")
            if (tag.isEmpty() || !isNewer(tag, BuildConfig.VERSION_NAME)) return State.UpToDate
            val assets = json.optJSONArray("assets") ?: return State.UpToDate
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                    val url = asset.optString("browser_download_url")
                    if (!trusted(url)) continue
                    return State.Available(tag, url, asset.optLong("size"), json.optString("body"))
                }
            }
            return State.UpToDate
        } finally {
            conn.disconnect()
        }
    }

    /** Downloads the APK of the available version and opens the system installer. */
    fun downloadAndInstall(context: Context) {
        val available = state as? State.Available ?: return
        val app = context.applicationContext
        state = State.Downloading(0)
        scope.launch {
            state = runCatching {
                val file = File(app.cacheDir, "updates/Pulso.apk").apply { parentFile?.mkdirs() }
                download(available, file)
                install(app, file)
            }.getOrElse { State.Failed("Falha ao baixar a atualização") }
        }
    }

    private fun download(update: State.Available, target: File) {
        val conn = open(update.url)
        try {
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: update.size
            var done = 0L
            conn.inputStream.use { input ->
                target.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) state = State.Downloading((done * 100 / total).toInt().coerceIn(0, 100))
                    }
                }
            }
            if (total > 0 && done != total) error("download incompleto")
        } finally {
            conn.disconnect()
        }
    }

    private fun install(app: Context, file: File): State {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !app.packageManager.canRequestPackageInstalls()) {
            runCatching {
                app.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return State.Failed("Permita instalar apps desconhecidos para o pulso.tv e tente de novo")
        }
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.update", file)
        app.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return State.Idle
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "pulso.tv/${BuildConfig.VERSION_NAME}")
        }

    /** Only download from GitHub itself, never from a URL that came from somewhere else. */
    private fun trusted(url: String): Boolean {
        val uri = Uri.parse(url)
        return uri.scheme == "https" && uri.host == "github.com" &&
            uri.path.orEmpty().startsWith("/${BuildConfig.GITHUB_REPO}/releases/download/")
    }

    private fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(remote)
        val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
}
