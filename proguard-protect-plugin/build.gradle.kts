plugins {
    `kotlin-dsl`
    `maven-publish`
}

repositories {
    mavenCentral()
    google()
}

group = "com.github.iambaljeet"
version = "1.0.0"

dependencies {
    implementation("com.android.tools.build:gradle:9.1.0")
    implementation("org.ow2.asm:asm:9.7.1")
    implementation("org.ow2.asm:asm-tree:9.7.1")
}

gradlePlugin {
    plugins {
        create("proguardProtect") {
            id = "lib.proguardprotect"
            displayName = "ProguardProtect"
            description = "Gradle plugin that detects R8/ProGuard runtime vulnerabilities by analyzing post-build APK/AAB artifacts"
            implementationClass = "lib.proguardprotect.ProguardProtectPlugin"
        }
    }
}

publishing {
    publications {
        withType<MavenPublication> {
            pom {
                name.set("ProguardProtect Gradle Plugin")
                description.set("Detects R8/ProGuard runtime vulnerabilities by analyzing post-build APK/AAB artifacts")
                url.set("https://github.com/iambaljeet/ProguardProtect")
                licenses {
                    license {
                        name.set("Apache License 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }
            }
        }
    }
}
