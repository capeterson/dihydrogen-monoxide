package casa.peterson.dhmo.convention

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("dhmo.quality")
        pluginManager.apply("dhmo.android.test")

        extensions.configure<ApplicationExtension> {
            configureAndroidCommon(this)

            defaultConfig {
                targetSdk = TARGET_SDK
            }
            buildFeatures {
                buildConfig = true
            }
            buildTypes {
                debug {
                    applicationIdSuffix = ".debug"
                }
                release {
                    isMinifyEnabled = true
                    isShrinkResources = true
                    proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
                }
            }
            packaging {
                resources {
                    excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
                }
            }
            lint {
                checkDependencies = true
            }
        }
    }
}
