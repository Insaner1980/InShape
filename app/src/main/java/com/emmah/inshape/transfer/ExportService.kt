package com.emmah.inshape.transfer

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.emmah.inshape.AppContainer
import com.emmah.inshape.data.newId
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class PreparedExport(val file: File, val format: String, val count: Int, var shared: Boolean = false) {
    val mime: String get() = if (format == "PDF") "application/pdf" else "application/zip"
}
class ExportService(private val container: AppContainer) {
    private val context = container.context
    private val root = File(context.cacheDir, "exports")
    suspend fun prepare(format: String, selection: ExportSelection): PreparedExport = withContext(Dispatchers.IO) {
        require(format in setOf("PDF", "CSV"))
        val snapshot = container.repository.snapshot()
        val count = selectedWorkouts(snapshot, selection).size
        cleanup(context)
        val folder = File(root, newId())
        check(folder.mkdirs()) { "Could not create export storage" }
        val file = File(folder, if (format == "PDF") "InShape.pdf" else "InShape-CSV.zip")
        var complete = false
        try {
            file.outputStream().use { output ->
                if (format ==
                    "PDF"
                ) {
                    PdfExporter.write(snapshot, selection, output)
                } else {
                    JournalExports.csv(snapshot, selection, output)
                }
                output.fd.sync()
            }
            currentCoroutineContext().ensureActive()
            WorkManager.getInstance(
                context
            ).enqueueUniqueWork(
                "export-cache-cleanup",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ExportCleanupWorker>().setInitialDelay(
                    49,
                    TimeUnit.HOURS
                ).build()
            )
            complete = true
            PreparedExport(file, format, count)
        } finally {
            if (!complete) folder.deleteRecursively()
        }
    }
    suspend fun save(prepared: PreparedExport, destination: Uri) = withContext(Dispatchers.IO) {
        (
            context.contentResolver.openOutputStream(destination, "wt")
                ?: throw IOException("Could not open the export destination")
            ).use { output ->
            prepared.file.inputStream().use {
                it.copyTo(
                    output,
                    32 * 1024
                )
            }
            output.flush()
        }
    }
    fun sharedUri(prepared: PreparedExport): Uri {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.exports",
            prepared.file
        )
        check(prepared.file.parentFile!!.setLastModified(System.currentTimeMillis())) {
            "Could not retain the export for sharing"
        }
        prepared.shared =
            true
        return uri
    }
    fun release(prepared: PreparedExport?) {
        if (prepared != null && !prepared.shared) {
            val folder = prepared.file.parentFile!!
            require(
                folder.canonicalFile.parentFile == root.canonicalFile &&
                    folder.name.matches(Regex("[a-f0-9-]{36}"))
            )
            folder.deleteRecursively()
        }
    }
    companion object {
        fun cleanup(context: Context) {
            val root = File(context.cacheDir, "exports")
            root.listFiles()?.filter {
                it.isDirectory && it.name.matches(Regex("[a-f0-9-]{36}")) &&
                    System.currentTimeMillis() - it.lastModified() > TimeUnit.HOURS.toMillis(48)
            }?.forEach { folder ->
                require(folder.canonicalFile.parentFile == root.canonicalFile)
                folder.deleteRecursively()
            }
        }
    }
}
class ExportCleanupWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        ExportService.cleanup(applicationContext)
        Result.success()
    }
}
