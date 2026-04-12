# 🛡️ ProguardProtect

A Gradle plugin that detects R8/ProGuard runtime crash vulnerabilities by analyzing **post-build artifacts** — mapping.txt, DEX files, and the actual release APK or AAB.

Unlike source-only lint rules, ProguardProtect checks what R8 **actually did** to your code, eliminating false positives that arise from differences in R8 behavior across code sizes, dependency graphs, AGP versions, and R8 modes.

## Why Post-Build Analysis?

R8's behavior is non-deterministic across configurations:
- **Code size** affects tree-shaking aggressiveness
- **Dependency graph** changes what gets inlined or removed
- **AGP/R8 version** introduces new optimizations (devirtualization, enum unboxing, etc.)
- **Full R8 vs compat mode** changes optimization scope

Pre-build source analysis against keep rules can produce false positives. ProguardProtect analyzes the **final build output** to report only real issues.

## Detected Crash Types

| # | Crash Type | Runtime Exception | Description |
|---|-----------|-------------------|-------------|
| 1 | **Reflection Class Loading** | `ClassNotFoundException` | `Class.forName()` with dynamically constructed strings — R8 can't adapt these |
| 2 | **Reflection Method Access** | `NoSuchMethodError` | `getDeclaredMethod()` on R8-renamed methods |
| 3 | **Gson Serialization** | Silent data corruption / `NullPointerException` | Field renaming breaks JSON mapping without `@SerializedName` |
| 4 | **Dynamic Enum Access** | `ClassNotFoundException` / `IllegalArgumentException` | `Enum.valueOf()` or `enumConstants` on R8-renamed enum classes |
| 5 | **Callback Interface Stripping** | `ClassNotFoundException` / `AbstractMethodError` | Dynamically loaded interface implementations stripped by R8 |
| 6 | **Reflection Field Access** | `NoSuchFieldError` | `getDeclaredField()` on R8-renamed fields |
| 7 | **Devirtualization** | `IllegalAccessError` | R8 routes calls to base class's private method instead of interface default |
| 8 | **ServiceLoader Pattern** | `ClassNotFoundException` | Dynamic class loading via constructed strings (ServiceLoader-like patterns) |

## Installation

### 1. Add the plugin as a composite build

Copy the `proguard-protect-plugin/` directory into your project root, then add to your **root** `settings.gradle.kts`:

```kotlin
includeBuild("proguard-protect-plugin")
```

### 2. Apply the plugin in your app module

In `app/build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application")
    id("lib.proguardprotect")
}
```

### 3. Configure (optional)

```kotlin
proguardProtect {
    // Fail the build if issues are found (default: true)
    failOnError.set(true)
    
    // Enable/disable the plugin (default: true)
    enabled.set(true)
    
    // Limit scanning to specific packages (empty = scan all)
    targetPackages.set(listOf("com.myapp"))
    
    // Additional ProGuard rules files for reference
    extraRulesFiles.set(listOf("extra-rules.pro"))
}
```

## Usage

### Analyzing APK Builds

```bash
# Build + analyze a specific variant
./gradlew :app:assembleFreeRelease :app:proguardProtectAnalyzeFreeRelease

# Build + analyze all minified variants at once
./gradlew :app:assembleRelease :app:proguardProtectAnalyze
```

### Analyzing AAB Builds

```bash
# Build + analyze a specific variant's AAB
./gradlew :app:bundlePaidRelease :app:proguardProtectAnalyzePaidRelease

# Build + analyze all minified variants as AAB
./gradlew :app:bundleRelease :app:proguardProtectAnalyze
```

The plugin dynamically discovers the built APK or AAB in the output directory — it does not hardcode file names. This works regardless of custom `archivesName`, `applicationId`, flavor naming, or AGP output conventions.

### Multi-Variant Support

The plugin automatically registers analysis tasks for every minified variant (build type × product flavor). For example, with flavors `free`, `paid`, `demo`, `internal` and build type `release`:

| Task | Description |
|------|-------------|
| `proguardProtectAnalyzeFreeRelease` | Analyze free + release variant |
| `proguardProtectAnalyzePaidRelease` | Analyze paid + release variant |
| `proguardProtectAnalyzeDemoRelease` | Analyze demo + release variant |
| `proguardProtectAnalyzeInternalRelease` | Analyze internal + release variant |
| `proguardProtectAnalyze` | Runs all of the above |

Each variant gets its own HTML and text report (e.g., `proguard-protect-freeRelease.html`).

### How It Works (Pipeline)

