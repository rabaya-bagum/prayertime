// Platform-neutral prayer-time logic for the Android app. Plain Kotlin/JVM with
// no Android dependencies, so it runs under `./gradlew :core:test` anywhere.
plugins {
    kotlin("jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("com.batoulapps.adhan:adhan2:0.0.7")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("testdata.dir", rootProject.projectDir.resolve("../shared/testdata").canonicalPath)
}
