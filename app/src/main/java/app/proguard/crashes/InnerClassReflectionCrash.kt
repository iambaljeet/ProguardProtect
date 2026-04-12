package app.proguard.crashes

import app.proguard.models.TaskDispatcher

/**
 * Crash Type 28: INNER_CLASS_REFLECTION_RENAMED
 *
 * Java nested classes are represented in bytecode with the outer class name, a dollar sign,
 * and the inner class name: `"OuterClass$InnerClass"`. When code uses [Class.forName] with
 * this dollar-separated notation, R8 renaming the outer class breaks the reference:
 *
 * The string `"app.proguard.models.TaskDispatcher$TaskResult"` is hardcoded.
 * After R8 renames [TaskDispatcher] to e.g. `h4`, the class becomes `h4$TaskResult`.
 * The original string no longer maps to any class in the DEX:
 *
 * ```
 * java.lang.ClassNotFoundException: Didn't find class
 *   "app.proguard.models.TaskDispatcher$TaskResult"
 * ```
 *
 * Reference: https://issuetracker.google.com/issues/369813108
 * Reference: https://stackoverflow.com/questions/5816914/proguard-and-inner-classes
 * Reference: https://r8.googlesource.com/r8/+/refs/heads/master/compatibility-faq.md
 *
 * Detection: Find Class.forName("X$Y") string literals → check if outer class FQCN or
 *   the nested class FQCN itself was renamed in mapping.txt or removed from DEX.
 * Fix: -keep class app.proguard.models.TaskDispatcher { *; }
 *      -keep class app.proguard.models.TaskDispatcher$TaskResult { *; }
 */
object InnerClassReflectionCrash {

    /**
     * Loads the nested [TaskDispatcher.TaskResult] class via [Class.forName] using the
     * dollar-sign nested class notation. After R8 renames [TaskDispatcher], the string
     * `"app.proguard.models.TaskDispatcher$TaskResult"` is stale → [ClassNotFoundException].
     */
    fun loadResultClass(): Class<*> {
        // The $ notation is how Java/Kotlin nested classes appear in bytecode.
        // R8 renames TaskDispatcher → h4; TaskDispatcher$TaskResult → h4$a
        // This hardcoded string no longer maps to any class in the final DEX.
        return Class.forName("app.proguard.models.TaskDispatcher\$TaskResult")
    }

    fun trigger(): String {
        return "InnerClassReflection: Class.forName(\"TaskDispatcher\$TaskResult\"). " +
            "R8 renames outer class TaskDispatcher → string reference is stale → " +
            "ClassNotFoundException at runtime."
    }
}
