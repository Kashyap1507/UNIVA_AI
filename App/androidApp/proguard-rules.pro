# ============================================
# Wizly Android App - ProGuard/R8 Rules
# ============================================
# SECURITY: Obfuscation rules for production builds
# These rules shrink, optimize, and obfuscate the code

# ============================================
# General Android & Kotlin
# ============================================
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose

# Keep source file names and line numbers for better crash reports
-keepattributes SourceFile,LineNumberTable

# Rename source file attribute to hide real source file names
-renamesourcefileattribute SourceFile

# ============================================
# Kotlin Metadata (Required for Kotlin Reflection)
# ============================================
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# ============================================
# Kotlin Multiplatform Shared Code
# ============================================
# Keep all shared module code from obfuscation
# This is critical for KMP to work across Android/iOS
-keep class com.kashyap.wizlyai.** { *; }
-keepclassmembers class com.kashyap.wizlyai.** { *; }

# Keep Companion objects (used heavily in KMP)
-keepclassmembers class com.kashyap.wizlyai.** {
    *** Companion;
}

# ============================================
# Kotlinx Serialization
# ============================================
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# Keep all serializers
-keep,includedescriptorclasses class com.kashyap.wizlyai.**$$serializer { *; }
-keepclassmembers class com.kashyap.wizlyai.** {
    *** Companion;
}

# Keep serializer methods
-keepclasseswithmembers class com.kashyap.wizlyai.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep serializable data classes
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *;
}

# ============================================
# Android Architecture Components
# ============================================
# Room Database
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Database class * { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Dao interface *

# ViewModel
-keep class * extends androidx.lifecycle.ViewModel {
    <init>();
}

# LiveData
-keep class * extends androidx.lifecycle.LiveData { *; }

# ============================================
# Ktor HTTP Client
# ============================================
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { *; }
-keep class kotlin.reflect.jvm.internal.** { *; }

# ============================================
# Kotlin Coroutines
# ============================================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ============================================
# Firebase
# ============================================
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Firebase Analytics
-keep class com.google.firebase.analytics.** { *; }
-keep class com.google.android.gms.measurement.** { *; }

# Firebase Crashlytics
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# ============================================
# OkHttp & Retrofit (used by Ktor)
# ============================================
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ============================================
# Android Security Crypto
# (EncryptedSharedPreferences)
# ============================================
-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }

# ============================================
# CameraX
# ============================================
-keep class androidx.camera.** { *; }
-keep interface androidx.camera.** { *; }

# ============================================
# ML Kit (Text Recognition)
# ============================================
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ============================================
# OpenCV (Document Scanner)
# ============================================
-keep class org.opencv.** { *; }

# ============================================
# Coil Image Loading
# ============================================
-keep class coil.** { *; }
-keep interface coil.** { *; }

# ============================================
# Jetpack Compose
# ============================================
-keep class androidx.compose.** { *; }
-keep @androidx.compose.runtime.Composable class *
-keep @androidx.compose.runtime.Stable class *
-keep @androidx.compose.runtime.Immutable class *

# ============================================
# Remove Logging in Release Builds
# ============================================
# SECURITY: Strip all Log.d, Log.v, Log.i calls from release builds
# Only keep Log.e and Log.w for crash reporting
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Remove println() calls
-assumenosideeffects class kotlin.io.ConsoleKt {
    public static *** println(...);
}

# ============================================
# Optimization
# ============================================
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-dontpreverify

# ============================================
# Warnings to Ignore
# ============================================
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ============================================
# Keep BuildConfig for debug flag
# ============================================
-keep class com.kashyap.wizlyai.android.BuildConfig { *; }
