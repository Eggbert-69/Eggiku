package eu.kanade.tachiyomi.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.library.components.ruleName
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.smart.EggikuSmart
import eu.kanade.tachiyomi.data.tags.EggikuTags
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.UUID

// KMK -->
/** Eggiku: show, hide, reorder and edit smart categories, or make new ones from rules */
class EggikuSmartRulesScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val raw by EggikuSmart.rulesPref().collectAsState()
        val rules = remember(raw) { EggikuSmart.parse(raw) }
        var editing by remember { mutableStateOf<EggikuSmart.Rule?>(null) }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(KMR.strings.smart_edit),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(KMR.strings.smart_new)) },
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    onClick = {
                        editing = EggikuSmart.Rule(
                            id = UUID.randomUUID().toString(),
                            name = "",
                            conditions = listOf(EggikuSmart.Condition(EggikuSmart.Type.UNREAD_AT_LEAST, 1)),
                        )
                    },
                )
            },
        ) { padding ->
            LazyColumn(contentPadding = padding) {
                item {
                    val enabled by EggikuSmart.enabled().collectAsState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(KMR.strings.smart_show_tab),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = enabled, onCheckedChange = { EggikuSmart.enabled().set(it) })
                    }
                }
                item {
                    Text(
                        text = stringResource(KMR.strings.smart_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                itemsIndexed(rules, key = { _, rule -> rule.id }) { index, rule ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editing = rule }
                            .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ruleName(rule), style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = describe(rule),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                        }
                        IconButton(
                            enabled = index > 0,
                            onClick = { EggikuSmart.save(rules.toMutableList().apply { add(index - 1, removeAt(index)) }) },
                        ) { Icon(Icons.Outlined.ArrowUpward, contentDescription = null) }
                        IconButton(
                            enabled = index < rules.lastIndex,
                            onClick = { EggikuSmart.save(rules.toMutableList().apply { add(index + 1, removeAt(index)) }) },
                        ) { Icon(Icons.Outlined.ArrowDownward, contentDescription = null) }
                        Switch(
                            checked = !rule.hidden,
                            onCheckedChange = { show ->
                                EggikuSmart.save(rules.map { if (it.id == rule.id) it.copy(hidden = !show) else it })
                            },
                        )
                    }
                    HorizontalDivider()
                }
            }
        }

        editing?.let { rule ->
            RuleDialog(
                initial = rule,
                isNew = rules.none { it.id == rule.id },
                onDismiss = { editing = null },
                onSave = { saved ->
                    val exists = rules.any { it.id == saved.id }
                    EggikuSmart.save(if (exists) rules.map { if (it.id == saved.id) saved else it } else rules + saved)
                    editing = null
                },
                onDelete = {
                    EggikuSmart.save(rules.filterNot { it.id == rule.id })
                    editing = null
                },
            )
        }
    }

    @Composable
    private fun describe(rule: EggikuSmart.Rule): String {
        val join = stringResource(if (rule.matchAll) KMR.strings.smart_and else KMR.strings.smart_or)
        return rule.conditions.map { conditionText(it) }.joinToString(" $join ")
    }

    @Composable
    private fun conditionText(condition: EggikuSmart.Condition): String {
        val label = stringResource(typeLabel(condition.type))
        return when (condition.type.input) {
            EggikuSmart.Input.NONE -> label
            EggikuSmart.Input.NUMBER -> "$label ${condition.number}"
            EggikuSmart.Input.TEXT, EggikuSmart.Input.TAG -> "$label \"${condition.text}\""
            EggikuSmart.Input.CATEGORY -> "$label ${condition.text.ifBlank { condition.number.toString() }}"
        }
    }

    @Composable
    private fun RuleDialog(
        initial: EggikuSmart.Rule,
        isNew: Boolean,
        onDismiss: () -> Unit,
        onSave: (EggikuSmart.Rule) -> Unit,
        onDelete: () -> Unit,
    ) {
        var rule by remember { mutableStateOf(initial) }
        var name by remember { mutableStateOf(initial.name) }
        val categories by produceState(emptyList<Category>()) {
            value = withIOContext { Injekt.get<GetCategories>().await().filterNot { it.isSystemCategory } }
        }
        val tags = remember { EggikuTags.all() }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(if (isNew) KMR.strings.smart_new else KMR.strings.smart_edit_one)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(KMR.strings.smart_name)) },
                        placeholder = { Text(ruleName(initial)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = rule.matchAll,
                            onClick = { rule = rule.copy(matchAll = true) },
                            label = { Text(stringResource(KMR.strings.smart_match_all)) },
                        )
                        FilterChip(
                            selected = !rule.matchAll,
                            onClick = { rule = rule.copy(matchAll = false) },
                            label = { Text(stringResource(KMR.strings.smart_match_any)) },
                        )
                    }
                    rule.conditions.forEachIndexed { index, condition ->
                        ConditionRow(
                            condition = condition,
                            categories = categories,
                            tags = tags,
                            onChange = { changed ->
                                rule = rule.copy(conditions = rule.conditions.toMutableList().apply { set(index, changed) })
                            },
                            onRemove = {
                                rule = rule.copy(conditions = rule.conditions.toMutableList().apply { removeAt(index) })
                            },
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            rule = rule.copy(
                                conditions = rule.conditions + EggikuSmart.Condition(EggikuSmart.Type.STARTED),
                            )
                        },
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Text(stringResource(KMR.strings.smart_add_condition), modifier = Modifier.padding(start = 6.dp))
                    }
                    Picker(
                        label = stringResource(KMR.strings.smart_sort),
                        current = stringResource(sortLabel(rule.sort)),
                        options = EggikuSmart.Sort.entries.map { it to sortLabel(it) },
                        onPick = { rule = rule.copy(sort = it) },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = rule.conditions.isNotEmpty() && (name.isNotBlank() || initial.preset),
                    onClick = { onSave(rule.copy(name = name.trim())) },
                ) { Text(stringResource(KMR.strings.smart_save)) }
            },
            dismissButton = {
                Row {
                    if (!isNew && !initial.preset) {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(KMR.strings.smart_delete))
                        }
                    }
                    TextButton(onClick = onDismiss) { Text(stringResource(KMR.strings.eggiku_close)) }
                }
            },
        )
    }

    @Composable
    private fun ConditionRow(
        condition: EggikuSmart.Condition,
        categories: List<Category>,
        tags: List<EggikuTags.Tag>,
        onChange: (EggikuSmart.Condition) -> Unit,
        onRemove: () -> Unit,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Picker(
                    label = null,
                    current = stringResource(typeLabel(condition.type)),
                    options = EggikuSmart.Type.entries.map { it to typeLabel(it) },
                    onPick = { onChange(EggikuSmart.Condition(it, defaultNumber(it))) },
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRemove) { Icon(Icons.Outlined.Close, contentDescription = null) }
            }
            when (condition.type.input) {
                EggikuSmart.Input.NUMBER -> OutlinedTextField(
                    value = condition.number.toString(),
                    onValueChange = { text -> text.filter(Char::isDigit).take(6).toLongOrNull()?.let { onChange(condition.copy(number = it)) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.width(140.dp),
                )
                EggikuSmart.Input.TEXT -> OutlinedTextField(
                    value = condition.text,
                    onValueChange = { onChange(condition.copy(text = it)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                EggikuSmart.Input.CATEGORY -> Picker(
                    label = null,
                    current = categories.find { it.id == condition.number }?.name ?: stringResource(KMR.strings.smart_pick),
                    options = categories.map { it to null },
                    optionText = { (it as Category).name },
                    onPick = { onChange(condition.copy(number = it.id, text = it.name)) },
                )
                EggikuSmart.Input.TAG -> Picker(
                    label = null,
                    current = condition.text.ifBlank { stringResource(KMR.strings.smart_pick) },
                    options = tags.map { it to null },
                    optionText = { (it as EggikuTags.Tag).name },
                    onPick = { onChange(condition.copy(text = it.name)) },
                )
                EggikuSmart.Input.NONE -> Unit
            }
        }
    }

    @Composable
    private fun <T> Picker(
        label: String?,
        current: String,
        options: List<Pair<T, StringResource?>>,
        onPick: (T) -> Unit,
        modifier: Modifier = Modifier,
        optionText: @Composable (Any?) -> String = { "" },
    ) {
        var open by remember { mutableStateOf(false) }
        Column(modifier = modifier) {
            if (label != null) Text(label, style = MaterialTheme.typography.labelMedium)
            OutlinedButton(onClick = { open = true }) {
                Text(current)
                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.padding(start = 6.dp))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { (value, res) ->
                    DropdownMenuItem(
                        text = { Text(res?.let { stringResource(it) } ?: optionText(value)) },
                        onClick = {
                            open = false
                            onPick(value)
                        },
                    )
                }
            }
        }
    }

    private fun defaultNumber(type: EggikuSmart.Type): Long = when (type) {
        EggikuSmart.Type.UNREAD_AT_LEAST, EggikuSmart.Type.UNREAD_AT_MOST -> 1
        EggikuSmart.Type.RATING_AT_LEAST -> 4
        EggikuSmart.Type.NO_UPDATE_DAYS, EggikuSmart.Type.LAST_READ_OVER_DAYS -> 90
        else -> 30
    }

    private fun typeLabel(type: EggikuSmart.Type): StringResource = when (type) {
        EggikuSmart.Type.UNREAD_AT_LEAST -> KMR.strings.smart_t_unread_at_least
        EggikuSmart.Type.UNREAD_AT_MOST -> KMR.strings.smart_t_unread_at_most
        EggikuSmart.Type.STARTED -> KMR.strings.smart_t_started
        EggikuSmart.Type.NEVER_READ -> KMR.strings.smart_t_never_read
        EggikuSmart.Type.LAST_READ_WITHIN_DAYS -> KMR.strings.smart_t_read_within
        EggikuSmart.Type.LAST_READ_OVER_DAYS -> KMR.strings.smart_t_read_over
        EggikuSmart.Type.UPDATED_WITHIN_DAYS -> KMR.strings.smart_t_updated_within
        EggikuSmart.Type.NO_UPDATE_DAYS -> KMR.strings.smart_t_no_update
        EggikuSmart.Type.ADDED_WITHIN_DAYS -> KMR.strings.smart_t_added_within
        EggikuSmart.Type.STATUS_ONGOING -> KMR.strings.smart_t_ongoing
        EggikuSmart.Type.STATUS_COMPLETED -> KMR.strings.smart_t_completed
        EggikuSmart.Type.STATUS_HIATUS -> KMR.strings.smart_t_hiatus
        EggikuSmart.Type.NOT_COMPLETED -> KMR.strings.smart_t_not_completed
        EggikuSmart.Type.IN_CATEGORY -> KMR.strings.smart_t_in_category
        EggikuSmart.Type.NOT_IN_CATEGORY -> KMR.strings.smart_t_not_in_category
        EggikuSmart.Type.GENRE_CONTAINS -> KMR.strings.smart_t_genre
        EggikuSmart.Type.RATING_AT_LEAST -> KMR.strings.smart_t_rating
        EggikuSmart.Type.HAS_TAG -> KMR.strings.smart_t_tag
        EggikuSmart.Type.HAS_DOWNLOADS -> KMR.strings.smart_t_downloads
        EggikuSmart.Type.COOKING -> KMR.strings.smart_t_cooking
        EggikuSmart.Type.TRACKED -> KMR.strings.smart_t_tracked
    }

    private fun sortLabel(sort: EggikuSmart.Sort): StringResource = when (sort) {
        EggikuSmart.Sort.LAST_READ -> KMR.strings.smart_s_last_read
        EggikuSmart.Sort.MOST_UNREAD -> KMR.strings.smart_s_most_unread
        EggikuSmart.Sort.LEAST_UNREAD -> KMR.strings.smart_s_least_unread
        EggikuSmart.Sort.RECENTLY_UPDATED -> KMR.strings.smart_s_recently_updated
        EggikuSmart.Sort.OLDEST_UPDATE -> KMR.strings.smart_s_oldest_update
        EggikuSmart.Sort.TITLE -> KMR.strings.smart_s_title
        EggikuSmart.Sort.RATING -> KMR.strings.smart_s_rating
        EggikuSmart.Sort.DATE_ADDED -> KMR.strings.smart_s_date_added
    }
}
// KMK <--
