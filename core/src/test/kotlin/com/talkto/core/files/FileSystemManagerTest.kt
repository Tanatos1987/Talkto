package com.talkto.core.files

import com.google.common.truth.Truth.assertThat
import com.talkto.core.error.TalktoError
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

class FileSystemManagerTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var root: Path
    private var now = 1_700_000_000_000L
    private lateinit var fsm: FileSystemManager

    @Before
    fun setUp() {
        root = tmp.newFolder("emulated0").toPath().toRealPath()
        listOf("Download", "DCIM/Camera", "Pictures", "Android/data/com.bank.app", "Documents").forEach {
            Files.createDirectories(root.resolve(it))
        }
        write("Download/report.pdf", 2_000)
        write("Download/IMG_0001.jpg", 5_000)
        write("Download/IMG_0002.JPG", 7_000)
        write("Download/song.mp3", 3_000)
        write("DCIM/Camera/IMG_0100.jpg", 9_000)
        write("Android/data/com.bank.app/secret.db", 100)
        fsm = FileSystemManager(PathGuard(listOf(root)), clock = { now })
    }

    private fun write(rel: String, size: Int): Path = root.resolve(rel).also {
        Files.createDirectories(it.parent)
        Files.write(it, ByteArray(size) { i -> (i % 251).toByte() })
    }

    private fun exists(rel: String) = Files.exists(root.resolve(rel))

    // ------------------------------------------------------------------ search

    @Test fun `search by glob is case-insensitive and recursive`() = runTest {
        val hits = fsm.search(SearchQuery(root = "~", namePattern = "img_*.jpg"))
        assertThat(hits.map { it.name }).containsExactly("IMG_0001.jpg", "IMG_0002.JPG", "IMG_0100.jpg")
    }

    @Test fun `search filters by extension and size`() = runTest {
        val hits = fsm.search(SearchQuery(root = "Download", extensions = listOf("jpg"), minSizeBytes = 6_000))
        assertThat(hits.map { it.name }).containsExactly("IMG_0002.JPG")
    }

    @Test fun `search never descends into app-private data`() = runTest {
        val hits = fsm.search(SearchQuery(root = "~", namePattern = "*.db"))
        assertThat(hits).isEmpty()
    }

    @Test fun `search honours limit`() = runTest {
        val hits = fsm.search(SearchQuery(root = "~", extensions = listOf("jpg"), limit = 2))
        assertThat(hits).hasSize(2)
    }

    // -------------------------------------------------------------- path guard

    @Test fun `path traversal outside the root is refused`() = runTest {
        val e = runCatching { fsm.list("Download/../../..") }.exceptionOrNull()
        assertThat(e).isInstanceOf(TalktoError.ProtectedPath::class.java)
    }

    @Test fun `symlink pointing outside the root is refused`() = runTest {
        val outside = tmp.newFolder("outside").toPath()
        Files.write(outside.resolve("x.txt"), byteArrayOf(1))
        Files.createSymbolicLink(root.resolve("Download/escape"), outside)
        val e = runCatching { fsm.list("Download/escape") }.exceptionOrNull()
        assertThat(e).isInstanceOf(TalktoError.ProtectedPath::class.java)
    }

    @Test fun `system prefixes are refused even as absolute paths`() {
        val guard = PathGuard(listOf(root))
        assertThrows(TalktoError.ProtectedPath::class.java) { guard.resolve("/system/bin/sh") }
        assertThrows(TalktoError.ProtectedPath::class.java) { guard.resolve("/data/data") }
    }

    @Test fun `standard folders cannot be deleted but their contents can`() = runTest {
        assertThat(runCatching { fsm.planDeletion(listOf("Download")) }.exceptionOrNull())
            .isInstanceOf(TalktoError.ProtectedPath::class.java)
        assertThat(fsm.planDeletion(listOf("Download/song.mp3")).fileCount).isEqualTo(1)
    }

    @Test fun `app-private data cannot be deleted`() = runTest {
        assertThat(runCatching { fsm.planDeletion(listOf("Android/data/com.bank.app/secret.db")) }.exceptionOrNull())
            .isInstanceOf(TalktoError.ProtectedPath::class.java)
    }

    // ---------------------------------------------------------- copy / move

    @Test fun `move into existing folder keeps the name`() = runTest {
        val r = fsm.move("Download/IMG_0001.jpg", "Pictures")
        assertThat(r.moves.single().to).endsWith("Pictures/IMG_0001.jpg")
        assertThat(exists("Download/IMG_0001.jpg")).isFalse()
        assertThat(exists("Pictures/IMG_0001.jpg")).isTrue()
    }

    @Test fun `move dry-run changes nothing`() = runTest {
        val r = fsm.move("Download/IMG_0001.jpg", "Pictures", dryRun = true)
        assertThat(r.dryRun).isTrue()
        assertThat(exists("Download/IMG_0001.jpg")).isTrue()
        assertThat(exists("Pictures/IMG_0001.jpg")).isFalse()
    }

    @Test fun `copy refuses to overwrite unless asked`() = runTest {
        fsm.copy("Download/report.pdf", "Documents")
        val e = runCatching { fsm.copy("Download/report.pdf", "Documents") }.exceptionOrNull()
        assertThat(e).isInstanceOf(TalktoError.AlreadyExists::class.java)
        fsm.copy("Download/report.pdf", "Documents", overwrite = true)
        assertThat(Files.size(root.resolve("Documents/report.pdf"))).isEqualTo(2_000)
    }

    @Test fun `copy of a folder is recursive`() = runTest {
        fsm.copy("DCIM/Camera", "Pictures")
        assertThat(exists("Pictures/Camera/IMG_0100.jpg")).isTrue()
        assertThat(exists("DCIM/Camera/IMG_0100.jpg")).isTrue()
    }

    @Test fun `cannot move a folder into itself`() = runTest {
        fsm.createDirectory("Download/box")
        val e = runCatching { fsm.move("Download/box", "Download/box/inner") }.exceptionOrNull()
        assertThat(e).isInstanceOf(TalktoError.InvalidInput::class.java)
    }

    @Test fun `rename rejects slashes`() = runTest {
        val e = runCatching { fsm.rename("Download/song.mp3", "../x.mp3") }.exceptionOrNull()
        assertThat(e).isInstanceOf(TalktoError.InvalidInput::class.java)
    }

    // --------------------------------------------------------------- deletion

    @Test fun `planDeletion is a pure dry run`() = runTest {
        val plan = fsm.planDeletion(listOf("Download/IMG_0001.jpg", "Download/song.mp3"))
        assertThat(plan.fileCount).isEqualTo(2)
        assertThat(plan.totalBytes).isEqualTo(8_000)
        assertThat(plan.permanent).isFalse()
        assertThat(exists("Download/IMG_0001.jpg")).isTrue()
        assertThat(exists("Download/song.mp3")).isTrue()
    }

    @Test fun `executeDeletion moves to trash and token is single-use`() = runTest {
        val plan = fsm.planDeletion(listOf("Download/song.mp3"))
        val result = fsm.executeDeletion(plan.token)
        assertThat(result.movedToTrash).isTrue()
        assertThat(exists("Download/song.mp3")).isFalse()
        val trashed = Files.walk(fsm.trashRoot).use { s -> s.filter { it.fileName.toString() == "song.mp3" }.count() }
        assertThat(trashed).isEqualTo(1)

        assertThat(runCatching { fsm.executeDeletion(plan.token) }.exceptionOrNull())
            .isInstanceOf(TalktoError.ConfirmationRequired::class.java)
    }

    @Test fun `permanent deletion removes data`() = runTest {
        val plan = fsm.planDeletion(listOf("Download/report.pdf"), permanent = true)
        val result = fsm.executeDeletion(plan.token)
        assertThat(result.movedToTrash).isFalse()
        assertThat(result.freedBytes).isEqualTo(2_000)
        assertThat(exists("Download/report.pdf")).isFalse()
        assertThat(Files.exists(fsm.trashRoot)).isFalse()
    }

    @Test fun `deletion refuses when files changed after the dry run`() = runTest {
        fsm.createDirectory("Download/tmp")
        write("Download/tmp/a.txt", 10)
        val plan = fsm.planDeletion(listOf("Download/tmp"))
        write("Download/tmp/b.txt", 10) // appeared after the user saw the plan
        assertThat(runCatching { fsm.executeDeletion(plan.token) }.exceptionOrNull())
            .isInstanceOf(TalktoError.ConfirmationRequired::class.java)
        assertThat(exists("Download/tmp/a.txt")).isTrue()
    }

    @Test fun `expired plans are refused`() = runTest {
        val plan = fsm.planDeletion(listOf("Download/song.mp3"))
        now += 10 * 60_000L
        assertThat(runCatching { fsm.executeDeletion(plan.token) }.exceptionOrNull())
            .isInstanceOf(TalktoError.ConfirmationRequired::class.java)
        assertThat(exists("Download/song.mp3")).isTrue()
    }

    @Test fun `nested targets are collapsed`() = runTest {
        fsm.createDirectory("Download/tmp")
        write("Download/tmp/a.txt", 10)
        val plan = fsm.planDeletion(listOf("Download/tmp", "Download/tmp/a.txt"))
        assertThat(plan.targets).hasSize(1)
        assertThat(plan.fileCount).isEqualTo(1)
    }

    @Test fun `trash is hidden from search`() = runTest {
        fsm.executeDeletion(fsm.planDeletion(listOf("Download/song.mp3")).token)
        assertThat(fsm.search(SearchQuery(namePattern = "song*"))).isEmpty()
    }

    // --------------------------------------------------------------- organize

    @Test fun `organize dry run plans without moving`() = runTest {
        val r = fsm.organize("Download", OrganizeStrategy.BY_TYPE, dryRun = true)
        assertThat(r.moves.map { it.to.substringAfter("Download/") })
            .containsExactly("Documents/report.pdf", "Images/IMG_0001.jpg", "Images/IMG_0002.JPG", "Audio/song.mp3")
        assertThat(exists("Download/report.pdf")).isTrue()
    }

    @Test fun `organize never overwrites and suffixes clashes`() = runTest {
        write("Download/Images/IMG_0001.jpg", 1)
        fsm.organize("Download", OrganizeStrategy.BY_TYPE, dryRun = false)
        assertThat(exists("Download/Images/IMG_0001.jpg")).isTrue()
        assertThat(exists("Download/Images/IMG_0001 (1).jpg")).isTrue()
        assertThat(Files.size(root.resolve("Download/Images/IMG_0001.jpg"))).isEqualTo(1)
    }

    @Test fun `io dispatcher is injectable`() = runTest {
        val manager = FileSystemManager(PathGuard(listOf(root)), ioDispatcher = StandardTestDispatcher(testScheduler))
        assertThat(manager.list("Download").map { it.name }).contains("report.pdf")
    }
}
