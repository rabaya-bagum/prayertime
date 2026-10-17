// Android side of adhan scheduling: AlarmManager, receivers, notification
// channels and the foreground service that plays the full adhan. All decisions
// (what to arm, when, with which sound) live in :core and are unit-tested there.
plugins {
    id("com.android.library") version "8.13.0"
    kotlin("android") version "2.4.0"
}

android {
    namespace = "io.github.rabayabagum.prayertime.notifications"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        targetSdk = 36
        warningsAsErrors = false
        abortOnError = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":core"))
}
