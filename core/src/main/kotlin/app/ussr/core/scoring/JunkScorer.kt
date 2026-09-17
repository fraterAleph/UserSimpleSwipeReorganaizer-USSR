package app.ussr.core.scoring

import app.ussr.core.analysis.Grouping
import app.ussr.core.analysis.MediaGroup
import app.ussr.core.analysis.PixelAnalysis
import app.ussr.core.analysis.SourceClassifier
import app.ussr.core.model.ContentSignals
import app.ussr.core.model.MediaItem
import app.ussr.core.model.MediaSource
import app.ussr.core.model.VisualSignals
import java.util.Locale
import kotlin.math.min

/**
 * Turns signals into a verdict per item. Nothing here deletes anything — the score only
 * decides what gets shown first and what the card says.
 */
class JunkScorer(private val config: Config = Config()) {

    data class Config(
        val nowMs: Long = System.currentTimeMillis(),
        /** Below this Laplacian variance a still counts as blurred. */
        val blurThreshold: Double = 60.0,
        /** A file this large is worth a look whatever it shows. */
        val heavyBytes: Long = 80L * 1024 * 1024,
        /** Untouched for this long is the "you never came back to it" signal. */
        val staleMs: Long = 180L * 24 * 60 * 60 * 1000,
        /** Recognised characters above this make a screenshot look like a document. */
        val documentTextLength: Int = 120,
    )

    fun score(
        item: MediaItem,
        visual: VisualSignals?,
        content: ContentSignals?,
        group: MediaGroup? = null,
    ): Verdict {
        val reasons = mutableListOf<Reason>()
        val source = SourceClassifier.classify(item)

        keepReasons(item, reasons)
        if (isProtected(item)) {
            return Verdict(
                itemId = item.id,
                junkScore = 0.0,
                confidence = 1.0,
                costOfError = 1.0,
                category = Category.Everything,
                reasons = reasons,
                protected = true,
            )
        }

        var junk = 0.0
        var confidence = BASE_CONFIDENCE
        var cost = BASE_COST
        var category = Category.Everything

        if (group != null && item.id in group.deletionCandidates) {
            when (group.kind) {
                MediaGroup.Kind.ExactDuplicate -> {
                    junk = 0.97; confidence = 0.99; cost = 0.02
                    category = Category.ExactDuplicates
                    reasons += Reason.ExactDuplicate
                }
                MediaGroup.Kind.NearDuplicate -> {
                    junk = 0.85; confidence = 0.9; cost = 0.1
                    category = Category.ExactDuplicates
                    reasons += Reason.NearDuplicate
                }
                MediaGroup.Kind.Burst -> {
                    junk = 0.7; confidence = 0.75; cost = 0.15
                    category = Category.Bursts
                    reasons += Reason.BurstSibling
                }
            }
        }

        if (visual != null) {
            if (PixelAnalysis.isBlank(visual)) {
                junk = maxOf(junk, 0.9); confidence = maxOf(confidence, 0.9); cost = min(cost, 0.05)
                category = pickCategory(category, Category.BlankOrBlurry)
                reasons += Reason.BlankFrame
            } else if (!item.isVideo && visual.laplacianVariance < config.blurThreshold) {
                junk = maxOf(junk, 0.75); confidence = maxOf(confidence, 0.7); cost = min(cost, 0.15)
                category = pickCategory(category, Category.BlankOrBlurry)
                reasons += Reason.Blurry
            }
        }

        when (source) {
            MediaSource.Screenshot -> {
                junk = maxOf(junk, 0.5)
                category = pickCategory(category, Category.Screenshots)
                reasons += Reason.Screenshot
            }
            MediaSource.ScreenRecording -> {
                junk = maxOf(junk, 0.6)
                category = pickCategory(category, Category.Screenshots)
                reasons += Reason.ScreenRecording
            }
            MediaSource.Messenger -> {
                junk = maxOf(junk, 0.6); confidence = maxOf(confidence, 0.7)
                category = pickCategory(category, Category.MessengerAndSocial)
                reasons += Reason.MessengerSave
            }
            MediaSource.SocialSave -> {
                junk = maxOf(junk, 0.55)
                category = pickCategory(category, Category.MessengerAndSocial)
                reasons += Reason.SocialSave
            }
            MediaSource.Download -> {
                junk = maxOf(junk, 0.45)
                category = pickCategory(category, Category.StaleDownloads)
            }
            MediaSource.Camera, MediaSource.Other -> Unit
        }

        if (item.sizeBytes >= config.heavyBytes) {
            junk = maxOf(junk, 0.4); cost = maxOf(cost, 0.3)
            if (item.isVideo) category = pickCategory(category, Category.HeavyVideos)
            reasons += Reason.Heavy
        }

        val age = config.nowMs - item.capturedAtMs
        if (age > config.staleMs) {
            junk = min(1.0, junk + 0.1)
            confidence = min(1.0, confidence + 0.05)
            reasons += Reason.Old
        }

        if (content != null) {
            confidence = min(1.0, confidence + 0.15)
            val kind = TextIntent.of(content)
            when (kind) {
                TextIntent.Document -> {
                    junk = min(junk, 0.15); cost = maxOf(cost, 0.85)
                    reasons += Reason.LooksLikeDocument
                }
                TextIntent.Receipt -> {
                    junk = min(junk, 0.1); cost = maxOf(cost, 0.9)
                    reasons += Reason.LooksLikeReceipt
                }
                TextIntent.Code -> {
                    junk = min(junk, 0.2); cost = maxOf(cost, 0.8)
                    reasons += Reason.LooksLikeCode
                }
                TextIntent.Sparse -> {
                    if (source == MediaSource.Screenshot) {
                        junk = min(1.0, junk + 0.25)
                        confidence = min(1.0, confidence + 0.1)
                        reasons += Reason.NoTextOnScreenshot
                    }
                }
                TextIntent.Unknown -> Unit
            }
            if (content.faceCount > 0) {
                junk = min(junk, 0.35); cost = maxOf(cost, 0.7)
                reasons += Reason.HasFaces
            }
        }

        return Verdict(
            itemId = item.id,
            junkScore = junk.coerceIn(0.0, 1.0),
            confidence = confidence.coerceIn(0.0, 1.0),
            costOfError = cost.coerceIn(0.0, 1.0),
            category = category,
            reasons = reasons,
        )
    }

