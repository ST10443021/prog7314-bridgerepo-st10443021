# DevLedger ProGuard rules
-keepattributes *Annotation*
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.devledger.app.data.remote.dto.** { *; }
-dontwarn kotlinx.serialization.**
