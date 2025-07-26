plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    kotlin("kapt")
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
    jacoco
    alias(libs.plugins.sonarqube)
    id("dagger.hilt.android.plugin")
}

kotlin {
    jvmToolchain(21)
}

android {
    namespace = "com.pocketagent"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pocketagent.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "com.pocketagent.testing.HiltTestRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"

            buildConfigField("String", "BASE_URL", "\"ws://localhost:8080\"")
            buildConfigField("boolean", "DEBUG_MODE", "true")
        }

        release {
            isMinifyEnabled = true
            isDebuggable = false
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )

            buildConfigField("String", "BASE_URL", "\"wss://your-server.com\"")
            buildConfigField("boolean", "DEBUG_MODE", "false")
        }
    }

    // Signing configuration commented out for development builds
    // Uncomment and configure when ready for production release

    /*
    signingConfigs {
        create("release") {
            // These will be set via environment variables or gradle.properties
            storeFile = project.findProperty("POCKET_AGENT_STORE_FILE")?.let { file(it) }
            storePassword = project.findProperty("POCKET_AGENT_STORE_PASSWORD") as? String
            keyAlias = project.findProperty("POCKET_AGENT_KEY_ALIAS") as? String
            keyPassword = project.findProperty("POCKET_AGENT_KEY_PASSWORD") as? String
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
        }
    }
     */

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Compose compiler is now configured by the Kotlin plugin

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }

    // Enable R8 full mode for better optimization in release builds only
}

// Kapt configuration for Hilt
kapt {
    correctErrorTypes = true
}

dependencies {
    // Core Android libraries
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.work.runtime.ktx)

    // Jetpack Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.animation)
    implementation(libs.compose.foundation)
    implementation(libs.compose.runtime.livedata)

    // Navigation
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    // Dependency Injection
    implementation(libs.hilt.android)
    implementation(libs.hilt.work)
    kapt(libs.hilt.compiler)
    kapt(libs.hilt.android.compiler)

    // Networking
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // Security
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.biometric)
    implementation(libs.bouncy.castle.bcprov)
    implementation(libs.bouncy.castle.bcpkix)
    implementation(libs.errorprone.annotations)

    // Storage
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    // Background processing
    implementation(libs.androidx.work.runtime.ktx)

    // Permissions
    implementation(libs.accompanist.permissions)

    // System UI
    implementation(libs.accompanist.systemuicontroller)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.hilt.android.testing)
    testImplementation(libs.okhttp.mockwebserver)
    kaptTest(libs.hilt.android.compiler)

    // Android Testing
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.navigation.testing)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.androidx.work.testing)
    kaptAndroidTest(libs.hilt.android.compiler)

    // Debug tools
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Code Quality Tools
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.8")
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-rules-libraries:1.23.8")
}

// ================================================================================================
// Android Lint Configuration
// ================================================================================================

android {
    lint {
        abortOnError = false
        checkReleaseBuilds = true
        ignoreWarnings = false
        ignoreTestSources = true
        checkGeneratedSources = false
        checkDependencies = false

        // Enable specific checks
        enable +=
            setOf(
                "UnusedResources",
                "UnusedIds",
                "IconDensities",
                "IconDuplicates",
                "ContentDescription",
                "SmallSp",
                "TextFields",
                "ViewHolder",
                "HandlerLeak",
                "CommitPrefEdits",
                "Recycle",
                "SecureRandom",
                "HardcodedText",
                "RelativeOverlap",
                "RtlCompat",
                "RtlEnabled",
                "SetTextI18n",
                "ViewConstructor",
                "UseCompoundDrawables",
                "UseSparseArrays",
                "UseValueOf",
                "Overdraw",
                "UnusedNamespace",
                "UnusedQuantity",
                "MissingTranslation",
                "ExtraTranslation",
                "TypographyFractions",
                "TypographyDashes",
                "TypographyQuotes",
                "TypographyEllipsis",
                "ButtonStyle",
                "ObsoleteLayoutParam",
                "InefficientWeight",
                "DisableBaselineAlignment",
                "ScrollViewSize",
                "NegativeMargin",
                "DuplicateIncludedIds",
                "StringFormatInvalid",
                "StringFormatMatches",
                "PluralsCandidate",
                "SuspiciousImport",
                "ShortAlarm",
                "UnprotectedSMSBroadcastReceiver",
                "UnsafeBroadcastReceiver",
                "UnsafeProtectedBroadcastReceiver",
                "UnregisteredReceiver",
                "MissingFirebaseInstanceTokenRefresh",
                "InvalidPackage",
                "PackageManagerGetSignatures",
                "MissingBackupPin",
                "AllowBackup",
                "FullBackupContent",
                "DataExtractionRules",
                "UnsafeImplicitIntentLaunch",
                "UnsafeIntentLaunch",
                "LaunchActivityFromNotification",
                "UnsafeNativeCodeLocation",
                "UnsafeNativeCodeLocation",
            )

        // Disable checks that might be too noisy
        disable +=
            setOf(
                "GoogleAppIndexingWarning",
                "HardcodedDebugMode",
                "SignatureOrSystemPermissions",
                "MissingApplicationIcon",
                "GradleCompatible",
                "NewerVersionAvailable",
                "GradleDependency",
                "GradlePluginVersion",
                "DevModeObsolete",
            )

        // Set severity levels
        error +=
            setOf(
                "StopShip",
                "SecureRandom",
                "HardcodedText",
                "CommitPrefEdits",
                "Recycle",
                "HandlerLeak",
            )

        warning +=
            setOf(
                "UnusedResources",
                "UnusedIds",
                "IconDensities",
                "ContentDescription",
                "SmallSp",
            )

        // Configure output
        xmlReport = true
        htmlReport = true
        textReport = true
        absolutePaths = false

        // Set baseline and configuration files
        baseline = file("${project.rootDir}/config/lint/lint-baseline.xml")
        lintConfig = file("${project.rootDir}/config/lint/lint.xml")
    }
    buildToolsVersion = "35.0.0"
}

