package app.ussr.core.analysis

import app.ussr.core.model.MediaItem
import app.ussr.core.model.VisualSignals

/**
 * A set of items that are the same picture, or the same moment shot several times.
 * [keeperId] is the one the app proposes to keep; the rest are the cheap deletions.
 */
data class MediaGroup(
    val kind: Kind,
    val itemIds: List<Long>,
    val keeperId: Long,
) {
    enum class Kind { ExactDuplicate, NearDuplicate, Burst }

    val deletionCandidates: List<Long> get() = itemIds.filter { it != keeperId }
}

object Grouping {

    /** Hamming distance at or below this counts as the same picture. */
    const val NEAR_DUPLICATE_DISTANCE = 6

    /** Shots this close together are one press of the shutter, not two decisions. */
    const val BURST_WINDOW_MS = 2_500L

    /**
     * Group near-identical stills.
     *
     * Comparing every hash with every other is quadratic and the library is the whole
     * camera roll, so candidates are pre-filtered by banded hashing: two hashes within
     * [NEAR_DUPLICATE_DISTANCE] bits must agree exactly on at least one 16-bit band when
     * the distance is under the band count, which turns the sweep near-linear.
     */
    fun groupDuplicates(
        items: List<MediaItem>,
        signals: Map<Long, VisualSignals>,
        maxDistance: Int = NEAR_DUPLICATE_DISTANCE,
    ): List<MediaGroup> {
        val hashed = items.filter { signals.containsKey(it.id) }
        if (hashed.size < 2) return emptyList()

        val union = UnionFind(hashed.map { it.id })
        val bands = HashMap<Long, MutableList<MediaItem>>()
        for (item in hashed) {
            val hash = signals.getValue(item.id).dHash
            for (band in 0 until BAND_COUNT) {
                val key = (band.toLong() shl 48) or ((hash ushr (band * BAND_BITS)) and BAND_MASK)
                bands.getOrPut(key) { mutableListOf() }.add(item)
            }
        }

        val compared = HashSet<Long>()
        for (bucket in bands.values) {
            if (bucket.size < 2) continue
            for (i in bucket.indices) {
                for (j in i + 1 until bucket.size) {
                    val a = bucket[i]
                    val b = bucket[j]
                    val pair = pairKey(a.id, b.id)
                    if (!compared.add(pair)) continue
                    val distance = PixelAnalysis.hammingDistance(
                        signals.getValue(a.id).dHash,
                        signals.getValue(b.id).dHash,
                    )
                    if (distance <= maxDistance) union.join(a.id, b.id)
                }
            }
        }

        val byId = hashed.associateBy { it.id }
        return union.groups()
            .filter { it.size > 1 }
            .map { ids ->
                val members = ids.mapNotNull { byId[it] }
                val exact = members.map { signals.getValue(it.id).dHash }.distinct().size == 1 &&
                    members.map { it.sizeBytes }.distinct().size == 1
                MediaGroup(
                    kind = if (exact) MediaGroup.Kind.ExactDuplicate else MediaGroup.Kind.NearDuplicate,
                    itemIds = members.sortedByDescending { keepRank(it, signals[it.id]) }.map { it.id },
                    keeperId = members.maxByOrNull { keepRank(it, signals[it.id]) }!!.id,
                )
            }
    }

    /**
     * Group consecutive shots of the same moment. Unlike duplicates these are not identical
     * pictures, so they are only grouped when they share a source, arrive inside
     * [BURST_WINDOW_MS] of each other and have the same shape.
     */
    fun groupBursts(items: List<MediaItem>, signals: Map<Long, VisualSignals>): List<MediaGroup> {
        val stills = items.filterNot { it.isVideo }.sortedBy { it.capturedAtMs }
        if (stills.size < 2) return emptyList()

        val groups = mutableListOf<MediaGroup>()
        var run = mutableListOf(stills.first())

        fun flush() {
            if (run.size > 1) {
                val keeper = run.maxByOrNull { keepRank(it, signals[it.id]) }!!
                groups += MediaGroup(
                    kind = MediaGroup.Kind.Burst,
                    itemIds = run.sortedByDescending { keepRank(it, signals[it.id]) }.map { it.id },
                    keeperId = keeper.id,
                )
            }
            run = mutableListOf()
        }

        for (item in stills.drop(1)) {
            val previous = run.lastOrNull()
            val continues = previous != null &&
                item.capturedAtMs - previous.capturedAtMs <= BURST_WINDOW_MS &&
                SourceClassifier.classify(item) == SourceClassifier.classify(previous) &&
                PixelAnalysis.aspectDelta(item.width, item.height, previous.width, previous.height) < 0.02
            if (!continues) flush()
            run.add(item)
        }
        flush()
        return groups
    }

    /**
     * Which member of a group survives. Favourites and edits win outright; otherwise the
     * sharpest frame, then the largest file.
     */
    private fun keepRank(item: MediaItem, signals: VisualSignals?): Double {
        var rank = 0.0
        if (item.isFavorite) rank += 1_000_000.0
        if (item.albumCount > 0) rank += 500_000.0
        if (item.wasEditedAfterImport) rank += 250_000.0
        rank += (signals?.laplacianVariance ?: 0.0) * 10
        rank += item.sizeBytes / 1_000_000.0
        return rank
    }

    private fun pairKey(a: Long, b: Long): Long {
        val low = minOf(a, b)
        val high = maxOf(a, b)
        return low * 31L + high
    }

    private const val BAND_BITS = 16
    private const val BAND_COUNT = 4
    private const val BAND_MASK = 0xFFFFL

    private class UnionFind(ids: Collection<Long>) {
        private val parent = HashMap<Long, Long>(ids.size * 2)

        init {
            ids.forEach { parent[it] = it }
        }

        fun find(id: Long): Long {
            var root = id
            while (parent.getValue(root) != root) root = parent.getValue(root)
            var walk = id
            while (parent.getValue(walk) != walk) {
                val next = parent.getValue(walk)
                parent[walk] = root
                walk = next
            }
            return root
        }

        fun join(a: Long, b: Long) {
            val rootA = find(a)
            val rootB = find(b)
            if (rootA != rootB) parent[rootB] = rootA
        }

        fun groups(): Collection<List<Long>> =
            parent.keys.groupBy { find(it) }.values
    }
}
