package app.ussr.data

import android.content.Context
import app.ussr.analysis.ContentAnalyzer
import app.ussr.analysis.ThumbnailAnalyzer
import app.ussr.core.analysis.Grouping
import app.ussr.core.analysis.MediaGroup
import app.ussr.core.model.ContentSignals
import app.ussr.core.model.MediaItem
import app.ussr.core.model.VisualSignals
import app.ussr.core.queue.Deck
import app.ussr.core.queue.QueueBuilder
import app.ussr.core.scoring.JunkScorer
import app.ussr.core.scoring.Verdict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Owns the pipeline: scan, analyse, score, deal. Everything above it deals in decks and
 * cards; everything below it deals in cursors and bitmaps.
 */
class TriageRepository(
    private val context: Context,
    private val database: UssrDatabase,
    private val scanner: MediaStoreScanner,
    private val thumbnails: ThumbnailAnalyzer,
) {

    val pendingDeletions: Flow<List<DecisionEntity>> = database.decisionDao().pendingDeletions()
    val reclaimedBytes: Flow<Long> = database.decisionDao().reclaimedBytes()
    val contentAnalysed: Flow<Int> = database.analysisDao().contentAnalysedCount()

    /**
     * The cheap sweep: metadata plus one thumbnail per item. Results are cached, so a second
     * run only touches files that are new or were edited since.
     */
    suspend fun sweep(onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val items = scanner.scan()
            val cached = database.analysisDao().all().associateBy { it.mediaId }
            val stale = items.filter { item ->
                val row = cached[item.id]
                row == null || row.dateModifiedMs != item.dateModifiedMs
            }

            val batch = ArrayList<AnalysisEntity>(BATCH_SIZE)
            stale.forEachIndexed { index, item ->
                val signals = thumbnails.analyse(scanner.contentUri(item))
                if (signals != null) {
                    batch += AnalysisEntity(
                        mediaId = item.id,
                        dateModifiedMs = item.dateModifiedMs,
                        dHash = signals.dHash,
                        laplacianVariance = signals.laplacianVariance,
                        meanLuma = signals.meanLuma,
                        ocrText = null,
                        textBlockCount = null,
                        labels = null,
                        faceCount = null,
                        analysedAtMs = System.currentTimeMillis(),
                    )
                }
                if (batch.size >= BATCH_SIZE) {
                    database.analysisDao().upsert(batch.toList())
                    batch.clear()
                }
                onProgress(index + 1, stale.size)
            }
            if (batch.isNotEmpty()) database.analysisDao().upsert(batch.toList())

            // Rows for files that have since left the library would otherwise linger forever.
            val liveIds = items.mapTo(HashSet()) { it.id }
            val orphans = cached.keys.filterNot { it in liveIds }
            if (orphans.isNotEmpty()) database.analysisDao().delete(orphans)

            items
        }

    /**
     * The expensive sweep, meant for a background worker: OCR and labels for items that do
     * not have them yet.
     */
    suspend fun analyseContent(
        items: List<MediaItem>,
        limit: Int = Int.MAX_VALUE,
        shouldStop: () -> Boolean = { false },
    ): Int = withContext(Dispatchers.Default) {
        val rows = database.analysisDao().all().associateBy { it.mediaId }
        val pending = items
            .filterNot { it.isVideo }
            .filter { rows[it.id]?.hasContentSignals != true }
            .take(limit)
        if (pending.isEmpty()) return@withContext 0

        var done = 0
        ContentAnalyzer(context).use { analyzer ->
            for (item in pending) {
                if (shouldStop()) break
                val signals = analyzer.analyse(scanner.contentUri(item)) ?: continue
                val existing = rows[item.id] ?: continue
                database.analysisDao().upsert(
                    existing.copy(
                        ocrText = signals.text,
                        textBlockCount = signals.textBlockCount,
                        labels = signals.labels.joinToString(LABEL_SEPARATOR),
                        faceCount = signals.faceCount,
                        analysedAtMs = System.currentTimeMillis(),
                    ),
                )
                done++
            }
        }
        done
    }

    /**
     * Score everything currently known and build the decks. Items already swiped in an
     * earlier session are left out.
     */
    suspend fun decks(items: List<MediaItem>): List<Deck> = withContext(Dispatchers.Default) {
        val rows = database.analysisDao().all().associateBy { it.mediaId }
        val decided = database.decisionDao().decidedIds().toHashSet()
        val fresh = items.filterNot { it.id in decided }

        val visual = rows.mapValues { (_, row) ->
            VisualSignals(row.dHash, row.laplacianVariance, row.meanLuma)
        }
        val groups = buildGroupIndex(fresh, visual)
        val scorer = JunkScorer()

        val verdicts = HashMap<Long, Verdict>(fresh.size)
        for (item in fresh) {
            val row = rows[item.id]
            verdicts[item.id] = scorer.score(
                item = item,
                visual = visual[item.id],
                content = row?.toContentSignals(),
                group = groups[item.id],
            )
        }
        QueueBuilder.build(fresh, verdicts)
    }

    suspend fun record(item: MediaItem, kind: DecisionKind) {
        database.decisionDao().upsert(
            DecisionEntity(
                mediaId = item.id,
                kind = kind,
                decidedAtMs = System.currentTimeMillis(),
                sizeBytes = item.sizeBytes,
            ),
        )
    }

    suspend fun undo(item: MediaItem) = database.decisionDao().forget(item.id)

    suspend fun markCommitted(ids: List<Long>) =
        database.decisionDao().markCommitted(ids, System.currentTimeMillis())

    suspend fun pendingDeletionsNow(): List<DecisionEntity> =
        database.decisionDao().pendingDeletionsNow()

    /** Duplicates and bursts both produce groups; an item can only belong to one. */
    private fun buildGroupIndex(
        items: List<MediaItem>,
        visual: Map<Long, VisualSignals>,
    ): Map<Long, MediaGroup> {
        val index = HashMap<Long, MediaGroup>()
        for (group in Grouping.groupDuplicates(items, visual)) {
            group.itemIds.forEach { index[it] = group }
        }
        for (group in Grouping.groupBursts(items, visual)) {
            group.itemIds.forEach { index.putIfAbsent(it, group) }
        }
        return index
    }

    private fun AnalysisEntity.toContentSignals(): ContentSignals? {
        val text = ocrText ?: return null
        return ContentSignals(
            text = text,
            textBlockCount = textBlockCount ?: 0,
            labels = labels?.split(LABEL_SEPARATOR)?.filter { it.isNotBlank() }.orEmpty(),
            faceCount = faceCount ?: 0,
        )
    }

    private companion object {
        const val BATCH_SIZE = 200

        /** ML Kit labels are single words or short phrases, never containing a newline. */
        const val LABEL_SEPARATOR = "\n"
    }
}
