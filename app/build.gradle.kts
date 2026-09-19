plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "br.com.controlequeijos"
    compileSdk = 35
    defaultConfig {
        applicationId = "br.com.controlequeijos"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}
dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui:1.8.1")
    implementation("androidx.compose.material3:material3:1.3.2")
    implementation("androidx.compose.ui:ui-tooling-preview:1.8.1")
    debugImplementation("androidx.compose.ui:ui-tooling:1.8.1")
}
