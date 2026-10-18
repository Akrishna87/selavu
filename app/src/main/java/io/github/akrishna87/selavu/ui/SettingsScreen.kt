@file:OptIn(ExperimentalLayoutApi::class)

package io.github.akrishna87.selavu.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CurrencyRupee
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.akrishna87.selavu.AppViewModel
import io.github.akrishna87.selavu.BuildConfig
import io.github.akrishna87.selavu.Screen
import io.github.akrishna87.selavu.core.BudgetPeriod
import io.github.akrishna87.selavu.core.Category
import io.github.akrishna87.selavu.core.CategoryColors
import io.github.akrishna87.selavu.core.Ledger
import io.github.akrishna87.selavu.core.Money
import io.github.akrishna87.selavu.core.TxType
import io.github.akrishna87.selavu.core.newId
import io.github.akrishna87.selavu.core.shortDate
import io.github.akrishna87.selavu.core.usage
import java.time.LocalDate

// The coloured squares in front of settings rows, as in the iOS Settings app.
private val Green = Color(0xFF34C759)
private val Orange = Color(0xFFFF9500)
private val Purple = Color(0xFFAF52DE)
private val Blue = Color(0xFF007AFF)
private val Red = Color(0xFFFF3B30)
private val Teal = Color(0xFF30B0C7)
private val Indigo = Color(0xFF5856D6)

@Composable
fun SettingsScreen(vm: AppViewModel, ledger: Ledger, padding: PaddingValues) {
    val s = ledger.settings
    var editBudget by remember { mutableStateOf(false) }
    var editCurrency by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let(vm::exportCsv)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importCsv)
    }

    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { LargeTitle("Settings") }
        item { SectionHeader("Budget") }
        item {
            GroupCard {
                ListRow(
                    "Spending budget",
                    value = if (s.budget <= 0) "Off"
                    else "${Money.format(s.budget, s.currency)} ${if (s.budgetPeriod == BudgetPeriod.WEEKLY) "a week" else "a month"}",
                    icon = { SettingsIcon(Icons.Rounded.Savings, Green) },
                ) { editBudget = true }
            }
        }
        item { SectionFooter("Home shows how much is left and what you can spend a day.") }
        item { SectionHeader("Your money") }
        item {
            GroupCard {
                ListRow(
                    "Categories", value = "${ledger.categories.size}",
                    icon = { SettingsIcon(Icons.Rounded.Category, Orange) },
                ) { vm.open(Screen.Categories) }
                RowDivider(inset = 59.dp)
                ListRow(
                    "Repeating entries", value = if (ledger.recurring.isEmpty()) "None" else "${ledger.recurring.size} active",
                    icon = { SettingsIcon(Icons.Rounded.Repeat, Purple) },
                ) { vm.open(Screen.Repeating) }
                RowDivider(inset = 59.dp)
                ListRow(
                    "Currency", value = s.currency.trim(),
                    icon = { SettingsIcon(Icons.Rounded.CurrencyRupee, Blue) },
                ) { editCurrency = true }
                RowDivider(inset = 59.dp)
                ListRow(
                    "Weeks start on Monday",
                    icon = { SettingsIcon(Icons.Rounded.CalendarToday, Red) },
                    trailing = {
                        Switch(
                            checked = s.weekStartsMonday,
                            onCheckedChange = { on -> vm.updateSettings { it.copy(weekStartsMonday = on) } },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = palette.green,
                                checkedThumbColor = Color.White,
                                checkedBorderColor = Color.Transparent,
                                uncheckedTrackColor = palette.fill,
                                uncheckedThumbColor = Color.White,
                                uncheckedBorderColor = Color.Transparent,
                            ),
                            thumbContent = {},
                        )
                    },
                )
            }
        }
        item { SectionHeader("Spreadsheet") }
        item {
            GroupCard {
                ListRow("Export to CSV", icon = { SettingsIcon(Icons.Rounded.FileUpload, Teal) }) {
                    export.launch("Selavu ${LocalDate.now()}.csv")
                }
                RowDivider(inset = 59.dp)
                ListRow("Import from CSV", icon = { SettingsIcon(Icons.Rounded.FileDownload, Indigo) }) {
                    importer.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream"))
                }
            }
        }
        item {
            SectionFooter(
                "Export saves all ${ledger.transactions.size} entries for Excel, Google Sheets or a backup. " +
                    "Import reads columns Date, Type, Category, Amount, Note and skips entries already here.",
            )
        }
        item {
            Text(
                "Selavu ${BuildConfig.VERSION_NAME}\nEverything stays on this phone. The app has no internet access.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.secondaryLabel,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp, start = 32.dp, end = 32.dp),
            )
        }
    }

    if (editBudget) BudgetDialog(ledger, onDismiss = { editBudget = false }) { amount, period ->
        vm.updateSettings { it.copy(budget = amount, budgetPeriod = period) }
        editBudget = false
    }
    if (editCurrency) CurrencyDialog(s.currency, onDismiss = { editCurrency = false }) { c ->
        vm.updateSettings { it.copy(currency = c) }
        editCurrency = false
    }
}

