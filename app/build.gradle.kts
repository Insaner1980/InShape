import io.gitlab.arturbosch.detekt.Detekt
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

plugins {
    jacoco
    alias(libs.plugins.dependency.check)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.android.application)
    alias(libs.plugins.stability.analyzer)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}
android {
    namespace = "com.emmah.inshape"
    compileSdk {
        version = release(37) { minorApiLevel = 1 }
    }
    defaultConfig {
        applicationId = "com.emmah.inshape"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["inShapeLabel"] = "@string/app_name"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    buildTypes {
        debug {
            if (providers.gradleProperty("assistantTestInstall").orNull == "true") {
                applicationIdSuffix = ".assistanttest"
                manifestPlaceholders["inShapeLabel"] = "InShape Assistant Test"
            }
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = providers.gradleProperty("sonarDeviceCoverage").orNull == "true"
        }
    }
    testCoverage { jacocoVersion = "0.8.16" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
    sourceSets {
        getByName("testDebug").kotlin.srcDir("src/sharedTest/java")
        getByName("androidTest").kotlin.srcDir("src/sharedTest/java")
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
// AGP Compose preview tasks require a host test component for every Compose variant.
androidComponents {
    beforeVariants(selector().withBuildType("release")) { variant ->
        variant.hostTests[com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE]?.enable = true
    }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }

jacoco { toolVersion = "0.8.16" }
tasks.withType<Test>().configureEach {
    if (name == "testDebugUnitTest") systemProperty("robolectric.graphicsMode", "NATIVE")
    extensions.configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

ktlint {
    version.set(libs.versions.ktlint)
    android.set(true)
    reporters {
        reporter(ReporterType.PLAIN)
        reporter(ReporterType.CHECKSTYLE)
    }
    filter {
        exclude("**/generated/**")
        exclude("**/build/**")
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt.yml"))
    source.setFrom("src/main/java", "src/debug/java", "src/test/java", "src/androidTest/java", "src/sharedTest/java")
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "17"
    reports {
        xml.required.set(true)
        html.required.set(true)
        sarif.required.set(true)
    }
}
dependencyCheck {
    suppressionFiles = listOf(rootProject.file("config/dependency-check-suppressions.xml").absolutePath)
    formats = listOf("HTML", "JSON")
    outputDirectory = rootProject.layout.projectDirectory.dir("reports")
    scanConfigurations = listOf("debugRuntimeClasspath", "releaseRuntimeClasspath")
    failBuildOnCVSS = 7f
    failOnError = true
    autoUpdate = providers.environmentVariable("DEPENDENCY_CHECK_AUTO_UPDATE")
        .map { it.lowercase() in listOf("1", "true", "yes", "on") }.getOrElse(true)
    analyzers {
        // OSS Index requires credentials; the NVD analyzer remains enabled.
        ossIndex { enabled = false }
    }
    nvd {
        providers.environmentVariable("NVD_API_KEY").orNull
            ?.trim()?.takeIf { it.isNotEmpty() }?.let { apiKey = it }
    }
}

tasks.withType<org.owasp.dependencycheck.gradle.tasks.ConfiguredTask>().configureEach {
    data.directory.set(
        providers.environmentVariable("DEPENDENCY_CHECK_DATA_DIRECTORY")
            .orElse(rootProject.layout.projectDirectory.dir(".gradle/dependency-check-data").asFile.absolutePath)
    )
}

dependencies {
    lintChecks(libs.android.security.lints)
    detektPlugins(libs.compose.rules.detekt)
    ktlintRuleset(libs.compose.rules.ktlint)
    constraints {
        // Health Connect and Robolectric request older Guava releases.
        implementation(libs.guava.android)
        // Glance 1.2 requests DataStore 1.0, which bundles vulnerable Protobuf code.
        implementation(libs.datastore.preferences)
        testImplementation("com.google.guava:guava:33.8.0-jre")
        add("ksp", "com.google.guava:guava:33.8.0-jre")
        androidTestImplementation("com.google.code.gson:gson:2.15.0")
        add("ktlint", "ch.qos.logback:logback-classic:1.6.6")
        add("ktlint", "ch.qos.logback:logback-core:1.6.6")
        // Android Lint resolves a separate copy of the build-tool libraries.
        add("androidLintTool", "com.google.guava:guava:33.8.0-jre")
        add("androidLintTool", "org.apache.commons:commons-lang3:3.22.0")
        add("androidLintTool", "org.apache.httpcomponents:httpclient:4.5.14")
        add("androidLintTool", "org.bouncycastle:bcpkix-jdk18on:1.87")
        add("androidLintTool", "org.bouncycastle:bcprov-jdk18on:1.87")
    }
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.exifinterface)
    debugImplementation(libs.compose.tooling)
    implementation(libs.activity)
    implementation(libs.lifecycle)
    implementation(libs.viewmodel)
    implementation(libs.navigation)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.okhttp)
    ksp(libs.room.compiler)
    implementation(libs.serialization)
    implementation(libs.coroutines)
    implementation(libs.work)
    implementation(libs.glance)
    implementation(libs.health)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.work.testing)
    testImplementation(libs.test.core)
    add("testDebugImplementation", platform(libs.compose.bom))
    add("testDebugImplementation", libs.androidx.junit)
    add("testDebugImplementation", libs.compose.test.junit)
    add("testDebugImplementation", libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.test.runner)
    // Compose Test still requests Espresso 3.5, which calls an InputManager API removed in Android 17.
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.compose.test.junit)
    debugImplementation(libs.compose.test.manifest)
}
