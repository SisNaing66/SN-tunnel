import java.net.URL
import java.io.File

plugins {
    id("com.android.application") version "8.2.2"
    id("org.jetbrains.kotlin.android") version "1.9.22"
}

// Build မစမီ libv2ray.aar ကို GitHub Release Link မှ Auto Download ဆွဲပေးမည့် Task
tasks.register("downloadLibV2ray") {
    doLast {
        val libsDir = File(projectDir, "libs")
        if (!libsDir.exists()) libsDir.mkdirs()

        val aarFile = File(libsDir, "libv2ray.aar")
        if (!aarFile.exists()) {
            println("Downloading libv2ray.aar from GitHub Release...")
            val downloadUrl = "https://github.com/2dust/AndroidLibXrayLite/releases/download/v26.7.19/libv2ray.aar"
            
            val connection = URL(downloadUrl).openConnection()
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            
            connection.getInputStream().use { input ->
                aarFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            println("Download libv2ray.aar completed successfully!")
        }
    }
}

tasks.named("preBuild") {
    dependsOn("downloadLibV2ray")
}

android {
    namespace = "com.myanmar.warpvpn"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.myanmar.warpvpn"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    
    implementation(files("libs/libv2ray.aar"))

    // Network / Coroutines
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.google.code.gson:gson:2.10.1")
}
