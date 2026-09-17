package app.ussr

import android.Manifest
import android.content.ContentUris
import android.content.IntentSender
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ussr.core.scoring.Category
import app.ussr.data.DecisionEntity
import app.ussr.trash.TrashRequest
import app.ussr.ui.TriageViewModel
import app.ussr.ui.screens.DeckPickerScreen
import app.ussr.ui.screens.ReviewScreen
import app.ussr.ui.screens.SwipeScreen
import app.ussr.ui.theme.UssrTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UssrTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    UssrApp()
                }
            }
        }
    }
}

private enum class Screen { Picker, Swipe, Review }

@Composable
private fun UssrApp(viewModel: TriageViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var granted by remember { mutableStateOf(false) }
    var screen by remember { mutableStateOf(Screen.Picker) }
    var pending by remember { mutableStateOf(emptyList<DecisionEntity>()) }
    var trashing by remember { mutableStateOf(emptyList<Long>()) }

    val permissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result -> granted = result.values.any { it } }

    val trashSheet = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        // Anything but a confirmation leaves the decisions pending, so a cancelled sheet
        // costs the user nothing and the list is still there next time.
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.onTrashConfirmed(trashing)
            screen = Screen.Picker
        }
        trashing = emptyList()
    }

    LaunchedEffect(Unit) {
        permissions.launch(
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO),
        )
    }

    LaunchedEffect(granted) {
        if (granted) viewModel.start()
    }

    fun uriFor(id: Long): Any =
        ContentUris.withAppendedId(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), id)

    fun openReview() {
        scope.launch {
            pending = ServiceLocator.repository(viewModel.getApplication()).pendingDeletionsNow()
            screen = Screen.Review
        }
    }

    fun confirmTrash() {
        scope.launch {
            val ids = viewModel.pendingDeletionIds()
            if (ids.isEmpty()) return@launch
            // Chunked: some OEM implementations refuse a single request with thousands of uris.
            val chunk = TrashRequest.chunks(ids).first()
            val sender: IntentSender? = TrashRequest.create(
                viewModel.getApplication<android.app.Application>().contentResolver,
                chunk,
            )
            if (sender != null) {
                trashing = chunk
                trashSheet.launch(IntentSenderRequest.Builder(sender).build())
            }
        }
    }

    if (!granted) {
        PermissionWall(
            onGrant = {
                permissions.launch(
                    arrayOf(
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO,
                    ),
                )
            },
        )
        return
    }

    when (screen) {
        Screen.Picker -> DeckPickerScreen(
            state = state,
            onOpenDeck = { category: Category? ->
                viewModel.openDeck(category)
                screen = Screen.Swipe
            },
            onReview = ::openReview,
        )

        Screen.Swipe -> SwipeScreen(
            state = state,
            contentUri = ::uriFor,
            onSwipe = viewModel::swipe,
            onUndo = viewModel::undo,
            onAcknowledge = viewModel::acknowledgePacing,
            onReview = {
                viewModel.acknowledgePacing()
                openReview()
            },
            onBack = {
                viewModel.closeDeck()
                screen = Screen.Picker
            },
        )

        Screen.Review -> ReviewScreen(
            pending = pending,
            contentUri = ::uriFor,
            onConfirm = ::confirmTrash,
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
        Button(onClick = onGrant) { Text(stringResource(R.string.permission_grant)) }
    }
}
