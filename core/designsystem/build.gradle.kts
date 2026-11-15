plugins {
    alias(libs.plugins.dhmo.android.library)
    alias(libs.plugins.dhmo.android.compose)
    alias(libs.plugins.dhmo.android.roborazzi)
}

dependencies {
    api(projects.core.model)
    implementation(libs.androidx.core.ktx)

    testImplementation(projects.core.testing)
}
