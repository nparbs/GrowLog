package com.nparbs.growlog.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.nparbs.growlog.data.Photo
import java.io.File
import java.util.UUID

private const val MAX_PICKED_PHOTOS = 20

@Composable
fun PlantThumbnail(path: String?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    if (path != null) {
        AsyncImage(
            model = File(path),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(64.dp).clip(shape),
        )
    } else {
        Box(
            modifier = modifier
                .size(64.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Spa,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/** Camera and gallery buttons. [onPicked] receives content URIs to import. */
@Composable
fun AddPhotoButtons(onPicked: (List<Uri>) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Saved because the camera app can cause this activity to be recreated while it's open.
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = cameraUri
        if (saved && uri != null) onPicked(listOf(Uri.parse(uri)))
        cameraUri = null
    }
    val gallery = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PICKED_PHOTOS),
    ) { uris ->
        if (uris.isNotEmpty()) onPicked(uris)
    }

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            modifier = Modifier.weight(1f),
            onClick = {
                val uri = newCameraUri(context)
                cameraUri = uri.toString()
                try {
                    camera.launch(uri)
                } catch (_: android.content.ActivityNotFoundException) {
                    cameraUri = null
                    Toast.makeText(context, "No camera app found", Toast.LENGTH_SHORT).show()
                }
            },
        ) {
            Icon(Icons.Filled.PhotoCamera, contentDescription = null, Modifier.padding(end = 8.dp))
            Text("Camera")
        }
        OutlinedButton(
            modifier = Modifier.weight(1f),
            onClick = {
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
        ) {
            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, Modifier.padding(end = 8.dp))
            Text("Gallery")
        }
    }
}

private fun newCameraUri(context: android.content.Context): Uri {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.files",
        File(dir, "${UUID.randomUUID()}.jpg"),
    )
}

@Composable
fun PhotoStrip(photos: List<Photo>, onClick: (Photo) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(photos, key = { it.id }) { photo ->
            AsyncImage(
                model = File(photo.path),
                contentDescription = photo.caption.ifBlank { "Plant photo" },
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onClick(photo) },
            )
        }
    }
}

@Composable
fun PhotoGridItem(photo: Photo, onClick: () -> Unit) {
    AsyncImage(
        model = File(photo.path),
        contentDescription = photo.caption.ifBlank { "Plant photo from ${formatDay(photo.date)}" },
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    )
}

@Composable
fun PhotoViewer(
    photo: Photo,
    onDismiss: () -> Unit,
    onSaveCaption: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var caption by remember(photo.id) { mutableStateOf(photo.caption) }
    var confirmDelete by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatDay(photo.date),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete photo")
                    }
                }
                AsyncImage(
                    model = File(photo.path),
                    contentDescription = caption.ifBlank { "Plant photo" },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    label = { Text("Caption") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                    TextButton(
                        enabled = caption != photo.caption,
                        onClick = { onSaveCaption(caption) },
                    ) { Text("Save caption") }
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete photo?",
            message = "This removes the photo from GrowLog. It can't be undone.",
            confirmLabel = "Delete",
            onConfirm = onDelete,
            onDismiss = { confirmDelete = false },
        )
    }
}
