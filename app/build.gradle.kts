plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "app.coc.tracker"
    compileSdk = 34
    defaultConfig {
        applicationId = "app.coc.tracker"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}
val copyDebugApk = tasks.register("copyDebugApk") {
    doLast {
        val apk = layout.buildDirectory.file("outputs/apk/debug/app-debug.apk").get().asFile
        val namedApk = layout.buildDirectory.file("outputs/apk/debug/COC-Timers.apk").get().asFile
        if (apk.exists()) apk.copyTo(namedApk, overwrite = true)
    }
}

tasks.configureEach {
    if (name == "assembleDebug") finalizedBy(copyDebugApk)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
}
