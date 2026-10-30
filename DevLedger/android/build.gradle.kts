// Top-level build file for DevLedger.
// Architecture reference: (Google Developers, n.d.a). See REFERENCES-CODE.md for full entries.
//
// Toolchain note (Google Developers, n.d.g): Google Play requires new apps and updates to
// target Android 16 (API 36) from 31 August 2026 (Google Play Console Help, n.d.), and the
// Android Gradle plugin needs 8.9.1 or newer to build against compileSdk 36. AGP 8.9.1
// needs Gradle 8.11.1 or newer (see gradle/wrapper/gradle-wrapper.properties).
plugins {
    id("com.android.application") version "8.9.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.dagger.hilt.android") version "2.52" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.25" apply false
}
