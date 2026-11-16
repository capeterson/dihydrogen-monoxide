plugins { alias(libs.plugins.dhmo.android.feature) }

dependencies {
    implementation(libs.androidx.activity.compose)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.navigation.testing)
}

// Each screenshot class gets a fresh Robolectric renderer.
tasks.withType<Test>().configureEach { forkEvery = 1 }
