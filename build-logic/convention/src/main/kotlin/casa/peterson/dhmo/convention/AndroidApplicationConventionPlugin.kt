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
            // Release signing comes from the environment (filled by scripts/release/build-release.sh from 1Password),
            // so no secret ever lands in the repository. Without it the release build is simply unsigned, which is
            // what CI lint and R8 checks need.
            val keystoreFile = providers.environmentVariable("DHMO_KEYSTORE_FILE").orNull
            val signingRelease =
                keystoreFile?.let { path ->
                    signingConfigs.create("release") {
                        storeFile = file(path)
                        storePassword = providers.environmentVariable("DHMO_KEYSTORE_PASSWORD").get()
                        keyAlias = providers.environmentVariable("DHMO_KEY_ALIAS").get()
                        keyPassword = providers.environmentVariable("DHMO_KEY_PASSWORD").get()
                    }
                }
            buildTypes {
                debug {
                    applicationIdSuffix = ".debug"
                }
                release {
                    signingConfig = signingRelease
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
