plugins {
    alias(libs.plugins.dhmo.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.core.model)
    api(libs.kotlinx.serialization.json)
}
