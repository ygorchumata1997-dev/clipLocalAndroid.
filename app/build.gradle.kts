plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "br.com.cliplocal"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.com.cliplocal"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    val downloaderVersion = "0.18.1"
    implementation("io.github.junkfood02.youtubedl-android:library:$downloaderVersion")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:$downloaderVersion")
}
