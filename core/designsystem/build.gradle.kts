plugins {
    alias(libs.plugins.dhmo.android.library)
    alias(libs.plugins.dhmo.android.compose)
    alias(libs.plugins.dhmo.android.roborazzi)
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.domain)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    testImplementation(projects.core.testing)
}

// Every test class gets a fresh JVM: after many Robolectric NATIVE-graphics captures in one JVM (Roborazzi 1.79) later
// captures intermittently come out blank, which would record or compare blank images.
tasks.withType<Test>().configureEach { forkEvery = 1 }
