plugins {
    alias(libs.plugins.dhmo.android.library)
    alias(libs.plugins.dhmo.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// Build properties (pass with -P, or put them in ~/.gradle/gradle.properties). A fork only needs `dhmo.update.repo`.
//   dhmo.update.repo     owner/name of the GitHub repository whose releases are offered as updates.
//   dhmo.build.tag       release tag this build will be published under (CI sets it), so the app can tell its own release apart.
//   dhmo.build.timeMs    epoch millis the build started (CI sets it); without it the install time stands in.
val updateRepo = providers.gradleProperty("dhmo.update.repo").orElse("capeterson/dihydrogen-monoxide").get()
val buildTag = providers.gradleProperty("dhmo.build.tag").orElse("").get()
val buildTimeMs = providers.gradleProperty("dhmo.build.timeMs").orElse("0").get().toLong()

require(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+").matches(updateRepo)) { "dhmo.update.repo must look like owner/name, was '$updateRepo'" }

android {
    buildFeatures.buildConfig = true
    defaultConfig {
        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")
        buildConfigField("String", "BUILD_TAG", "\"$buildTag\"")
        buildConfigField("long", "BUILD_TIME_MS", "${buildTimeMs}L")
    }
}

dependencies {
    api(projects.core.data)
    implementation(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.network)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)

    testImplementation(projects.core.testing)
    testImplementation(libs.okhttp.mockwebserver)
}
