plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.compose.compiler)
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

android {
    // Demo guide layout: the launcher code lives in App/Akshat/android-launcher
    sourceSets.getByName("main").java.srcDirs("../Akshat/android-launcher")
    namespace = "com.kashyap.wizlyai.android"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.kashyap.wizlyai.android"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            // applicationIdSuffix = ".debug"  // Commented out to match google-services.json
            versionNameSuffix = "-debug"

            // BuildConfig fields for debug mode detection
            buildConfigField("Boolean", "DEBUG", "true")
        }

        release {
            // SECURITY: Enable code obfuscation and shrinking
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true

            // ProGuard/R8 configuration
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )

            // BuildConfig fields for production mode detection
            buildConfigField("Boolean", "DEBUG", "false")

            // TODO: Configure signing for release builds
            // See: keystore.properties.example for setup instructions
            // signingConfig = signingConfigs.getByName("release")
        }
    }

    // Enable BuildConfig generation
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    // Disable NullSafeMutableLiveData lint check due to Kotlin 2.1.10 + AGP 8.7.3 incompatibility
    // Safe to disable: This is a Compose-based app using StateFlow/MutableStateFlow, not LiveData
    // The lint detector crashes with: "Found class KaCallableMemberCall, but interface was expected"
    lint {
        disable += "NullSafeMutableLiveData"
    }
}

dependencies {
    implementation(projects.shared)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.material)
    implementation(libs.androidx.activity.compose)
    implementation("androidx.core:core-splashscreen:1.0.1")
    debugImplementation(libs.compose.ui.tooling)

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    implementation("com.google.firebase:firebase-messaging") // FCM for push notifications

    // Google Sign-In
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    // OpenCV for document edge detection
    implementation("org.opencv:opencv:4.9.0")
}
