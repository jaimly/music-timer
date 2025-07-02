package org.ganquan.musictimer.tools

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.UUID
import java.util.concurrent.TimeUnit

private const val TAG: String = "timerWorker"

class OneTimeWorker{
    private val activity: Context
    private val startRequestId: UUID = UUID.randomUUID()
    private val endRequestId: UUID = UUID.randomUUID()

    constructor(activity1: Context) {
        activity = activity1
    }

    fun request(
        playTime: Long,
        delayTime: Long = 1
    ) {
        val startRequest = OneTimeWorkRequestBuilder<StartWork>()
            .addTag(TAG)
            .setId(startRequestId)
            .setInitialDelay(delayTime, TimeUnit.SECONDS)
            .build()

        val endRequest = OneTimeWorkRequestBuilder<EndWork>()
            .addTag(TAG)
            .setId(endRequestId)
            .setInitialDelay(delayTime + playTime, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(activity)
            .enqueueUniqueWork("startTask", ExistingWorkPolicy.REPLACE, startRequest)
        WorkManager.getInstance(activity)
            .enqueueUniqueWork("endTask", ExistingWorkPolicy.REPLACE, endRequest)

        WorkManager.getInstance(activity)
            .beginWith(startRequest)
            .then(endRequest)
            .enqueue()
    }

    fun cancel() {
        WorkManager.getInstance(activity).cancelAllWorkByTag(TAG)
    }
}

class StartWork(appContext: Context, workerParams: WorkerParameters):
    Worker(appContext, workerParams) {
    private val context = appContext
    override fun doWork(): Result {
        Broadcast.sendLocal(context, "start worker")
        return Result.success()
    }
}

class EndWork(appContext: Context, workerParams: WorkerParameters):
    Worker(appContext, workerParams) {
    private val context = appContext
    override fun doWork(): Result {
        Broadcast.sendLocal(context, "end worker")
        return Result.success()
    }
}