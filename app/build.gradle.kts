plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.classlist"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.classlist"
        minSdk = 29
        targetSdk = 37
        versionCode = 3
        versionName = "1.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isDebuggable = false
        }
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
