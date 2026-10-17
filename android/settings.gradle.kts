pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "prayertime"
include(":core")

// Android modules need the Android SDK. Leaving them out without it lets the
// pure-Kotlin core build and test anywhere (CI provides the SDK).
if (System.getenv("ANDROID_HOME") != null || file("local.properties").exists()) {
    include(":notifications")
}
