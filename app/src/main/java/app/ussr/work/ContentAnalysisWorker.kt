package app.ussr.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.ussr.ServiceLocator

/**
 * Runs OCR and image labelling over the library in the background.
 *
 * This is the slow half of the analysis and the reason the app never blocks on it: the
 * heuristics alone already fill the first decks, and each item this worker finishes only
 * sharpens later decks. It is constrained to a charging device so a full library sweep is
 * never paid for out of the user's battery.
 */
class ContentAnalysisWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = ServiceLocator.repository(applicationContext)
        return runCatching {
            val items = repository.sweep()
            repository.analyseContent(items, limit = BATCH_LIMIT, shouldStop = { isStopped })
        }.fold(
            onSuccess = { done ->
                // More left to do: come back on the next charge rather than hold the device.
                if (done >= BATCH_LIMIT) Result.retry() else Result.success()
            },
            onFailure = { if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure() },
        )
    }

    companion object {
        private const val NAME = "content-analysis"
        private const val BATCH_LIMIT = 1_500
        private const val MAX_ATTEMPTS = 3

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<ContentAnalysisWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresCharging(true)
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
