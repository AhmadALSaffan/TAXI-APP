plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.hilt.android.plugin) apply false
    alias(libs.plugins.google.gms.google.services) apply false
    alias(libs.plugins.ksp) apply false
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