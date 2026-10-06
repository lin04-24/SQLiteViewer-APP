package com.example.dbviewer.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val preferencesStore = PreferencesStore(applicationContext)
            val updateChecker = UpdateChecker()

            // Get current version from BuildConfig
            val currentVersion = inputData.getString("current_version") ?: return@withContext Result.failure()

            // Check for updates
            val result = updateChecker.checkForUpdate(currentVersion)

            result.fold(
                onSuccess = { updateInfo ->
                    if (updateInfo != null) {
                        // Update available - notification will be handled by the app
                        preferencesStore.updateLastCheckTime()
                        Result.success()
                    } else {
                        // No update available
                        preferencesStore.updateLastCheckTime()
                        Result.success()
                    }
                },
                onFailure = {
                    // Network error or API failure
                    Result.retry()
                }
            )
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
