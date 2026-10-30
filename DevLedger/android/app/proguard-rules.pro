# =============================================================================
# DevLedger release shrinking rules (R8 / ProGuard)
#
# References (IIE Harvard - full entries in REFERENCES-CODE.md):
#   Shrink, obfuscate and optimise your app: (Google Developers, n.d.j)
#   Retrofit, OkHttp, Room, Hilt, WorkManager and Firebase ship their own consumer rules,
#   so only application-specific rules are needed here.
# =============================================================================

# Keep annotations, generic signatures and inner-class metadata that reflection-based
# libraries (Retrofit, kotlinx.serialization, Room) read at runtime.
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault

# Readable crash reports: keep line numbers, hide the original source file name.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# --- kotlinx.serialization (Kotlin, n.d.) -----------------------------------------
# Generated serializers and companions of every @Serializable DTO must survive shrinking,
# otherwise JSON (de)serialisation fails only in the minified release build.
-keepclassmembers class com.devledger.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.devledger.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.devledger.app.**$$serializer { *; }
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Retrofit service interfaces and DTOs (Square, n.d.) -------------------------
-keep interface com.devledger.app.data.remote.DevLedgerApiService { *; }
-keep class com.devledger.app.data.remote.dto.** { *; }
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# --- Room entities are referenced by generated code (Google Developers, n.d.b) --
-keep class com.devledger.app.data.local.entity.** { *; }

# --- Hilt-injected WorkManager workers are instantiated reflectively --------------
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# --- Tink (androidx.security-crypto) references optional annotation classes ------
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-dontwarn com.google.crypto.tink.**
