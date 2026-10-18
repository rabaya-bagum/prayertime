// Every plugin is loaded here, on the root classpath, so all modules share one
// copy. Android plugins only load when an Android SDK is present, which keeps
// :core building and testing anywhere (same check as settings.gradle.kts).
buildscript {
    val kotlinVersion = "2.4.0"
    val androidSdk = System.getenv("ANDROID_HOME") != null ||
        java.io.File(sourceFile?.parentFile, "local.properties").exists()
    repositories {
        mavenCentral()
        if (androidSdk) google()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
        if (androidSdk) {
            classpath("com.android.tools.build:gradle:8.13.0")
            classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:$kotlinVersion")
            classpath("org.jetbrains.kotlin:kotlin-serialization:$kotlinVersion")
        }
    }
}