    /**
     * Items the app refuses to put on a deletion card. Being wrong here is far more
     * expensive than missing a few megabytes.
     */
    private fun isProtected(item: MediaItem): Boolean =
        item.isFavorite || item.albumCount > 0 || item.wasEditedAfterImport

    private fun keepReasons(item: MediaItem, into: MutableList<Reason>) {
        if (item.isFavorite) into += Reason.Favorite
        if (item.albumCount > 0) into += Reason.InAlbum
        if (item.wasEditedAfterImport) into += Reason.EditedAfterImport
    }

    /** Keep whichever bucket is more specific; Everything is the fallback. */
    private fun pickCategory(current: Category, candidate: Category): Category =
        if (current == Category.Everything) candidate else current

    private companion object {
        const val BASE_CONFIDENCE = 0.4
        const val BASE_COST = 0.4
    }
}

/**
 * What the recognised text on a still is for. Cheap keyword matching, deliberately biased
 * towards keeping: a false "this is a receipt" costs nothing but one extra swipe.
 */
enum class TextIntent {
    Receipt,
    Document,
    Code,
    Sparse,
    Unknown;

    companion object {
        fun of(content: ContentSignals, documentTextLength: Int = 120): TextIntent {
            val text = content.text.lowercase(Locale.ROOT)
            if (text.isBlank() && content.labels.none { it in TEXTY_LABELS }) return Sparse
            if (RECEIPT_WORDS.any { text.contains(it) }) return Receipt
            if (CODE_WORDS.any { text.contains(it) }) return Code
            if (DOCUMENT_WORDS.any { text.contains(it) }) return Document
            if (text.length >= documentTextLength || content.textBlockCount >= 6) return Document
            if (text.length < SPARSE_TEXT_LENGTH) return Sparse
            return Unknown
        }

        private const val SPARSE_TEXT_LENGTH = 24

        private val TEXTY_LABELS = setOf("text", "document", "paper", "screenshot", "receipt")

        private val RECEIPT_WORDS = setOf(
            "итого", "к оплате", "чек", "счёт", "счет", "заказ №", "order no", "total",
            "subtotal", "invoice", "receipt", "ндс", "vat", "оплачено", "paid",
            "бронирование", "booking", "boarding", "посадочный", "трек-номер", "tracking",
        )

        private val DOCUMENT_WORDS = setOf(
            "паспорт", "договор", "справка", "полис", "выписка", "инструкция",
            "passport", "contract", "prescription", "insurance", "itinerary",
        )

        private val CODE_WORDS = setOf(
            "код подтверждения", "verification code", "one-time", "одноразовый",
            "2fa", "otp", "seed phrase", "recovery phrase", "backup code",
        )
    }
}