/** An alert in the iOS manner: plain title, the content, Cancel and a bold action. */
@Composable
private fun Alert(
    title: String,
    confirm: String,
    confirmEnabled: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.card,
        title = { Text(title, style = MaterialTheme.typography.titleMedium, color = palette.label) },
        text = content,
        confirmButton = { TextAction(confirm, bold = true, enabled = confirmEnabled, onClick = onConfirm) },
        dismissButton = { TextAction("Cancel", onClick = onDismiss) },
    )
}

@Composable
private fun BudgetDialog(ledger: Ledger, onDismiss: () -> Unit, onSave: (Long, BudgetPeriod) -> Unit) {
    val s = ledger.settings
    var text by rememberSaveable { mutableStateOf(if (s.budget > 0) Money.plain(s.budget) else "") }
    var period by rememberSaveable { mutableStateOf(s.budgetPeriod) }
    Alert("Spending budget", "Save", onConfirm = { onSave(Money.parse(text) ?: 0, period) }, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Segmented(listOf("Weekly", "Monthly"), if (period == BudgetPeriod.WEEKLY) 0 else 1) {
                period = if (it == 0) BudgetPeriod.WEEKLY else BudgetPeriod.MONTHLY
            }
            WellField(
                text, { v -> text = v.filter { it.isDigit() || it == '.' }.take(14) }, "Budget amount",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), prefix = s.currency,
            )
            Text("Leave it empty to turn the budget off.", style = MaterialTheme.typography.bodySmall, color = palette.secondaryLabel)
        }
    }
}

@Composable
private fun CurrencyDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(current) }
    Alert("Currency", "Save", onConfirm = { onSave(text.ifBlank { "₹" }) }, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("₹", "$", "€", "£", "¥", "AED ", "SGD ", "RM ").forEach { c ->
                    Pill(c.trim(), text == c) { text = c }
                }
            }
            WellField(text, { text = it.take(5) }, "Symbol shown before amounts")
            Text("Only the symbol changes; amounts aren't converted.", style = MaterialTheme.typography.bodySmall, color = palette.secondaryLabel)
        }
    }
}

@Composable
fun CategoriesScreen(vm: AppViewModel, ledger: Ledger, padding: PaddingValues) {
    var type by rememberSaveable { mutableStateOf(TxType.EXPENSE) }
    var editing by remember { mutableStateOf<Category?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Category?>(null) }
    val list = ledger.categories(type)
    Column(Modifier.fillMaxSize().padding(padding)) {
        NavBar(
            title = "Categories",
            leading = { BackAction("Settings") { vm.back() } },
            trailing = {
                Box(Modifier.size(44.dp).clip(CircleShape).tap { adding = true }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add, contentDescription = "New category", tint = palette.tint, modifier = Modifier.size(28.dp))
                }
            },
        )
        Segmented(
            listOf("Spending", "Income"), if (type == TxType.EXPENSE) 0 else 1,
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) { type = if (it == 0) TxType.EXPENSE else TxType.INCOME }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)) {
            item {
                GroupCard {
                    list.forEachIndexed { i, c ->
                        if (i > 0) RowDivider(inset = 64.dp)
                        val used = ledger.usage(c.id)
                        ListRow(
                            c.name,
                            value = if (used == 0) null else "$used",
                            icon = { CategoryIcon(c) },
                        ) { editing = c }
                    }
                }
            }
            item { SectionFooter("Tap a category to rename it or change its emoji or colour.") }
            if (list.size > 1) {
                item { SectionHeader("Remove") }
                item {
                    GroupCard {
                        list.forEachIndexed { i, c ->
                            if (i > 0) RowDivider()
                            ListRow("Remove ${c.name}", titleColor = palette.red, chevron = false) { deleting = c }
                        }
                    }
                }
            }
        }
    }

    if (adding || editing != null) {
        CategoryDialog(editing, type, onDismiss = { adding = false; editing = null }) { c ->
            vm.saveCategory(c)
            adding = false
            editing = null
        }
    }
    deleting?.let { c ->
        val others = ledger.categories(c.type).filter { it.id != c.id }
        var moveTo by remember(c.id) { mutableStateOf(others.firstOrNull { it.name.startsWith("Other") }?.id ?: others.first().id) }
        val used = ledger.usage(c.id)
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = palette.card,
            title = { Text("Remove ${c.name}?", style = MaterialTheme.typography.titleMedium) },
            text = {
                if (used == 0) Text("It isn't used by any entry.", style = MaterialTheme.typography.bodyMedium)
                else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Its $used ${if (used == 1) "entry moves" else "entries move"} to:", style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        others.forEach { o -> Pill("${o.emoji} ${o.name}", moveTo == o.id) { moveTo = o.id } }
                    }
                }
            },
            confirmButton = { TextAction("Remove", bold = true, color = palette.red) { vm.deleteCategory(c.id, moveTo); deleting = null } },
            dismissButton = { TextAction("Cancel") { deleting = null } },
        )
    }
}

