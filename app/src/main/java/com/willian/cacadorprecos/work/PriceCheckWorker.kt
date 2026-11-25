package com.willian.cacadorprecos.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.willian.cacadorprecos.data.Repo

class PriceCheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        Repo.start(applicationContext)
        val id = inputData.getString("id")
        try {
            if (id == null) Checker.runAll(applicationContext) else Checker.runOne(applicationContext, id)
        } catch (e: Exception) {
            // erro inesperado: tenta de novo na próxima janela
        }
        return Result.success()
    }
}
