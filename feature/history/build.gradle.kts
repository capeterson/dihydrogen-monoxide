plugins { alias(libs.plugins.dhmo.android.feature) }

dependencies {
    implementation(libs.androidx.paging.compose)
    testImplementation(libs.androidx.paging.testing)
}

// A separate JVM per screenshot class keeps Robolectric native captures independent.
tasks.withType<Test>().configureEach { forkEvery = 1 }
