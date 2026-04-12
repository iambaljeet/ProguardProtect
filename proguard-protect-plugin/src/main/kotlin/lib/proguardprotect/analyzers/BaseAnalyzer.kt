package lib.proguardprotect.analyzers

import lib.proguardprotect.models.ProguardIssue
import lib.proguardprotect.utils.KeepRule
import lib.proguardprotect.utils.SourceFile

/**
 * Abstract base class for all ProguardProtect source-level analyzers.
 *
 * Each analyzer scans source files for a specific category of dynamic access patterns
 * that are vulnerable to R8/ProGuard minification. In the post-build analysis pipeline,
 * analyzers are run with **empty keep rules** to find ALL potentially vulnerable patterns,
 * which are then cross-referenced with mapping.txt to confirm actual issues.
 *
 * Subclasses must implement:
 * - [issueType]: The category of vulnerability this analyzer detects
 * - [analyze]: The detection logic that scans source files and returns found issues
 *
 * @see lib.proguardprotect.PostBuildAnalysisTask The task that orchestrates analyzers
 */
abstract class BaseAnalyzer {
    /** The type of issue this analyzer detects. */
    abstract val issueType: ProguardIssue.IssueType

    /**
     * Scans source files for vulnerable patterns.
     *
     * @param sourceFiles List of parsed source files to analyze
     * @param keepRules ProGuard keep rules (empty in post-build mode to find ALL patterns)
     * @return List of detected issues with file locations and affected class/member names
     */
    abstract fun analyze(sourceFiles: List<SourceFile>, keepRules: List<KeepRule>): List<ProguardIssue>
}