1. **Build**: You invoke `assemble{Variant}` or `bundle{Variant}` to generate the APK/AAB with R8
2. **Parse mapping.txt**: Determines what R8 actually renamed, removed, or inlined
3. **Inspect DEX**: Lists classes in the final DEX (via `apkanalyzer` for APK, `dexdump` for AAB)
4. **Source pattern detection**: Runs analyzers with **empty keep rules** to find ALL potentially vulnerable patterns
5. **Cross-reference**: Each pattern is validated against mapping.txt and DEX — only patterns where R8 actually renamed/removed the target are reported
6. **Report**: Generates detailed HTML and text reports

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│  Build Release   │────▶│  Parse mapping   │────▶│  Inspect DEX    │
│  APK/AAB with R8 │     │  .txt            │     │  (APK or AAB)   │
└─────────────────┘     └──────────────────┘     └─────────────────┘
                                                          │
┌─────────────────┐     ┌──────────────────┐              │
│  Generate HTML   │◀────│  Cross-reference │◀─────────────┘
│  Report          │     │  with mapping    │
└─────────────────┘     └──────────────────┘
                                ▲
                        ┌──────────────────┐
                        │  Source pattern   │
                        │  detection       │
                        └──────────────────┘
```

## Reports

Reports are generated at:
- **HTML**: `app/build/reports/proguardProtect/proguard-protect-{variant}.html`
- **Text**: `app/build/reports/proguardProtect/proguard-protect-{variant}.txt`

The HTML report includes:
- 📊 Summary dashboard with issue counts by type
- 🔍 Detailed issue cards with severity and type
- 📝 Source code snippets with highlighted issue line
- 🗺️ R8 mapping details showing actual renames
- 🔧 Fix suggestions with copy-ready ProGuard keep rules

## CI/CD Integration

```yaml
# GitHub Actions example — analyze all variants
- name: Build Release APKs
  run: ./gradlew :app:assembleRelease

- name: Run ProguardProtect Analysis
  run: ./gradlew :app:proguardProtectAnalyze

- name: Upload Report
  if: always()
  uses: actions/upload-artifact@v4
  with:
    name: proguard-protect-report
    path: app/build/reports/proguardProtect/
```

For AAB-based CI pipelines, replace `assembleRelease` with `bundleRelease`.

Set `failOnError.set(true)` (default) to fail the CI build when issues are found.

## Example Fixes

### ClassNotFoundException (Crash Types 1, 4, 5, 8)
```proguard
-keep class com.myapp.models.UserProfile { *; }
```

### NoSuchMethodError / NoSuchFieldError (Crash Types 2, 6)
```proguard
-keepclassmembers class com.myapp.models.PaymentProcessor { *; }
```

### Gson Serialization (Crash Type 3)
```proguard
# Option A: Keep the whole class
-keep class com.myapp.models.ApiResponse { *; }

# Option B: Use @SerializedName on each field (preferred)
```

### Devirtualization IllegalAccessError (Crash Type 7)
```java
// Override the method in the child class
public class DataProcessor extends BaseProcessor implements Processable {
    @Override
    public String process(String input) {
        return Processable.super.process(input);
    }
}
```

## Requirements

- Android Gradle Plugin 9.x+
- Gradle 9.x+
- Kotlin 2.x+
- Android SDK with `cmdline-tools` installed (for `apkanalyzer`) and/or `build-tools` (for `dexdump` — used for AAB analysis)

## Project Structure

```
proguard-protect-plugin/
├── build.gradle.kts
├── settings.gradle.kts
└── src/main/kotlin/lib/proguardprotect/
    ├── ProguardProtectPlugin.kt      # Plugin entry point & variant registration
    ├── ProguardProtectExtension.kt   # DSL configuration
    ├── PostBuildAnalysisTask.kt      # Main analysis task (mapping + DEX + source)
    ├── models/
    │   └── ProguardIssue.kt          # Issue data model
    ├── analyzers/
    │   ├── BaseAnalyzer.kt           # Abstract analyzer base
    │   ├── ReflectionClassAnalyzer.kt
    │   ├── ReflectionMethodAnalyzer.kt
    │   ├── SerializationAnalyzer.kt
    │   ├── EnumAnalyzer.kt
    │   ├── CallbackAnalyzer.kt
    │   ├── DevirtualizationAnalyzer.kt
    │   └── (extensible — add your own)
    ├── utils/
    │   ├── MappingParser.kt          # mapping.txt parser
    │   ├── ProguardRulesParser.kt    # Keep rule parser
    │   └── SourceScanner.kt          # Source file scanner
    └── report/
        └── HtmlReportGenerator.kt    # HTML report generator
```

## License

MIT
