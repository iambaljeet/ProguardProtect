package lib.proguardprotect.report

import lib.proguardprotect.models.ProguardIssue
import lib.proguardprotect.utils.MappingParser.ClassMapping
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Generates professional HTML reports for ProguardProtect analysis results.
 *
 * The report includes:
 * - Summary dashboard with issue counts by type and severity
 * - Detailed issue cards with source code snippets
 * - R8 mapping information showing what was renamed/removed
 * - Fix suggestions with copy-ready ProGuard rules
 * - Responsive design with collapsible sections
 *
 * ## Usage
 * ```kotlin
 * val generator = HtmlReportGenerator()
 * generator.generate(
 *     outputFile = File("report.html"),
 *     variant = "release",
 *     issues = confirmedIssues,
 *     mapping = mappingData,
 *     sourceDirs = listOf(File("src/main/java")),
 *     buildArtifact = "/path/to/app-release.apk"
 * )
 * ```
 */
class HtmlReportGenerator {

    /**
     * Generates an HTML report file from analysis results.
     *
     * @param outputFile The file to write the HTML report to
     * @param variant Build variant name (e.g., "release")
     * @param issues List of confirmed post-build issues
     * @param mapping Parsed mapping.txt data for R8 rename details
     * @param sourceDirs Source directories for extracting code snippets
     * @param buildArtifact Path to the analyzed APK/AAB file
     */
    fun generate(
        outputFile: File,
        variant: String,
        issues: List<ProguardIssue>,
        mapping: Map<String, ClassMapping>,
        sourceDirs: List<File>,
        buildArtifact: String
    ) {
        val html = buildString {
            appendLine("<!DOCTYPE html>")
            appendLine("<html lang=\"en\">")
            appendLine("<head>")
            appendLine("<meta charset=\"UTF-8\">")
            appendLine("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
            appendLine("<title>ProguardProtect Report — $variant</title>")
            appendLine("<style>")
            appendLine(CSS)
            appendLine("</style>")
            appendLine("</head>")
            appendLine("<body>")

            // Header
            appendLine("<div class=\"header\">")
            appendLine("  <div class=\"header-content\">")
            appendLine("    <h1>🛡️ ProguardProtect</h1>")
            appendLine("    <p class=\"subtitle\">Post-Build R8/ProGuard Analysis Report</p>")
            appendLine("  </div>")
            appendLine("</div>")

            // Metadata bar
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            appendLine("<div class=\"meta-bar\">")
            appendLine("  <span><strong>Variant:</strong> $variant</span>")
            appendLine("  <span><strong>Generated:</strong> $timestamp</span>")
            appendLine("  <span><strong>Artifact:</strong> ${File(buildArtifact).name}</span>")
            appendLine("</div>")

            // Summary dashboard
            val errors = issues.count { it.severity == ProguardIssue.Severity.ERROR }
            val warnings = issues.count { it.severity == ProguardIssue.Severity.WARNING }
            val issuesByType = issues.groupBy { it.type }

            appendLine("<div class=\"dashboard\">")
            if (issues.isEmpty()) {
                appendLine("  <div class=\"summary-card success\">")
                appendLine("    <div class=\"summary-number\">✅</div>")
                appendLine("    <div class=\"summary-label\">No Issues Found</div>")
                appendLine("    <div class=\"summary-detail\">All dynamic access patterns are properly protected</div>")
                appendLine("  </div>")
            } else {
                appendLine("  <div class=\"summary-card ${if (errors > 0) "danger" else "warning"}\">")
                appendLine("    <div class=\"summary-number\">${issues.size}</div>")
                appendLine("    <div class=\"summary-label\">Total Issues</div>")
                appendLine("  </div>")
                appendLine("  <div class=\"summary-card danger\">")
                appendLine("    <div class=\"summary-number\">$errors</div>")
                appendLine("    <div class=\"summary-label\">Errors</div>")
                appendLine("  </div>")
                appendLine("  <div class=\"summary-card warning\">")
                appendLine("    <div class=\"summary-number\">$warnings</div>")
                appendLine("    <div class=\"summary-label\">Warnings</div>")
                appendLine("  </div>")
                appendLine("  <div class=\"summary-card info\">")
                appendLine("    <div class=\"summary-number\">${issuesByType.size}</div>")
                appendLine("    <div class=\"summary-label\">Issue Types</div>")
                appendLine("  </div>")
            }
            appendLine("</div>")

            // Issue type breakdown
            if (issues.isNotEmpty()) {
                appendLine("<div class=\"section\">")
                appendLine("  <h2>Issue Breakdown by Type</h2>")
                appendLine("  <div class=\"type-grid\">")
                for ((type, typeIssues) in issuesByType) {
                    val icon = getIssueTypeIcon(type)
                    val label = getIssueTypeLabel(type)
                    appendLine("    <div class=\"type-card\">")
                    appendLine("      <span class=\"type-icon\">$icon</span>")
                    appendLine("      <span class=\"type-label\">$label</span>")
                    appendLine("      <span class=\"type-count\">${typeIssues.size}</span>")
                    appendLine("    </div>")
                }
                appendLine("  </div>")
                appendLine("</div>")
            }

            // Detailed issues
            if (issues.isNotEmpty()) {
                appendLine("<div class=\"section\">")
                appendLine("  <h2>Detailed Issues</h2>")

                issues.forEachIndexed { index, issue ->
                    val icon = getIssueTypeIcon(issue.type)
                    val severityClass = if (issue.severity == ProguardIssue.Severity.ERROR) "error" else "warn"
                    val mappingEntry = issue.className?.let { mapping[it] }

                    appendLine("  <div class=\"issue-card $severityClass\">")
                    appendLine("    <div class=\"issue-header\">")
                    appendLine("      <span class=\"issue-badge $severityClass\">${issue.severity}</span>")
                    appendLine("      <span class=\"issue-type\">$icon ${getIssueTypeLabel(issue.type)}</span>")
                    appendLine("      <span class=\"issue-number\">#${index + 1}</span>")
                    appendLine("    </div>")

                    // Location
                    appendLine("    <div class=\"issue-location\">")
                    appendLine("      📍 <code>${issue.filePath}:${issue.lineNumber}</code>")
                    appendLine("    </div>")

                    // Message
                    appendLine("    <div class=\"issue-message\">${escapeHtml(issue.message)}</div>")

                    // R8 Mapping info
                    if (mappingEntry != null && mappingEntry.obfuscatedName != mappingEntry.originalName) {
                        appendLine("    <div class=\"mapping-info\">")
                        appendLine("      <div class=\"mapping-title\">🔄 R8 Mapping</div>")
                        appendLine("      <code class=\"mapping-detail\">${escapeHtml(mappingEntry.originalName)} → ${escapeHtml(mappingEntry.obfuscatedName)}</code>")

                        // Show renamed members if relevant
                        if (issue.memberName != null) {
                            val memberMap = mappingEntry.members.find { it.originalName == issue.memberName }
                            if (memberMap != null && memberMap.obfuscatedName != memberMap.originalName) {
                                appendLine("      <br><code class=\"mapping-detail\">  .${escapeHtml(memberMap.originalName)} → .${escapeHtml(memberMap.obfuscatedName)}</code>")
                            }
                        }
                        appendLine("    </div>")
                    }

                    // Source code snippet
                    val snippet = getSourceSnippet(issue.filePath, issue.lineNumber, sourceDirs)
                    if (snippet != null) {
                        appendLine("    <div class=\"code-section\">")
                        appendLine("      <div class=\"code-title\">📝 Source Code</div>")
                        appendLine("      <pre class=\"code-block\"><code>${snippet}</code></pre>")
                        appendLine("    </div>")
                    }

                    // Fix suggestion
                    appendLine("    <div class=\"suggestion\">")
                    appendLine("      <div class=\"suggestion-title\">💡 Suggested Fix</div>")
                    appendLine("      <code class=\"suggestion-code\">${escapeHtml(issue.suggestion)}</code>")
                    appendLine("    </div>")

                    appendLine("  </div>")
                }

                appendLine("</div>")
            }

            // Footer
            appendLine("<div class=\"footer\">")
            appendLine("  <p>Generated by <strong>ProguardProtect</strong></p>")
            appendLine("  <p class=\"footer-sub\">Post-build analysis using mapping.txt + DEX inspection</p>")
            appendLine("</div>")

            appendLine("</body>")
            appendLine("</html>")
        }

        outputFile.writeText(html)
    }

    /**
     * Extracts a source code snippet (±3 lines) around the issue location.
     * Highlights the issue line with a special marker.
     */
    private fun getSourceSnippet(filePath: String, lineNumber: Int, sourceDirs: List<File>): String? {
        val file = File(filePath)
        if (!file.exists()) return null

        val lines = file.readLines()
        if (lineNumber < 1 || lineNumber > lines.size) return null

        val startLine = maxOf(1, lineNumber - 3)
        val endLine = minOf(lines.size, lineNumber + 3)

        return buildString {
            for (i in startLine..endLine) {
                val lineNum = i.toString().padStart(4)
                val marker = if (i == lineNumber) " ▶ " else "   "
                val lineClass = if (i == lineNumber) "highlight-line" else ""
                appendLine("<span class=\"$lineClass\">$lineNum$marker${escapeHtml(lines[i - 1])}</span>")
            }
        }.trimEnd()
    }

    /** Returns the display icon for each issue type. */
    private fun getIssueTypeIcon(type: ProguardIssue.IssueType): String = when (type) {
        ProguardIssue.IssueType.REFLECTION_CLASS_FOR_NAME -> "🔍"
        ProguardIssue.IssueType.REFLECTION_METHOD_ACCESS -> "⚡"
        ProguardIssue.IssueType.SERIALIZATION_FIELD_RENAME -> "📦"
        ProguardIssue.IssueType.ENUM_VALUE_OF -> "🏷️"
        ProguardIssue.IssueType.CALLBACK_INTERFACE_STRIPPED -> "🔌"
        ProguardIssue.IssueType.DEVIRTUALIZATION_ILLEGAL_ACCESS -> "🚫"
        ProguardIssue.IssueType.NO_CLASS_DEF_FOUND -> "❌"
    }

    /** Returns a human-readable label for each issue type. */
    private fun getIssueTypeLabel(type: ProguardIssue.IssueType): String = when (type) {
        ProguardIssue.IssueType.REFLECTION_CLASS_FOR_NAME -> "ClassNotFoundException (Reflection)"
        ProguardIssue.IssueType.REFLECTION_METHOD_ACCESS -> "NoSuchMethod/FieldError (Reflection)"
        ProguardIssue.IssueType.SERIALIZATION_FIELD_RENAME -> "Serialization Field Rename"
        ProguardIssue.IssueType.ENUM_VALUE_OF -> "Enum valueOf Failure"
        ProguardIssue.IssueType.CALLBACK_INTERFACE_STRIPPED -> "Callback Interface Stripped"
        ProguardIssue.IssueType.DEVIRTUALIZATION_ILLEGAL_ACCESS -> "IllegalAccessError (Devirtualization)"
        ProguardIssue.IssueType.NO_CLASS_DEF_FOUND -> "NoClassDefFoundError"
    }

    /** Escapes HTML special characters. */
    private fun escapeHtml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    companion object {
        /** Embedded CSS for the HTML report — modern, professional design. */
        private val CSS = """
            :root {
                --bg: #0d1117;
                --surface: #161b22;
                --surface-2: #1c2128;
                --border: #30363d;
                --text: #e6edf3;
                --text-muted: #8b949e;
                --accent: #58a6ff;
                --error: #f85149;
                --error-bg: #3d1418;
                --warning: #d29922;
                --warning-bg: #3d2e00;
                --success: #3fb950;
                --success-bg: #0d2818;
                --info: #58a6ff;
                --info-bg: #0c2d6b;
                --code-bg: #0d1117;
            }
            * { box-sizing: border-box; margin: 0; padding: 0; }
            body {
                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, sans-serif;
                background: var(--bg);
                color: var(--text);
                line-height: 1.6;
            }
            .header {
                background: linear-gradient(135deg, #1a1e2e 0%, #0d1117 100%);
                border-bottom: 1px solid var(--border);
                padding: 32px 24px;
            }
            .header-content { max-width: 1200px; margin: 0 auto; }
            .header h1 {
                font-size: 28px;
                font-weight: 700;
                background: linear-gradient(90deg, var(--accent), #a5d6ff);
                -webkit-background-clip: text;
                -webkit-text-fill-color: transparent;
            }
            .subtitle { color: var(--text-muted); margin-top: 4px; font-size: 14px; }
            .meta-bar {
                max-width: 1200px;
                margin: 16px auto;
                padding: 12px 24px;
                display: flex;
                gap: 24px;
                flex-wrap: wrap;
                font-size: 13px;
                color: var(--text-muted);
                background: var(--surface);
                border-radius: 8px;
                border: 1px solid var(--border);
            }
            .dashboard {
                max-width: 1200px;
                margin: 24px auto;
                padding: 0 24px;
                display: grid;
                grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                gap: 16px;
            }
            .summary-card {
                background: var(--surface);
                border: 1px solid var(--border);
                border-radius: 12px;
                padding: 24px;
                text-align: center;
                transition: transform 0.2s;
            }
            .summary-card:hover { transform: translateY(-2px); }
            .summary-card.danger { border-left: 4px solid var(--error); }
            .summary-card.warning { border-left: 4px solid var(--warning); }
            .summary-card.success { border-left: 4px solid var(--success); }
            .summary-card.info { border-left: 4px solid var(--info); }
            .summary-number { font-size: 36px; font-weight: 700; }
            .summary-card.danger .summary-number { color: var(--error); }
            .summary-card.warning .summary-number { color: var(--warning); }
            .summary-card.success .summary-number { color: var(--success); }
            .summary-card.info .summary-number { color: var(--info); }
            .summary-label { font-size: 14px; color: var(--text-muted); margin-top: 4px; }
            .summary-detail { font-size: 12px; color: var(--text-muted); margin-top: 8px; }
            .section {
                max-width: 1200px;
                margin: 32px auto;
                padding: 0 24px;
            }
            .section h2 {
                font-size: 20px;
                font-weight: 600;
                margin-bottom: 16px;
                padding-bottom: 8px;
                border-bottom: 1px solid var(--border);
            }
            .type-grid {
                display: grid;
                grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
                gap: 12px;
            }
            .type-card {
                background: var(--surface);
                border: 1px solid var(--border);
                border-radius: 8px;
                padding: 16px;
                display: flex;
                align-items: center;
                gap: 12px;
            }
            .type-icon { font-size: 24px; }
            .type-label { flex: 1; font-size: 14px; }
            .type-count {
                background: var(--error-bg);
                color: var(--error);
                padding: 4px 10px;
                border-radius: 12px;
                font-size: 13px;
                font-weight: 600;
            }
            .issue-card {
                background: var(--surface);
                border: 1px solid var(--border);
                border-radius: 12px;
                margin-bottom: 16px;
                overflow: hidden;
            }
            .issue-card.error { border-left: 4px solid var(--error); }
            .issue-card.warn { border-left: 4px solid var(--warning); }
            .issue-header {
                padding: 16px 20px;
                display: flex;
                align-items: center;
                gap: 12px;
                background: var(--surface-2);
                border-bottom: 1px solid var(--border);
            }
            .issue-badge {
                padding: 3px 10px;
                border-radius: 4px;
                font-size: 11px;
                font-weight: 700;
                text-transform: uppercase;
                letter-spacing: 0.5px;
            }
            .issue-badge.error { background: var(--error-bg); color: var(--error); }
            .issue-badge.warn { background: var(--warning-bg); color: var(--warning); }
            .issue-type { font-size: 15px; font-weight: 600; }
            .issue-number { margin-left: auto; color: var(--text-muted); font-size: 13px; }
            .issue-location {
                padding: 12px 20px;
                font-size: 13px;
                color: var(--text-muted);
                border-bottom: 1px solid var(--border);
            }
            .issue-location code {
                color: var(--accent);
                background: var(--code-bg);
                padding: 2px 6px;
                border-radius: 4px;
                font-size: 12px;
            }
            .issue-message {
                padding: 16px 20px;
                font-size: 14px;
                line-height: 1.7;
                color: var(--text);
            }
            .mapping-info {
                margin: 0 20px 16px;
                padding: 12px 16px;
                background: var(--info-bg);
                border-radius: 8px;
                border: 1px solid rgba(88, 166, 255, 0.2);
            }
            .mapping-title { font-size: 13px; font-weight: 600; color: var(--info); margin-bottom: 6px; }
            .mapping-detail {
                font-size: 13px;
                color: var(--text);
                background: var(--code-bg);
                padding: 2px 8px;
                border-radius: 4px;
            }
            .code-section {
                margin: 0 20px 16px;
                border-radius: 8px;
                overflow: hidden;
                border: 1px solid var(--border);
            }
            .code-title {
                padding: 8px 16px;
                font-size: 13px;
                font-weight: 600;
                background: var(--surface-2);
                border-bottom: 1px solid var(--border);
            }
            .code-block {
                padding: 12px 16px;
                background: var(--code-bg);
                font-size: 12px;
                line-height: 1.8;
                overflow-x: auto;
                font-family: 'SF Mono', 'Fira Code', 'Cascadia Code', Menlo, monospace;
            }
            .code-block .highlight-line {
                background: rgba(248, 81, 73, 0.15);
                display: inline-block;
                width: 100%;
                margin: 0 -16px;
                padding: 0 16px;
            }
            .suggestion {
                margin: 0 20px 20px;
                padding: 12px 16px;
                background: var(--success-bg);
                border-radius: 8px;
                border: 1px solid rgba(63, 185, 80, 0.2);
            }
            .suggestion-title { font-size: 13px; font-weight: 600; color: var(--success); margin-bottom: 6px; }
            .suggestion-code {
                font-size: 13px;
                color: var(--text);
                background: var(--code-bg);
                padding: 4px 8px;
                border-radius: 4px;
                display: inline-block;
                word-break: break-all;
            }
            .footer {
                max-width: 1200px;
                margin: 48px auto 32px;
                padding: 24px;
                text-align: center;
                color: var(--text-muted);
                border-top: 1px solid var(--border);
            }
            .footer-sub { font-size: 12px; margin-top: 4px; }
            @media (max-width: 768px) {
                .dashboard { grid-template-columns: repeat(2, 1fr); }
                .meta-bar { flex-direction: column; gap: 8px; }
            }
        """.trimIndent()
    }
}
