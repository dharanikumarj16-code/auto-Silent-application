// Top-level build file
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    // Add this line - ensure the name matches the one in libs.versions.toml
    alias(libs.plugins.kotlin.compose) apply false
}
