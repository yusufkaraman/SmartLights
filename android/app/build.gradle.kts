plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.smartlights.app"
    compileSdk = 36
    defaultConfig { applicationId = "com.smartlights.app"; minSdk = 26; targetSdk = 36; versionCode = 1; versionName = "1.0" }
    // CI points SIGNING_KEYSTORE at the restored key so phone-installed builds can update each other.
    signingConfigs { getByName("debug") { System.getenv("SIGNING_KEYSTORE")?.let { storeFile = file(it) } } }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildTypes { release { isMinifyEnabled = true; isShrinkResources = true; signingConfig = signingConfigs.getByName("debug"); proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") } }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui:1.9.0")
    implementation("androidx.compose.foundation:foundation:1.9.0")
    implementation("androidx.compose.material3:material3:1.5.0-alpha07")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation(files("libs/bridge.aar"))
    // Gemini Nano via AICore: the model lives in the system, only the client library ships in the APK.
    implementation("com.google.mlkit:genai-prompt:1.0.0-beta5")
    testImplementation("junit:junit:4.13.2")
}

