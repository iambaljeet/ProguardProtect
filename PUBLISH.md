# Publishing ProguardProtect to JitPack

## Overview

ProguardProtect is published via [JitPack](https://jitpack.io), which builds directly from
the GitHub repository. No manual publishing step is required — JitPack builds on demand
when a consumer first requests a version.

---

## For Consumers: Using the Plugin

### 1. Add JitPack to your settings.gradle.kts

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "lib.proguardprotect") {
                useModule("com.github.iambaljeet:ProguardProtect:TAG")
            }
        }
    }
}
```

Replace `TAG` with the desired release tag (e.g., `1.0.0`) or `main-SNAPSHOT` for the latest.

### 2. Apply the plugin in your app/build.gradle.kts

```kotlin
plugins {
    id("lib.proguardprotect") version "TAG"
}
```

### 3. Configure (optional)

```kotlin
proguardProtect {
    failOnError.set(true)   // Fail build if confirmed crash vulnerabilities found
    targetPackages.set(listOf("com.example.myapp"))  // Limit scan scope
}
```

### 4. Run analysis after building release APK

```bash
./gradlew :app:assembleRelease
./gradlew :app:proguardProtectAnalyzeRelease
```

---

## For Maintainers: Publishing a New Release

JitPack builds automatically from GitHub tags. To publish a new version:

### 1. Update the version in proguard-protect-plugin/build.gradle.kts

```kotlin
version = "1.1.0"
```

### 2. Commit and tag

```bash
git add -A
git commit -m "Release version 1.1.0"
git tag 1.1.0
git push origin main --tags
```

### 3. Trigger JitPack build (first use)

Visit `https://jitpack.io/#iambaljeet/ProguardProtect/1.1.0` to trigger and monitor the build.

### 4. Verify

```
https://jitpack.io/com/github/iambaljeet/ProguardProtect/1.1.0/
```

Should show the published artifact files.

---

## Available Versions

| Version | Tag    | Notes                                        |
|---------|--------|----------------------------------------------|
| 1.0.0   | 1.0.0  | Initial release — 18 crash type detectors    |

---

## Detected Crash Types

| # | Type | Crash |
|---|------|-------|
| 1 | REFLECTION_CLASS_FOR_NAME | ClassNotFoundException |
| 2 | REFLECTION_METHOD_ACCESS | NoSuchMethodError |
| 3 | SERIALIZATION_FIELD_RENAME | Silent data corruption / NPE |
| 4 | ENUM_VALUE_OF | IllegalArgumentException |
| 5 | CALLBACK_INTERFACE_STRIPPED | ClassNotFoundException |
| 6 | DEVIRTUALIZATION_ILLEGAL_ACCESS | IllegalAccessError |
| 7 | NO_CLASS_DEF_FOUND | NoClassDefFoundError |
| 8 | GSON_TYPE_TOKEN_STRIPPED | Type info lost |
| 9 | COMPANION_OBJECT_STRIPPED | ClassNotFoundException |
| 10 | SEALED_SUBCLASS_STRIPPED | ClassNotFoundException |
| 11 | ANNOTATION_STRIPPED | getAnnotation() returns null |
| 12 | PARCELABLE_CLASS_RENAMED | BadParcelableException |
| 13 | JAVASCRIPT_INTERFACE_STRIPPED | JS bridge silent failure |
| 14 | NATIVE_METHOD_RENAMED | UnsatisfiedLinkError |
| 15 | WORKMANAGER_WORKER_STRIPPED | ClassNotFoundException |
| 16 | CUSTOM_VIEW_STRIPPED | InflateException |
| 17 | KOTLIN_OBJECT_INSTANCE_REMOVED | NoSuchFieldError |
