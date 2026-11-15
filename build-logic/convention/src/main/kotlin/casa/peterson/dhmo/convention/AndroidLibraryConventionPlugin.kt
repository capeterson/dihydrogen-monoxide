package casa.peterson.dhmo.convention

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("dhmo.quality")
        pluginManager.apply("dhmo.android.test")

        extensions.configure<LibraryExtension> {
            configureAndroidCommon(this)

            // :feature:today -> casa.peterson.dhmo.feature.today and resource prefix feature_today_
            val segments = path.removePrefix(":").split(":")
            namespace = "$BASE_NAMESPACE.${segments.joinToString(".")}"
            resourcePrefix = segments.joinToString("_") + "_"

            defaultConfig {
                consumerProguardFiles("consumer-rules.pro")
            }
            testOptions {
                targetSdk = TARGET_SDK
            }
            lint {
                checkDependencies = false
            }
        }
    }
}
