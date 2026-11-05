import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

// Release signing stays out of source. Debug builds do not require this file.
// A missing or incomplete keystore.properties leaves release unsigned so every variant still configures.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
val keystoreProblems = mutableListOf<String>()
if (keystorePropertiesFile.isFile) {
    FileInputStream(keystorePropertiesFile).use { keystoreProperties.load(it) }
    val required = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    val missing = required.filter { keystoreProperties.getProperty(it).isNullOrBlank() }
    if (missing.isNotEmpty()) {
        keystoreProblems += "missing entries: ${missing.joinToString(", ")}"
    }
    val storeName = keystoreProperties.getProperty("storeFile")
    if (!storeName.isNullOrBlank() && !rootProject.file(storeName).isFile) {
        keystoreProblems += "keystore file '$storeName' was not found"
    }
}
val hasReleaseKeystore = keystorePropertiesFile.isFile && keystoreProblems.isEmpty()
if (!keystorePropertiesFile.isFile) {
    logger.lifecycle(
        "keystore.properties not found. Debug builds are unaffected. " +
            "Release APKs will be unsigned until you copy keystore.properties.example."
    )
} else if (keystoreProblems.isNotEmpty()) {
    logger.lifecycle(
        "keystore.properties cannot be used (${keystoreProblems.joinToString("; ")}). " +
            "Debug builds are unaffected. Release APKs will be unsigned. See keystore.properties.example."
    )
}

android {
    namespace = "com.bettercaper"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bettercaper"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    if (hasReleaseKeystore) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/*.SF"
            excludes += "META-INF/*.DSA"
            excludes += "META-INF/*.RSA"
            excludes += "META-INF/LICENSE*"
            excludes += "META-INF/NOTICE*"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.viewmodel.compose)
    implementation(libs.sshj)
    implementation(libs.bcprov.jdk18on)
    implementation(libs.security.crypto)
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.eddsa)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)
    debugImplementation(libs.androidx.ui.tooling)
    implementation("com.google.android.gms:play-services-location:21.2.0")
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
}
