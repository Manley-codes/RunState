import java.io.File
import javax.inject.Inject
import org.gradle.api.configuration.BuildFeatures

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

abstract class ReleaseBuildServices {
    @get:Inject abstract val features: BuildFeatures
}

// Spotify's client ID is public app identity, never a client secret or user token.
val spotifyClientId = providers.gradleProperty("runstate.spotifyClientId").orElse("").get()
require(spotifyClientId.isEmpty() || spotifyClientId.matches(Regex("[a-fA-F0-9]{32}"))) {
    "runstate.spotifyClientId must be the public Spotify application ID."
}

// Ordinary development and CI stay unsigned for release. Signing is an explicit local step.
val signRelease = providers.gradleProperty("runstate.signRelease").orElse("false").get().let {
    require(it == "true" || it == "false") { "runstate.signRelease must be true or false." }
    it == "true"
}
val requestedVersionCode = providers.gradleProperty("runstate.versionCode").orNull
val requestedVersionName = providers.gradleProperty("runstate.versionName").orNull
require(!signRelease || (requestedVersionCode != null && requestedVersionName != null)) {
    "Signed releases require explicit runstate.versionCode and runstate.versionName."
}
val appVersionCode = (requestedVersionCode ?: "1").let {
    val parsed = it.toIntOrNull()
    require(it.matches(Regex("[1-9][0-9]*")) && parsed != null && parsed <= 2_100_000_000) {
        "runstate.versionCode must be a whole number from 1 to 2100000000."
    }
    parsed
}
val appVersionName = requestedVersionName ?: "1.0"
require(appVersionName.length <= 64 &&
    appVersionName.matches(Regex("[0-9]+\\.[0-9]+(?:\\.[0-9]+)?(?:[-+][A-Za-z0-9][A-Za-z0-9.+-]*)?"))) {
    "runstate.versionName must be a version such as 1.0, 1.0.0 or 1.0.0-beta.1 (maximum 64 characters)."
}

val releaseSigningValues = if (signRelease) {
    // Check before reading credentials so they never enter configuration-cache state.
    require(!objects.newInstance(ReleaseBuildServices::class.java).features.configurationCache.active.get()) {
        "Signed releases require --no-configuration-cache to avoid retaining signing credentials."
    }
    require(!gradle.startParameter.isBuildScan &&
        gradle.startParameter.logLevel != org.gradle.api.logging.LogLevel.DEBUG) {
        "Build signed releases without --scan or --debug to protect signing credentials."
    }
    listOf("RUNSTATE_RELEASE_STORE_FILE", "RUNSTATE_RELEASE_STORE_PASSWORD",
        "RUNSTATE_RELEASE_KEY_ALIAS", "RUNSTATE_RELEASE_KEY_PASSWORD").associateWith { name ->
        val value = providers.environmentVariable(name).orNull
        require(!value.isNullOrBlank()) { "Signed release configuration is missing $name." }
        value
    }.also { values ->
        val keyStore = File(values.getValue("RUNSTATE_RELEASE_STORE_FILE"))
        require(keyStore.isAbsolute && keyStore.isFile && keyStore.canRead()) {
            "RUNSTATE_RELEASE_STORE_FILE must point to a readable keystore using an absolute path."
        }
    }
} else emptyMap()

android {
    namespace = "com.runstate.mobile"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.runstate.mobile"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName

        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"$spotifyClientId\"")
        buildConfigField("String", "SPOTIFY_REDIRECT_URI", "\"runstate://spotify-callback\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    if (signRelease) {
        signingConfigs.create("release") {
            storeFile = file(releaseSigningValues.getValue("RUNSTATE_RELEASE_STORE_FILE"))
            storePassword = releaseSigningValues.getValue("RUNSTATE_RELEASE_STORE_PASSWORD")
            keyAlias = releaseSigningValues.getValue("RUNSTATE_RELEASE_KEY_ALIAS")
            keyPassword = releaseSigningValues.getValue("RUNSTATE_RELEASE_KEY_PASSWORD")
        }
    }
    buildTypes {
        debug {
            buildConfigField("boolean", "OWNER_TOOLS_ENABLED", "true")
        }
        release {
            buildConfigField("boolean", "OWNER_TOOLS_ENABLED", "false")
            if (signRelease) signingConfig = signingConfigs.getByName("release")
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
    // Official Spotify login library (Maven Central). Opens Spotify's approval screen while
    // RunState is on screen; see SpotifyAppRemoteConnection.connectFromUserAction.
    implementation(libs.spotify.auth)

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
