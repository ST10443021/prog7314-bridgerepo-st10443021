plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.gms.google-services")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
}

/*
 * Release signing (Google Developers, n.d.h).
 * The upload keystore and its passwords are NEVER committed. They are read from either
 *   1. DevLedger/android/keystore.properties (git-ignored; see keystore.properties.example), or
 *   2. the DEVLEDGER_KEYSTORE_* environment variables used by .github/workflows/release.yml.
 * When neither is present the release build is produced unsigned so CI and fresh clones
 * still compile.
 */
val keystoreProperties = java.util.Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(propertyName: String, environmentName: String): String? =
    (keystoreProperties.getProperty(propertyName) ?: System.getenv(environmentName))
        ?.takeIf { it.isNotBlank() }

android {
    namespace = "com.devledger.app"
    // Google Play requires new apps and updates to target Android 16 (API 36) from
    // 31 August 2026 (Google Play Console Help, n.d.).
    compileSdk = 36

    defaultConfig {
        applicationId = "com.devledger.app"
        minSdk = 26
        targetSdk = 36
        // Final PoE release. versionCode must increase for every Play upload.
        versionCode = 100
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Base URL of the hosted DevLedger REST API (Render deployment). Must end with "/".
        buildConfigField("String", "BASE_URL", "\"https://devledger-api-op1w.onrender.com/\"")

        // OAuth *client identifiers* are public by design; the matching secrets live only on
        // the API server (GitHub, n.d.a; Google, n.d.).
        // Web client ID used to request the Google ID token that the API verifies.
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"293392981245-br01csqhvog5audb2r5hvmq3lp9p15at.apps.googleusercontent.com\"")

        // GitHub OAuth app client ID (the client secret stays server-side).
        buildConfigField("String", "GITHUB_CLIENT_ID", "\"Ov23liCiru7JpKPZZKwn\"")
    }

    signingConfigs {
        create("release") {
            val storePath = signingValue("storeFile", "DEVLEDGER_KEYSTORE_FILE")
            if (storePath != null) {
                storeFile = rootProject.file(storePath)
                storePassword = signingValue("storePassword", "DEVLEDGER_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "DEVLEDGER_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "DEVLEDGER_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed only when a keystore is supplied (see the comment above).
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
        }
    }

    // The in-app language picker (English / Afrikaans / isiXhosa) switches locale at runtime,
    // so every language must ship in the base APK of an App Bundle (Google Developers, n.d.i).
    bundle {
        language {
            enableSplit = false
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
    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    // Core / Compose
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0") // for AppCompatDelegate per-app language API
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.annotation:annotation-experimental:1.5.1")

    // Browser (for custom tabs)
    implementation("androidx.browser:browser:1.8.0")

    // Room (offline-first local persistence — Google Developers, n.d.b)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // WorkManager (background sync — Google Developers, n.d.c)
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // Hilt (dependency injection)
    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-android-compiler:2.52")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Retrofit + OkHttp (REST API client)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")

    // DataStore (settings persistence)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Biometric authentication (Google Developers, n.d.d)
    implementation("androidx.biometric:biometric:1.1.0")

    // Firebase Cloud Messaging for real-time push (Firebase, n.d.). Authentication is handled by
    // the DevLedger API (JWT) with Google/GitHub SSO, so the unused firebase-auth module was removed.
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.firebase:firebase-installations") // Classify retryable installation errors.

    // Google Sign-In (SSO)
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // Security (encrypted local storage for tokens)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Coil (image loading for avatars/attachments)
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Testing - Unit Tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("app.cash.turbine:turbine:1.1.0")
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("com.google.truth:truth:1.4.4")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("androidx.room:room-testing:2.6.1")
    testImplementation("androidx.work:work-testing:2.9.1")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")

    // Testing - Instrumentation / Android Tests
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("com.google.truth:truth:1.4.4")

    // Debug Tooling
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
