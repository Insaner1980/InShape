package com.emmah.inshape.transfer

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.storage.StorageManager
import android.util.Log
import androidx.room.withTransaction
import com.emmah.inshape.AppContainer
import com.emmah.inshape.BuildConfig
import com.emmah.inshape.data.HealthShareState
import com.emmah.inshape.data.JournalDao
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.PhotoAsset
import com.emmah.inshape.data.Preferences
import com.emmah.inshape.data.newId
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ValidatedBackup internal constructor(val staged: StagedBackup, internal val owner: BackupService) : AutoCloseable {
    val identity: String = staged.directory.name
    internal var consumed = false
    override fun close() {
        if (!consumed) {
            consumed = true
            owner.removeStaging(staged.directory)
        }
    }
}

class BackupService(private val container: AppContainer, private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO) {
    private val root = File(container.storageRoot, "transfer-staging")
    private val transferLock = Mutex()
    suspend fun createBackup(destination: Uri) = withContext(ioDispatcher) {
        transferLock.withLock {
            container.photos.assetLock.withLock {
                val directory = newStaging()
                val archive = File(directory, "backup.inshape-backup")
                try {
                    val data = container.repository.snapshot()
                    archive.outputStream().use {
                        BackupArchive.write(
                            data,
                            container.photos::file,
                            it,
                            container.clock.instant().toString(),
                            BuildConfig.VERSION_NAME
                        )
                        it.fd.sync()
                    }
                    (
                        container.context.contentResolver.openOutputStream(destination, "wt")
                            ?: throw IOException("Could not open the backup destination")
                        ).use { output ->
                        archive.inputStream().use {
                            it.copyTo(
                                output,
                                32 * 1024
                            )
                        }
                        output.flush()
                    }
                    // A cancelled picker or a failed destination close never reaches this update.
                    container.database.withTransaction {
                        val prefs = container.database.journal().preferences() ?: Preferences()
                        container.database.journal().put(
                            prefs.copy(lastBackupAt = container.clock.instant().toString())
                        )
                    }
                } finally {
                    removeStaging(directory)
                }
            }
        }
    }
    suspend fun validateBackup(source: Uri): ValidatedBackup = withContext(ioDispatcher) {
        transferLock.withLock {
            val directory = newStaging()
            var validated = false
            try {
                val staged = (
                    container.context.contentResolver.openInputStream(source)
                        ?: throw IOException("Could not open this backup")
                    ).use {
                    BackupArchive.read(it, directory) { bytes -> reserveRestoreSpace(directory, bytes) }
                }
                staged.data.photos.forEach { asset ->
                    validateImage(File(directory, asset.path), asset.mediaType)
                }
                ValidatedBackup(staged, this@BackupService).also { validated = true }
            } finally {
                if (!validated) removeStaging(directory)
            }
        }
    }

    private fun reserveRestoreSpace(directory: File, bytes: Long) {
        if (directory.usableSpace >= bytes) return
        val storage = container.context.getSystemService(StorageManager::class.java)
        try {
            val volume = storage.getUuidForPath(directory)
            if (storage.getAllocatableBytes(volume) < bytes) {
                throw IOException("Insufficient allocatable storage")
            }
            storage.allocateBytes(volume, bytes)
        } catch (failure: IOException) {
            throw IOException("Not enough free storage to stage this backup safely", failure)
        }
    }

