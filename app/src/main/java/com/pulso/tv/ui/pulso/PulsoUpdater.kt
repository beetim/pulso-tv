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
        /** APK downloaded, waiting for the person to tap "Instalar agora". */
        data class Ready(val version: String) : State
        data class Failed(val reason: String) : State
    }

    var state by mutableStateOf<State>(State.Idle)
        private set

    /**
     * The newest release found. Kept apart from [state] so a failure or an opened installer
     * never loses it: the header pill, the card and the "Atualizar agora" button all read this.
     */
    var pending by mutableStateOf<State.Available?>(null)
        private set

    /** The person tapped "Depois": hide the Home shortcut until the next version. */
    var dismissed by mutableStateOf(false)

    /** True while the Home is on screen: the installer is only opened over a screen the person is looking at. */
    @Volatile
    var homeVisible = false

    val enabled: Boolean get() = BuildConfig.GITHUB_REPO.isNotBlank()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastCheck = 0L

    /** GitHub answered, but not with a release (403 rate limit, 5xx...). */
    private class HttpFailure(val code: Int) : Exception("HTTP $code")

    /** Cheap automatic check: at most once every 6 hours per process. */
    fun checkIfDue(context: Context) {
        if (!enabled) return
        if (System.currentTimeMillis() - lastCheck < CHECK_INTERVAL_MS) return
        check(context, quiet = true)
    }

    /** Manual check ("Verificar atualizações"): always asks and shows the progress. */
    fun checkNow(context: Context) = check(context, quiet = false)

    @Suppress("UNUSED_PARAMETER")
    private fun check(context: Context, quiet: Boolean) {
        if (!enabled || state is State.Checking || state is State.Downloading) return
        val before = state
        if (!quiet) state = State.Checking
        scope.launch {
            runCatching { fetchLatest() }
                .onSuccess { found ->
                    lastCheck = System.currentTimeMillis() // only a successful check postpones the next one
                    if (found != null) {
                        if (found.version != pending?.version) dismissed = false
                        pending = found
                        state = if (before is State.Ready && before.version == found.version) before else found
                    } else {
                        pending = null
                        state = State.UpToDate
                    }
                }
                .onFailure {
                    // quiet checks stay silent and keep whatever the screen already shows
                    state = if (quiet) before else State.Failed(
                        if (it is HttpFailure) "GitHub indisponível, tente mais tarde" else "Sem conexão com o GitHub"
                    )
                }
        }
    }

    /** The newest release when it is newer than this build; null when this build is current. */
    private fun fetchLatest(): State.Available? {
        val conn = open("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest")
        try {
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            if (conn.responseCode == 404) return null // no release published yet
            if (conn.responseCode != 200) throw HttpFailure(conn.responseCode)
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = json.optString("tag_name").removePrefix("v")
            if (tag.isEmpty() || !isNewer(tag, BuildConfig.VERSION_NAME)) return null
            val assets = json.optJSONArray("assets") ?: return null
            // a release may carry several .apk files: take Pulso.apk, else one named like the
            // app, else the only .apk there is; never just "whichever comes first"
            val apks = (0 until assets.length()).map { assets.getJSONObject(it) }
                .filter { it.optString("name").endsWith(".apk", ignoreCase = true) }
                .filter { trusted(it.optString("browser_download_url")) }
            val asset = apks.firstOrNull { it.optString("name").equals("Pulso.apk", ignoreCase = true) }
                ?: apks.firstOrNull { it.optString("name").contains("pulso", ignoreCase = true) }
                ?: apks.singleOrNull()
                ?: return null
            return State.Available(tag, asset.optString("browser_download_url"), asset.optLong("size"), json.optString("body"))
        } finally {
            conn.disconnect()
        }
    }

    /**
     * "Atualizar agora": makes sure installing is allowed (before spending the download),
     * downloads the APK (or reuses a complete one) and opens the system installer.
     */
    fun downloadAndInstall(context: Context) {
        val release = pending ?: return
        if (state is State.Downloading) return
        val app = context.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !app.packageManager.canRequestPackageInstalls()) {
            state = State.Failed(
                if (openUnknownSourcesScreen(app)) "Permita instalar apps desconhecidos para o pulso.tv e toque em Atualizar agora de novo"
                else "Ative \"Instalar apps desconhecidos\" para o pulso.tv em Configurações > Apps > Acesso especial"
            )
            return
        }
        val file = apkFile(app, release.version)
        if (isComplete(file, release)) {
            state = installOrWait(app, file, release.version)
            return
        }
        state = State.Downloading(0)
        scope.launch {
            state = runCatching {
                file.parentFile?.listFiles()?.forEach { if (it != file) it.delete() } // old versions and leftovers
                val part = File(file.parentFile, file.name + ".part")
                download(release, part)
                file.delete()
                if (!part.renameTo(file)) error("não consegui salvar o APK")
                installOrWait(app, file, release.version)
            }.getOrElse {
                State.Failed(if (it is HttpFailure) "GitHub indisponível, tente mais tarde" else "Falha ao baixar a atualização")
            }
        }
    }

    /** Opens the "install unknown apps" page of this app; false when the TV has no such screen. */
    private fun openUnknownSourcesScreen(app: Context): Boolean =
        runCatching {
            app.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.isSuccess

    private fun apkFile(app: Context, version: String) =
        File(app.cacheDir, "updates/Pulso-$version.apk").apply { parentFile?.mkdirs() }

    private fun isComplete(file: File, release: State.Available) =
        file.isFile && file.length() > 0 && (release.size <= 0 || file.length() == release.size)

    private fun download(update: State.Available, target: File) {
        val conn = open(update.url)
        try {
            if (conn.responseCode != 200) throw HttpFailure(conn.responseCode)
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

    /**
     * Opens the installer only while the Home is in front. If the person left the app during
     * the download, the system installer would pop up over whatever they are watching, so we
     * park in [State.Ready] and wait for the tap.
     */
    private fun installOrWait(app: Context, file: File, version: String): State {
        if (homeVisible) {
            val uri = FileProvider.getUriForFile(app, "${app.packageName}.update", file)
            val opened = runCatching {
                app.startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.isSuccess
            if (!opened) return State.Failed("Não consegui abrir o instalador")
        }
        // the person may cancel the installer: the file stays and "Instalar agora" reuses it
        return State.Ready(version)
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
        return uri.scheme == "https" && uri.host.equals("github.com", ignoreCase = true) &&
            uri.path.orEmpty().startsWith("/${BuildConfig.GITHUB_REPO}/releases/download/", ignoreCase = true)
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
