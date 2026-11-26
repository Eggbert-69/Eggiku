package eu.kanade.presentation.library.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.rating.EggikuRatings
import eu.kanade.tachiyomi.data.shelves.EggikuShelves
import eu.kanade.tachiyomi.data.smart.EggikuSmart
import eu.kanade.tachiyomi.data.tags.EggikuTags
import eu.kanade.tachiyomi.ui.library.EggikuShelvesScreen
import eu.kanade.tachiyomi.ui.library.LibraryItem
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import java.util.concurrent.TimeUnit

// KMK -->

/**
 * Eggiku bulk edit for the library selection: tags, dropped, waiting list and ratings for many
 * series at once, with one undo for the whole batch. Categories already have their own button.
 */
object EggikuBulkEdit {

    sealed interface Action {
        data class AddTag(val name: String) : Action
        data class RemoveTag(val name: String) : Action
        data class Drop(val reason: EggikuShelves.Reason) : Action
        data object Undrop : Action
        data object Wait : Action
        data object Unwait : Action
        data class Rate(val stars: Int) : Action
    }

    /** Everything a batch can touch, as it was before */
    class Snapshot(
        private val tags: Set<String>,
        private val ratings: Set<String>,
        private val shelves: String,
    ) {
        fun restore() {
            EggikuTags.assignmentsPref().set(tags)
            EggikuRatings.pref().set(ratings)
            EggikuShelves.pref().set(shelves)
        }
    }

    fun apply(selection: List<LibraryManga>, action: Action): Snapshot {
        val snapshot = Snapshot(
            tags = EggikuTags.assignmentsPref().get(),
            ratings = EggikuRatings.pref().get(),
            shelves = EggikuShelves.pref().get(),
        )
        when (action) {
            is Action.AddTag -> EggikuTags.assignmentsPref().set(
                EggikuTags.assignmentsPref().get() + selection.map { "${it.id}|${action.name}" },
            )
            is Action.RemoveTag -> EggikuTags.assignmentsPref().set(
                EggikuTags.assignmentsPref().get() - selection.map { "${it.id}|${action.name}" }.toSet(),
            )
            is Action.Drop -> selection.forEach {
                EggikuShelves.drop(it.id, action.reason, "", it.totalChapters, it.manga.status)
            }
            Action.Undrop -> selection.forEach { EggikuShelves.undrop(it.id) }
            Action.Wait -> selection.forEach { EggikuShelves.setWaiting(it.id, true) }
            Action.Unwait -> selection.forEach { EggikuShelves.setWaiting(it.id, false) }
            is Action.Rate -> selection.forEach { EggikuRatings.set(it.id, action.stars) }
        }
        return snapshot
    }

    /** Quick ways to select, plus every smart category's rules */
    sealed interface SelectRule {
        data object NotReadInSixMonths : SelectRule
        data object NeverStarted : SelectRule
        data object Finished : SelectRule
        data object SameSource : SelectRule
        data object Dropped : SelectRule
        data object Waiting : SelectRule
        data class Smart(val rule: EggikuSmart.Rule) : SelectRule
    }

    fun select(items: List<LibraryItem>, rule: SelectRule, selectedSources: Set<Long>): List<Long> {
        val now = System.currentTimeMillis()
        val store = EggikuShelves.store()
        val ctx = EggikuSmart.Context()
        return items.filter { item ->
            val manga = item.libraryManga
            when (rule) {
                SelectRule.NotReadInSixMonths -> manga.lastRead > 0 && now - manga.lastRead > TimeUnit.DAYS.toMillis(182)
                SelectRule.NeverStarted -> !manga.hasStarted
                SelectRule.Finished -> EggikuShelves.isFinished(manga.manga.status)
                SelectRule.SameSource -> manga.manga.source in selectedSources
                SelectRule.Dropped -> manga.id in store.dropped
                SelectRule.Waiting -> manga.id in store.waiting
                is SelectRule.Smart -> EggikuSmart.matches(
                    rule.rule,
                    EggikuSmart.Item(manga, item.downloadCount, tracked = false),
                    ctx,
                )
            }
        }.map { it.id }
    }
}

