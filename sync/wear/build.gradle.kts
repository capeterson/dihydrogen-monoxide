plugins {
    alias(libs.plugins.dhmo.android.library)
    alias(libs.plugins.dhmo.android.hilt)
}

// Phone side of the Wear OS companion (issue #1): receives drinks logged on the watch and publishes today's state.
dependencies {
    implementation(projects.core.data)
    implementation(projects.core.wear)
    implementation(projects.core.model)
    implementation(projects.core.common)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(projects.core.testing)
}
