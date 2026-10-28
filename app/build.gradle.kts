import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.apollo)
}

kotlin {
    jvmToolchain(21)
}

// Signing material lives outside the source tree in keystore.properties.
val pulsoKeystore = rootProject.file("keystore.properties")
val pulsoProps = Properties().apply {
    if (pulsoKeystore.exists()) pulsoKeystore.inputStream().use { load(it) }
}

android {
    signingConfigs {
        if (pulsoKeystore.exists()) {
            create("pulso") {
                storeFile = rootProject.file(pulsoProps.getProperty("storeFile"))
                storePassword = pulsoProps.getProperty("storePassword")
                keyAlias = pulsoProps.getProperty("keyAlias")
                keyPassword = pulsoProps.getProperty("keyPassword")
            }
        }
        getByName("debug") {
            keyAlias = "debug"
            keyPassword = "123456"
            storeFile = file("debug-keystore.jks")
            storePassword = "123456"
        }
    }
    namespace = "com.pulso.tv"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.pulso.tv"
        minSdk = 28 // Android 9+: the system TLS is enough, no bundled Conscrypt
        targetSdk = 37
        versionCode = 129
        versionName = "1.0.7"
        // "dono/repositorio" dos Releases consultados pelo atualizador; vazio desliga a checagem
        buildConfigField("String", "GITHUB_REPO", "\"${(findProperty("pulso.githubRepo") as String?).orEmpty()}\"")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isShrinkResources = true
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // TVs are ARM: the x86 libraries (native libraries) were ~7 MB of dead weight
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
            signingConfig = if (pulsoKeystore.exists()) {
                signingConfigs.getByName("pulso")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    androidResources {
        generateLocaleConfig = true
    }
    lint {
        disable += "ContentDescription"
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.paging.compose)
    implementation(libs.coil.compose)
    implementation(libs.zxing.core)
    compileOnly("com.google.j2objc:j2objc-annotations:3.0.0") // OkHttpDataSource SettableFuture

    implementation(libs.activity)
    implementation(libs.core)
    implementation(libs.lifecycle.service)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.paging.runtime)
    implementation(libs.preference.ktx)
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.room.paging)

    implementation(libs.cronet.api)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.serialization.json)
    implementation(libs.apollo.api)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)

    implementation(libs.coil)
    implementation(libs.coil.gif)
    implementation(libs.coil.okhttp)

    implementation(libs.coroutines)
}

apollo {
    @Suppress("ApolloEndpointNotConfigured")
    service("service") {
        packageName.set("com.pulso.tv.graphql")
    }
}

// Delete large build log files from ~/.gradle/daemon/X.X/daemon-XXX.out.log
// Source: https://discuss.gradle.org/t/gradle-daemon-produces-a-lot-of-logs/9905
File("${project.gradle.gradleUserHomeDir.absolutePath}/daemon/${project.gradle.gradleVersion}").listFiles()?.forEach {
    if (it.name.endsWith(".out.log")) {
        // println("Deleting gradle log file: $it") // Optional debug output
        it.delete()
    }
}