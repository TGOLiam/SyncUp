plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.sync_up"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.sync_up"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Each feature supplies a standard Android resource directory with its own layout folder.
    sourceSets.getByName("main").res.directories.addAll(
        listOf(
            "src/main/res-features/loading",
            "src/main/res-features/event-list",
            "src/main/res-features/join-event",
            "src/main/res-features/create-event"
        )
    )

    buildTypes {
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
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
