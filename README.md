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

ProguardProtect detects **29 categories** of R8/ProGuard runtime crash vulnerabilities, all confirmed against the actual post-build artifacts.

| # | Crash Type | Runtime Exception | Description |
|---|-----------|-------------------|-------------|
| 1 | **Reflection Class Loading** | `ClassNotFoundException` | `Class.forName()` with dynamically constructed strings — R8 can't trace these |
| 2 | **Reflection Method Access** | `NoSuchMethodError` | `getDeclaredMethod()` on R8-renamed methods |
| 3 | **Gson Serialization** | Silent data corruption / `NullPointerException` | Field renaming breaks JSON mapping without `@SerializedName` |
| 4 | **Dynamic Enum Access** | `ClassNotFoundException` / `IllegalArgumentException` | `Enum.valueOf()` or `enumConstants` on R8-renamed enum classes |
| 5 | **Callback Interface Stripping** | `ClassNotFoundException` / `AbstractMethodError` | Dynamically loaded interface implementations stripped by R8 |
| 6 | **Reflection Field Access** | `NoSuchFieldError` | `getDeclaredField()` on R8-renamed fields |
| 7 | **Devirtualization** | `IllegalAccessError` | R8 routes calls to base class's private method instead of interface default |
| 8 | **ServiceLoader Pattern** | `ClassNotFoundException` | Dynamic class loading via constructed strings (ServiceLoader-like patterns) |
| 9 | **Gson TypeToken Generic** | `ClassCastException` / `NullPointerException` | Missing `-keepattributes Signature` breaks `TypeToken<List<T>>` generic type resolution |
| 10 | **Kotlin Companion Object** | `NoSuchFieldError` | Kotlin companion object accessed via reflection without keep rules |
| 11 | **Sealed Class Subtypes** | `ClassNotFoundException` | Sealed class subtype reflection (`sealedSubclasses`) fails after renaming |
| 12 | **Custom Annotation Stripping** | `NoSuchMethodException` | Custom runtime annotations stripped — `@Retention(RUNTIME)` annotations removed |
| 13 | **Parcelable Class Renamed** | `BadParcelableException` | Android stores original FQCN in Parcel; R8 renames class → deserialization fails |
| 14 | **JavascriptInterface Stripped** | JavaScript calls silently fail | `@JavascriptInterface` methods renamed → `undefined` in WebView JS bridge |
| 15 | **Native/JNI Method Renamed** | `UnsatisfiedLinkError` | JNI method signatures require exact class+method names — R8 breaks them |
| 16 | **WorkManager Worker Renamed** | `ClassNotFoundException` | WorkManager stores worker class names as strings in its database |
| 17 | **Custom View Renamed** | `InflateException` | Layout inflation fails when R8 renames a custom View referenced by XML |
| 18 | **Kotlin Object INSTANCE Removed** | `NoSuchFieldError` | Kotlin object `INSTANCE` field removed/renamed — reflection on `.INSTANCE` fails |
| 19 | **Resource Shrunk by Name** | `Resources$NotFoundException` | `getIdentifier()` string lookups fail when strict `shrinkResources` removes the resource |
| 20 | **Generic Signature Stripped** | `ClassCastException` | `genericSuperclass` / `ParameterizedType` fails when `-keepattributes Signature` is missing |
| 21 | **Component Class Not Found** | `ActivityNotFoundException` | `ComponentName`/`setClassName()` with original string — R8 renames the Activity/Service |
| 22 | **Data Class Members Stripped** | `NoSuchMethodException` | R8 full mode removes `copy()` / `componentN()` from Kotlin data classes accessed via reflection |
| 23 | **Dynamic Proxy Stripped** | `IllegalArgumentException` / `AbstractMethodError` | `Proxy.newProxyInstance()` interface renamed → InvocationHandler dispatch fails |
| 24 | **JSON Asset Resource Stripped** | `Resources$NotFoundException` | Resource names in JSON config files invisible to aapt2 — removed by strict resource shrinker |
| 25 | **Fragment Class Renamed** | `ClassNotFoundException` | FragmentManager stores FQCN in saved instance state; R8 rename breaks back-stack restoration |
| 26 | **Serializable Class Renamed** | `ClassNotFoundException` / `InvalidClassException` | R8 renames Serializable classes — cross-build deserialization fails without `serialVersionUID` |
| 27 | **ObjectAnimator Property Renamed** | `NoSuchMethodError` | `ObjectAnimator.ofFloat(view, "propertyName", ...)` references setter by string — R8 renames the method |
| 28 | **Inner Class Reflection Renamed** | `ClassNotFoundException` | `Class.forName("Outer$Inner")` — R8 renames nested classes; `$`-notation breaks |
| 29 | **Android XML onClick Method Renamed** | `NoSuchMethodException` | `android:onClick="methodName"` in layout XML — R8 renames the handler method, View dispatch fails |

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

### ClassNotFoundException (Types 1, 4, 5, 8, 16, 17, 21, 25, 26)
```proguard
-keep class com.myapp.models.UserProfile { *; }
-keep class * extends androidx.fragment.app.Fragment { <init>(); }
-keep class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
```

### NoSuchMethodError / NoSuchFieldError (Types 2, 6, 22)
```proguard
-keepclassmembers class com.myapp.models.PaymentProcessor { *; }
-keepclassmembers class com.myapp.models.DataClass { *; }
```

