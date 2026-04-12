package lib.proguardprotect

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import java.io.File

/**
 * ProguardProtect Gradle Plugin — detects R8/ProGuard runtime crash vulnerabilities
 * by analyzing post-build artifacts (mapping.txt, DEX files) from release APK/AAB builds.
 *
 * Unlike source-only analysis, this plugin checks what R8 **actually did** to your code,
 * eliminating false positives that arise from differences in R8 behavior across code sizes,
 * dependency graphs, AGP versions, and R8 modes (full vs compat).
 *
 * ## Variant support
 * The plugin registers analysis tasks for every minified variant (build type × product flavor).
 * For example, a project with flavors `free`/`paid` and build type `release` gets:
 * - `proguardProtectAnalyzeFreeRelease`
 * - `proguardProtectAnalyzePaidRelease`
 * - `proguardProtectAnalyze` (runs all)
 *
 * ## APK and AAB support
 * The plugin dynamically discovers the built APK or AAB in the output directory — it does not
 * hardcode file names. This works regardless of custom `archivesName`, `applicationId`, or
 * AGP output naming conventions.
 *
 * @see PostBuildAnalysisTask The main analysis task
 * @see ProguardProtectExtension DSL configuration options
 */
class ProguardProtectPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create(
            "proguardProtect",
            ProguardProtectExtension::class.java
        )

        extension.failOnError.convention(true)
        extension.enabled.convention(true)
        extension.targetPackages.convention(emptyList())
        extension.extraRulesFiles.convention(emptyList())

        project.afterEvaluate(object : Action<Project> {
            override fun execute(proj: Project) {
                if (!extension.enabled.get()) {
                    proj.logger.lifecycle("ProguardProtect: Plugin is disabled.")
                    return
                }

                val androidExt = proj.extensions.findByType(ApplicationExtension::class.java)
                if (androidExt == null) {
                    proj.logger.warn("ProguardProtect: No Android app extension found. Skipping.")
                    return
                }

                val registeredTaskNames = mutableListOf<String>()

                // Collect source directories
                val mainJavaDir = proj.file("src/main/java")
                val mainKotlinDir = proj.file("src/main/kotlin")

                // Locate apkanalyzer from Android SDK (shared across tasks)
                val androidHome = System.getenv("ANDROID_HOME")
                    ?: System.getenv("ANDROID_SDK_ROOT")
                    ?: "${System.getProperty("user.home")}/Library/Android/sdk"
                val analyzerPath = "$androidHome/cmdline-tools/latest/bin/apkanalyzer"
                val hasApkAnalyzer = File(analyzerPath).exists()

                // Discover all minified variants
                // Variants = buildTypes × productFlavors (or just buildTypes if no flavors)
                val hasFlavors = androidExt.productFlavors.isNotEmpty()
                val minifiedBuildTypes = androidExt.buildTypes.filter { it.isMinifyEnabled }.map { it.name }

                if (minifiedBuildTypes.isEmpty()) {
                    proj.logger.info("ProguardProtect: No minified build types found. Skipping.")
                    return
                }

                // Build variant names: flavor + buildType or just buildType
                val variantConfigs = mutableListOf<VariantConfig>()

                if (hasFlavors) {
                    androidExt.productFlavors.forEach { flavor ->
                        minifiedBuildTypes.forEach { btName ->
                            val variantName = "${flavor.name}${btName.replaceFirstChar { it.uppercase() }}"
                            variantConfigs.add(VariantConfig(
                                variantName = variantName,
                                buildTypeName = btName,
                                flavorName = flavor.name
                            ))
                        }
                    }
                } else {
                    minifiedBuildTypes.forEach { btName ->
                        variantConfigs.add(VariantConfig(
                            variantName = btName,
                            buildTypeName = btName,
                            flavorName = null
                        ))
                    }
                }

                for (config in variantConfigs) {
                    val capitalizedVariant = config.variantName.replaceFirstChar { it.uppercase() }
                    val taskName = "proguardProtectAnalyze$capitalizedVariant"
                    registeredTaskNames.add(taskName)

                    // Source dirs: main + buildType + flavor
                    val variantSrcDirs = mutableListOf<File>()
                    if (mainJavaDir.exists()) variantSrcDirs.add(mainJavaDir)
                    if (mainKotlinDir.exists()) variantSrcDirs.add(mainKotlinDir)
                    for (srcSet in listOf(config.buildTypeName, config.flavorName, config.variantName)) {
                        if (srcSet == null) continue
                        val javaDir = proj.file("src/$srcSet/java")
                        val kotlinDir = proj.file("src/$srcSet/kotlin")
                        if (javaDir.exists()) variantSrcDirs.add(javaDir)
                        if (kotlinDir.exists()) variantSrcDirs.add(kotlinDir)
                    }

                    // Collect proguard files
                    val pgFiles = mutableListOf<File>()
                    val bt = androidExt.buildTypes.findByName(config.buildTypeName)
                    bt?.proguardFiles?.forEach { f -> pgFiles.add(f) }
                    extension.extraRulesFiles.get().forEach { path -> pgFiles.add(proj.file(path)) }

                    // APK output path differs based on flavors:
                    //   No flavors:   outputs/apk/{buildType}/
                    //   With flavors: outputs/apk/{flavor}/{buildType}/
                    // AAB & mapping always use variant name:
                    //   outputs/bundle/{variantName}/
                    //   outputs/mapping/{variantName}/
                    val apkSubPath = if (config.flavorName != null) {
                        "outputs/apk/${config.flavorName}/${config.buildTypeName}"
                    } else {
                        "outputs/apk/${config.buildTypeName}"
                    }

                    proj.tasks.register(taskName, PostBuildAnalysisTask::class.java, object : Action<PostBuildAnalysisTask> {
                        override fun execute(task: PostBuildAnalysisTask) {
                            task.variantName.set(config.variantName)
                            task.failOnError.set(extension.failOnError)
                            task.targetPackages.set(extension.targetPackages)
                            task.sourceDirs.setFrom(variantSrcDirs)
                            task.proguardFiles.setFrom(pgFiles)
                            task.reportDir.set(proj.layout.buildDirectory.dir("reports/proguardProtect"))

                            // mapping.txt location (same for APK and AAB)
                            task.mappingFile.set(
                                proj.layout.buildDirectory.file("outputs/mapping/${config.variantName}/mapping.txt")
                            )

                            // APK output directory
                            task.apkOutputDir.set(
                                proj.layout.buildDirectory.dir(apkSubPath)
                            )

                            // AAB output directory
                            task.aabOutputDir.set(
                                proj.layout.buildDirectory.dir("outputs/bundle/${config.variantName}")
                            )

                            if (hasApkAnalyzer) {
                                task.apkAnalyzerPath.set(analyzerPath)
                            }

                            // Use mustRunAfter so the task can work with either assemble or bundle
                            // (the user chains: `assembleFreeRelease proguardProtectAnalyzeFreeRelease`
                            //  or `bundleFreeRelease proguardProtectAnalyzeFreeRelease`)
                            task.mustRunAfter("assemble$capitalizedVariant", "bundle$capitalizedVariant")
                        }
                    })

                    proj.logger.lifecycle("ProguardProtect: Registered task '$taskName' for variant '${config.variantName}'")
                }

                // Aggregate task
                proj.tasks.register("proguardProtectAnalyze", object : Action<Task> {
                    override fun execute(t: Task) {
                        t.group = "verification"
                        t.description = "Runs ProguardProtect post-build analysis on all minified variants"
                        registeredTaskNames.forEach { name -> t.dependsOn(name) }
                    }
                })
            }
        })
    }

    /** Configuration for a single build variant. */
    private data class VariantConfig(
        val variantName: String,
        val buildTypeName: String,
        val flavorName: String?
    )
}
