plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.jacoco) apply false
    alias(libs.plugins.sonarqube) apply false
}

// For Hilt compatibility
buildscript {
    dependencies {
        classpath("com.google.dagger:hilt-android-gradle-plugin:2.56.2")
    }
}