### Gson Serialization (Types 3, 9)
```proguard
# Option A: Keep the whole class
-keep class com.myapp.models.ApiResponse { *; }

# Option B: Use @SerializedName on each field (preferred)

# TypeToken generic support
-keepattributes Signature
-keep class * extends com.google.gson.reflect.TypeToken
```

### Parcelable / Serializable (Types 13, 26)
```proguard
-keep class com.myapp.models.ShippingAddress { *; }
# For Serializable: also add serialVersionUID to the class
```

### JavascriptInterface (Type 14)
```proguard
-keep class com.myapp.WebBridgeHandler { *; }
-keepclassmembers class com.myapp.WebBridgeHandler {
    @android.webkit.JavascriptInterface <methods>;
}
```

### JNI / Native (Type 15)
```proguard
-keepclasseswithmembernames class * { native <methods>; }
```

### Resource Shrinking (Types 19, 24)
```xml
<!-- res/raw/keep.xml -->
<resources xmlns:tools="http://schemas.android.com/tools"
    tools:shrinkMode="strict"
    tools:keep="@drawable/promo_banner,@drawable/feature_banner">
</resources>
```

### Devirtualization (Type 7)
```java
// Override the method in the child class — this is a code fix, not a keep rule
public class DataProcessor extends BaseProcessor implements Processable {
    @Override
    public String process(String input) {
        return Processable.super.process(input);
    }
}
```

### ObjectAnimator Property (Type 27)
```proguard
# Keep the setter/getter used by ObjectAnimator
-keepclassmembers class com.myapp.views.AnimatedMeterView {
    public void setMeterLevel(float);
    public float getMeterLevel();
}
```

### Inner Class Reflection (Type 28)
```proguard
-keep class com.myapp.models.OuterClass { *; }
-keep class com.myapp.models.OuterClass$InnerClass { *; }
```

### android:onClick (Type 29)
```proguard
-keep class com.myapp.MyActivity {
    public void myClickHandler(android.view.View);
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
    ├── ProguardProtectPlugin.kt          # Plugin entry point & variant registration
    ├── ProguardProtectExtension.kt       # DSL configuration
    ├── PostBuildAnalysisTask.kt          # Main analysis task (mapping + DEX + source)
    ├── models/
    │   └── ProguardIssue.kt              # Issue data model (29 IssueType values)
    ├── analyzers/                        # One analyzer per crash category
    │   ├── BaseAnalyzer.kt
    │   ├── ReflectionClassAnalyzer.kt    # Type 1: Class.forName()
    │   ├── ReflectionMethodAnalyzer.kt   # Type 2: getDeclaredMethod()
    │   ├── SerializationAnalyzer.kt      # Type 3: Gson field renaming
    │   ├── EnumAnalyzer.kt               # Type 4: Enum.valueOf() dynamic
    │   ├── CallbackAnalyzer.kt           # Type 5: Callback interface stripping
    │   ├── ReflectionFieldAnalyzer.kt    # Type 6: getDeclaredField()
    │   ├── DevirtualizationAnalyzer.kt   # Type 7: Private base-class devirt
    │   ├── ServiceLoaderAnalyzer.kt      # Type 8: ServiceLoader-like patterns
    │   ├── GsonTypeTokenAnalyzer.kt      # Type 9: TypeToken generic signature
    │   ├── KotlinCompanionAnalyzer.kt    # Type 10: Companion object reflection
    │   ├── SealedClassAnalyzer.kt        # Type 11: Sealed class subtypes
    │   ├── AnnotationRetentionAnalyzer.kt# Type 12: Custom annotation stripping
    │   ├── ParcelableAnalyzer.kt         # Type 13: Parcelable class renamed
    │   ├── JavascriptInterfaceAnalyzer.kt# Type 14: @JavascriptInterface stripped
    │   ├── NativeMethodAnalyzer.kt       # Type 15: JNI method signatures
    │   ├── WorkManagerAnalyzer.kt        # Type 16: WorkManager Worker renamed
    │   ├── CustomViewAnalyzer.kt         # Type 17: Custom View InflateException
    │   ├── KotlinObjectAnalyzer.kt       # Type 18: Kotlin object INSTANCE
    │   ├── ResourceNameAnalyzer.kt       # Type 19: getIdentifier() shrinkResources
    │   ├── GenericSignatureAnalyzer.kt   # Type 20: genericSuperclass signature
    │   ├── ComponentNameAnalyzer.kt      # Type 21: ComponentName string ref
    │   ├── DataClassMemberAnalyzer.kt    # Type 22: copy()/componentN() removed
    │   ├── DynamicProxyAnalyzer.kt       # Type 23: Proxy.newProxyInstance()
    │   ├── JsonAssetResourceAnalyzer.kt  # Type 24: JSON asset resource names
    │   ├── FragmentClassAnalyzer.kt      # Type 25: Fragment class renamed
    │   ├── SerializableAnalyzer.kt       # Type 26: Serializable class renamed
    │   ├── ObjectAnimatorAnalyzer.kt     # Type 27: ObjectAnimator property string renamed
    │   ├── InnerClassReflectionAnalyzer.kt # Type 28: Inner class forName($) renamed
    │   └── AndroidOnClickAnalyzer.kt     # Type 29: android:onClick XML method renamed
    ├── utils/
    │   ├── MappingParser.kt              # mapping.txt parser
    │   ├── ProguardRulesParser.kt        # Keep rule parser
    │   └── SourceScanner.kt             # Source file scanner
    └── report/
        └── HtmlReportGenerator.kt        # HTML report generator
```

## License

MIT
