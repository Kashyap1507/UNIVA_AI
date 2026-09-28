import co.touchlab.skie.configuration.FlowInterop
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

skie {
    features {
        group {
            FlowInterop.Enabled(value = false)
        }
    }
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.jetbrainsCompose)
    id("co.touchlab.skie") version "0.10.5"
    id("com.google.devtools.ksp") version "2.1.10-1.0.30"
    kotlin("native.cocoapods")
}

kotlin {
    androidTarget {
        compilations.all {
            val compilationName = name
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(
                        if (compilationName.contains("UnitTest", ignoreCase = true)) {
                            JvmTarget.JVM_11
                        } else {
                            JvmTarget.JVM_1_8
                        },
                    )
                }
            }
        }
    }

    val xcf = XCFramework()

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach {
        it.binaries.framework {
            baseName = "shared"
            isStatic = true
            xcf.add(this)
            binaryOption("bundleId", "com.kashyap.wizlyai.shared")
        }
    }

    // CocoaPods configuration for iOS
    // TEMPORARILY DISABLED: Firebase CocoaPods removed due to Xcode 16.4/Kotlin incompatibility
    // TODO: Re-enable when Kotlin/Native adds Xcode 16.4 support or find alternative analytics/crash reporting
    cocoapods {
        version = "1.0"
        summary = "Wizly Shared Module"
        homepage = "https://github.com/wizly-ai/wizly-ai" // TODO: set your repo URL
        ios.deploymentTarget = "14.0"

        framework {
            baseName = "shared"
            isStatic = true
        }

        // Firebase Analytics for iOS - DISABLED (Xcode 16.4 incompatibility)
        // Alternative: Consider Firebase Swift SDK or Crashlytics alternatives like Sentry
        // pod("FirebaseAnalytics") {
        //     version = "~> 10.0"
        //     extraOpts += listOf("-compiler-option", "-fmodules")
        // }

        // Firebase Crashlytics for iOS - DISABLED (Xcode 16.4 incompatibility)
        // pod("FirebaseCrashlytics") {
        //     version = "~> 10.0"
        //     extraOpts += listOf("-compiler-option", "-fmodules")
        // }

        // Sentry for iOS - DISABLED (Xcode 16.4 compatibility issues)
        // Using Firebase Crashlytics from main iOS app Podfile instead
        // pod("Sentry") {
        //     version = "~> 8.38.0"
        // }
    }

    sourceSets {
        // Demo guide layout: all app code lives in App/<Member>/ (common, android, ios, tests)
        commonMain { kotlin.srcDirs("../Karthick/common", "../Akshat/common", "../Shashang/common", "../Kashyap/common", "../Zehra/common") }
        androidMain { kotlin.srcDirs("../Karthick/android", "../Akshat/android", "../Shashang/android", "../Kashyap/android", "../Zehra/android") }
        commonTest { kotlin.srcDir("../Zehra/tests/common") }

        commonMain.dependencies {
            // Compose Multiplatform
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(compose.materialIconsExtended)

            // Markdown rendering for tutor answers (approved in migration plan §6, Phase 3)
            implementation("com.mikepenz:multiplatform-markdown-renderer-m3:0.34.0")

            // Coroutines
            implementation(libs.kotlinx.coroutines.core)

            // Serialization
            implementation(libs.kotlinx.serialization.json)

            // DateTime
            implementation(libs.kotlinx.datetime)

            // Ktor HTTP Client - Core
            implementation("io.ktor:ktor-client-core:2.3.7")
            implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
            implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
            implementation("io.ktor:ktor-client-logging:2.3.7")
            implementation("io.ktor:ktor-client-auth:2.3.7")
            implementation("io.ktor:ktor-client-encoding:2.3.7")

            // SKIE
            implementation("co.touchlab.skie:configuration-annotations:0.10.5")

            // Coil Image Loading
            implementation("io.coil-kt.coil3:coil-compose:3.0.0")
            implementation("io.coil-kt.coil3:coil:3.0.0")
        }

        androidMain.dependencies {
            // Vico Charts for Analytics (Android-only)
            implementation("com.patrykandpatrick.vico:compose-m3:2.0.0-alpha.28")
            implementation("com.patrykandpatrick.vico:compose:2.0.0-alpha.28")
            implementation("com.patrykandpatrick.vico:core:2.0.0-alpha.28")

            // Room for local storage
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.room.ktx)
            implementation("androidx.room:room-paging:2.6.1")

            // Paging 3
            implementation("androidx.paging:paging-runtime:3.2.1")
            implementation("androidx.paging:paging-compose:3.2.1")

            // WorkManager for background sync
            implementation("androidx.work:work-runtime-ktx:2.9.0")

            // Image compression
            implementation("id.zelory:compressor:3.0.1")

            // Android specific
            implementation(libs.androidx.lifecycle.viewmodel.ktx)

            // Android-specific Ktor engine (OkHttp)
            implementation("io.ktor:ktor-client-okhttp:2.3.7")
            implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

            // Coroutines Android
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

            // Coil Network
            implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.0")

            // CameraX for scanning
            implementation("androidx.camera:camera-camera2:1.3.1")
            implementation("androidx.camera:camera-lifecycle:1.3.1")
            implementation("androidx.camera:camera-view:1.3.1")

            // ML Kit for OCR
            implementation("com.google.mlkit:text-recognition:16.0.0")

            // Android Security
            implementation("androidx.security:security-crypto:1.1.0-alpha06")

            // OpenCV for document edge detection
            implementation("org.opencv:opencv:4.9.0")

            // Firebase Analytics and Crashlytics for crash reporting
            implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
            implementation("com.google.firebase:firebase-analytics-ktx")
            implementation("com.google.firebase:firebase-crashlytics-ktx")

            // Sentry for Android - DISABLED (using Firebase Crashlytics instead)
            // implementation("io.sentry:sentry-android:7.14.0")
            // implementation("io.sentry:sentry-compose-android:7.14.0")
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation("io.ktor:ktor-client-mock:2.3.7")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
        }

        val androidUnitTest by getting {
            kotlin.srcDir("../Zehra/tests/android")
            dependencies {
                implementation(libs.kotlin.test)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
                implementation("io.mockk:mockk:1.13.8")
                implementation("io.mockk:mockk-android:1.13.8")
                implementation("androidx.arch.core:core-testing:2.2.0")
            }
        }

        val iosX64Main by getting
        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting
        val iosMain by creating {
            kotlin.srcDirs("../Karthick/ios", "../Akshat/ios", "../Shashang/ios", "../Kashyap/ios", "../Zehra/ios")
            dependsOn(commonMain.get())
            iosX64Main.dependsOn(this)
            iosArm64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)
        }

        iosMain.dependencies {
            // iOS-specific Ktor engine (Darwin/NSURLSession)
            implementation("io.ktor:ktor-client-darwin:2.3.7")

            // Coil3 network engine for iOS (required for loading remote images)
            // Note: Using coil-network-ktor2 for Ktor 2.x compatibility
            implementation("io.coil-kt.coil3:coil-network-ktor2:3.0.0")
        }
    }
}

android {
    namespace = "com.kashyap.wizlyai"
    compileSdk = 35
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    // Disable NullSafeMutableLiveData lint check due to Kotlin 2.1.10 + AGP 8.7.3 incompatibility
    // Safe to disable: This is a Compose-based app using StateFlow/MutableStateFlow, not LiveData
    // The lint detector crashes with: "Found class KaCallableMemberCall, but interface was expected"
    lint {
        disable += "NullSafeMutableLiveData"
    }
}

dependencies {
    implementation(libs.androidx.ui.tooling.preview.android)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.activity.compose) // For BackHandler support

    // Room KSP for annotation processing
    add("kspAndroid", "androidx.room:room-compiler:2.6.1")
}
