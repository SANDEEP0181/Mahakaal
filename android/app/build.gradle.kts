plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
    namespace="com.mahakaal.app"
    compileSdk=35
    buildFeatures { buildConfig = true }
    defaultConfig {
        applicationId="com.mahakaal.app"
        minSdk=26
        targetSdk=35
        versionCode=4
        versionName="0.4.0"
        buildConfigField("String","MAHAAKAAL_API_URL","\"https://YOUR_API_DOMAIN\"")
    }
    buildTypes {
        debug {
            buildConfigField("String","MAHAAKAAL_API_URL","\"http://10.0.2.2:3000\"")
            manifestPlaceholders["allowCleartext"]="true"
        }
        release {
            manifestPlaceholders["allowCleartext"]="false"
        }
    }
}
dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui:1.7.8")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}