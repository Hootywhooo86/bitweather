import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

// CI passes -PbuildNumber=<run number>, matching the Android release (1.0.<build>)
val buildNumber = (project.findProperty("buildNumber") as String?)?.toIntOrNull() ?: 0

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation("org.json:json:20240303")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation(compose.desktop.uiTestJUnit4)
}

val appVersion = "1.0.$buildNumber"

tasks.withType<Jar> { manifest { attributes("Implementation-Version" to appVersion) } }

compose.desktop {
    application {
        mainClass = "com.hootywhooo86.bitweather.desktop.MainKt"
        jvmArgs += listOf("-Dbitweather.version=$appVersion", "-Dbitweather.build=$buildNumber")
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Bitweather"
            // MSI versions must be numeric x.y.z with x >= 1
            packageVersion = appVersion
            description = "Pixel weather with desktop widgets"
            vendor = "128bit"
            modules("java.naming", "jdk.crypto.ec", "java.net.http")
            windows {
                iconFile.set(project.file("icon.ico"))
                menu = true
                menuGroup = "Bitweather"
                shortcut = true
                perUserInstall = true
                dirChooser = false
                // fixed so new versions install over the old one
                upgradeUuid = "3a7c1f62-8a9e-4c63-9a8c-6b0d2f1e5b71"
            }
        }
    }
}
