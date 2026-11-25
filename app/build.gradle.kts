import java.io.FileOutputStream
import java.net.URL
import java.util.Properties
import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

val appVersionName = "1.0.1"
val appVersionCode = 2

android {
    namespace = "com.fredsystems.brilliantboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fredsystems.brilliantboard"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    val keystoreProps = Properties()
    val keystorePropsFile = rootProject.file("keystore.properties")
    if (keystorePropsFile.exists()) {
        keystoreProps.load(keystorePropsFile.inputStream())
    }

    signingConfigs {
        create("release") {
            val base64 = System.getenv("KEYSTORE_BASE64")
            val fileFromProps = keystoreProps.getProperty("storeFile")
            when {
                !base64.isNullOrBlank() -> {
                    val keystoreFile = File(rootProject.projectDir, "release-keystore.jks")
                    keystoreFile.writeBytes(Base64.getDecoder().decode(base64))
                    storeFile = keystoreFile
                    storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
                    keyAlias = System.getenv("KEY_ALIAS") ?: ""
                    keyPassword = System.getenv("KEY_PASSWORD") ?: ""
                }
                !fileFromProps.isNullOrBlank() -> {
                    storeFile = rootProject.file(fileFromProps)
                    storePassword = keystoreProps.getProperty("storePassword") ?: ""
                    keyAlias = keystoreProps.getProperty("keyAlias") ?: ""
                    keyPassword = keystoreProps.getProperty("keyPassword") ?: ""
                }
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = !project.hasProperty("skipMinify")
            isShrinkResources = !project.hasProperty("skipMinify")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/DEPENDENCIES"
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.1")

    implementation(platform("androidx.compose:compose-bom:2025.03.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("io.insert-koin:koin-core:4.0.2")
    implementation("io.insert-koin:koin-android:4.0.2")
    implementation("io.insert-koin:koin-androidx-compose:4.0.2")

    implementation("io.ktor:ktor-client-core:3.1.1")
    implementation("io.ktor:ktor-client-okhttp:3.1.1")
    implementation("io.ktor:ktor-client-content-negotiation:3.1.1")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.1.1")
    implementation("io.ktor:ktor-client-logging:3.1.1")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")

    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")

    implementation("androidx.datastore:datastore-preferences:1.1.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
}

/**
 * Downloads the official Stockfish Android binaries and places them in jniLibs so the
 * UCI engine can be executed from nativeLibraryDir at runtime. The task is intentionally
 * fault tolerant: an offline build (unit tests, CI without network) simply keeps any
 * previously vendored binaries and continues, printing a warning instead of failing.
 */
tasks.register("downloadStockfish") {
    group = "setup"
    description = "Downloads arm64-v8a and x86_64 Stockfish binaries into app/src/main/jniLibs"
    val jniLibs = File(projectDir, "src/main/jniLibs")
    // The official release publishes an arm64 Android binary; no x86_64 Android asset
    // exists, so that ABI is left to a user-supplied binary (engine features optional).
    val binaries = mapOf(
        "arm64-v8a" to "https://github.com/official-stockfish/Stockfish/releases/latest/download/stockfish-android-arm64-universal.tar.gz"
    )
    doLast {
        binaries.forEach { (abi, url) ->
            val target = File(jniLibs, "$abi/libstockfish.so")
            if (target.exists() && target.length() > 0) {
                logger.lifecycle("Stockfish binary already present for $abi, skipping download")
                return@forEach
            }
            runCatching {
                target.parentFile.mkdirs()
                val archive = File(project.buildDir, "stockfish-$abi.tar.gz")
                URL(url).openStream().use { input ->
                    FileOutputStream(archive).use { output -> input.copyTo(output) }
                }
                project.exec {
                    commandLine("tar", "-xzf", archive.absolutePath, "-C", target.parentFile.absolutePath)
                }
                val extracted = target.parentFile.listFiles()?.firstOrNull {
                    it.isFile && it.name.startsWith("stockfish") && it.name != target.name
                }
                if (extracted != null && !target.exists()) {
                    extracted.renameTo(target)
                }
                if (target.exists()) target.setExecutable(true)
                logger.lifecycle("Stockfish binary for $abi ready at ${target.absolutePath}")
            }.onFailure { error ->
                logger.warn("Could not download Stockfish for $abi (${error.message}). " +
                    "Offline fallback: the app will disable engine features until a binary is bundled.")
            }
        }
    }
}

if (project.hasProperty("downloadStockfishOnBuild")) {
    tasks.named("preBuild") {
        dependsOn("downloadStockfish")
    }
}
