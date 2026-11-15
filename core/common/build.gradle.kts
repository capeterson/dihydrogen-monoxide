plugins { alias(libs.plugins.dhmo.jvm.library) }

dependencies {
    api(libs.javax.inject)
    api(libs.kotlinx.coroutines.core)
}
