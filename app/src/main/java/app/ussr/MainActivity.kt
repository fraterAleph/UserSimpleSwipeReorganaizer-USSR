package app.ussr

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ussr.core.scoring.Category
import app.ussr.data.DecisionEntity
import app.ussr.trash.MediaRequests
import app.ussr.ui.TriageViewModel
import app.ussr.ui.screens.DeckPickerScreen
import app.ussr.ui.screens.JournalScreen
import app.ussr.ui.screens.ReviewScreen
import app.ussr.ui.screens.SwipeScreen
import app.ussr.ui.theme.PixelButton
import app.ussr.ui.theme.UssrTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw behind the status and navigation bars, then inset the content back out of
        // them below. Without this the header sits under the clock, which it did.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            UssrTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                        UssrApp()
                    }
                }
            }
        }
    }
}

private enum class Screen { Picker, Swipe, Review, Journal }

/**
 * READ_MEDIA_IMAGES and READ_MEDIA_VIDEO only exist from API 33. The app supports 30, and
 * asking for a permission the platform has never heard of is silently denied, so older
 * versions get the storage permission they do understand.
 */
private val mediaPermissions: Array<String>
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

private fun Context.hasMediaAccess(): Boolean = mediaPermissions.any {
    ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
}

@Composable
private fun UssrApp(viewModel: TriageViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var granted by remember { mutableStateOf(context.hasMediaAccess()) }
    var screen by remember { mutableStateOf(Screen.Picker) }
    var pending by remember { mutableStateOf(emptyList<DecisionEntity>()) }

    // Confirming runs up to two system sheets back to back — favourites, then the trash —
    // because each is a separate write to media the app only has read permission for.
    var favouritesInFlight by remember { mutableStateOf(emptyList<Long>()) }
    var trashInFlight by remember { mutableStateOf(emptyList<Uri>()) }

    val permissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result -> granted = result.values.any { it } }

    // The ids are kept alongside the uris: the uris go to the system, the ids are what the
    // app marks as done once the system says yes.
    var trashedIds by remember { mutableStateOf(emptyList<Long>()) }

    val trashSheet = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        // Anything but a confirmation leaves the decisions pending, so a cancelled sheet
        // costs the user nothing and the list is still there next time.
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onTrashConfirmed(trashedIds)
            screen = Screen.Picker
        }
        trashInFlight = emptyList()
        trashedIds = emptyList()
    }

    val favoriteSheet = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onFavoritesConfirmed(favouritesInFlight)
        }
        favouritesInFlight = emptyList()
        // Whether or not the favourites went through, the deletions still need their turn.
        val uris = trashInFlight
        if (uris.isNotEmpty()) {
            val sender = MediaRequests.trash(context.contentResolver, uris)
            if (sender != null) {
                trashSheet.launch(IntentSenderRequest.Builder(sender).build())
            } else {
                Toast.makeText(context, R.string.error_request_refused, Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!granted) permissions.launch(mediaPermissions)
    }

    LaunchedEffect(granted) {
        if (granted) viewModel.start()
    }

    fun uriFor(id: Long): Any =
        ContentUris.withAppendedId(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), id)

    fun openReview() {
        scope.launch {
            pending = ServiceLocator.repository(context).pendingDeletionsNow()
            screen = Screen.Review
        }
    }

    fun confirm() {
        scope.launch {
            val repository = ServiceLocator.repository(context)

            // One chunk per sheet: some OEM implementations refuse a single request carrying
            // thousands of uris, and a shorter list is easier to read besides.
            val deletionIds = MediaRequests.chunks(viewModel.pendingDeletionIds()).firstOrNull().orEmpty()
            trashedIds = deletionIds
            trashInFlight = repository.writableUris(deletionIds)

            val favouriteIds = MediaRequests.chunks(viewModel.pendingFavoriteIds()).firstOrNull().orEmpty()
            if (favouriteIds.isNotEmpty()) {
                val uris = repository.writableUris(favouriteIds)
                val sender = MediaRequests.favorite(context.contentResolver, uris)
                if (sender != null) {
                    favouritesInFlight = favouriteIds
                    favoriteSheet.launch(IntentSenderRequest.Builder(sender).build())
                    return@launch
                }
            }

            if (trashInFlight.isNotEmpty()) {
                val sender = MediaRequests.trash(context.contentResolver, trashInFlight)
                if (sender != null) {
                    trashSheet.launch(IntentSenderRequest.Builder(sender).build())
                } else {
                    // The platform refused to build the dialog. Nothing was lost — the queue
                    // is still there — but silence here is what read as "the button does
                    // nothing", so say it out loud.
                    Toast.makeText(context, R.string.error_request_refused, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    if (!granted) {
        PermissionWall(onGrant = { permissions.launch(mediaPermissions) })
        return
    }

    when (screen) {
        Screen.Picker -> DeckPickerScreen(
            state = state,
            onOpenDeck = { category: Category? ->
                viewModel.openDeck(category)
                screen = Screen.Swipe
            },
            onSetMode = viewModel::setMode,
            onReview = ::openReview,
        )

        Screen.Swipe -> SwipeScreen(
            state = state,
            contentUri = ::uriFor,
            onSwipe = viewModel::swipe,
            onUndo = viewModel::undo,
            onAcknowledge = viewModel::acknowledgePacing,
            onBatch = viewModel::acceptBatch,
            onReturnAnimationDone = viewModel::clearReturnAnimation,
            onOpenJournal = { screen = Screen.Journal },
            onReview = {
                viewModel.acknowledgePacing()
                openReview()
            },
            onBack = {
                viewModel.closeDeck()
                screen = Screen.Picker
            },
        )

        Screen.Journal -> JournalScreen(
            history = state.history,
            contentUri = ::uriFor,
            onRewindTo = { index ->
                viewModel.rewindTo(index)
                screen = Screen.Swipe
            },
            onBack = { screen = Screen.Swipe },
        )

        Screen.Review -> ReviewScreen(
            pending = pending,
            pendingFavorites = state.pendingFavorites,
            contentUri = ::uriFor,
            onConfirm = ::confirm,
            onBack = { screen = Screen.Picker },
        )
    }
}

@Composable
private fun PermissionWall(onGrant: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.permission_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.permission_body), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
        PixelButton(stringResource(R.string.permission_grant), onGrant)
    }
}
