plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Source verification is a separate, non-delivery mode: no signing, packaging or installation.
val verifySourcesOnly = System.getenv("NOVALISTEN_VERIFY_SOURCES") == "true"
// Nova Rush method: ONE delivery key committed with its password, so every build is signed with
// the same certificate and each version installs over the previous one. The repository must stay
// PRIVATE. The pinned fingerprint is enforced by scripts/signing_policy.py before Gradle signs.
val deliveryKeystore = rootProject.file("config/signing/NovaListen-release.p12")
val deliveryPassword = rootProject.file("config/signing/signing-password.txt")
val prepareUnsignedParallel = System.getenv("NOVALISTEN_PREPARE_PARALLEL") == "true"

android {
    namespace = "com.novalisten.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.novalisten.app.next"
        manifestPlaceholders["novaApplicationLabel"] = "NovaListen Next"
        minSdk = 26
        targetSdk = 35
        versionCode = 12
        versionName = "0.5.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }
    // Both build types use the same committed key. Without it, no signing config is attached and
    // the task graph below refuses to produce any installable binary: never a silent debug key.
    signingConfigs {
        if (!verifySourcesOnly) {
            create("delivery") {
                if (deliveryKeystore.isFile && deliveryPassword.isFile) {
                    storeFile = deliveryKeystore
                    storeType = "PKCS12"
                    storePassword = deliveryPassword.readText().trim()
                    keyAlias = "novalisten-release"
                    keyPassword = deliveryPassword.readText().trim()
                }
            }
        }
    }
    buildTypes {
        // The committed key is the app's permanent identity: the release build is signed too,
        // so no future delivery can silently use a different certificate.
        getByName("debug") {
            signingConfig = if (verifySourcesOnly || prepareUnsignedParallel) null else signingConfigs.findByName("delivery")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (verifySourcesOnly || prepareUnsignedParallel) null else signingConfigs.findByName("delivery")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    testOptions { animationsDisabled = true; unitTests.isIncludeAndroidResources = true }
    lint { abortOnError = true; checkReleaseBuilds = false }
}

// Official read-only Maven Central mirror: both repo1 and repo.maven.apache.org
// have returned HTTP 429 for Robolectric framework downloads on fresh CI runners.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2/")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.03.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.media3:media3-exoplayer:1.6.1")
    implementation("androidx.media3:media3-session:1.6.1")
    implementation("androidx.media3:media3-ui:1.6.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

gradle.taskGraph.whenReady {
    val packaging = Regex("^(assemble.*|install.*|validateSigning.*|package(Debug|Release)(AndroidTest)?|bundle(Debug|Release))$")
    val producesBinary = allTasks.any { packaging.matches(it.name) }
    if (verifySourcesOnly) {
        check(!producesBinary) { "Vérification des sources uniquement : génération d’APK/AAB et signature interdites." }
    } else if (producesBinary) {
        // Fail closed, in CI as well as locally: a missing or replaced key never falls back to a
        // freshly generated debug key. Only the explicit unsigned-intermediate mode skips signing.
        check(deliveryKeystore.isFile) { "Keystore de livraison absent (config/signing/NovaListen-release.p12) : aucun APK." }
        check(deliveryPassword.isFile) { "Mot de passe de signature absent : aucun APK." }
        if (!prepareUnsignedParallel) {
            val checkKey = ProcessBuilder("python3", rootProject.file("scripts/signing_policy.py").absolutePath, "verify-keystore")
                .directory(rootProject.projectDir).redirectErrorStream(true).start()
            checkKey.outputStream.close()
            val report = checkKey.inputStream.bufferedReader().use { it.readText() }
            check(checkKey.waitFor() == 0) { "Clé de signature non vérifiée : livraison interdite.\n${report.take(1500)}" }
        }
    }
}
