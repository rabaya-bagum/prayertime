import com.android.build.api.variant.ApplicationAndroidComponentsExtension

plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "io.github.rabayabagum.prayertime"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.rabayabagum.prayertime"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Each UI test starts from a fresh install state: no saved city, settings or grants.
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
    }

    testOptions {
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
        animationsDisabled = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

/**
 * The city database and magnetic model are shared with iOS (ios/PrayerTime/Resources):
 * cities.tsv from tools/gen_cities.py, WMM2025.COF from NOAA. Copy just those
 * files into the assets rather than adding the whole iOS resources folder.
 */
abstract class CopySharedAssets : DefaultTask() {
    @get:InputFiles
    abstract val sources: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val target = outputDir.get().asFile.apply { mkdirs() }
        sources.forEach { it.copyTo(target.resolve(it.name), overwrite = true) }
    }
}

val iosResources = rootProject.layout.projectDirectory.dir("../ios/PrayerTime/Resources")

val copySharedAssets = tasks.register<CopySharedAssets>("copySharedAssets") {
    sources.from(iosResources.file("cities.tsv"), iosResources.file("WMM2025.COF"))
    outputDir.set(layout.buildDirectory.dir("generated/sharedAssets"))
}

extensions.getByType<ApplicationAndroidComponentsExtension>().onVariants { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(copySharedAssets, CopySharedAssets::outputDir)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":notifications"))

    implementation(platform("androidx.compose:compose-bom:2025.06.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")

    androidTestImplementation(platform("androidx.compose:compose-bom:2025.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestUtil("androidx.test:orchestrator:1.5.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

tasks.withType<Test>().configureEach {
    systemProperty("cities.tsv", iosResources.file("cities.tsv").asFile.path)
    systemProperty("wmm.cof", iosResources.file("WMM2025.COF").asFile.path)
    // NOAA's published test values, shared with the iOS MagneticModelTests.
    systemProperty("wmm.testValues", rootProject.layout.projectDirectory.file("../ios/PrayerTimeTests/WMM2025_TestValues.txt").asFile.path)
}
