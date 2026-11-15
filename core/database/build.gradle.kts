plugins {
    alias(libs.plugins.dhmo.android.library)
    alias(libs.plugins.dhmo.android.hilt)
    alias(libs.plugins.dhmo.android.room)
}

android {
    sourceSets["androidTest"].assets.directories.add("$projectDir/schemas")
}

dependencies {
    api(projects.core.model)
    api(libs.androidx.room.runtime)
    api(libs.androidx.room.ktx)
    api(libs.androidx.paging.runtime)
    api(libs.kotlinx.coroutines.android)

    testImplementation(libs.kotlinx.serialization.json)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test>().configureEach {
    systemProperty("dhmo.schemaDir", "$projectDir/schemas")
}
