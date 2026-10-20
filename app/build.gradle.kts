
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.jarvis.nexus"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jarvis.nexus"
        minSdk = 26
        targetSdk = 35

        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false

            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),
                "proguard-rules.pro"
            )
        }

        debug {
            isMinifyEnabled = false
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
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    // Android Core
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")

    // Jetpack Compose BOM
    implementation(
        platform("androidx.compose:compose-bom:2025.04.01")
    )

    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")

    // Material 3 - CORRETTO
    implementation("androidx.compose.material3:material3")

    // Material Icons
    implementation(
        "androidx.compose.material:material-icons-extended"
    )

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.9")

    // ViewModel
    implementation(
        "androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0"
    )

    // Coroutines
    implementation(
        "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2"
    )

    // Networking per Gemini API
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON
    implementation("org.json:json:20240303")

    // AI locale con MediaPipe
    implementation(
        "com.google.mediapipe:tasks-genai:0.10.22"
    )

    // Test
    testImplementation("junit:junit:4.13.2")

    androidTestImplementation(
        "androidx.test.ext:junit:1.2.1"
    )

    androidTestImplementation(
        "androidx.test.espresso:espresso-core:3.6.1"
    )

    debugImplementation(
        "androidx.compose.ui:ui-tooling"
    )
}
