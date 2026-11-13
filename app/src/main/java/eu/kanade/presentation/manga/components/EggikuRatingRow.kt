package eu.kanade.presentation.manga.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.rating.EggikuRatings
import eu.kanade.tachiyomi.data.tags.EggikuTags
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.eggikuPop
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

// KMK -->
internal val EggikuStarColor = Color(0xFFFFB81C)

/**
 * Eggiku: your star rating and your own tags, right under the cover. Tap the same star again to
 * clear the rating; tap a tag to manage tags.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EggikuRatingRow(mangaId: Long, modifier: Modifier = Modifier) {
    val ratingEntries by EggikuRatings.pref().collectAsState()
    val stars = remember(ratingEntries, mangaId) { EggikuRatings.parse(ratingEntries)[mangaId] ?: 0 }
    val definitions by EggikuTags.definitionsPref().collectAsState()
    val assignments by EggikuTags.assignmentsPref().collectAsState()
    val allTags = remember(definitions) { EggikuTags.parseDefinitions(definitions) }
    val mine = remember(assignments, mangaId) { EggikuTags.parseAssignments(assignments)[mangaId].orEmpty() }
    var managing by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            (1..5).forEach { star ->
                IconButton(onClick = { EggikuRatings.set(mangaId, if (star == stars) 0 else star) }) {
                    Icon(
                        imageVector = if (star <= stars) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription = "$star",
                        tint = if (star <= stars) EggikuStarColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(28.dp)
                            .eggikuPop(star <= stars, peak = 1.35f),
                    )
                }
            }
            Text(
                text = if (stars == 0) stringResource(KMR.strings.eggiku_rating_none) else stringResource(KMR.strings.eggiku_your_rating),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        FlowRow(
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            mine.forEach { name ->
                val color = Color(allTags.find { it.name == name }?.color ?: 0xFF90A4AE)
                TagPill(name, color, selected = true) { managing = true }
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
                    .clickable { managing = true }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(stringResource(KMR.strings.eggiku_tags_add), style = MaterialTheme.typography.labelMedium)
            }
        }
    }

    if (managing) {
        TagsDialog(mangaId, allTags, mine) { managing = false }
    }
}

@Composable
private fun TagPill(name: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = name,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) {
            if (color.luminance() > 0.5f) Color.Black else Color.White
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) color else Color.Transparent)
            .border(1.dp, color, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsDialog(
    mangaId: Long,
    allTags: List<EggikuTags.Tag>,
    mine: List<String>,
    onDismiss: () -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    var newColor by remember { mutableLongStateOf(EggikuTags.palette[allTags.size % EggikuTags.palette.size]) }
    var deleting by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(KMR.strings.eggiku_tags)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (allTags.isEmpty()) {
                    Text(stringResource(KMR.strings.eggiku_tags_none), style = MaterialTheme.typography.bodySmall)
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    allTags.forEach { tag ->
                        if (deleting) {
                            FilterChip(
                                selected = false,
                                onClick = { EggikuTags.delete(tag.name) },
                                label = { Text(tag.name) },
                                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                            )
                        } else {
                            TagPill(tag.name, Color(tag.color), selected = tag.name in mine) {
                                EggikuTags.toggle(mangaId, tag.name)
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it.take(24) },
                    label = { Text(stringResource(KMR.strings.eggiku_tags_new)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EggikuTags.palette.forEach { c ->
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(
                                    width = if (c == newColor) 3.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape,
                                )
                                .clickable { newColor = c },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = newName.isNotBlank(),
                onClick = {
                    EggikuTags.create(newName, newColor)
                    EggikuTags.toggle(mangaId, newName.trim())
                    newName = ""
                },
            ) { Text(stringResource(KMR.strings.eggiku_tags_create)) }
        },
        dismissButton = {
            Row {
                if (allTags.isNotEmpty()) {
                    TextButton(onClick = { deleting = !deleting }) {
                        Text(stringResource(if (deleting) KMR.strings.eggiku_tags_done else KMR.strings.smart_delete))
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(KMR.strings.eggiku_close)) }
            }
        },
    )
}
// KMK <--