    suspend fun replaceJournal(backup: ValidatedBackup, confirmedIdentity: String) = withContext(ioDispatcher) {
        transferLock.withLock {
            container.healthSharing.lock.withLock {
                container.photos.assetLock.withLock {
                    require(
                        backup.owner === this@BackupService && !backup.consumed &&
                            backup.identity == confirmedIdentity
                    ) { "Validate and confirm this backup again." }
                    val generation = File(container.photos.root, "restore-${backup.identity}")
                    var committed = false
                    try {
                        check(generation.mkdirs()) { "Could not stage restored photos" }
                        val data = backup.staged.data
                        val restoredPhotos = data.photos.map { asset ->
                            val source = File(backup.staged.directory, asset.path)
                            val target = File(generation, source.name)
                            check(source.renameTo(target)) {
                                "Could not move a staged photo. The current journal is unchanged."
                            }
                            asset.copy(path = "${generation.name}/${target.name}")
                        }
                        currentCoroutineContext().ensureActive()
                        withContext(NonCancellable) {
                            replaceData(data, restoredPhotos)
                            committed = true
                            backup.consumed = true
                        }
                        cleanupCommittedRestore(backup)
                        container.onForeground()
                    } finally {
                        if (!committed) {
                            if (generation.isDirectory) removeOwnedGeneration(generation)
                            backup.close()
                        }
                    }
                }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun cleanupCommittedRestore(backup: ValidatedBackup) {
        try {
            removeStaging(backup.staged.directory)
            removeUnreferencedPhotos()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Log.w("BackupService", "Journal was restored; startup recovery will retry photo and staging cleanup")
        }
    }

    private suspend fun replaceData(data: JournalSnapshot, restoredPhotos: List<PhotoAsset>) {
        container.database.withTransaction {
            val dao = container.database.journal()
            val current = container.repository.snapshot()
            val deletions = current.outbox.filter { it.operation == "DELETE" }
            val deletionStates = current.healthStates.filter { state ->
                deletions.any {
                    it.workoutId ==
                        state.workoutId
                }
            }
            dao.clearWorkouts()
            dao.clearRoutines()
            dao.clearPhotos()
            dao.clearExercises()
            dao.clearDrafts()
            dao.clearHealthStates()
            dao.clearOutbox()
            dao.clearProgramEnrollment()
            dao.clearProgramLinks()
            dao.clearProgramDayOverrides()
            dao.clearAssistantDrafts()
            dao.clearAssistantReceipts()
            dao.clearAssistantMemories()
            data.exercises.forEach { dao.put(it) }
            data.programEnrollment?.let { dao.put(it) }
            data.programLinks.forEach { dao.put(it) }
            data.programDayOverrides.forEach { dao.put(it) }
            data.assistantMemories.forEach { dao.put(it) }
            data.routines.forEach { dao.put(it) }
            data.routineItems.forEach { dao.put(it) }
            data.workouts.forEach { dao.insertWorkout(it) }
            data.items.forEach { dao.put(it) }
            data.sets.forEach { dao.put(it) }
            data.checkIns.forEach { dao.put(it) }
            data.observations.forEach { dao.put(it) }
            data.drafts.forEach { dao.put(it) }
            restoredPhotos.forEach { dao.put(it) }
            dao.put(
                data.preferences.copy(
                    lastBackupAt = current.preferences.lastBackupAt,
                    remindersHeld = data.preferences.remindersEnabled,
                    shareWorkouts = false,
                    readSteps = false,
                    readSleep = false,
                    readHeartRate = false,
                    heartRateSource = null
                )
            )
            holdRestoredSharing(dao, data)
            deletionStates.forEach { state ->
                dao.put(state.copy(restoredHold = true))
            }
            deletions.forEach { dao.put(it) }
            // Insert can succeed even when SQLite cannot read a row into a cursor window.
            // Read every restored record through the normal DAO queries before committing.
            container.repository.snapshot()
        }
    }

    private suspend fun holdRestoredSharing(dao: JournalDao, data: JournalSnapshot) {
        data.workouts.forEach { workout ->
            val prior =
                data.healthStates.find { it.workoutId == workout.id }
                    ?: HealthShareState(workout.id)
            dao.put(
                prior.copy(
                    platformId = null,
                    acknowledgedRevision = 0,
                    status = "HELD",
                    error = null,
                    restoredHold = true
                )
            )
        }
        val heldRemovals = data.healthStates.filter {
            it.status == "REMOVAL_HELD"
        }.associateBy { it.workoutId }.toMutableMap()
        data.outbox.forEach { pending ->
            val prior =
                data.healthStates.find { it.workoutId == pending.workoutId }
                    ?: HealthShareState(pending.workoutId)
            heldRemovals[pending.workoutId] =
                prior.copy(
                    clientVersion = maxOf(
                        prior.clientVersion,
                        pending.revision
                    )
                )
        }
        heldRemovals.values.forEach {
            dao.put(
                it.copy(
                    platformId = null,
                    acknowledgedRevision = 0,
                    status = "REMOVAL_HELD",
                    restoredHold = true,
                    error = null
                )
            )
        }
    }

    private fun newStaging(): File {
        check(root.isDirectory || root.mkdirs()) { "Could not create transfer storage" }
        return File(root, newId()).also {
            check(it.mkdir()) { "Could not create transfer storage" }
        }
    }
    internal fun removeStaging(directory: File) {
        require(
            directory.canonicalFile.parentFile == root.canonicalFile &&
                directory.name.matches(Regex("[a-f0-9-]{36}"))
        )
        directory.deleteRecursively()
    }
    private fun removeOwnedGeneration(directory: File) {
        require(
            directory.canonicalFile.parentFile == container.photos.root.canonicalFile &&
                directory.name.matches(Regex("restore-[a-f0-9-]{36}"))
        )
        directory.deleteRecursively()
    }
    private suspend fun removeUnreferencedPhotos() {
        val retained = container.database.journal().photos().map {
            container.photos.file(it).canonicalPath
        }.toSet()
        container.photos.root.walkBottomUp().forEach { file ->
            if (file == container.photos.root) return@forEach
            require(
                file.canonicalPath.startsWith(container.photos.root.canonicalPath + File.separator)
            )
            val unused = file.isFile && file.canonicalPath !in retained
            val emptyDirectory = file.isDirectory && file.list()?.isEmpty() == true
            if (!unused && !emptyDirectory) return@forEach
            if (!file.delete() && file.exists()) {
                Log.w("BackupService", "Unused photo storage could not be removed; startup recovery will retry")
            }
        }
    }
    suspend fun recoverOwnedStaging() = withContext(ioDispatcher) {
        transferLock.withLock {
            container.photos.assetLock.withLock {
                root.listFiles()?.filter {
                    it.isDirectory && it.name.matches(Regex("[a-f0-9-]{36}"))
                }?.forEach(::removeStaging)
                removeUnreferencedPhotos()
            }
        }
    }
    private fun validateImage(file: File, mime: String) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        require(
            bounds.outWidth > 0 && bounds.outHeight > 0 &&
                bounds.outWidth.toLong() * bounds.outHeight <= 100_000_000 &&
                bounds.outMimeType == mime
        ) { "Backup contains an unreadable or unsupported photo" }
        bounds.inJustDecodeBounds = false
        bounds.inSampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / bounds.inSampleSize >
            512
        ) {
            bounds.inSampleSize *= 2
        }
        val bitmap =
            BitmapFactory.decodeFile(file.path, bounds) ?: error("Backup photo cannot be decoded")
        bitmap.recycle()
    }
}
