plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

// Spotify's client ID is public app identity, never a client secret or user token.
val spotifyClientId = providers.gradleProperty("runstate.spotifyClientId").orElse("").get()
require(spotifyClientId.isEmpty() || spotifyClientId.matches(Regex("[a-fA-F0-9]{32}"))) {
    "runstate.spotifyClientId must be the public Spotify application ID."
}

android {
    namespace = "com.runstate.mobile"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.runstate.mobile"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"$spotifyClientId\"")
        buildConfigField("String", "SPOTIFY_REDIRECT_URI", "\"runstate://spotify-callback\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

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
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

androidComponents {
    beforeVariants(selector().withBuildType("release")) { variant ->
        (variant as com.android.build.api.variant.HasUnitTestBuilder).enableUnitTest = true
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {

    // Room 2.8.4 reads the exported schema JSON during migration tests using kotlinx
    // serialization 1.8.1, but AGP resolves the androidTest classpath consistently with
    // the app classpath, where Lifecycle pulls serialization 1.7.3. Room's serializers
    // then meet a runtime too old to satisfy them and every migration test dies with
    // AbstractMethodError. Raising the version everywhere keeps the two classpaths on
    // one runtime. This is a constraint, not a dependency: nothing new is added to the
    // app, an existing transitive library simply resolves to the version Room needs.
    constraints {
        implementation(libs.kotlinx.serialization.core)
    }

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.room.runtime)

    // Used directly for the application scope's main dispatcher, so declared rather than
    // left to arrive through Compose and Lifecycle.
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.gson)
    implementation(libs.maplibre)
    implementation(libs.okhttp)
    // Official Spotify release, pinned locally; provenance and checksum are in third-party notices.
    implementation(files("libs/spotify-app-remote-release-0.8.0.aar"))

    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
