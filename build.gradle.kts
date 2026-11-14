plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.hilt.android.plugin) apply false
    alias(libs.plugins.google.gms.google.services) apply false
    alias(libs.plugins.ksp) apply false
    // Must match the Kotlin version bundled with AGP (2.2.10 for AGP 9.1).
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.google.gms:google-services:4.4.4")

    }
}