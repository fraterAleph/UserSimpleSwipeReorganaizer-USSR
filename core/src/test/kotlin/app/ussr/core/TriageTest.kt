package app.ussr.core

import app.ussr.core.analysis.Grouping
import app.ussr.core.analysis.MediaGroup
import app.ussr.core.analysis.SourceClassifier
import app.ussr.core.model.ContentSignals
import app.ussr.core.model.MediaItem
import app.ussr.core.model.MediaSource
import app.ussr.core.model.VisualSignals
import app.ussr.core.queue.QueueBuilder
import app.ussr.core.scoring.Category
import app.ussr.core.scoring.JunkScorer
import app.ussr.core.scoring.Reason
import app.ussr.core.scoring.TextIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TriageTest {

    private val now = 1_700_000_000_000L
    private val day = 24 * 60 * 60 * 1000L
    private val scorer = JunkScorer(JunkScorer.Config(nowMs = now))

    @Test
    fun `folders and names decide the source`() {
        assertEquals(MediaSource.Screenshot, SourceClassifier.classify(item(path = "Pictures/Screenshots/")))
        assertEquals(MediaSource.Screenshot, SourceClassifier.classify(item(path = "DCIM/", name = "Screenshot_2024.png")))
        assertEquals(MediaSource.Messenger, SourceClassifier.classify(item(path = "Android/media/com.whatsapp/WhatsApp Images/")))
        assertEquals(MediaSource.SocialSave, SourceClassifier.classify(item(path = "Pictures/Instagram/")))
        assertEquals(MediaSource.Camera, SourceClassifier.classify(item(path = "DCIM/Camera/")))
        assertEquals(MediaSource.Other, SourceClassifier.classify(item(path = "Pictures/Wedding/")))
    }

    @Test
    fun `a still the exact size of the screen reads as a screenshot`() {
        val shot = item(path = "Pictures/Saved/", width = 1080, height = 2400)
        assertTrue(SourceClassifier.looksLikeScreenSized(shot, 1080, 2400))
        assertFalse(SourceClassifier.looksLikeScreenSized(item(width = 4000, height = 3000), 1080, 2400))
    }

    @Test
    fun `identical hashes group and the sharpest copy survives`() {
        val blurry = item(id = 1, size = 2_000_000)
        val sharp = item(id = 2, size = 2_000_000)
        val signals = mapOf(
            1L to VisualSignals(dHash = 0xABCD, laplacianVariance = 30.0, meanLuma = 120.0),
            2L to VisualSignals(dHash = 0xABCD, laplacianVariance = 900.0, meanLuma = 120.0),
        )
        val group = Grouping.groupDuplicates(listOf(blurry, sharp), signals).single()
        assertEquals(MediaGroup.Kind.ExactDuplicate, group.kind)
        assertEquals(2L, group.keeperId)
        assertEquals(listOf(1L), group.deletionCandidates)
    }

    @Test
    fun `a favourite always wins the keeper slot`() {
        val plain = item(id = 1)
        val favourite = item(id = 2, favorite = true)
        val signals = mapOf(
            1L to VisualSignals(0xFF00, 5_000.0, 120.0),
            2L to VisualSignals(0xFF00, 10.0, 120.0),
        )
        assertEquals(2L, Grouping.groupDuplicates(listOf(plain, favourite), signals).single().keeperId)
    }

    @Test
    fun `unrelated pictures do not group`() {
        val signals = mapOf(
            1L to VisualSignals(0x0000_0000_0000_0000, 500.0, 120.0),
            2L to VisualSignals(0x7FFF_FFFF_FFFF_FFFF, 500.0, 120.0),
        )
        assertTrue(Grouping.groupDuplicates(listOf(item(id = 1), item(id = 2)), signals).isEmpty())
    }

    @Test
    fun `shots inside the burst window group and a later one does not`() {
        val a = item(id = 1, takenMs = now)
        val b = item(id = 2, takenMs = now + 800)
        val c = item(id = 3, takenMs = now + 60_000)
        val signals = (1L..3L).associateWith { VisualSignals(it, 500.0, 120.0) }
        val groups = Grouping.groupBursts(listOf(a, b, c), signals)
        assertEquals(1, groups.size)
        assertEquals(setOf(1L, 2L), groups.single().itemIds.toSet())
    }

    @Test
    fun `an exact duplicate outranks everything else in the queue`() {
        val duplicate = item(id = 1, path = "DCIM/Camera/")
        val group = MediaGroup(MediaGroup.Kind.ExactDuplicate, listOf(1, 2), keeperId = 2)
        val verdict = scorer.score(duplicate, VisualSignals(1, 800.0, 120.0), null, group)
        assertTrue(verdict.junkScore > 0.9)
        assertTrue(verdict.confidence > 0.9)
        assertTrue(verdict.costOfError < 0.1)
        assertEquals(Category.ExactDuplicates, verdict.category)
        assertTrue(verdict.dealPriority > 0.9)
    }

    @Test
    fun `a favourite is protected and never dealt`() {
        val verdict = scorer.score(item(favorite = true, path = "Pictures/Screenshots/"), null, null)
        assertTrue(verdict.protected)
        assertEquals(0.0, verdict.dealPriority, 1e-9)
        assertTrue(Reason.Favorite in verdict.reasons)
    }

    @Test
    fun `an edited photo is protected even when it looks like junk`() {
        val edited = item(path = "Pictures/Screenshots/", addedMs = now - 300 * day, modifiedMs = now - 200 * day)
        assertTrue(scorer.score(edited, VisualSignals(1, 5.0, 120.0), null).protected)
    }

    @Test
    fun `a screenshot with a receipt on it is pushed to the bottom`() {
        val shot = item(path = "Pictures/Screenshots/", addedMs = now - 300 * day)
        val receipt = ContentSignals(
            text = "Заказ № 4417 Итого: 3 480 ₽ Оплачено картой",
            textBlockCount = 4,
            labels = listOf("text"),
        )
        val verdict = scorer.score(shot, VisualSignals(1, 800.0, 200.0), receipt)
        assertTrue(Reason.LooksLikeReceipt in verdict.reasons)
        assertTrue(verdict.junkScore <= 0.1)
        assertTrue(verdict.costOfError >= 0.9)
        assertTrue(verdict.dealPriority < QueueBuilder.DECK_FLOOR)
    }

    @Test
    fun `a screenshot with no text on it is pushed up`() {
        val shot = item(path = "Pictures/Screenshots/", addedMs = now - 300 * day)
        val empty = ContentSignals(text = "", textBlockCount = 0, labels = listOf("cartoon"))
        val verdict = scorer.score(shot, VisualSignals(1, 800.0, 140.0), empty)
        assertTrue(Reason.NoTextOnScreenshot in verdict.reasons)
        assertTrue(verdict.junkScore > 0.7)
    }

    @Test
    fun `a face keeps the cost of error high`() {
        val photo = item(path = "DCIM/Camera/")
        val withFace = ContentSignals(text = "", textBlockCount = 0, labels = listOf("person"), faceCount = 2)
        val verdict = scorer.score(photo, VisualSignals(1, 30.0, 120.0), withFace)
        assertTrue(Reason.Blurry in verdict.reasons)
        assertTrue(Reason.HasFaces in verdict.reasons)
        assertTrue("cost was ${verdict.costOfError}", verdict.costOfError >= 0.7)
    }

    @Test
    fun `a one-time code is never treated as junk`() {
        val shot = item(path = "Pictures/Screenshots/")
        val code = ContentSignals("Ваш код подтверждения: 8821", 1, listOf("text"))
        assertEquals(TextIntent.Code, TextIntent.of(code))
        assertTrue(scorer.score(shot, null, code).junkScore <= 0.2)
    }

    @Test
    fun `decks are ordered by deal priority and cheap wins come first`() {
        val duplicate = item(id = 1, path = "DCIM/Camera/")
        val heavyVideo = item(id = 2, path = "DCIM/Camera/", mime = "video/mp4", size = 400_000_000)
        val verdicts = mapOf(
            1L to scorer.score(duplicate, VisualSignals(1, 800.0, 120.0), null, MediaGroup(MediaGroup.Kind.ExactDuplicate, listOf(1, 9), 9)),
            2L to scorer.score(heavyVideo, null, null),
        )
        val decks = QueueBuilder.build(listOf(duplicate, heavyVideo), verdicts)
        val first = decks.flatMap { it.cards }.maxByOrNull { it.verdict.dealPriority }!!
        assertEquals(1L, first.item.id)
        assertTrue(first.batchable)
        assertTrue(decks.flatMap { it.cards }.none { it.item.id == 2L && it.batchable })
    }

    @Test
    fun `protected items never reach a deck`() {
        val favourite = item(id = 1, favorite = true, path = "Pictures/Screenshots/")
        val verdicts = mapOf(1L to scorer.score(favourite, null, null))
        assertTrue(QueueBuilder.build(listOf(favourite), verdicts).isEmpty())
    }

    @Test
    fun `interleaving alternates decks so one kind never runs forever`() {
        val decks = QueueBuilder.build(
            items = (1L..40L).map { item(id = it, path = if (it % 2 == 0L) "Pictures/Screenshots/" else "Android/media/com.whatsapp/") },
            verdicts = (1L..40L).associateWith {
                scorer.score(
                    item(id = it, path = if (it % 2 == 0L) "Pictures/Screenshots/" else "Android/media/com.whatsapp/"),
                    VisualSignals(it, 800.0, 120.0),
                    ContentSignals("", 0, emptyList()),
                )
            },
        )
        val stream = QueueBuilder.interleave(decks, runLength = 5)
        assertEquals(decks.sumOf { it.size }, stream.size)
        assertTrue(stream.distinctBy { it.item.id }.size == stream.size)
    }

    private fun item(
        id: Long = 1,
        name: String = "IMG_0001.jpg",
        path: String = "DCIM/Camera/",
        mime: String = "image/jpeg",
        size: Long = 2_500_000,
        width: Int = 4000,
        height: Int = 3000,
        addedMs: Long = now - 10 * day,
        modifiedMs: Long = addedMs,
        takenMs: Long? = null,
        favorite: Boolean = false,
        albumCount: Int = 0,
    ) = MediaItem(
        id = id,
        displayName = name,
        relativePath = path,
        mimeType = mime,
        sizeBytes = size,
        width = width,
        height = height,
        dateAddedMs = addedMs,
        dateModifiedMs = modifiedMs,
        dateTakenMs = takenMs,
        isFavorite = favorite,
        albumCount = albumCount,
    )
}
