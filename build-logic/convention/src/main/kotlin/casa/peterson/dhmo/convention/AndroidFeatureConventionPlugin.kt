package casa.peterson.dhmo.convention

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("dhmo.android.library")
        pluginManager.apply("dhmo.android.compose")
        pluginManager.apply("dhmo.android.hilt")
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

        dependencies {
            // The feature set (§3.2).
            add("implementation", project(":core:data"))
            add("implementation", project(":core:domain"))
            add("implementation", project(":core:model"))
            add("implementation", project(":core:common"))
            add("implementation", project(":core:designsystem"))
            add("testImplementation", project(":core:testing"))
            add("testImplementation", libs.library("androidx.compose.ui.test.junit4"))
            add("testImplementation", libs.library("androidx.test.espresso.core"))

            add("implementation", libs.library("androidx.navigation.compose"))
            add("implementation", libs.library("androidx.hilt.navigation.compose"))
            add("implementation", libs.library("androidx.lifecycle.runtime.compose"))
            add("implementation", libs.library("androidx.lifecycle.viewmodel.compose"))
            add("implementation", libs.library("kotlinx.serialization.json"))
            add("implementation", libs.library("kotlinx.coroutines.android"))
        }
    }
}
