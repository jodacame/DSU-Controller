// SPDX-License-Identifier: MIT

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.jodacame.dsucontroller"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.jodacame.dsucontroller"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            // The shipped code is the readable code: no shrinking, no obfuscation.
            // There is deliberately no signingConfig here; release APKs are signed
            // outside Gradle (docs/decisions/0004).
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    // Tests only. Nothing may be added to the app's own configurations (docs/decisions/0002).
    testImplementation("junit:junit:4.13.2")
}