// ================================================================================================
// Code Quality Configuration
// ================================================================================================

// Ktlint Configuration
ktlint {
    version.set("1.6.0")
    android.set(true)
    ignoreFailures.set(false)
    reporters {
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.SARIF)
    }
    filter {
        exclude("**/generated/**")
        exclude("**/build/**")
        include("**/kotlin/**")
        include("**/java/**")
    }
}

// Detekt Configuration
detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(file("${project.rootDir}/config/detekt/detekt.yml"))
    baseline = file("${project.rootDir}/config/detekt/baseline.xml")

    source.setFrom("src/main/java", "src/main/kotlin")

    // Reports configuration moved to task level
}

// Configure detekt reports at task level
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    reports {
        html.required.set(true)
        xml.required.set(true)
        txt.required.set(true)
        sarif.required.set(true)
        md.required.set(true)
    }
}

// Spotless Configuration
spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**/*.kt")

        // Use ktlint for comprehensive formatting (aligns with Detekt expectations)
        ktlint("1.6.0")

        // Standard formatting rules
        trimTrailingWhitespace()
        endWithNewline()
    }

    kotlinGradle {
        target("*.gradle.kts")
        ktlint("1.6.0")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// Jacoco Configuration
jacoco {
    toolVersion = "0.8.11"
}

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    val fileFilter =
        listOf(
            "**/R.class",
            "**/R\$*.class",
            "**/BuildConfig.*",
            "**/Manifest*.*",
            "**/*Test*.*",
            "android/**/*.*",
            "**/di/**/*.*",
            "**/*Module*.*",
            "**/*Activity*.*",
            "**/*Fragment*.*",
            "**/*Application*.*",
            "**/*Hilt*.*",
            "**/*_Factory*.*",
            "**/*_MembersInjector*.*",
        )

    val debugTree = fileTree("${project.layout.buildDirectory.get()}/tmp/kotlin-classes/debug")
    debugTree.exclude(fileFilter)
    classDirectories.setFrom(debugTree)

    val sourceDirs =
        arrayOf(
            "src/main/java",
            "src/main/kotlin",
        )
    sourceDirectories.setFrom(project.files(sourceDirs))

    executionData.setFrom(fileTree(project.layout.buildDirectory.get()).include("**/*.exec"))
}

// SonarQube Configuration
sonarqube {
    properties {
        property("sonar.projectName", "Pocket Agent Mobile App")
        property("sonar.projectKey", "pocket-agent-mobile-app")
        property("sonar.host.url", "http://localhost:9000")
        property("sonar.language", "kotlin")
        property("sonar.sources", "src/main/java,src/main/kotlin")
        property("sonar.tests", "src/test/java,src/test/kotlin,src/androidTest/java,src/androidTest/kotlin")
        property("sonar.binaries", "${project.layout.buildDirectory.get()}/tmp/kotlin-classes/debug")
        property("sonar.java.binaries", "${project.layout.buildDirectory.get()}/tmp/kotlin-classes/debug")
        property(
            "sonar.coverage.jacoco.xmlReportPaths",
            "${project.layout.buildDirectory.get()}/reports/jacoco/jacocoTestReport/jacocoTestReport.xml",
        )
        property("sonar.kotlin.detekt.reportPaths", "${project.layout.buildDirectory.get()}/reports/detekt/detekt.xml")
        property("sonar.exclusions", "**/R.class,**/R\$*.class,**/BuildConfig.*,**/Manifest*.*,**/*Test*.*,android/**/*.*")
    }
}

// ================================================================================================
// Quality Gate Tasks
// ================================================================================================

tasks.register("codeQualityCheck") {
    group = "verification"
    description = "Run all code quality checks"
    dependsOn("ktlintCheck", "detekt", "spotlessCheck", "lint")
}

tasks.register("codeQualityFix") {
    group = "formatting"
    description = "Fix all code quality issues"
    dependsOn("ktlintFormat", "spotlessApply")
}

tasks.register("fullQualityCheck") {
    group = "verification"
    description = "Run all quality checks including tests and coverage"
    dependsOn("codeQualityCheck", "testDebugUnitTest", "jacocoTestReport")
}

// Make sure quality checks run before build
// Removed preBuild dependency on codeQualityCheck to avoid circular dependency
// Quality checks can be run separately with ./gradlew codeQualityCheck

// Ensure Jacoco report is generated after tests
tasks.matching { it.name.contains("test") && it.name.contains("UnitTest") }.configureEach {
    finalizedBy("jacocoTestReport")
}
