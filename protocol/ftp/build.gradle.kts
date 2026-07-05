plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":core:common"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.commons.net)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}

kotlin {
    jvmToolchain(21)
}
