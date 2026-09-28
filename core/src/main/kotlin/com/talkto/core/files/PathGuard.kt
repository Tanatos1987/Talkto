package com.talkto.core.files

import com.talkto.core.error.TalktoError
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths

/**
 * The single gatekeeper every path passes through before the file engine touches it.
 *
 * Rules, in order:
 * 1. Paths are canonicalised (symlinks resolved on the longest existing prefix), so
 *    `/sdcard/Download/../../../system` or a symlink pointing at `/data` cannot escape.
 * 2. The canonical path must live under one of [allowedRoots].
 * 3. Paths under [systemPrefixes] are rejected even if a root was misconfigured to include them.
 * 4. For mutations, [protectedSubtrees] (relative to any root) and the "anchor" folders
 *    (the roots themselves plus [anchorDirectoryNames]) cannot be deleted, moved or renamed.
 *    Their *contents* stay fully manageable.
 */
class PathGuard(
    allowedRoots: List<Path>,
    private val protectedSubtrees: List<String> = DEFAULT_PROTECTED_SUBTREES,
    private val anchorDirectoryNames: Set<String> = DEFAULT_ANCHORS,
    private val systemPrefixes: List<Path> = DEFAULT_SYSTEM_PREFIXES.map { Paths.get(it) },
) {
    val roots: List<Path> = allowedRoots.map { canonical(it.toAbsolutePath()) }.also {
        require(it.isNotEmpty()) { "At least one allowed root is required" }
    }

    /** Primary root, used for relative paths such as `Download/report.pdf`. */
    val primaryRoot: Path get() = roots.first()

    fun resolve(raw: String): Path {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) throw TalktoError.InvalidInput("Empty path")
        if (trimmed.contains('\u0000')) throw TalktoError.InvalidInput("Path contains NUL byte")
        val expanded = when {
            trimmed == "~" || trimmed == "/sdcard" -> primaryRoot.toString()
            trimmed.startsWith("~/") -> primaryRoot.resolve(trimmed.removePrefix("~/")).toString()
            trimmed.startsWith("/sdcard/") -> primaryRoot.resolve(trimmed.removePrefix("/sdcard/")).toString()
            else -> trimmed
        }
        val p = Paths.get(expanded)
        val absolute = if (p.isAbsolute) p else primaryRoot.resolve(p)
        val canon = canonical(absolute.normalize())
        systemPrefixes.firstOrNull { canon.startsWith(it) }?.let {
            throw TalktoError.ProtectedPath(canon.toString(), "system directory ($it)")
        }
        if (roots.none { canon.startsWith(it) }) {
            throw TalktoError.ProtectedPath(canon.toString(), "outside the storage Talkto is allowed to manage")
        }
        return canon
    }

    /** Throws unless [path] may be deleted, moved or renamed. */
    fun requireMutable(path: Path) {
        val root = rootOf(path)
        if (path == root) throw TalktoError.ProtectedPath(path.toString(), "storage root")
        val rel = root.relativize(path)
        val relStr = rel.toString().replace('\\', '/')
        protectedSubtrees.firstOrNull { relStr == it || relStr.startsWith("$it/") }?.let {
            throw TalktoError.ProtectedPath(path.toString(), "app-private area '$it'")
        }
        if (rel.nameCount == 1 && rel.getName(0).toString() in anchorDirectoryNames) {
            throw TalktoError.ProtectedPath(path.toString(), "standard Android folder (its contents can be managed)")
        }
    }

    /** Throws unless files may be written *into* [dir]. */
    fun requireWritableDestination(dir: Path) {
        val rel = rootOf(dir).relativize(dir).toString().replace('\\', '/')
        protectedSubtrees.firstOrNull { rel == it || rel.startsWith("$it/") }?.let {
            throw TalktoError.ProtectedPath(dir.toString(), "app-private area '$it'")
        }
    }

    fun isHiddenFromSearch(path: Path): Boolean {
        val rel = runCatching { rootOf(path).relativize(path).toString().replace('\\', '/') }.getOrNull() ?: return true
        return protectedSubtrees.any { rel == it || rel.startsWith("$it/") }
    }

    fun rootOf(path: Path): Path = roots.filter { path.startsWith(it) }.maxByOrNull { it.nameCount }
        ?: throw TalktoError.ProtectedPath(path.toString(), "outside allowed roots")

    companion object {
        val DEFAULT_PROTECTED_SUBTREES = listOf("Android/data", "Android/obb", "Android/media")
        val DEFAULT_ANCHORS = setOf(
            "Android", "DCIM", "Download", "Documents", "Pictures", "Movies", "Music",
            "Alarms", "Notifications", "Podcasts", "Ringtones", "Recordings", "Audiobooks",
        )
        val DEFAULT_SYSTEM_PREFIXES = listOf(
            "/system", "/proc", "/sys", "/dev", "/data", "/vendor", "/apex", "/product", "/odm",
            "/cache", "/mnt/secure", "/metadata", "/efs",
        )

        /** Resolves symlinks on the longest existing prefix; the non-existing tail is appended as-is. */
        fun canonical(path: Path): Path {
            var existing: Path? = path
            val tail = ArrayDeque<String>()
            while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
                existing.fileName?.let { tail.addFirst(it.toString()) }
                existing = existing.parent
            }
            // toRealPath can fail on paths we may stat but not traverse (e.g. before All Files Access is granted).
            var result = existing?.let { runCatching { it.toRealPath() }.getOrDefault(it) } ?: path.root ?: path
            tail.forEach { result = result.resolve(it) }
            return result.normalize()
        }
    }
}
