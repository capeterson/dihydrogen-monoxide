plugins {
    alias(libs.plugins.dhmo.android.library)
    alias(libs.plugins.dhmo.android.compose)
    alias(libs.plugins.dhmo.android.hilt)
}

// Used only as testImplementation / androidTestImplementation of other modules, so the test libraries are exposed as api.
// Robolectric and Roborazzi are compileOnly: they must never reach an instrumented (device) test classpath.
dependencies {
    api(platform(libs.androidx.compose.bom))
    api(projects.core.data)
    api(projects.core.model)
    api(projects.core.domain)
    api(projects.core.common)
    api(projects.core.designsystem)

    api(libs.junit4)
    api(libs.truth)
    api(libs.turbine)
    api(libs.kotlinx.coroutines.test)
    compileOnly(libs.robolectric)
    api(libs.androidx.test.core)
    api(libs.androidx.test.ext.junit)
    api(libs.androidx.compose.ui.test.junit4)
    compileOnly(libs.roborazzi)
    compileOnly(libs.roborazzi.compose)
    compileOnly(libs.roborazzi.junit.rule)
    api(libs.hilt.android.testing)
}
