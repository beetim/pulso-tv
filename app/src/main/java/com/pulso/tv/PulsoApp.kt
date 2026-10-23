package com.pulso.tv

import android.annotation.SuppressLint
import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.network.NetworkClient
import coil3.network.NetworkFetcher
import coil3.network.NetworkHeaders
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import coil3.network.NetworkResponseBody
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.util.DebugLogger
import com.pulso.tv.util.C
import com.pulso.tv.util.NetworkUtils
import com.pulso.tv.util.coil.CacheControlCacheStrategy
import com.pulso.tv.util.prefs
import kotlinx.coroutines.suspendCancellableCoroutine
import okio.Buffer
import okio.buffer
import okio.source
import org.chromium.net.apihelpers.UploadDataProviders
import org.conscrypt.Conscrypt
import java.security.Security

class PulsoApp : Application(), SingletonImageLoader.Factory {

    companion object {
        lateinit var INSTANCE: Application
    }

    lateinit var pulsoModule: PulsoModule

    override fun onCreate() {
        super.onCreate()
        INSTANCE = this
        com.pulso.tv.ui.pulso.PulsoLang.init(this)
        // The app never calls setDefaultValues, so android:defaultValue in the
        // preference XML never reaches SharedPreferences. Seed on first run.
        val preferences = prefs()
        // Automático (adaptive: steps down on a bad connection, up to the best the link allows)
        // is the default; the flag moves installs that still carry the old fixed 720 once.
        if (!preferences.contains(C.PLAYER_DEFAULT_QUALITY) || !preferences.getBoolean("pulso_auto_quality_v1", false)) {
            preferences.edit()
                .putString(C.PLAYER_DEFAULT_QUALITY, "auto")
                .putBoolean("pulso_auto_quality_v1", true)
                .apply()
        }
        pulsoModule = PulsoModule(this)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val conscrypt = Conscrypt.newProvider()
            Security.insertProviderAt(conscrypt, 1)
        }
        warmUpNetwork()
    }

    /**
     * Opens the DNS/TLS connections to Twitch while the Home is still drawing, so the first
     * stream does not pay for them (cold start was ~2 s of network before the first frame).
     * The connections stay in the shared OkHttp pool.
     */
    private fun warmUpNetwork() {
        Thread {
            val client = pulsoModule.okHttpClient.value
            listOf("https://gql.twitch.tv/", "https://usher.ttvnw.net/", "https://api.twitch.tv/").forEach { url ->
                runCatching {
                    client.newCall(okhttp3.Request.Builder().url(url).head().build()).execute().close()
                }
            }
        }.apply { priority = Thread.MIN_PRIORITY }.start()
    }

    @OptIn(ExperimentalCoilApi::class)
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context).apply {
            if (BuildConfig.DEBUG) {
                logger(DebugLogger())
            }
            components {
                val networkLibrary = prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP)
                when {
                    networkLibrary == C.HTTP_ENGINE && pulsoModule.httpEngine.value != null -> @SuppressLint("NewApi") {
                        add(NetworkFetcher.Factory(
                            networkClient = {
                                object : NetworkClient {
                                    override suspend fun <T> executeRequest(request: NetworkRequest, block: suspend (NetworkResponse) -> T): T {
                                        val requestBody = request.body?.let {
                                            val buffer = Buffer()
                                            it.writeTo(buffer)
                                            buffer.readByteArray()
                                        }
                                        val requestMillis = System.currentTimeMillis()
                                        val response = suspendCancellableCoroutine { continuation ->
                                            val timeout = NetworkUtils.HttpEngineTimeout()
                                            val request = pulsoModule.httpEngine.value!!.newUrlRequestBuilder(
                                                request.url,
                                                pulsoModule.cronetExecutor.value,
                                                NetworkUtils.ByteArrayUrlCallback(continuation, timeout)
                                            ).apply {
                                                request.headers.asMap().forEach { entry ->
                                                    entry.value.forEach {
                                                        addHeader(entry.key, it)
                                                    }
                                                }
                                                requestBody?.let {
                                                    setUploadDataProvider(NetworkUtils.ByteArrayUploadProvider(requestBody), pulsoModule.cronetExecutor.value)
                                                }
                                                setHttpMethod(request.method)
                                            }.build()
                                            timeout.start(request, continuation)
                                            request.start()
                                            continuation.invokeOnCancellation {
                                                request.cancel()
                                                timeout.stop()
                                            }
                                        }
                                        val responseMillis = System.currentTimeMillis()
                                        return block(
                                            NetworkResponse(
                                                code = response.info.httpStatusCode,
                                                requestMillis = requestMillis,
                                                responseMillis = responseMillis,
                                                headers = NetworkHeaders.Builder().apply {
                                                    response.info.headers.asList.forEach {
                                                        add(it.key, it.value)
                                                    }
                                                }.build(),
                                                body = response.body.inputStream().source().buffer().let(::NetworkResponseBody),
                                            )
                                        )
                                    }
                                }
                            },
                            cacheStrategy = { CacheControlCacheStrategy() }
                        ))
                    }
                    networkLibrary == C.CRONET && pulsoModule.cronetEngine.value != null -> {
                        add(NetworkFetcher.Factory(
                            networkClient = {
                                object : NetworkClient {
                                    override suspend fun <T> executeRequest(request: NetworkRequest, block: suspend (NetworkResponse) -> T): T {
                                        val requestBody = request.body?.let {
                                            val buffer = Buffer()
                                            it.writeTo(buffer)
                                            buffer.readByteArray()
                                        }
                                        val requestMillis = System.currentTimeMillis()
                                        val response = suspendCancellableCoroutine { continuation ->
                                            val timeout = NetworkUtils.CronetTimeout()
                                            val request = pulsoModule.cronetEngine.value!!.newUrlRequestBuilder(
                                                request.url,
                                                NetworkUtils.ByteArrayCronetCallback(continuation, timeout),
                                                pulsoModule.cronetExecutor.value
                                            ).apply {
                                                request.headers.asMap().forEach { entry ->
                                                    entry.value.forEach {
                                                        addHeader(entry.key, it)
                                                    }
                                                }
                                                requestBody?.let {
                                                    setUploadDataProvider(UploadDataProviders.create(requestBody), pulsoModule.cronetExecutor.value)
                                                }
                                                setHttpMethod(request.method)
                                            }.build()
                                            timeout.start(request, continuation)
                                            request.start()
                                            continuation.invokeOnCancellation {
                                                request.cancel()
                                                timeout.stop()
                                            }
                                        }
                                        val responseMillis = System.currentTimeMillis()
                                        return block(
                                            NetworkResponse(
                                                code = response.info.httpStatusCode,
                                                requestMillis = requestMillis,
                                                responseMillis = responseMillis,
                                                headers = NetworkHeaders.Builder().apply {
                                                    response.info.allHeadersAsList.forEach {
                                                        add(it.key, it.value)
                                                    }
                                                }.build(),
                                                body = response.body.inputStream().source().buffer().let(::NetworkResponseBody),
                                            )
                                        )
                                    }
                                }
                            },
                            cacheStrategy = { CacheControlCacheStrategy() }
                        ))
                    }
                    else -> {
                        add(OkHttpNetworkFetcherFactory(
                            callFactory = { pulsoModule.okHttpClient.value },
                            cacheStrategy = { CacheControlCacheStrategy() }
                        ))
                    }
                }
            }
        }.build()
    }
}
