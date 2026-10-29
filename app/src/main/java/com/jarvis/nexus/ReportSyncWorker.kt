package com.jarvis.nexus

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Sincronizzazione periodica dei report (solo download, solo su Wi-Fi). Se il PC è spento riprova al giro dopo. */
class ReportSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (ReportSync.pairing(applicationContext) == null) return@withContext Result.success()
        ReportSync.sync(applicationContext)
        Result.success() // nessun retry aggressivo: il prossimo giro periodico basta
    }

    companion object {
        private const val NAME = "report_sync"

        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<ReportSyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun cancel(ctx: Context) { WorkManager.getInstance(ctx).cancelUniqueWork(NAME) }
    }
}
