plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.test.kiosk"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.test.kiosk"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("sign") {
            storeFile = file("${rootDir.path}/app/keystore.jks")
            storePassword = "allscores2026"
            keyAlias = "allscores"
            keyPassword = "allscores2026"
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("sign")
            optimization {
                enable = false
            }
        }
        debug {
            signingConfig = signingConfigs.getByName("sign")
            optimization {
                enable = false
            }
        }
    }

    buildFeatures {
        viewBinding = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}