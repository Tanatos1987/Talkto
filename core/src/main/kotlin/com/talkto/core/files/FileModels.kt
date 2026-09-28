package com.talkto.core.files

import kotlinx.serialization.Serializable

@Serializable
data class FileEntry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val modifiedEpochMs: Long,
)

@Serializable
data class SearchQuery(
    /** Directory to search in; relative paths resolve against the primary storage root. */
    val root: String = "~",
    /** Glob on the file name, e.g. `*.pdf` or `IMG_2024*`. Case-insensitive. */
    val namePattern: String? = null,
    /** Extensions without dot, e.g. `["jpg","png"]`. */
    val extensions: List<String> = emptyList(),
    val minSizeBytes: Long? = null,
    val maxSizeBytes: Long? = null,
    val modifiedAfterEpochMs: Long? = null,
    val modifiedBeforeEpochMs: Long? = null,
    val includeDirectories: Boolean = false,
    val maxDepth: Int = 8,
    val limit: Int = 200,
)

@Serializable
data class PlannedMove(val from: String, val to: String)

@Serializable
data class OperationReport(
    val operation: String,
    val dryRun: Boolean,
    val moves: List<PlannedMove> = emptyList(),
    val affectedCount: Int = moves.size,
    val totalBytes: Long = 0,
    val skipped: List<String> = emptyList(),
)

@Serializable
data class DeletionPlan(
    /** Opaque token; the only way to execute this exact plan. */
    val token: String,
    val targets: List<String>,
    val fileCount: Int,
    val directoryCount: Int,
    val totalBytes: Long,
    /** First files that would disappear, for the confirmation dialog. */
    val sample: List<String>,
    /** When false the items go to the Talkto trash and can be recovered. */
    val permanent: Boolean,
    val expiresAtEpochMs: Long,
)

@Serializable
data class DeletionResult(
    val deletedCount: Int,
    val freedBytes: Long,
    val movedToTrash: Boolean,
    val trashLocation: String? = null,
)

@Serializable
enum class OrganizeStrategy { BY_TYPE, BY_MONTH, BY_EXTENSION }

@Serializable
data class CategoryUsage(val category: String, val files: Int, val bytes: Long)

@Serializable
data class StorageReport(
    val root: String,
    val totalFiles: Int,
    val totalBytes: Long,
    val categories: List<CategoryUsage>,
    val largest: List<FileEntry>,
    val trashBytes: Long,
)

@Serializable
data class DuplicateGroup(val sizeBytes: Long, val paths: List<String>) {
    /** Space freed by keeping only the first (oldest) copy. */
    val wastedBytes: Long get() = sizeBytes * (paths.size - 1)
}

@Serializable
data class DuplicateReport(
    val groups: List<DuplicateGroup>,
    val wastedBytes: Long,
    val scannedFiles: Int,
    val truncated: Boolean,
)
