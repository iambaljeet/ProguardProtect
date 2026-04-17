# Publishing ProguardProtect Plugin

This guide explains how to publish the ProguardProtect Gradle plugin to [JitPack](https://jitpack.io).

## About JitPack

JitPack is a free package repository for JVM and Android projects hosted on GitHub.
It builds directly from source using Git tags, so no separate publish step or API keys are required.

---

## Prerequisites

- Write access to the [iambaljeet/ProguardProtect](https://github.com/iambaljeet/ProguardProtect) GitHub repository
- Git installed locally with the repo cloned

---

## Releasing a New Version

### 1. Update the Plugin Version

Edit `proguard-protect-plugin/build.gradle.kts` and bump the version:

```kotlin
group = "com.github.iambaljeet"
version = "1.1.0"   // ← bump this
```

### 2. Update the README

Update the version badge and all usage examples in `README.md` to reference the new version.

### 3. Commit the Changes

```bash
git add proguard-protect-plugin/build.gradle.kts README.md
git commit -m "chore: release v1.1.0"
git push origin main
```

### 4. Create and Push a Git Tag

JitPack builds are triggered by tags. The tag name becomes the version used in dependency declarations.

```bash
# Replace 1.1.0 with your actual version
git tag v1.1.0
git push origin v1.1.0
```

### 5. Trigger the JitPack Build

Open the following URL in your browser to trigger and monitor the build:

```
https://jitpack.io/#iambaljeet/ProguardProtect/v1.1.0
```

Click **"Get it"** if available, or wait a few minutes for the build to complete automatically.
A green ✅ badge means the build succeeded and the artifact is ready to use.

> **Note:** The first build for a new tag may take 2–5 minutes. Subsequent builds are cached.

---

## How the Build Works

JitPack uses `jitpack.yml` at the repo root to know how to build the plugin:

```yaml
jdk:
  - openjdk17
install:
  - ./gradlew -p proguard-protect-plugin publishToMavenLocal
```

This tells JitPack to:
1. Use JDK 17
2. Run Gradle from the `proguard-protect-plugin/` subdirectory using the root `gradlew`
3. Publish the plugin JAR + plugin marker to the local Maven cache
4. JitPack then serves those artifacts at `https://jitpack.io`

---

## Consumer Setup (How Users Install the Plugin)

Users add the following to their Android project:

**`settings.gradle.kts`**:
```kotlin
pluginManagement {
    repositories {
        maven("https://jitpack.io")
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "lib.proguardprotect") {
                useModule("com.github.iambaljeet:proguard-protect-plugin:${requested.version}")
            }
        }
    }
}
```

**`app/build.gradle.kts`** (or root `build.gradle.kts`):
```kotlin
plugins {
    id("lib.proguardprotect") version "v1.1.0"
}
```

> **Why `resolutionStrategy`?**  
> JitPack serves artifacts by Maven coordinates. Gradle's plugin resolution needs a hint to map
> the plugin ID (`lib.proguardprotect`) to the JitPack artifact (`com.github.iambaljeet:proguard-protect-plugin`).
> The `resolutionStrategy` block provides this mapping.

---

## Published Artifact Coordinates

| Coordinate   | Value                                         |
|-------------|-----------------------------------------------|
| Group ID    | `com.github.iambaljeet`                       |
| Artifact ID | `proguard-protect-plugin`                     |
| Version     | Git tag (e.g., `v1.0.0`)                      |
| JitPack URL | `https://jitpack.io/#iambaljeet/ProguardProtect` |

Direct JAR URL pattern:
```
https://jitpack.io/com/github/iambaljeet/proguard-protect-plugin/{version}/proguard-protect-plugin-{version}.jar
```

---

## Testing Locally Before Publishing

To verify the plugin builds and publishes correctly before tagging:

```bash
# From the repo root
./gradlew -p proguard-protect-plugin publishToMavenLocal

# Verify the artifact was installed
ls ~/.m2/repository/com/github/iambaljeet/proguard-protect-plugin/
```

Then in a consumer project, add `mavenLocal()` to repositories to test against the local build:
```kotlin
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
```

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| JitPack build fails | Check the build log at `https://jitpack.io/#iambaljeet/ProguardProtect/{tag}` |
| `Could not find com.github.iambaljeet:proguard-protect-plugin` | Ensure the JitPack build succeeded (green badge) and you've added the `maven("https://jitpack.io")` repository |
| Old version cached | Clear Gradle cache: `./gradlew --refresh-dependencies` |
| Plugin ID not resolved | Ensure `resolutionStrategy` block is in `pluginManagement` in `settings.gradle.kts` |
