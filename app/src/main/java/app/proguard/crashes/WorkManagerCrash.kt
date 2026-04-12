package app.proguard.crashes

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import app.proguard.models.DataSyncWorker

/**
 * Crash Type 16: WORKMANAGER_WORKER_STRIPPED
 *
 * WorkManager enqueues work by storing the Worker class FQN in its database.
 * At execution time, WorkManager does:
 *   Class.forName("app.proguard.models.DataSyncWorker")
 * If R8 renamed DataSyncWorker to something like "b4" → ClassNotFoundException.
 *
 * Reference: https://www.codestudy.net/blog/android-work-manager-could-not-instantiate-worker/
 * Reference: https://drjansari.medium.com/mastering-proguard-in-android-multi-module-projects-agp-8-4-r8-and-consumable-rules-ae28074b6f1f
 *
 * Detection: Find classes extending Worker/ListenableWorker → check if renamed in mapping.
 * Fix: -keep class * extends androidx.work.Worker { public <init>(android.content.Context, androidx.work.WorkerParameters); }
 */
object WorkManagerCrash {
    fun enqueueWork(context: Context) {
        val request = OneTimeWorkRequestBuilder<DataSyncWorker>().build()
        // WorkManager stores "app.proguard.models.DataSyncWorker" in DB
        // After R8: class renamed → ClassNotFoundException on execution
        WorkManager.getInstance(context).enqueue(request)
    }

    fun trigger(): String {
        return "WorkManager: DataSyncWorker extends Worker. R8 will rename it → WorkManager " +
            "stores the original FQCN in DB and fails with ClassNotFoundException on execution."
    }
}
