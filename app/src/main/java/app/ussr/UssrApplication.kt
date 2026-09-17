package app.ussr

import android.app.Application

class UssrApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Warm the database off the first frame; everything else is built lazily.
        ServiceLocator.database(this)
    }
}
