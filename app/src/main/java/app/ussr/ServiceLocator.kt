package app.ussr

import android.content.Context
import androidx.room.Room
import app.ussr.analysis.ThumbnailAnalyzer
import app.ussr.data.MediaStoreScanner
import app.ussr.data.TriageRepository
import app.ussr.data.UssrDatabase

/**
 * A plain singleton holder. The app has one graph and no variants to swap, so a DI
 * framework would be ceremony.
 */
object ServiceLocator {

    @Volatile
    private var database: UssrDatabase? = null

    @Volatile
    private var repository: TriageRepository? = null

    fun database(context: Context): UssrDatabase =
        database ?: synchronized(this) {
            database ?: Room.databaseBuilder(
                context.applicationContext,
                UssrDatabase::class.java,
                "ussr.db",
            ).build().also { database = it }
        }

    fun repository(context: Context): TriageRepository =
        repository ?: synchronized(this) {
            repository ?: run {
                val app = context.applicationContext
                TriageRepository(
                    context = app,
                    database = database(app),
                    scanner = MediaStoreScanner(app.contentResolver),
                    thumbnails = ThumbnailAnalyzer(app.contentResolver),
                ).also { repository = it }
            }
        }
}
