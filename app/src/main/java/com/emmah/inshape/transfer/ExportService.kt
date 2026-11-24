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
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class PreparedExport(val file: File, val format: String, val count: Int, var shared: Boolean = false) {
    val id: String get() = file.parentFile!!.name
    val mime: String get() = if (format == "PDF") "application/pdf" else "application/zip"
}
class ExportService(private val container: AppContainer, private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO) {
    private val context = container.context
    private val root = File(context.cacheDir, "exports")
    suspend fun prepare(format: String, selection: ExportSelection): PreparedExport = withContext(ioDispatcher) {
        require(format in setOf("PDF", "CSV"))
        val snapshot = container.repository.snapshot()
        val count = selectedWorkouts(snapshot, selection).size
        cleanup(context)
        val folder = File(root, newId())
        check(folder.mkdirs()) { "Could not create export storage" }
        val file = File(folder, exportName(format))
        var complete = false
        try {
            file.outputStream().use { output ->
                if (format ==
                    "PDF"
                ) {
                    PdfExporter.write(context, snapshot, selection, output)
                } else {
                    JournalExports.csv(context, snapshot, selection, output)
                }
                output.fd.sync()
            }
            currentCoroutineContext().ensureActive()
            scheduleCleanup(context, folder)
            complete = true
            PreparedExport(file, format, count)
        } finally {
            if (!complete) folder.deleteRecursively()
        }
    }
    internal fun findPreparedFile(id: String, format: String): File? {
        if (!id.matches(exportIdPattern)) return null
        val folder = File(root.canonicalFile, id)
        val file = File(folder, exportName(format))
        return file.takeIf { it.isFile && it.canonicalFile == it }
    }

    suspend fun save(prepared: PreparedExport, destination: Uri) = save(prepared.file, destination)

    internal suspend fun save(file: File, destination: Uri) = withContext(ioDispatcher) {
        val input = try {
            file.inputStream()
        } catch (_: FileNotFoundException) {
            throw IOException(EXPORT_MISSING_MESSAGE)
        }
        input.use {
            val output = context.contentResolver.openOutputStream(destination, "wt")
                ?: throw IOException("Could not open the export destination")
            output.use {
                input.copyTo(output, 32 * 1024)
                output.flush()
            }
        }
    }
    fun sharedUri(prepared: PreparedExport): Uri = synchronized(cacheLock) {
        check(prepared.file.isFile) { EXPORT_MISSING_MESSAGE }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.exports",
            prepared.file
        )
        val folder = checkNotNull(prepared.file.parentFile)
        check(folder.setLastModified(System.currentTimeMillis())) {
            "Could not retain the export for sharing"
        }
        scheduleCleanup(context, folder)
        prepared.shared = true
        uri
    }
    fun release(prepared: PreparedExport?) {
        if (prepared != null && !prepared.shared) {
            release(prepared.id, prepared.format)
        }
    }

    internal fun release(id: String, format: String) {
        findPreparedFile(id, format)?.parentFile?.deleteRecursively()
    }

    private fun exportName(format: String) = if (format == "PDF") "InShape.pdf" else "InShape-CSV.zip"

    companion object {
        internal const val EXPORT_MISSING_MESSAGE = "This prepared export is no longer available. Prepare the export again."
        internal const val CACHE_EXPIRY_WORK = "export-cache-expiry"
        private val exportIdPattern = Regex("[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}")
        private val cacheLock = Any()

        internal fun scheduleCleanup(context: Context, folder: File) {
            val runAfter = folder.lastModified() + TimeUnit.HOURS.toMillis(49)
            WorkManager.getInstance(context).enqueueUniqueWork(
                "$CACHE_EXPIRY_WORK:${folder.name}",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ExportCleanupWorker>()
                    .addTag(CACHE_EXPIRY_WORK)
                    .setInitialDelay((runAfter - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
                    .build()
            )
        }

        internal fun cleanup(context: Context): List<File> = synchronized(cacheLock) {
            val root = File(context.cacheDir, "exports")
            val expiry = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(48)
            val folders = root.listFiles()?.filter { it.isDirectory && it.name.matches(exportIdPattern) }.orEmpty()
            val (expired, retained) = folders.partition { it.lastModified() <= expiry }
            expired.forEach { folder ->
                require(folder.canonicalFile.parentFile == root.canonicalFile)
                folder.deleteRecursively()
            }
            retained
        }
    }
}
class ExportCleanupWorker @JvmOverloads constructor(
    context: Context,
    parameters: WorkerParameters,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(ioDispatcher) {
        val retained = ExportService.cleanup(applicationContext)
        if (ExportService.CACHE_EXPIRY_WORK !in tags) {
            // An older one-time sweep can run before the expiry of a subsequently shared export.
            retained.forEach { ExportService.scheduleCleanup(applicationContext, it) }
        }
        Result.success()
    }
}
