package com.willian.cacadorprecos.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object Scheduler {
    private const val PERIODIC = "verificacao_periodica"

    /** Intervalos oferecidos nos Ajustes, em minutos. O Android não aceita menos de 15. */
    val INTERVALS: List<Int> = listOf(15, 30, 60, 180, 360, 720)

    private fun net(): Constraints =
        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedule(ctx: Context, minutes: Int) {
        val req = PeriodicWorkRequestBuilder<PriceCheckWorker>(
            minutes.toLong().coerceAtLeast(15L), TimeUnit.MINUTES
        ).setConstraints(net()).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, req
        )
    }

    /** Verifica todos os produtos agora. */
    fun runNow(ctx: Context) {
        val req = OneTimeWorkRequestBuilder<PriceCheckWorker>().setConstraints(net()).build()
        WorkManager.getInstance(ctx).enqueueUniqueWork("verificar_agora", ExistingWorkPolicy.REPLACE, req)
    }

    /** Verifica apenas um produto agora. */
    fun runOne(ctx: Context, productId: String) {
        val req = OneTimeWorkRequestBuilder<PriceCheckWorker>()
            .setConstraints(net())
            .setInputData(Data.Builder().putString("id", productId).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            "verificar_" + productId, ExistingWorkPolicy.REPLACE, req
        )
    }
}
