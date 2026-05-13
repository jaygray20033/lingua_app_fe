# Default ProGuard rules for Lingua Android
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Retrofit
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# Gson
-keep class com.google.gson.** { *; }
-keep class com.lingua.app.models.** { *; }
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

# AndroidX Security (EncryptedSharedPreferences)
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# Keep model classes for serialization
-keep class com.lingua.app.models.** { <fields>; <init>(...); }