/** Adding ([existing] null) or changing a category: name, emoji and colour. */
@Composable
fun CategoryDialog(existing: Category?, type: TxType, onDismiss: () -> Unit, onSave: (Category) -> Unit) {
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var emoji by rememberSaveable { mutableStateOf(existing?.emoji ?: "🏷️") }
    var color by rememberSaveable { mutableStateOf(existing?.color ?: CategoryColors.all.random()) }
    Alert(
        if (existing == null) "New category" else "Edit category", "Save",
        confirmEnabled = name.isNotBlank(),
        onConfirm = {
            onSave(
                (existing ?: Category(newId(), "", "", type, color))
                    .copy(name = name.trim(), emoji = emoji.trim().ifEmpty { "🏷️" }, color = color),
            )
        },
        onDismiss = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryIcon(Category("", name, emoji.ifBlank { "🏷️" }, type, color), 52.dp)
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    WellField(name, { name = it.take(30) }, "Name")
                    WellField(emoji, { emoji = it.take(8) }, "Emoji")
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CategoryColors.all.forEach { c ->
                    Box(
                        Modifier.size(30.dp).background(categoryColor(c), CircleShape)
                            .then(if (c == color) Modifier.border(3.dp, palette.card, CircleShape).border(5.dp, categoryColor(c), CircleShape) else Modifier)
                            .clip(CircleShape).tap { color = c },
                    )
                }
            }
        }
    }
}

@Composable
fun RepeatingScreen(vm: AppViewModel, ledger: Ledger, padding: PaddingValues) {
    var stopping by remember { mutableStateOf<String?>(null) }
    val today = LocalDate.now()
    val items = ledger.recurring.sortedBy { it.nextDate }
    Column(Modifier.fillMaxSize().padding(padding)) {
        NavBar(title = "Repeating", leading = { BackAction("Settings") { vm.back() } })
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)) {
            if (items.isEmpty()) {
                item { EmptyState("🔁", "Nothing repeats yet", "When adding an expense or income, choose how often it repeats and it will be logged on its own.") }
            } else {
                item {
                    GroupCard {
                        items.forEachIndexed { i, r ->
                            if (i > 0) RowDivider(inset = 62.dp)
                            val c = ledger.category(r.categoryId)
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                CategoryIcon(c)
                                Spacer(Modifier.size(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        listOfNotNull(c?.name, r.note.takeIf { it.isNotBlank() }).joinToString(" · "),
                                        style = MaterialTheme.typography.bodyLarge, color = palette.label,
                                    )
                                    Text(
                                        "${Money.format(r.amount, ledger.settings.currency)}, ${r.repeat.label.lowercase()}. Next on ${shortDate(r.nextDate, today)}",
                                        style = MaterialTheme.typography.bodySmall, color = palette.secondaryLabel,
                                    )
                                }
                                TextAction("Stop", color = palette.red) { stopping = r.id }
                            }
                        }
                    }
                }
                item { SectionFooter("Stopping keeps what was already logged.") }
            }
        }
    }
    stopping?.let { id ->
        AlertDialog(
            onDismissRequest = { stopping = null },
            containerColor = palette.card,
            title = { Text("Stop repeating?", style = MaterialTheme.typography.titleMedium) },
            text = { Text("Nothing more will be logged for it. What it already logged stays in your history.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { TextAction("Stop", bold = true, color = palette.red) { vm.stopRecurring(id); stopping = null } },
            dismissButton = { TextAction("Cancel") { stopping = null } },
        )
    }
}
