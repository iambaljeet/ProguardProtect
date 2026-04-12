package lib.proguardprotect.utils

import java.io.File

/**
 * Represents a parsed source file with its metadata for analysis.
 *
 * @property file The physical file on disk
 * @property lines All lines of the file content
 * @property packageName The declared package (e.g., "com.example.models")
 * @property imports List of import statements for class name resolution
 */
data class SourceFile(
    val file: File,
    val lines: List<String>,
    val packageName: String,
    val imports: List<String>
) {
    /**
     * Resolves a simple class name to its fully-qualified form using imports and package context.
     *
     * @param simpleName Simple class name (e.g., "UserProfile")
     * @return Fully-qualified name if resolvable, or null if ambiguous (wildcard imports)
     */
    fun resolveClassName(simpleName: String): String? {
        if (simpleName.contains(".")) return simpleName
        val matchingImport = imports.find { it.endsWith(".$simpleName") }
        if (matchingImport != null) return matchingImport
        val wildcardImports = imports.filter { it.endsWith(".*") }
        if (wildcardImports.isNotEmpty()) return null
        return if (packageName.isNotEmpty()) "$packageName.$simpleName" else simpleName
    }
}

/**
 * Scans directories for Kotlin and Java source files and parses them into [SourceFile] objects.
 *
 * Extracts package name and import statements from each file for use by analyzers
 * during class name resolution.
 */
class SourceScanner {

    /**
     * Recursively scans a directory for source files.
     *
     * @param dir The directory to scan
     * @param extensions File extensions to include (default: kt, java)
     * @return List of parsed [SourceFile] objects
     */
    fun scanDirectory(dir: File, extensions: List<String> = listOf("kt", "java")): List<SourceFile> {
        if (!dir.exists()) return emptyList()

        return dir.walkTopDown()
            .filter { it.isFile && extensions.any { ext -> it.name.endsWith(".$ext") } }
            .map { parseSourceFile(it) }
            .toList()
    }

    private fun parseSourceFile(file: File): SourceFile {
        val lines = file.readLines()
        val packageName = lines
            .firstOrNull { it.trimStart().startsWith("package ") }
            ?.substringAfter("package ")
            ?.trim()
            ?.trimEnd(';')
            ?: ""

        val imports = lines
            .filter { it.trimStart().startsWith("import ") }
            .map { it.substringAfter("import ").trim().trimEnd(';') }

        return SourceFile(file, lines, packageName, imports)
    }
}
