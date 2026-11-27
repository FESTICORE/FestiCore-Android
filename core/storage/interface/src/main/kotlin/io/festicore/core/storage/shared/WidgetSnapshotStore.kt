package io.festicore.core.storage.shared

/** Opaque payload; serialization and account interpretation belong to domain. */
data class WidgetSnapshotRecord(
    val revision: Long = 0,
    val generation: Long = 0,
    val payload: String? = null,
    val unavailable: Boolean = false,
)

interface WidgetSnapshotStore {
    suspend fun read(): WidgetSnapshotRecord
    suspend fun compareAndSet(
        expectedRevision: Long,
        payload: String?,
        invalidate: Boolean = false,
    ): Boolean
}
