plugins { alias(libs.plugins.dhmo.android.feature) }

dependencies {
    implementation(projects.core.backup)
    testImplementation(libs.androidx.compose.ui.test.junit4)
}

// Each screenshot class gets a fresh Robolectric renderer.
tasks.withType<Test>().configureEach { forkEvery = 1 }
