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
 * The city database is shared with iOS (ios/PrayerTime/Resources/cities.tsv,
 * built by tools/gen_cities.py). Copy just that file into the assets rather
 * than adding the whole iOS resources folder.
 */
abstract class CopyCityDatabase : DefaultTask() {
    @get:InputFile
    abstract val source: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val target = outputDir.get().asFile.apply { mkdirs() }
        source.get().asFile.copyTo(target.resolve("cities.tsv"), overwrite = true)
    }
}

val copyCityDatabase = tasks.register<CopyCityDatabase>("copyCityDatabase") {
    source.set(rootProject.layout.projectDirectory.file("../ios/PrayerTime/Resources/cities.tsv"))
    outputDir.set(layout.buildDirectory.dir("generated/cityAssets"))
}

extensions.getByType<ApplicationAndroidComponentsExtension>().onVariants { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(copyCityDatabase, CopyCityDatabase::outputDir)
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
}

tasks.withType<Test>().configureEach {
    systemProperty("cities.tsv", rootProject.layout.projectDirectory.file("../ios/PrayerTime/Resources/cities.tsv").asFile.path)
}
