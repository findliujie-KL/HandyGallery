plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

base { archivesName.set("FullResShare_Claude") }

android {
    namespace = "com.findliujie.fullresshare"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.findliujie.fullresshare_claude"
        // 30+: MediaStore.createWriteRequest is the only sane way to edit other apps' photos in place.
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    buildFeatures { compose = true }
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
