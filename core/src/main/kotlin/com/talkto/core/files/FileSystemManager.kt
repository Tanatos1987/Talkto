package com.talkto.core.files

import com.talkto.core.error.ErrorMapper
import com.talkto.core.error.TalktoError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.FileSystems
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.EnumSet
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * File Operations Engine. Pure `java.nio`, so the same code runs on Android (API 26+) and in JVM tests.
 *
 * Safety model:
 * - every path goes through [PathGuard] (canonicalisation, root jail, protected folders);
 * - deletion is two-phase: [planDeletion] is a dry run that returns a single-use token,
 *   [executeDeletion] only accepts that token, and refuses if the files changed in between;
 * - by default deleted items go to `.talkto_trash` on the primary root, not into the void;
 * - move/copy/organize accept `dryRun = true` and then only report what they *would* do.
 */
class FileSystemManager(
    private val guard: PathGuard,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
    private val planTtlMs: Long = 5 * 60_000L,
) {
    private data class StoredPlan(val plan: DeletionPlan, val paths: List<Path>, val fingerprint: Fingerprint)
    private data class Fingerprint(val files: Int, val dirs: Int, val bytes: Long)

    private val plans = ConcurrentHashMap<String, StoredPlan>()

    val trashRoot: Path get() = guard.primaryRoot.resolve(TRASH_DIR)

    // ---------------------------------------------------------------- queries

    suspend fun list(dir: String): List<FileEntry> = onIo {
        val path = guard.resolve(dir)
        if (!Files.isDirectory(path)) throw TalktoError.NotFound(path.toString())
        Files.newDirectoryStream(path).use { stream ->
            stream.filterNot { isTrash(it) || guard.isHiddenFromSearch(it) }
                .map { it.toEntry() }
                .sortedWith(compareByDescending<FileEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
        }
    }

    suspend fun search(query: SearchQuery): List<FileEntry> = onIo {
        require(query.limit in 1..5_000) { "limit must be in 1..5000" }
        require(query.maxDepth in 1..64) { "maxDepth must be in 1..64" }
        val start = guard.resolve(query.root)
        if (!Files.isDirectory(start)) throw TalktoError.NotFound(start.toString())

        val matcher = query.namePattern?.takeIf { it.isNotBlank() }?.let {
            FileSystems.getDefault().getPathMatcher("glob:" + it.lowercase())
        }
        val exts = query.extensions.map { it.lowercase().removePrefix(".") }.toSet()
        val out = ArrayList<FileEntry>()
        val ctx = currentCoroutineContext()

        Files.walkFileTree(start, EnumSet.noneOf(java.nio.file.FileVisitOption::class.java), query.maxDepth,
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    ctx.ensureActive()
                    if (dir != start && (isTrash(dir) || guard.isHiddenFromSearch(dir))) return FileVisitResult.SKIP_SUBTREE
                    if (dir != start && query.includeDirectories && matches(dir, attrs, matcher, exts, query)) {
                        out += dir.toEntry(attrs)
                    }
                    return if (out.size >= query.limit) FileVisitResult.TERMINATE else FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (attrs.isRegularFile && matches(file, attrs, matcher, exts, query)) out += file.toEntry(attrs)
                    return if (out.size >= query.limit) FileVisitResult.TERMINATE else FileVisitResult.CONTINUE
                }

                // Unreadable folders are skipped instead of aborting the whole search.
                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE
            })
        out
    }

    // ------------------------------------------------------------- mutations

    suspend fun createDirectory(path: String): FileEntry = onIo {
        val target = guard.resolve(path)
        guard.requireWritableDestination(target)
        Files.createDirectories(target).toEntry()
    }

    /**
     * Copies [source] into [destination]. If [destination] is an existing directory the source keeps
     * its name inside it; otherwise [destination] is the new full path.
     */
    suspend fun copy(source: String, destination: String, overwrite: Boolean = false, dryRun: Boolean = false): OperationReport =
        transfer("copy", source, destination, overwrite, dryRun, move = false)

    suspend fun move(source: String, destination: String, overwrite: Boolean = false, dryRun: Boolean = false): OperationReport =
        transfer("move", source, destination, overwrite, dryRun, move = true)

    suspend fun rename(source: String, newName: String, dryRun: Boolean = false): OperationReport = onIo {
        if (newName.isBlank() || newName.contains('/') || newName.contains('\\') || newName == "." || newName == "..") {
            throw TalktoError.InvalidInput("Invalid file name: '$newName'")
        }
        val src = guard.resolve(source)
        if (!Files.exists(src, LinkOption.NOFOLLOW_LINKS)) throw TalktoError.NotFound(src.toString())
        guard.requireMutable(src)
        val dst = src.resolveSibling(newName)
        if (Files.exists(dst, LinkOption.NOFOLLOW_LINKS)) throw TalktoError.AlreadyExists(dst.toString())
        if (!dryRun) Files.move(src, dst)
        OperationReport("rename", dryRun, listOf(PlannedMove(src.toString(), dst.toString())), totalBytes = sizeOf(src))
    }

    /** Phase 1 of deletion. Touches nothing; returns what would be removed plus a single-use token. */
    suspend fun planDeletion(targets: List<String>, permanent: Boolean = false): DeletionPlan = onIo {
        if (targets.isEmpty()) throw TalktoError.InvalidInput("Nothing to delete")
        val paths = targets.map { raw ->
            guard.resolve(raw).also { p ->
                if (!Files.exists(p, LinkOption.NOFOLLOW_LINKS)) throw TalktoError.NotFound(p.toString())
                guard.requireMutable(p)
            }
        }.distinct()
        // Nested targets would be counted twice and deleted in an unsafe order.
        val collapsed = paths.filter { p -> paths.none { other -> other != p && p.startsWith(other) } }

        val sample = ArrayList<String>()
        val fp = fingerprint(collapsed, sample)
        val plan = DeletionPlan(
            token = UUID.randomUUID().toString(),
            targets = collapsed.map { it.toString() },
            fileCount = fp.files,
            directoryCount = fp.dirs,
            totalBytes = fp.bytes,
            sample = sample,
            permanent = permanent,
            expiresAtEpochMs = clock() + planTtlMs,
        )
        purgeExpiredPlans()
        plans[plan.token] = StoredPlan(plan, collapsed, fp)
        plan
    }

    /** Phase 2 of deletion. Only an unexpired, unchanged plan from [planDeletion] is executed. */
    suspend fun executeDeletion(token: String): DeletionResult = onIo {
        val stored = plans.remove(token)
            ?: throw TalktoError.ConfirmationRequired("Unknown or already used deletion token. Run a dry-run first.")
        if (clock() > stored.plan.expiresAtEpochMs) {
            throw TalktoError.ConfirmationRequired("Deletion plan expired. Run a new dry-run.")
        }
        stored.paths.forEach(guard::requireMutable)
        if (fingerprint(stored.paths, null) != stored.fingerprint) {
            throw TalktoError.ConfirmationRequired("Files changed after the dry-run. Review a new plan.")
        }

        if (stored.plan.permanent) {
            stored.paths.forEach { deleteRecursively(it) }
            DeletionResult(stored.plan.fileCount + stored.plan.directoryCount, stored.plan.totalBytes, movedToTrash = false)
        } else {
            val bucket = trashRoot.resolve(TRASH_STAMP.format(Instant.ofEpochMilli(clock())) + "-" + token.take(8))
            stored.paths.forEach { p ->
                val rel = guard.rootOf(p).relativize(p)
                val dst = bucket.resolve(rel.toString())
                Files.createDirectories(dst.parent)
                moveAcrossStores(p, dst)
            }
            DeletionResult(stored.plan.fileCount + stored.plan.directoryCount, 0, movedToTrash = true, trashLocation = bucket.toString())
        }
    }

    /** The pending plan behind [token], for showing it in a confirmation dialog. Null if unknown or expired. */
    fun peekPlan(token: String): DeletionPlan? = plans[token]?.plan?.takeIf { clock() <= it.expiresAtEpochMs }

    fun discardPlan(token: String) {
        plans.remove(token)
    }

    /** Permanently removes trash buckets older than [olderThanMs]. */
    suspend fun emptyTrash(olderThanMs: Long = 0): Int = onIo {
        if (!Files.isDirectory(trashRoot)) return@onIo 0
        val cutoff = clock() - olderThanMs
        var removed = 0
        Files.newDirectoryStream(trashRoot).use { buckets ->
            buckets.forEach { b ->
                if (Files.getLastModifiedTime(b).toMillis() <= cutoff) {
                    deleteRecursively(b); removed++
                }
            }
        }
        removed
    }

    /**
     * "Reorder" a folder: sorts its direct files into sub-folders by type, month or extension.
     * Name clashes get a ` (n)` suffix; nothing is ever overwritten.
     */
    suspend fun organize(dir: String, strategy: OrganizeStrategy, dryRun: Boolean = true): OperationReport = onIo {
        val root = guard.resolve(dir)
        if (!Files.isDirectory(root)) throw TalktoError.NotFound(root.toString())
        guard.requireWritableDestination(root)
        val moves = ArrayList<PlannedMove>()
        val skipped = ArrayList<String>()
        val reserved = HashSet<Path>()
        var bytes = 0L
        Files.newDirectoryStream(root).use { stream ->
            for (file in stream) {
                currentCoroutineContext().ensureActive()
                val attrs = runCatching { Files.readAttributes(file, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS) }
                    .getOrNull()
                if (attrs == null || !attrs.isRegularFile || file.fileName.toString().startsWith(".")) {
                    skipped += file.toString(); continue
                }
                val bucket = when (strategy) {
                    OrganizeStrategy.BY_TYPE -> FileTypes.categoryOf(file.fileName.toString())
                    OrganizeStrategy.BY_EXTENSION -> file.extension().ifEmpty { "no-extension" }.uppercase()
                    OrganizeStrategy.BY_MONTH -> MONTH.format(Instant.ofEpochMilli(attrs.lastModifiedTime().toMillis()))
                }
                val dst = uniqueTarget(root.resolve(bucket).resolve(file.fileName.toString()), reserved)
                reserved.add(dst)
                moves += PlannedMove(file.toString(), dst.toString())
                bytes += attrs.size()
            }
        }
        if (!dryRun) {
            moves.forEach { m ->
                val dst = java.nio.file.Paths.get(m.to)
                Files.createDirectories(dst.parent)
                Files.move(java.nio.file.Paths.get(m.from), dst)
            }
        }
        OperationReport("organize:${strategy.name.lowercase()}", dryRun, moves, totalBytes = bytes, skipped = skipped)
    }

    // ------------------------------------------------------------ analysis

    /** What takes space under [dir]: totals per category, the largest files and the ZnaiKo trash size. */
    suspend fun storageReport(dir: String = "~", maxDepth: Int = 16, top: Int = 10): StorageReport = onIo {
        val start = guard.resolve(dir)
        if (!Files.isDirectory(start)) throw TalktoError.NotFound(start.toString())
        val perCategory = HashMap<String, LongArray>() // [files, bytes]
        val largest = java.util.PriorityQueue<Pair<Path, BasicFileAttributes>>(compareBy { it.second.size() })
        var files = 0
        var bytes = 0L
        walkReadable(start, maxDepth) { file, attrs ->
            files++; bytes += attrs.size()
            val c = perCategory.getOrPut(FileTypes.categoryOf(file.fileName.toString())) { LongArray(2) }
            c[0]++; c[1] += attrs.size()
            largest += file to attrs
            if (largest.size > top) largest.poll()
        }
        StorageReport(
            root = start.toString(),
            totalFiles = files,
            totalBytes = bytes,
            categories = perCategory.map { (k, v) -> CategoryUsage(k, v[0].toInt(), v[1]) }.sortedByDescending { it.bytes },
            largest = largest.sortedByDescending { it.second.size() }.map { (p, a) -> p.toEntry(a) },
            trashBytes = if (Files.isDirectory(trashRoot)) sizeOf(trashRoot) else 0,
        )
    }

    /**
     * Byte-identical files under [dir]. Candidates are grouped by size, then by a hash of the first 64 KB,
     * and only then fully hashed, so a phone full of photos is not read end to end. Within a group the
     * oldest file comes first: it is the one to keep.
     */
    suspend fun findDuplicates(
        dir: String = "~",
        minSizeBytes: Long = 16 * 1024,
        maxFiles: Int = 50_000,
        maxGroups: Int = 200,
    ): DuplicateReport = onIo {
        val start = guard.resolve(dir)
        if (!Files.isDirectory(start)) throw TalktoError.NotFound(start.toString())
        val bySize = HashMap<Long, MutableList<Pair<Path, Long>>>()
        var scanned = 0
        var truncated = false
        walkReadable(start, 32) { file, attrs ->
            if (attrs.size() < minSizeBytes) return@walkReadable
            if (scanned >= maxFiles) {
                truncated = true; return@walkReadable
            }
            scanned++
            bySize.getOrPut(attrs.size()) { ArrayList() } += file to attrs.lastModifiedTime().toMillis()
        }
        val groups = ArrayList<DuplicateGroup>()
        for ((size, candidates) in bySize.entries.sortedByDescending { it.key }) {
            if (candidates.size < 2) continue
            currentCoroutineContext().ensureActive()
            val byHead = candidates.groupBy { runCatching { hash(it.first, HEAD_BYTES) }.getOrNull() }
            for ((head, sameHead) in byHead) {
                if (head == null || sameHead.size < 2) continue
                val byFull = if (size <= HEAD_BYTES) mapOf(head to sameHead) else sameHead.groupBy { runCatching { hash(it.first, Long.MAX_VALUE) }.getOrNull() }
                for ((full, same) in byFull) {
                    if (full == null || same.size < 2) continue
                    groups += DuplicateGroup(size, same.sortedBy { it.second }.map { it.first.toString() })
                    if (groups.size >= maxGroups) break
                }
                if (groups.size >= maxGroups) break
            }
            if (groups.size >= maxGroups) {
                truncated = true; break
            }
        }
        DuplicateReport(groups, groups.sumOf { it.wastedBytes }, scanned, truncated)
    }

    /** Folders under [dir] with nothing inside (anchors and protected areas excluded). */
    suspend fun findEmptyDirectories(dir: String = "~", limit: Int = 200): List<String> = onIo {
        val start = guard.resolve(dir)
        if (!Files.isDirectory(start)) throw TalktoError.NotFound(start.toString())
        val out = ArrayList<String>()
        Files.walkFileTree(start, EnumSet.noneOf(java.nio.file.FileVisitOption::class.java), 32, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(d: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (d != start && (isTrash(d) || guard.isHiddenFromSearch(d) || d.fileName.toString().startsWith("."))) return FileVisitResult.SKIP_SUBTREE
                val empty = runCatching { Files.newDirectoryStream(d).use { !it.iterator().hasNext() } }.getOrDefault(false)
                if (d != start && empty && runCatching { guard.requireMutable(d) }.isSuccess) out += d.toString()
                return if (out.size >= limit) FileVisitResult.TERMINATE else FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE
        })
        out
    }

    private inline fun walkReadable(start: Path, maxDepth: Int, crossinline onFile: (Path, BasicFileAttributes) -> Unit) {
        Files.walkFileTree(start, EnumSet.noneOf(java.nio.file.FileVisitOption::class.java), maxDepth, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult =
                if (dir != start && (isTrash(dir) || guard.isHiddenFromSearch(dir))) FileVisitResult.SKIP_SUBTREE else FileVisitResult.CONTINUE

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (attrs.isRegularFile) onFile(file, attrs)
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE
        })
    }

    private fun hash(file: Path, limit: Long): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        Files.newInputStream(file).use { input ->
            val buf = ByteArray(64 * 1024)
            var left = limit
            while (left > 0) {
                val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                if (n < 0) break
                md.update(buf, 0, n)
                left -= n
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    // --------------------------------------------------------------- helpers

    private suspend fun transfer(
        op: String, source: String, destination: String, overwrite: Boolean, dryRun: Boolean, move: Boolean,
    ): OperationReport = onIo {
        val src = guard.resolve(source)
        if (!Files.exists(src, LinkOption.NOFOLLOW_LINKS)) throw TalktoError.NotFound(src.toString())
        if (move) guard.requireMutable(src)

        val rawDst = guard.resolve(destination)
        val dst = if (Files.isDirectory(rawDst)) rawDst.resolve(src.fileName.toString()) else rawDst
        guard.requireWritableDestination(dst.parent ?: dst)
        if (dst == src) throw TalktoError.InvalidInput("Source and destination are the same")
        if (Files.isDirectory(src) && dst.startsWith(src)) throw TalktoError.InvalidInput("Cannot $op a folder into itself")
        if (Files.exists(dst, LinkOption.NOFOLLOW_LINKS)) {
            if (!overwrite) throw TalktoError.AlreadyExists(dst.toString())
            guard.requireMutable(dst)
        }
        val size = sizeOf(src)
        if (!dryRun) {
            Files.createDirectories(dst.parent)
            if (overwrite && Files.exists(dst, LinkOption.NOFOLLOW_LINKS)) deleteRecursively(dst)
            if (move) moveAcrossStores(src, dst) else copyRecursively(src, dst)
        }
        OperationReport(op, dryRun, listOf(PlannedMove(src.toString(), dst.toString())), totalBytes = size)
    }

    /** Same-volume moves are a rename; cross-volume (SD card) moves fall back to copy + delete. */
    private fun moveAcrossStores(src: Path, dst: Path) {
        try {
            Files.move(src, dst, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            try {
                Files.move(src, dst)
            } catch (_: DirectoryNotEmptyException) {
                copyRecursively(src, dst)
                deleteRecursively(src)
            }
        }
    }

    private fun copyRecursively(src: Path, dst: Path) {
        if (!Files.isDirectory(src, LinkOption.NOFOLLOW_LINKS)) {
            Files.copy(src, dst, StandardCopyOption.COPY_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS)
            return
        }
        Files.walkFileTree(src, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.createDirectories(dst.resolve(src.relativize(dir).toString()))
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.copy(file, dst.resolve(src.relativize(file).toString()), StandardCopyOption.COPY_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS)
                return FileVisitResult.CONTINUE
            }
        })
    }

    private fun deleteRecursively(path: Path) {
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            Files.deleteIfExists(path); return
        }
        Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.delete(file); return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                if (exc != null) throw exc
                Files.delete(dir); return FileVisitResult.CONTINUE
            }
        })
    }

    private fun fingerprint(paths: List<Path>, sample: MutableList<String>?): Fingerprint {
        var files = 0; var dirs = 0; var bytes = 0L
        paths.forEach { p ->
            Files.walkFileTree(p, object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    dirs++; return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    files++; bytes += attrs.size()
                    if (sample != null && sample.size < SAMPLE_SIZE) sample += file.toString()
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = throw ErrorMapper.map(exc)
            })
        }
        return Fingerprint(files, dirs, bytes)
    }

    private fun sizeOf(p: Path): Long {
        if (!Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) return Files.size(p)
        var total = 0L
        Files.walkFileTree(p, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                total += attrs.size(); return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE
        })
        return total
    }

    private fun uniqueTarget(candidate: Path, reserved: Set<Path>): Path {
        if (!Files.exists(candidate) && candidate !in reserved) return candidate
        val name = candidate.fileName.toString()
        val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
        val base = name.substring(0, dot)
        val ext = name.substring(dot)
        var n = 1
        while (true) {
            val next = candidate.resolveSibling("$base ($n)$ext")
            if (!Files.exists(next) && next !in reserved) return next
            n++
        }
    }

    private fun isTrash(p: Path) = p.startsWith(trashRoot)

    private fun purgeExpiredPlans() {
        val now = clock()
        plans.entries.removeIf { it.value.plan.expiresAtEpochMs < now }
    }

    private fun matches(p: Path, a: BasicFileAttributes, m: java.nio.file.PathMatcher?, exts: Set<String>, q: SearchQuery): Boolean {
        val name = p.fileName?.toString() ?: return false
        if (m != null && !m.matches(java.nio.file.Paths.get(name.lowercase()))) return false
        if (exts.isNotEmpty() && (a.isDirectory || p.extension() !in exts)) return false
        if (!a.isDirectory) {
            q.minSizeBytes?.let { if (a.size() < it) return false }
            q.maxSizeBytes?.let { if (a.size() > it) return false }
        }
        val mtime = a.lastModifiedTime().toMillis()
        q.modifiedAfterEpochMs?.let { if (mtime < it) return false }
        q.modifiedBeforeEpochMs?.let { if (mtime > it) return false }
        return true
    }

    private fun Path.extension(): String = fileName.toString().substringAfterLast('.', "").lowercase()

    private fun Path.toEntry(attrs: BasicFileAttributes = Files.readAttributes(this, BasicFileAttributes::class.java)) = FileEntry(
        path = toString(),
        name = fileName?.toString() ?: toString(),
        isDirectory = attrs.isDirectory,
        sizeBytes = if (attrs.isDirectory) 0 else attrs.size(),
        modifiedEpochMs = attrs.lastModifiedTime().toMillis(),
    )

    /** Runs [block] on the IO dispatcher and converts every failure into a [TalktoError]. */
    private suspend inline fun <T> onIo(crossinline block: suspend () -> T): T = withContext(ioDispatcher) {
        try {
            block()
        } catch (t: Throwable) {
            throw ErrorMapper.map(t)
        }
    }

    companion object {
        const val TRASH_DIR = ".talkto_trash"
        private const val HEAD_BYTES = 64L * 1024
        private const val SAMPLE_SIZE = 20
        private val TRASH_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault())
        private val MONTH = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneId.systemDefault())
    }
}

internal object FileTypes {
    private val map = mapOf(
        "Images" to setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "svg", "raw", "dng"),
        "Videos" to setOf("mp4", "mkv", "mov", "avi", "webm", "3gp", "m4v"),
        "Audio" to setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "amr"),
        "Documents" to setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "txt", "rtf", "csv", "md", "epub"),
        "Archives" to setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz"),
        "APKs" to setOf("apk", "apks", "xapk"),
    )

    fun categoryOf(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return map.entries.firstOrNull { ext in it.value }?.key ?: "Other"
    }
}
