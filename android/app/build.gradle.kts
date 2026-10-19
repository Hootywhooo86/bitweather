plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// CI passes -PbuildNumber=<run number>; local builds are 1.0.0-local.
val buildNumber = (project.findProperty("buildNumber") as String?)?.toIntOrNull()

android {
    namespace = "com.hootywhooo86.bitweather"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hootywhooo86.bitweather"
        minSdk = 26
        targetSdk = 35
        versionCode = 100 + (buildNumber ?: 0)
        versionName = if (buildNumber != null) "1.0.$buildNumber" else "1.0.0-local"
    }

    signingConfigs {
        // A fixed key so every build (local or CI) can update the installed app in place.
        create("bitweather") {
            storeFile = file("../keystore/bitweather.jks")
            storePassword = "bitweather"
            keyAlias = "bitweather"
            keyPassword = "bitweather"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("bitweather")
        }
        debug {
            signingConfig = signingConfigs.getByName("bitweather")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2") }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
