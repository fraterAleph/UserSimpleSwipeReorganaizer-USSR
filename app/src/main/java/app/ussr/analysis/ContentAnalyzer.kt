package app.ussr.analysis

import android.content.Context
import android.net.Uri
import app.ussr.core.model.ContentSignals
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The expensive pass: on-device OCR plus generic image labelling. This is what separates a
 * screenshot of a boarding pass from a screenshot of a meme, and it is the reason the app
 * can put a photo of a receipt at the very bottom of the deck instead of the top.
 *
 * Both models run locally; nothing is uploaded. Close it when the worker is done.
 */
class ContentAnalyzer(context: Context) : AutoCloseable {

    private val appContext = context.applicationContext
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            .setConfidenceThreshold(LABEL_CONFIDENCE)
            .build(),
    )

    suspend fun analyse(uri: Uri): ContentSignals? = withContext(Dispatchers.Default) {
        val image = runCatching { InputImage.fromFilePath(appContext, uri) }.getOrNull()
            ?: return@withContext null

        val text = runCatching { recognizer.process(image).await() }.getOrNull()
        val labels = runCatching { labeler.process(image).await() }.getOrNull()

        ContentSignals(
            text = text?.text?.trim().orEmpty(),
            textBlockCount = text?.textBlocks?.size ?: 0,
            labels = labels.orEmpty().map { it.text.lowercase(Locale.ROOT) },
        )
    }

    override fun close() {
        recognizer.close()
        labeler.close()
    }

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
        suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { continuation.resume(it) }
            addOnFailureListener { continuation.resumeWithException(it) }
            addOnCanceledListener { continuation.cancel() }
        }

    private companion object {
        const val LABEL_CONFIDENCE = 0.6f
    }
}
