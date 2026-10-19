plugins {
    id("com.android.library")
}

val supportedAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
val configuredAbis = providers.gradleProperty("ellaAbi")
    .orNull
    ?.split(",")
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    ?.ifEmpty { null }
    ?: supportedAbis
val buildNative = providers.gradleProperty("ellaBuildNative")
    .map { it.toBoolean() }
    // Only arm64 has a checked-in JNI prebuilt; the four static FFmpeg archives are available
    // for multi-ABI builds, so link their JNI bridge automatically when another ABI is requested.
    .getOrElse(configuredAbis.any { it != "arm64-v8a" })

android {
    namespace = "androidx.media3.decoder.ffmpeg"
    compileSdk = 34
    ndkVersion = "26.1.10909125"

    defaultConfig {
        minSdk = 21
        ndk {
            abiFilters += configuredAbis
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    if (buildNative && file("src/main/jni/ffmpeg").exists()) {
        externalNativeBuild {
            cmake {
                path = file("src/main/jni/CMakeLists.txt")
                version = "3.22.1"
            }
        }
    }
}

androidComponents {
    finalizeDsl { extension ->
        if (buildNative) {
            extension.sourceSets.getByName("main").jniLibs.setSrcDirs(emptyList<String>())
        }
    }
}

dependencies {
    compileOnly("androidx.media3:media3-decoder:1.3.1")
    compileOnly("androidx.media3:media3-exoplayer:1.3.1")
    compileOnly("androidx.annotation:annotation:1.7.1")
    compileOnly("org.jetbrains.kotlin:kotlin-annotations-jvm:1.9.23")
}

