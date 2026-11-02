plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.boory.smartflow"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.boory.smartflow"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
}
