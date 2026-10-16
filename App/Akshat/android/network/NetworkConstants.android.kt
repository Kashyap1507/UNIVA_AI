package com.kashyap.wizlyai.network

import android.os.Build

/**
 * Android-specific implementation to get base URL
 * Returns 10.0.2.2 for emulator, machine IP for physical device
 */
internal actual fun getPlatformSpecificBaseUrl(): String {
    // Check if running on emulator
    val isEmulator =
        Build.FINGERPRINT.contains("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for x86") ||
            Build.MANUFACTURER.contains("Genymotion") ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu") ||
            Build.PRODUCT.contains("sdk") ||
            Build.PRODUCT.contains("emulator")

    return if (isEmulator) {
        NetworkConstants.BaseUrls.ANDROID_EMULATOR // 10.0.2.2:5000
    } else {
        NetworkConstants.BaseUrls.ANDROID_DEVICE // 192.168.0.78:5000
    }
}

/**
 * Android-specific implementation to detect debug mode
 * SECURITY: Reads from BuildConfig.DEBUG which is set at compile time
 *
 * Returns:
 * - true in debug builds (enables HTTP, local servers)
 * - false in release builds (enforces HTTPS, production servers)
 */
internal actual fun getPlatformDebugFlag(): Boolean {
    return try {
        // Use reflection to access BuildConfig.DEBUG from the Android app module
        val buildConfigClass = Class.forName("com.kashyap.wizlyai.android.BuildConfig")
        val debugField = buildConfigClass.getField("DEBUG")

        // Handle both primitive boolean and Boolean object types
        val debugValue =
            when (debugField.type) {
                Boolean::class.javaPrimitiveType -> debugField.getBoolean(null)
                Boolean::class.javaObjectType -> debugField.get(null) as Boolean
                else -> {
                    println("⚠️ [NETWORK] Unexpected DEBUG field type: ${debugField.type}")
                    false
                }
            }

        // 🔍 DEBUG: Log the detected debug flag
        println("🏗️ [NETWORK] Debug Mode Detection:")
        println("   BuildConfig.DEBUG = $debugValue")
        println("   Field Type: ${debugField.type}")

        debugValue
    } catch (e: ClassNotFoundException) {
        // BuildConfig not found - default to production (false)
        println("⚠️ [NETWORK] BuildConfig class not found - defaulting to PRODUCTION mode")
        println("   Error: ${e.message}")
        false
    } catch (e: NoSuchFieldException) {
        // DEBUG field not found - default to production (false)
        println("⚠️ [NETWORK] BuildConfig.DEBUG field not found - defaulting to PRODUCTION mode")
        println("   Error: ${e.message}")
        false
    } catch (e: Exception) {
        // Any other error - default to production (false) for security
        println("⚠️ [NETWORK] Error detecting debug mode - defaulting to PRODUCTION mode")
        println("   Error: ${e.message}")
        e.printStackTrace()
        false
    }
}