@Composable
fun EggikuSelectByRuleDialog(
    hasSelection: Boolean,
    onDismiss: () -> Unit,
    onPick: (EggikuBulkEdit.SelectRule) -> Unit,
) {
    val smartRules = remember { EggikuSmart.rules().filter { it.conditions.isNotEmpty() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(KMR.strings.bulk_select_by_rule)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Choice(stringResource(KMR.strings.bulk_rule_not_read_6m)) { onPick(EggikuBulkEdit.SelectRule.NotReadInSixMonths) }
                Choice(stringResource(KMR.strings.bulk_rule_never_started)) { onPick(EggikuBulkEdit.SelectRule.NeverStarted) }
                Choice(stringResource(KMR.strings.bulk_rule_finished)) { onPick(EggikuBulkEdit.SelectRule.Finished) }
                if (hasSelection) {
                    Choice(stringResource(KMR.strings.bulk_rule_same_source)) { onPick(EggikuBulkEdit.SelectRule.SameSource) }
                }
                Choice(stringResource(KMR.strings.bulk_rule_dropped)) { onPick(EggikuBulkEdit.SelectRule.Dropped) }
                Choice(stringResource(KMR.strings.bulk_rule_waiting)) { onPick(EggikuBulkEdit.SelectRule.Waiting) }
                if (smartRules.isNotEmpty()) {
                    Text(
                        text = stringResource(KMR.strings.bulk_rule_smart),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                    smartRules.forEach { rule ->
                        val name = rule.name.ifBlank {
                            EggikuSmart.presetNames[rule.id]?.let { stringResource(it) } ?: rule.id
                        }
                        Choice(name) { onPick(EggikuBulkEdit.SelectRule.Smart(rule)) }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(MR.strings.action_cancel)) } },
    )
}

private enum class BulkStep { MENU, ADD_TAG, REMOVE_TAG, DROP, RATE }

@Composable
fun EggikuBulkEditDialog(
    count: Int,
    onDismiss: () -> Unit,
    onApply: (EggikuBulkEdit.Action) -> Unit,
) {
    var step by remember { mutableStateOf(BulkStep.MENU) }
    val tags = remember { EggikuTags.all() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (step) {
                    BulkStep.MENU -> stringResource(KMR.strings.bulk_edit_title, count)
                    BulkStep.ADD_TAG -> stringResource(KMR.strings.bulk_add_tag)
                    BulkStep.REMOVE_TAG -> stringResource(KMR.strings.bulk_remove_tag)
                    BulkStep.DROP -> stringResource(KMR.strings.drop_why)
                    BulkStep.RATE -> stringResource(KMR.strings.bulk_rate)
                },
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                when (step) {
                    BulkStep.MENU -> {
                        if (tags.isNotEmpty()) {
                            Choice(stringResource(KMR.strings.bulk_add_tag)) { step = BulkStep.ADD_TAG }
                            Choice(stringResource(KMR.strings.bulk_remove_tag)) { step = BulkStep.REMOVE_TAG }
                        }
                        Choice(stringResource(KMR.strings.drop_series)) { step = BulkStep.DROP }
                        Choice(stringResource(KMR.strings.shelf_undrop)) { onApply(EggikuBulkEdit.Action.Undrop) }
                        Choice(stringResource(KMR.strings.waiting_add)) { onApply(EggikuBulkEdit.Action.Wait) }
                        Choice(stringResource(KMR.strings.shelf_waiting_remove)) { onApply(EggikuBulkEdit.Action.Unwait) }
                        Choice(stringResource(KMR.strings.bulk_rate)) { step = BulkStep.RATE }
                        Text(
                            text = stringResource(KMR.strings.bulk_edit_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    BulkStep.ADD_TAG, BulkStep.REMOVE_TAG -> tags.forEach { tag ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("●", color = Color(tag.color), modifier = Modifier.padding(end = 8.dp))
                            Choice(tag.name) {
                                onApply(
                                    if (step == BulkStep.ADD_TAG) {
                                        EggikuBulkEdit.Action.AddTag(tag.name)
                                    } else {
                                        EggikuBulkEdit.Action.RemoveTag(tag.name)
                                    },
                                )
                            }
                        }
                    }
                    BulkStep.DROP -> EggikuShelves.Reason.entries.forEach { reason ->
                        Choice(stringResource(EggikuShelvesScreen.reasonLabel(reason))) {
                            onApply(EggikuBulkEdit.Action.Drop(reason))
                        }
                    }
                    BulkStep.RATE -> {
                        (5 downTo 1).forEach { stars ->
                            Choice("★".repeat(stars)) { onApply(EggikuBulkEdit.Action.Rate(stars)) }
                        }
                        Choice(stringResource(KMR.strings.bulk_clear_rating)) { onApply(EggikuBulkEdit.Action.Rate(0)) }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { if (step == BulkStep.MENU) onDismiss() else step = BulkStep.MENU }) {
                Text(stringResource(if (step == BulkStep.MENU) MR.strings.action_cancel else KMR.strings.bulk_back))
            }
        },
    )
}

@Composable
private fun Choice(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    )
}
// KMK <--
