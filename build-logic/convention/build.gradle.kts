plugins {
    `kotlin-dsl`
    alias(libs.plugins.ktlint)
}

group = "casa.peterson.dhmo.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        allWarningsAsErrors = true
    }
}

ktlint {
    version.set("1.8.0")
    android.set(true)
    outputToConsole.set(true)
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.room.gradlePlugin)
    compileOnly(libs.hilt.gradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)
    compileOnly(libs.ktlint.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "dhmo.android.application"
            implementationClass = "casa.peterson.dhmo.convention.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "dhmo.android.library"
            implementationClass = "casa.peterson.dhmo.convention.AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "dhmo.android.compose"
            implementationClass = "casa.peterson.dhmo.convention.AndroidComposeConventionPlugin"
        }
        register("androidHilt") {
            id = "dhmo.android.hilt"
            implementationClass = "casa.peterson.dhmo.convention.AndroidHiltConventionPlugin"
        }
        register("androidRoom") {
            id = "dhmo.android.room"
            implementationClass = "casa.peterson.dhmo.convention.AndroidRoomConventionPlugin"
        }
        register("androidFeature") {
            id = "dhmo.android.feature"
            implementationClass = "casa.peterson.dhmo.convention.AndroidFeatureConventionPlugin"
        }
        register("androidTest") {
            id = "dhmo.android.test"
            implementationClass = "casa.peterson.dhmo.convention.AndroidTestConventionPlugin"
        }
        register("jvmLibrary") {
            id = "dhmo.jvm.library"
            implementationClass = "casa.peterson.dhmo.convention.JvmLibraryConventionPlugin"
        }
        register("quality") {
            id = "dhmo.quality"
            implementationClass = "casa.peterson.dhmo.convention.QualityConventionPlugin"
        }
    }
}
