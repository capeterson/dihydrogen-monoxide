plugins { alias(libs.plugins.dhmo.android.feature) }

dependencies {
    implementation(projects.core.backup)
    testImplementation(libs.androidx.compose.ui.test.junit4)
}
