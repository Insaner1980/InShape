import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "InShape"
include(":app")

abstract class WorkspaceBuildLock : BuildService<WorkspaceBuildLock.Parameters>, AutoCloseable {
    interface Parameters : BuildServiceParameters {
        val lockFile: RegularFileProperty
    }

    private val channel: FileChannel

    init {
        val path = parameters.lockFile.get().asFile.toPath()
        Files.createDirectories(path.parent)
        channel = FileChannel.open(path, CREATE, WRITE)
        try {
            channel.lock()
        } catch (error: Exception) {
            channel.close()
            throw error
        }
    }

    override fun close() = channel.close()
}

// CLI builds and IDE imports share this checkout's generated sources and caches.
val workspaceLockFile = layout.settingsDirectory.file(".gradle/inshape-build.lock")
gradle.sharedServices.registerIfAbsent("workspaceBuildLock", WorkspaceBuildLock::class.java) {
    parameters.lockFile.set(workspaceLockFile)
}.get()
