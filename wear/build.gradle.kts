plugins {
    alias(libs.plugins.dhmo.android.application)
}

// The Wear OS companion (issue #1): a tile and a complication that log water through the phone.
android {
    namespace = "casa.peterson.dhmo.wear"

    defaultConfig {
        // The Data Layer only pairs apps with the same package name and signing key as the phone app.
        applicationId = "casa.peterson.dhmo"
        minSdk = 30 // Wear OS 3
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation(projects.core.wear)
    implementation(projects.core.model)
    implementation(projects.core.domain)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.wear.tiles)
    implementation(libs.androidx.wear.protolayout)
    implementation(libs.androidx.wear.protolayout.material)
    implementation(libs.androidx.wear.protolayout.expression)
    implementation(libs.androidx.wear.watchface.complications.data.source.ktx)
    implementation(libs.androidx.concurrent.futures.ktx)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.androidx.wear.tiles.testing)
}
