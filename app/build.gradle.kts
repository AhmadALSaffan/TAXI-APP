import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    id("com.google.dagger.hilt.android")
    id("com.google.gms.google-services")
    id("org.jetbrains.kotlin.plugin.compose")
}

// API keys live in local.properties (git-ignored), never in source.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun secret(name: String): String = localProps.getProperty(name) ?: System.getenv(name) ?: ""
val mapsApiKey = secret("MAPS_API_KEY")

android {
    namespace = "com.taxiapp"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.taxiapp"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        resValue("string", "google_maps_key", mapsApiKey)
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        resValues = true
        compose = true
    }

}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation("com.google.dagger:hilt-android:2.59.2")
    implementation("com.google.firebase:firebase-auth:24.0.1")
    implementation("com.google.firebase:firebase-database:22.0.1")
    implementation("com.google.firebase:firebase-storage:22.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    ksp("com.google.dagger:hilt-compiler:2.59.2")
    implementation("com.google.android.libraries.places:places:4.1.0")
    implementation("com.google.firebase:firebase-messaging:25.0.1")
    implementation("com.google.firebase:firebase-functions:22.0.0")

    // Jetpack Compose (new UI)
    implementation(platform("androidx.compose:compose-bom:2025.10.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("com.google.maps.android:maps-compose:6.4.1")
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Sign in with Google (Credential Manager)
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Optional video background for onboarding
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-ui:1.8.0")

}
apply( plugin = "com.google.gms.google-services")