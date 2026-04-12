plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation("com.android.tools.build:gradle:9.1.0")
    implementation("org.ow2.asm:asm:9.7.1")
    implementation("org.ow2.asm:asm-tree:9.7.1")
}

gradlePlugin {
    plugins {
        create("proguardProtect") {
            id = "lib.proguardprotect"
            implementationClass = "lib.proguardprotect.ProguardProtectPlugin"
        }
    }
}
