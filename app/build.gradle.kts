plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.easysell"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.easysell"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        multiDexEnabled = true
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:34.3.0"))

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation("com.google.firebase:firebase-firestore")
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)


    implementation(libs.google.gms.auth)
    // Google Sign-In
    implementation("com.google.android.gms:play-services-auth:21.4.0")

    // Networking with Retrofit
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging.interceptor) // For debugging network calls
    implementation("com.google.firebase:firebase-messaging:25.0.1")
// Cloudinary (Image Uploads)
    implementation("com.cloudinary:cloudinary-android:3.1.2")
    implementation("com.google.firebase:firebase-auth:24.0.1")
    // Image Loading
    implementation(libs.glide)
    implementation("androidx.multidex:multidex:2.0.1")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0") // <--- ADD THIS (Make sure version matches your glide version)

    
}