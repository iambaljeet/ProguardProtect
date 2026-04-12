package lib.proguardprotect.analyzers

import lib.proguardprotect.models.ProguardIssue
import lib.proguardprotect.models.ProguardIssue.IssueType
import lib.proguardprotect.models.ProguardIssue.Severity
import lib.proguardprotect.utils.KeepRule
import lib.proguardprotect.utils.ProguardRulesParser
import lib.proguardprotect.utils.SourceFile

/**
 * Detects WorkManager Worker subclasses that will be renamed/stripped by R8.
 *
 * WorkManager stores worker class FQCNs in its job database and instantiates them
 * via reflection (Class.forName + constructor lookup). R8 renaming breaks this.
 *
 * References:
 * - https://www.codestudy.net/blog/android-work-manager-could-not-instantiate-worker/
 * - https://drjansari.medium.com/mastering-proguard-in-android-multi-module-projects-agp-8-4-r8-and-consumable-rules-ae28074b6f1f
 */
class WorkManagerAnalyzer : BaseAnalyzer() {
    override val issueType = IssueType.WORKMANAGER_WORKER_STRIPPED

    private val workerInheritPattern = Regex(
        """:\s*(?:[A-Za-z0-9.]*\.)?(?:Worker|CoroutineWorker|ListenableWorker|RxWorker)\s*[\({(]"""
    )

    override fun analyze(sourceFiles: List<SourceFile>, keepRules: List<KeepRule>): List<ProguardIssue> {
        val issues = mutableListOf<ProguardIssue>()
        val parser = ProguardRulesParser()

        for (source in sourceFiles) {
            val fullContent = source.lines.joinToString("\n")
            if (!workerInheritPattern.containsMatchIn(fullContent)) continue

            var classLine = 1
            source.lines.forEachIndexed { index, line ->
                if (workerInheritPattern.containsMatchIn(line)) {
                    classLine = index + 1
                    return@forEachIndexed
                }
            }

            val fqn = "${source.packageName}.${source.file.nameWithoutExtension}"
            if (!parser.isClassKept(fqn, keepRules)) {
                issues.add(ProguardIssue(
                    type = IssueType.WORKMANAGER_WORKER_STRIPPED,
                    severity = Severity.WARNING,
                    filePath = source.file.path,
                    lineNumber = classLine,
                    message = "WorkManager Worker class '$fqn' will be renamed by R8. " +
                        "WorkManager stores the original FQCN in its job database and uses Class.forName() to instantiate workers → ClassNotFoundException at runtime.",
                    suggestion = "-keep class $fqn { public <init>(android.content.Context, androidx.work.WorkerParameters); }",
                    className = fqn
                ))
            }
        }
        return issues
    }
}
