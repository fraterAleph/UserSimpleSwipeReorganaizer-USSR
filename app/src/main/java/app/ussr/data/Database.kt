package app.ussr.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.coroutines.flow.Flow

/**
 * Cached analysis for one media item. Keyed by the MediaStore id plus the file's own
 * modification time, so an edited file is re-analysed instead of reusing a stale verdict.
 */
@Entity(tableName = "analysis")
data class AnalysisEntity(
    @PrimaryKey val mediaId: Long,
    val dateModifiedMs: Long,
    val dHash: Long,
    val laplacianVariance: Double,
    val meanLuma: Double,
    /** Null until the ML pass reaches this item. */
    val ocrText: String?,
    val textBlockCount: Int?,
    val labels: String?,
    val faceCount: Int?,
    val analysedAtMs: Long,
)

/**
 * Declared outside the entity on purpose: Room inspects every property of an @Entity class,
 * and a computed one inside it is an easy way to confuse the schema generator.
 */
val AnalysisEntity.hasContentSignals: Boolean get() = ocrText != null

enum class DecisionKind { Delete, Keep, Favorite, Skip }

/** Enums are stored by name so the DAO queries can read as plain SQL. */
class DecisionKindConverter {
    @TypeConverter
    fun toName(kind: DecisionKind): String = kind.name

    @TypeConverter
    fun fromName(name: String): DecisionKind = DecisionKind.valueOf(name)
}

/**
 * A swipe. Deletions stay here as [DecisionKind.Delete] until the review screen sends them
 * to the system trash — nothing leaves the library on a swipe alone.
 */
@Entity(tableName = "decisions")
data class DecisionEntity(
    @PrimaryKey val mediaId: Long,
    val kind: DecisionKind,
    val decidedAtMs: Long,
    val sizeBytes: Long,
    /** Set once the item has actually been handed to MediaStore's trash. */
    val committedAtMs: Long? = null,
)

@Dao
interface AnalysisDao {
    @Query("SELECT * FROM analysis")
    suspend fun all(): List<AnalysisEntity>

    @Query("SELECT * FROM analysis WHERE mediaId = :id")
    suspend fun byId(id: Long): AnalysisEntity?

    @Query("SELECT COUNT(*) FROM analysis WHERE ocrText IS NOT NULL")
    fun contentAnalysedCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rows: List<AnalysisEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: AnalysisEntity)

    @Query("DELETE FROM analysis WHERE mediaId IN (:ids)")
    suspend fun delete(ids: List<Long>)
}

@Dao
interface DecisionDao {
    @Query("SELECT * FROM decisions WHERE committedAtMs IS NULL AND kind = 'Delete'")
    fun pendingDeletions(): Flow<List<DecisionEntity>>

    @Query("SELECT * FROM decisions WHERE committedAtMs IS NULL AND kind = 'Delete'")
    suspend fun pendingDeletionsNow(): List<DecisionEntity>

    @Query("SELECT * FROM decisions WHERE committedAtMs IS NULL AND kind = 'Favorite'")
    suspend fun pendingFavoritesNow(): List<DecisionEntity>

    @Query("SELECT mediaId FROM decisions")
    suspend fun decidedIds(): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: DecisionEntity)

    @Query("DELETE FROM decisions WHERE mediaId = :id")
    suspend fun forget(id: Long)

    @Query("UPDATE decisions SET committedAtMs = :atMs WHERE mediaId IN (:ids)")
    suspend fun markCommitted(ids: List<Long>, atMs: Long)

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM decisions WHERE committedAtMs IS NOT NULL")
    fun reclaimedBytes(): Flow<Long>
}

@Database(
    entities = [AnalysisEntity::class, DecisionEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(DecisionKindConverter::class)
abstract class UssrDatabase : RoomDatabase() {
    abstract fun analysisDao(): AnalysisDao
    abstract fun decisionDao(): DecisionDao
}
