plugins { alias(libs.plugins.dhmo.android.feature) }

dependencies {
    implementation(libs.androidx.activity.compose)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.navigation.testing)
}

// Give each screenshot class a fresh renderer process.
tasks.withType<Test>().configureEach { forkEvery = 1 }
