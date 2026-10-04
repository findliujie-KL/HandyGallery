plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

base { archivesName.set("handygallery_0.8") }

android {
    namespace = "com.lmxyapp.handygallery"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.lmxyapp.handygallery"
        // 30+: MediaStore.createWriteRequest is the only sane way to edit other apps' photos in place.
        minSdk = 30
        targetSdk = 36
        versionCode = 2
        versionName = "0.8"
        // Last day a trial build works, as yyyy-MM-dd; empty means no limit. Overridable with -PtrialExpiry=...
        buildConfigField("String", "TRIAL_EXPIRY", "\"${providers.gradleProperty("trialExpiry").getOrElse("")}\"")
    }
    buildTypes {
        // A release-style build for handing out: not debuggable, and it stops prepping and sharing as file
        // after the date below. Signed with the debug key until there is a release keystore.
        create("trial") {
            initWith(getByName("release"))
            // Strip unused code and resources: most of an unshrunk APK is library code the app never calls.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            buildConfigField("String", "TRIAL_EXPIRY", "\"${providers.gradleProperty("trialExpiry").getOrElse("2026-11-30")}\"")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.08.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.exifinterface:exifinterface:1.4.1")
}
