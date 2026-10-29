plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // ✅ REQUIRED for Compose (Kotlin 2.0+)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.myapplication"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.autosilent.pro"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    // ✅ THIS is the key fix
    buildFeatures {
        compose = true
        viewBinding = true
    }
}

dependencies {

    // ---------- NORMAL ANDROID UI ----------
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // ---------- COMPOSE (REQUIRED FOR Color.kt) ----------
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)

    // ---------- TEST ----------
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
