package org.ganquan.musictimer

import android.content.Context
import android.os.Environment
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.UUID
import java.util.concurrent.TimeUnit

private const val TAG: String = "timerWorker"

class MusicWorker{
    private val activity: Context
    private val startRequestId: UUID = UUID.randomUUID()
    private val endRequestId: UUID = UUID.randomUUID()

    constructor(activity1: Context) {
        activity = activity1
    }

    fun request(
        playTime: Long,
        delayTime: Long = 1,
        folderPath: String = "${Environment.getExternalStorageDirectory()}/${Environment.DIRECTORY_MUSIC}",
        musicName: String = ""
    ) {
        val startRequest = OneTimeWorkRequestBuilder<StartWork>()
            .addTag(TAG)
            .setId(startRequestId)
            .setInitialDelay(delayTime, TimeUnit.SECONDS)
            .setInputData(
                workDataOf(
                    "FOLDER_PATH" to folderPath,
                    "MUSIC_NAME" to musicName
                )
            )
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
        Music.stop()
        WorkManager.getInstance(activity).cancelAllWorkByTag(TAG)
    }
}

class StartWork(appContext: Context, workerParams: WorkerParameters):
    Worker(appContext, workerParams) {
    private val context = appContext
    override fun doWork(): Result {
        val folderPath: String = inputData.getString("FOLDER_PATH").toString()
        val name: String = inputData.getString("MUSIC_NAME").toString()
        val list = Utils.getFileList(folderPath)
        Music.playCircle(list, name)
        Broadcast.sendLocal(context, "start worker")
        return Result.success()
    }
}

class EndWork(appContext: Context, workerParams: WorkerParameters):
    Worker(appContext, workerParams) {
    private val context = appContext
    override fun doWork(): Result {
        Music.stop()
        Broadcast.sendLocal(context, "end worker")
        return Result.success()
    }
}