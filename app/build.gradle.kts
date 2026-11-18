import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
}

// Local settings are ignored by Git and let Android Studio use the local test stack.
val localConfig = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}

// Gradle properties or environment variables override machine-local settings.
fun buildConfigString(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
        .replace("\n", "\\n").replace("\r", "\\r") + "\""

val supabaseUrl = providers.gradleProperty("syncup.supabase.url")
    .orElse(providers.environmentVariable("SYNCUP_SUPABASE_URL"))
    .orElse(localConfig.getProperty("syncup.supabase.url", ""))
val supabasePublishableKey = providers.gradleProperty("syncup.supabase.publishableKey")
    .orElse(providers.environmentVariable("SYNCUP_SUPABASE_PUBLISHABLE_KEY"))
    .orElse(localConfig.getProperty("syncup.supabase.publishableKey", ""))

val voiceServiceUrl = providers.gradleProperty("syncup.voice.url")
    .orElse(providers.environmentVariable("SYNCUP_VOICE_URL"))
    .orElse(localConfig.getProperty("syncup.voice.url", ""))

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
        buildConfigField("String", "VOICE_SERVICE_URL", buildConfigString(voiceServiceUrl.get()))
        buildConfigField("String", "SUPABASE_URL", buildConfigString(supabaseUrl.get()))
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", buildConfigString(supabasePublishableKey.get()))
    }

    // Each feature supplies a standard Android resource directory with its own layout folder.
    sourceSets.getByName("main").res.directories.addAll(
        listOf(
            "src/main/res-features/loading",
            "src/main/res-features/auth",
            "src/main/res-features/event-list",
            "src/main/res-features/join-event",
            "src/main/res-features/create-event",
            "src/main/res-features/workspace"
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
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.ktor.client.android)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
