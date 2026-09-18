plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Release signing comes from the environment when it is configured, and falls back to the
// committed testing key when it is not — see signing/README.md for what that key is and is
// not for. The fallback is what lets a clean checkout, and a fork's CI, produce an APK that
// actually installs without anyone setting up secrets first.
val releaseKeystore: File? = System.getenv("USSR_KEYSTORE_FILE")
    ?.let(::File)
    ?.takeIf { it.exists() }

android {
    namespace = "app.ussr"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.ussr"
        // Android 11. The 30-day system trash (MediaStore.createTrashRequest) arrived in
        // API 30, and "nothing is destroyed today" is the whole safety story here, so the
        // app does not ship to versions that cannot honour it.
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("testing") {
            storeFile = rootProject.file("signing/testing.keystore")
            storePassword = "ussr-testing"
            keyAlias = "ussr-testing"
            keyPassword = "ussr-testing"
        }
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("USSR_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("USSR_KEY_ALIAS")
                keyPassword = System.getenv("USSR_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("testing")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    // ML Kit ships its OCR and labelling pipelines as native libraries, and one APK holding
    // all four ABIs is 88MB of which 80MB is those libraries — for a phone that can only
    // ever run one of them. Splitting per ABI cuts what a device actually installs to about
    // a quarter of that. x86 builds are kept because that is what an emulator runs.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = false
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.image.labeling)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
}
