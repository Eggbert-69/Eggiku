package eu.kanade.presentation.manga.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.achievements.EggikuCompare
import eu.kanade.tachiyomi.data.drop.EggikuDrops
import eu.kanade.tachiyomi.data.rating.EggikuRatings
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.eggikuPop
import tachiyomi.presentation.core.i18n.stringResource

// KMK -->
/**
 * Eggiku Egg Drop: pick the stars and write a short note before sending a series to a friend.
 * Stars start from your own rating; changing them here only changes the drop.
 * "Just the link" sends the old plain share message instead.
 */
@Composable
fun EggikuDropDialog(
    manga: Manga,
    onDismissRequest: () -> Unit,
    onSend: (stars: Int, note: String) -> Unit,
    onPlainShare: () -> Unit,
) {
    var stars by rememberSaveable { mutableIntStateOf(EggikuRatings.get(manga.id)) }
    var note by rememberSaveable { mutableStateOf("") }
    val nicknamePref = remember { EggikuCompare.nickname() }
    var nickname by rememberSaveable { mutableStateOf(nicknamePref.get()) }
    // Only ask for a name if there isn't one yet; it's the same name friend compare uses
    val askName = remember { nicknamePref.get().isBlank() }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(KMR.strings.egg_drop_send_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MangaCover.Book(
                        data = manga,
                        modifier = Modifier.width(56.dp),
                    )
                    Text(
                        text = manga.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                    )
                }
                Column {
                    Text(
                        text = stringResource(KMR.strings.egg_drop_your_stars),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Row {
                        (1..5).forEach { star ->
                            IconButton(onClick = { stars = if (star == stars) 0 else star }) {
                                Icon(
                                    imageVector = if (star <= stars) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                    contentDescription = "$star",
                                    tint = if (star <= stars) {
                                        EggikuStarColor
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier
                                        .size(28.dp)
                                        .eggikuPop(if (star <= stars) stars else null),
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(EggikuDrops.NOTE_MAX) },
                    label = { Text(stringResource(KMR.strings.egg_drop_note)) },
                    placeholder = { Text(stringResource(KMR.strings.egg_drop_note_hint)) },
                    supportingText = { Text("${note.length}/${EggikuDrops.NOTE_MAX}") },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (askName) {
                    OutlinedTextField(
                        value = nickname,
                        onValueChange = { nickname = it.take(24) },
                        label = { Text(stringResource(KMR.strings.egg_drop_your_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (askName && nickname.isNotBlank()) nicknamePref.set(nickname.trim())
                    onSend(stars, note)
                },
            ) {
                Text(stringResource(KMR.strings.egg_drop_send))
            }
        },
        dismissButton = {
            TextButton(onClick = onPlainShare) {
                Text(stringResource(KMR.strings.egg_drop_plain_share))
            }
        },
    )
}
// KMK <--
