plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.adarshkumar.omnitrix"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.adarshkumar.omnitrix"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.0.0-diagnostic"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
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

    lint {
        // Static analysis report is uploaded by CI; do not block the APK build on it.
        abortOnError = false
    }

    // Name the diagnostic APK per project convention: OMNITRIX-debug.apk
    applicationVariants.all {
        if (buildType.name == "debug") {
            outputs.all {
                val apkOutput = this as? com.android.build.gradle.internal.api.ApkVariantOutputImpl
                apkOutput?.outputFileName = "OMNITRIX-debug.apk"
            }
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    testImplementation("junit:junit:4.13.2")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
