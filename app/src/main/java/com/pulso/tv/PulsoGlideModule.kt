package com.pulso.tv

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.module.AppGlideModule
import java.io.InputStream

@GlideModule
class PulsoGlideModule : AppGlideModule() {

    override fun isManifestParsingEnabled(): Boolean = false

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        val pulsoModule = (context as PulsoApp).pulsoModule
        registry.replace(GlideUrl::class.java, InputStream::class.java, OkHttpUrlLoader.Factory(pulsoModule.okHttpClient.value))
        super.registerComponents(context, glide, registry)
    }
}
