package casa.peterson.dhmo.convention

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        val androidExtension = extensions.getByType(CommonExtension::class.java)
        androidExtension.buildFeatures.compose = true

        configureKotlinCompilerOptions(listOf("androidx.compose.material3.ExperimentalMaterial3Api"))

        extensions.configure<ComposeCompilerGradlePluginExtension> {
            if (providers.gradleProperty("dhmo.composeReports").map(String::toBoolean).orElse(false).get()) {
                reportsDestination.set(layout.buildDirectory.dir("compose_compiler"))
                metricsDestination.set(layout.buildDirectory.dir("compose_compiler"))
            }
        }

        dependencies {
            val bom = libs.library("androidx.compose.bom")
            add("implementation", platform(bom))
            add("androidTestImplementation", platform(bom))
            add("implementation", libs.library("androidx.compose.ui"))
            add("implementation", libs.library("androidx.compose.ui.tooling.preview"))
            add("implementation", libs.library("androidx.compose.material3"))
            add("debugImplementation", libs.library("androidx.compose.ui.tooling"))
            add("debugImplementation", libs.library("androidx.compose.ui.test.manifest"))
        }
    }
}
