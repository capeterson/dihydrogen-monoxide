plugins {
    alias(libs.plugins.dhmo.android.application)
    alias(libs.plugins.dhmo.android.compose)
    alias(libs.plugins.dhmo.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "casa.peterson.dhmo"

    defaultConfig {
        applicationId = "casa.peterson.dhmo"
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.model)
    implementation(projects.core.domain)
    implementation(projects.core.backup)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(projects.core.network)
    implementation(projects.core.reminders)
    implementation(projects.core.weather)
    implementation(projects.feature.onboarding)
    implementation(projects.feature.today)
    implementation(projects.feature.history)
    implementation(projects.feature.insights)
    implementation(projects.feature.profile)
    implementation(projects.feature.reminders)
    implementation(projects.feature.settings)
    implementation(projects.sync.healthconnect)
    implementation(projects.sync.drive)
    implementation(projects.widget)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(projects.core.testing)

    androidTestImplementation(projects.core.testing)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
