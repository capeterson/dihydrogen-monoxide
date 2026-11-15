plugins {
    alias(libs.plugins.dhmo.android.library)
    alias(libs.plugins.dhmo.android.hilt)
}

dependencies {
    implementation(projects.core.common)
    api(libs.okhttp)
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.okhttp.mockwebserver)
}
