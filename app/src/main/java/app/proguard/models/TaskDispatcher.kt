package app.proguard.models

/**
 * Task dispatcher that processes [TaskResult] payloads.
 *
 * The inner class [TaskResult] is loaded via [Class.forName] using the `$`-separated
 * Java nested class notation: `"app.proguard.models.TaskDispatcher$TaskResult"`.
 *
 * R8 renames [TaskDispatcher] (e.g., to `h4`). After renaming, the nested class also
 * changes path to `h4$TaskResult` (or similar). The hardcoded string
 * `"app.proguard.models.TaskDispatcher$TaskResult"` no longer maps to any class in the DEX,
 * causing [ClassNotFoundException] at runtime.
 *
 * Reference: https://issuetracker.google.com/issues/369813108
 * Reference: https://stackoverflow.com/questions/5816914/proguard-and-inner-classes
 */
class TaskDispatcher {

    /**
     * Result payload for a dispatched task.
     * Referenced reflectively as `"app.proguard.models.TaskDispatcher$TaskResult"`.
     */
    data class TaskResult(
        val taskId: String,
        val success: Boolean,
        val output: String = ""
    )

    /** Processes a task and returns its result. */
    fun dispatch(taskId: String, action: () -> String): TaskResult {
        return try {
            val output = action()
            TaskResult(taskId, success = true, output = output)
        } catch (e: Exception) {
            TaskResult(taskId, success = false, output = e.message ?: "error")
        }
    }
}
