plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Source verification is a separate, non-delivery mode: no signing, packaging or installation.
val verifySourcesOnly = System.getenv("NOVALISTEN_VERIFY_SOURCES") == "true"
val deliveryKeystore = System.getenv("NOVALISTEN_KEYSTORE_PATH")
// Explicitly authorized parallel installation. CI prepares ONLY an unsigned Next APK;
// the owner-backed Next key never enters Gradle or GitHub Actions.
val parallelInstall = providers.gradleProperty("novalistenParallel").orNull == "true"
val prepareUnsignedParallel = System.getenv("NOVALISTEN_PREPARE_PARALLEL") == "true"
check(!prepareUnsignedParallel || parallelInstall) { "Préparation parallèle interdite pour l’identifiant historique." }
check(!prepareUnsignedParallel || deliveryKeystore.isNullOrBlank()) { "Aucune clé privée admise dans la compilation parallèle." }

android {
    namespace = "com.novalisten.app"
    compileSdk = 35
    defaultConfig {
        applicationId = if (parallelInstall) "com.novalisten.app.next" else "com.novalisten.app"
        manifestPlaceholders["novaApplicationLabel"] = if (parallelInstall) "NovaListen Next" else "@string/app_name"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "0.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }
    // CI must restore the existing, fingerprint-verified key before Gradle can sign anything.
    if (System.getenv("GITHUB_ACTIONS") == "true" && !verifySourcesOnly && !parallelInstall) {
        check(!deliveryKeystore.isNullOrBlank()) { "Clé de signature existante requise : aucune génération automatique en CI." }
    }
    deliveryKeystore?.takeIf { it.isNotBlank() && !parallelInstall }?.let {
        val existingKeystore = rootProject.file(it)
        check(existingKeystore.isFile) { "Keystore original absent : aucune clé de remplacement autorisée." }
        signingConfigs.getByName("debug").storeFile = existingKeystore
    }
    buildTypes {
        getByName("debug") { if (verifySourcesOnly || parallelInstall) signingConfig = null }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    testOptions { animationsDisabled = true }
    lint { abortOnError = true; checkReleaseBuilds = false }
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

// The original identity still refuses all replacement keys/unsigned APKs.
// Only the distinct, authorized Next identity may prepare unsigned intermediates.
gradle.taskGraph.whenReady {
    val packaging = Regex("^(assemble.*|install.*|validateSigning.*|package(Debug|Release)(AndroidTest)?|bundle(Debug|Release))$")
    val producesBinary = allTasks.any { packaging.matches(it.name) }
    if (verifySourcesOnly) {
        check(!producesBinary) { "Vérification des sources uniquement : génération d’APK/AAB et signature interdites." }
    } else if (producesBinary && parallelInstall) {
        check(prepareUnsignedParallel) { "NovaListen Next : préparation non signée explicite obligatoire ; signature locale séparée." }
        check(allTasks.filter { packaging.matches(it.name) }.all { it.name in setOf("assembleDebug", "packageDebug") }) {
            "Préparation Next limitée à l’APK debug non signé : aucune signature, installation ou autre variante."
        }
    } else if (producesBinary) {
        // Also fail closed outside GitHub Actions: never let AGP silently generate a debug key.
        check(!deliveryKeystore.isNullOrBlank()) { "Clé privée originale 0.2.3 obligatoire : aucun APK ni nouvelle clé de remplacement." }
        val checkKey = ProcessBuilder("python3", rootProject.file("scripts/signing_policy.py").absolutePath,
            "verify-keystore", rootProject.file(deliveryKeystore).absolutePath)
            .directory(rootProject.projectDir).redirectErrorStream(true).start()
        checkKey.outputStream.close()
        val report = checkKey.inputStream.bufferedReader().use { it.readText() }
        check(checkKey.waitFor() == 0) { "Clé privée originale 0.2.3 non vérifiée : livraison interdite.\n${report.take(1500)}" }
    }
}
