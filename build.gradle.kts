buildscript {
    dependencies {
        constraints {
            // Patch vulnerable transitive build-tool libraries found by dependency-check.
            classpath("com.fasterxml.jackson.core:jackson-core:2.23.0")
            classpath("com.fasterxml.jackson.core:jackson-databind:2.23.0")
            classpath("org.jsoup:jsoup:1.24.1")
            classpath("org.json:json:20260814")
            classpath("com.google.guava:guava:33.8.0-jre")
            classpath("org.apache.commons:commons-lang3:3.22.0")
            classpath("org.bitbucket.b_c:jose4j:0.9.7")
            classpath("org.bouncycastle:bcpkix-jdk18on:1.87")
            classpath("org.bouncycastle:bcprov-jdk18on:1.87")
            classpath("org.jdom:jdom2:2.0.6.1")
            classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
        }
    }
}

plugins {
    alias(libs.plugins.sonarqube)
    alias(libs.plugins.stability.analyzer) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.dependency.check) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}

sonar {
    properties {
        val settings = java.util.Properties().apply {
            rootProject.file("sonar-project.properties").inputStream().use(::load)
        }
        settings.forEach { key, value -> property(key.toString(), value.toString()) }
    }
}

val sonarDeviceCoverage = providers.gradleProperty("sonarDeviceCoverage").orNull == "true"

project(":app") {
    val coverageReports = mutableListOf("reports/coverage/test/debug/report.xml")
    if (sonarDeviceCoverage) coverageReports += "reports/coverage/androidTest/debug/connected/report.xml"
    sonar {
        properties {
            property(
                "sonar.coverage.jacoco.xmlReportPaths",
                coverageReports.joinToString(",") { layout.buildDirectory.file(it).get().asFile.absolutePath }
            )
        }
    }
}

tasks.named("sonar") {
    dependsOn(":app:assembleDebug", ":app:createDebugUnitTestCoverageReport", ":app:lintDebug")
    if (sonarDeviceCoverage) dependsOn(":app:createDebugAndroidTestCoverageReport")
}
