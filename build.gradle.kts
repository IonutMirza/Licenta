// Top-level build file where you can add configuration options common to all sub-projects/modules.

// 1) Definim repositories și classpath pentru Google Services
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // Plugin-ul Google Services (trebuie să fie aceeași versiune pe care o folosești în module)
        classpath("com.google.gms:google-services:4.4.2")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // Nu uită să declari plugin-ul Google Services cu „apply false”
    id("com.google.gms.google-services") version "4.4.2" apply false
}

// 2) Dacă ai nevoie de setări suplimentare (ex.: dependencyResolutionManagement), le lași în settings.gradle.kts
