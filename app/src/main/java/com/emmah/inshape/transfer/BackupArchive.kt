package com.emmah.inshape.transfer

import com.emmah.inshape.data.BodyObservation
import com.emmah.inshape.data.CheckIn
import com.emmah.inshape.data.EditorDraft
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.HealthOutbox
import com.emmah.inshape.data.HealthShareState
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.PhotoAsset
import com.emmah.inshape.data.Preferences
import com.emmah.inshape.data.Routine
import com.emmah.inshape.data.RoutineItem
import com.emmah.inshape.data.SessionItem
import com.emmah.inshape.data.SessionSet
import com.emmah.inshape.data.WorkoutEntity
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable data class BackupEntry(val bytes: Long, val sha256: String)

@Serializable data class BackupManifest(
    val formatVersion: Int = 1,
    val createdAt: String,
    val appVersion: String,
    val counts: Map<String, Int>,
    val entries: Map<String, BackupEntry>
)
data class StagedBackup(val manifest: BackupManifest, val data: JournalSnapshot, val directory: File)

@Serializable private data class BackupJournal(
    val workouts: List<WorkoutEntity>,
    val items: List<SessionItem>,
    val sets: List<SessionSet>,
    val checkIns: List<CheckIn>,
    val observations: List<BodyObservation>,
    val drafts: List<EditorDraft>,
    val healthStates: List<HealthShareState>,
    val pendingDeletions: List<HealthOutbox> = emptyList()
)

