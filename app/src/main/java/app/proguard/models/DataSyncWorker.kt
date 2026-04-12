package app.proguard.models

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * WorkManager background worker for data synchronization.
 * Demonstrates R8 WorkManager worker stripping (Crash Type 16).
 * Reference: https://www.codestudy.net/blog/android-work-manager-could-not-instantiate-worker/
 * Reference: https://issuetracker.google.com/issues/232518959
 *
 * WorkManager stores the worker class name (FQCN) in its job queue database.
 * When the job is scheduled to run, WorkManager does Class.forName(className)
 * to instantiate the worker. If R8 renamed DataSyncWorker → ClassNotFoundException.
 */
class DataSyncWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        // Simulate data sync
        return Result.success()
    }
}
