plugins { alias(libs.plugins.dhmo.android.feature) }

dependencies {
    implementation(libs.vico.compose.m3)
    testImplementation(libs.androidx.compose.ui.test.junit4)
}

// Each screenshot class gets a fresh Robolectric renderer.
tasks.withType<Test>().configureEach { forkEvery = 1 }