@Serializable private data class BackupLibrary(
    val exercises: List<Exercise>,
    val photos: List<PhotoAsset>,
    val routines: List<Routine>,
    val routineItems: List<RoutineItem>
)
object BackupArchive {
    private const val MAX_TOTAL_BYTES = 1024L * 1024 * 1024
    private const val MAX_JSON_BYTES = 16L * 1024 * 1024
    private val json = Json { encodeDefaults = true }
    private val metadataNames = setOf("journal.json", "library.json", "preferences.json")
    private fun digest() = MessageDigest.getInstance("SHA-256")
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
    private fun counts(data: JournalSnapshot) = mapOf(
        "workouts" to data.workouts.size,
        "items" to data.items.size, "sets" to data.sets.size,
        "checkIns" to data.checkIns.size, "observations" to data.observations.size,
        "drafts" to data.drafts.size, "healthStates" to data.healthStates.size,
        "pendingDeletions" to data.outbox.count { it.operation == "DELETE" },
        "exercises" to data.exercises.size, "photos" to data.photos.size,
        "routines" to data.routines.size, "routineItems" to data.routineItems.size
    )
    fun write(data: JournalSnapshot, photoFile: (PhotoAsset) -> File, output: OutputStream, createdAt: String, appVersion: String) {
        validateBackupData(data)
        Instant.parse(createdAt)
        val photos = data.photos.map {
            it.copy(path = "photos/${it.id}.${extensionFor(it.mediaType)}")
        }
        val metadata = metadata(data, photos)
        require(
            metadata.values.all {
                it.size <= MAX_JSON_BYTES
            }
        ) { "Journal metadata exceeds the 16 MB per-file backup limit" }
        val entries = linkedMapOf<String, BackupEntry>()
        metadata.forEach { (name, bytes) ->
            entries[name] =
                BackupEntry(bytes.size.toLong(), digest().digest(bytes).hex())
        }
        data.photos.zip(photos).forEach { (original, portable) ->
            verifyPhoto(original, photoFile)
            entries[portable.path] = BackupEntry(original.byteLength, original.checksum)
        }
        require(
            entries.size <= 2003 && entries.values.sumOf {
                it.bytes
            } <= MAX_TOTAL_BYTES
        ) { "Backup exceeds the 1 GB or 2000-photo limit" }
        val manifest = json.encodeToString(
            BackupManifest(
                createdAt = createdAt,
                appVersion = appVersion,
                counts = counts(data),
                entries = entries
            )
        ).toByteArray(Charsets.UTF_8)
        require(manifest.size <= 1024 * 1024)
        // Finish ZIP structure without closing the caller's stream, so it can fsync before closing.
        ZipOutputStream(object : FilterOutputStream(output) {
            override fun close() {
                flush()
            }
        }).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest)
            zip.closeEntry()
            metadata.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
            data.photos.zip(photos).forEach { (original, portable) ->
                zip.putNextEntry(ZipEntry(portable.path))
                photoFile(original).inputStream().use {
                    it.copyTo(
                        zip,
                        32 * 1024
                    )
                }
                zip.closeEntry()
            }
        }
    }
    fun read(input: InputStream, directory: File, availableBytes: Long = directory.usableSpace): StagedBackup {
        require(directory.isDirectory && directory.list()?.isEmpty() == true) {
            "Restore staging must be empty"
        }
        ZipInputStream(input.buffered()).use { zip ->
            val manifest = readManifest(zip, availableBytes)
            val seen = mutableSetOf<String>()
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory && seen.add(entry.name)) {
                    "Duplicate or directory entry in backup"
                }
                val declared = manifest.entries[entry.name] ?: error("Undeclared backup entry")
                readEntry(zip, directory, entry.name, declared)
                zip.closeEntry()
            }
            require(seen == manifest.entries.keys) { "Backup is missing required files" }
            val data = readData(directory, manifest)
            return StagedBackup(manifest, data, directory)
        }
    }
    private fun metadata(data: JournalSnapshot, photos: List<PhotoAsset>): LinkedHashMap<String, ByteArray> {
        val metadata = linkedMapOf(
            "journal.json" to
                json.encodeToString(
                    BackupJournal(
                        data.workouts,
                        data.items,
                        data.sets,
                        data.checkIns,
                        data.observations,
                        data.drafts,
                        data.healthStates,
                        data.outbox.filter {
                            it.operation ==
                                "DELETE"
                        }
                    )
                ).toByteArray(Charsets.UTF_8),
            "library.json" to
                json.encodeToString(
                    BackupLibrary(data.exercises, photos, data.routines, data.routineItems)
                ).toByteArray(Charsets.UTF_8),
            "preferences.json" to
                json.encodeToString(
                    data.preferences.copy(lastBackupAt = null)
                ).toByteArray(Charsets.UTF_8)
        )
        return metadata
    }

    private fun verifyPhoto(original: PhotoAsset, photoFile: (PhotoAsset) -> File) {
        val file = photoFile(original)
        require(file.isFile && file.length() == original.byteLength) {
            "A journal photo is missing or changed"
        }
        val hash = digest()
        file.inputStream().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <
                    0
                ) {
                    break
                }
                hash.update(buffer, 0, read)
            }
        }
        require(hash.digest().hex() == original.checksum) {
            "A journal photo failed its checksum"
        }
    }

    private fun readManifest(zip: ZipInputStream, availableBytes: Long): BackupManifest {
        require(zip.nextEntry?.name == "manifest.json") { "This is not an InShape backup" }
        val manifest = json.decodeFromString<BackupManifest>(
            readBounded(zip, 1024 * 1024).decodeToString(throwOnInvalidSequence = true)
        )
        require(manifest.formatVersion == 1) {
            "Unsupported backup version ${manifest.formatVersion}. Update InShape before restoring."
        }
        Instant.parse(manifest.createdAt)
        require(
            manifest.entries.size in 3..2003 && manifest.entries.keys.containsAll(metadataNames)
        ) {
            "Invalid backup entries"
        }
        manifest.entries.forEach { (name, entry) ->
            require(
                name in metadataNames ||
                    name.matches(Regex("photos/[a-f0-9-]{36}\\.(png|jpg|webp|heic|gif)"))
            ) {
                "Unsafe or unknown backup path"
            }
            require(
                entry.bytes in 1..if (name in
                    metadataNames
                ) {
                    MAX_JSON_BYTES
                } else {
                    MAX_PHOTO_BYTES
                }
            )
            require(entry.sha256.matches(Regex("[a-f0-9]{64}")))
        }
        val total = manifest.entries.values.sumOf { it.bytes }
        require(total <= MAX_TOTAL_BYTES) { "Backup exceeds the 1 GB limit" }
        require(availableBytes >= total + 10L * 1024 * 1024) {
            "Not enough free storage to stage this backup safely"
        }
        return manifest
    }

    private fun readEntry(zip: ZipInputStream, directory: File, name: String, declared: BackupEntry) {
        val stagingRoot = directory.canonicalFile.toPath()
        val destination = File(directory, name).canonicalFile.toPath()
        if (!destination.startsWith(stagingRoot) || destination == stagingRoot) {
            throw IllegalArgumentException("Unsafe backup path")
        }
        val file = destination.toFile()
        check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
        val hash = digest()
        var written = 0L
        file.outputStream().use { target ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val read = zip.read(buffer)
                if (read < 0) break
                written += read
                require(written <= declared.bytes) {
                    "Backup entry exceeds its declared size"
                }
                target.write(buffer, 0, read)
                hash.update(buffer, 0, read)
            }
            target.fd.sync()
        }
        require(written == declared.bytes && hash.digest().hex() == declared.sha256) {
            "Backup is incomplete or failed its checksum"
        }
    }

    private fun readData(directory: File, manifest: BackupManifest): JournalSnapshot {
        val journal = json.decodeFromString<BackupJournal>(
            File(
                directory,
                "journal.json"
            ).readBytes().decodeToString(throwOnInvalidSequence = true)
        )
        val library = json.decodeFromString<BackupLibrary>(
            File(
                directory,
                "library.json"
            ).readBytes().decodeToString(throwOnInvalidSequence = true)
        )
        val preferences = json.decodeFromString<Preferences>(
            File(
                directory,
                "preferences.json"
            ).readBytes().decodeToString(throwOnInvalidSequence = true)
        )
        require(
            journal.pendingDeletions.all {
                it.operation == "DELETE"
            }
        ) { "Backup contains executable sharing operations" }
        val data =
            JournalSnapshot(
                library.exercises,
                library.photos,
                library.routines,
                library.routineItems,
                journal.workouts,
                journal.items,
                journal.sets,
                journal.checkIns,
                journal.observations,
                preferences,
                journal.drafts,
                journal.healthStates,
                journal.pendingDeletions
            )
        validateBackupData(data)
        require(counts(data) == manifest.counts) { "Backup record counts do not match" }
        require(
            manifest.entries.keys == metadataNames + data.photos.map {
                it.path
            }.toSet()
        ) { "Backup photo references do not match" }
        data.photos.forEach { photo ->
            require(photo.path == "photos/${photo.id}.${extensionFor(photo.mediaType)}") {
                "Invalid photo reference"
            }
            require(
                manifest.entries[photo.path] == BackupEntry(photo.byteLength, photo.checksum)
            ) {
                "Photo metadata does not match its content"
            }
        }
        return data
    }
    private fun readBounded(input: InputStream, limit: Int): ByteArray {
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read <
                0
            ) {
                break
            }
            require(bytes.size() + read <= limit) { "Backup manifest is too large" }
            bytes.write(buffer, 0, read)
        }
        return bytes.toByteArray()
    }
}
