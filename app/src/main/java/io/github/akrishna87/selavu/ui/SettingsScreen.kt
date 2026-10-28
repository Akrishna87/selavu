@file:OptIn(ExperimentalLayoutApi::class)

package io.github.akrishna87.selavu.ui

import android.Manifest
import android.os.Build
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CurrencyRupee
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Notifications
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
import io.github.akrishna87.selavu.core.Account
import io.github.akrishna87.selavu.core.AccountType
import io.github.akrishna87.selavu.core.Budget
import io.github.akrishna87.selavu.core.BudgetPeriod
import io.github.akrishna87.selavu.core.Currencies
import io.github.akrishna87.selavu.core.accountUsage
import io.github.akrishna87.selavu.core.balance
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
    var editBudget by remember { mutableStateOf<String?>(null) }
    var editCurrency by remember { mutableStateOf(false) }
    val currencies = ledger.currencies()
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let(vm::exportCsv)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importCsv)
    }
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) vm.message = "Notifications are off for Selavu, so reminders can't be shown. You can allow them in the phone's settings."
    }

    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { LargeTitle("Settings") }
        item { SectionHeader("Budget") }
        item {
            GroupCard {
                // One budget per currency; spending in one never counts against another.
                currencies.forEachIndexed { i, c ->
                    if (i > 0) RowDivider(inset = 59.dp)
                    val b = s.budgets[c]
                    ListRow(
                        if (currencies.size == 1) "Spending budget" else "Spending budget (${c.trim()})",
                        value = if (b == null) "Off"
                        else "${Money.format(b.amount, c)} ${if (b.period == BudgetPeriod.WEEKLY) "a week" else "a month"}",
                        icon = { SettingsIcon(Icons.Rounded.Savings, Green) },
                    ) { editBudget = c }
                }
            }
        }
        item { SectionFooter("Home shows how much is left and what you can spend a day.") }
        item { SectionHeader("Your money") }
        item {
            GroupCard {
                ListRow(
                    "Accounts", value = "${ledger.accounts.size}",
                    icon = { SettingsIcon(Icons.Rounded.AccountBalance, Teal) },
                ) { vm.open(Screen.Accounts) }
                RowDivider(inset = 59.dp)
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
                    "Main currency", value = s.currency.trim(),
                    icon = { SettingsIcon(Icons.Rounded.CurrencyRupee, Blue) },
                ) { editCurrency = true }
                RowDivider(inset = 59.dp)
                ListRow(
                    "Weeks start on Monday",
                    icon = { SettingsIcon(Icons.Rounded.CalendarToday, Red) },
                    trailing = {
                        IosSwitch(s.weekStartsMonday) { on -> vm.updateSettings { it.copy(weekStartsMonday = on) } }
                    },
                )
            }
        }
        if (ledger.accounts.any { it.type == AccountType.CARD }) {
            item { SectionHeader("Reminders") }
            item {
                GroupCard {
                    ListRow(
                        "Card bill reminders",
                        icon = { SettingsIcon(Icons.Rounded.Notifications, Red) },
                        trailing = {
                            IosSwitch(s.billReminders) { on ->
                                if (on && Build.VERSION.SDK_INT >= 33) notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                vm.setBillReminders(on)
                            }
                        },
                    )
                }
            }
            item { SectionFooter("A notification from three days before a card's due date, while there's still something to pay. Set each card's statement and due days in Accounts.") }
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

    editBudget?.let { c ->
        BudgetDialog(ledger, c, onDismiss = { editBudget = null }) { amount, period ->
            vm.setBudget(c, Budget(amount, period))
            editBudget = null
        }
    }
    if (editCurrency) CurrencyDialog(s.currency, currencies.takeIf { it.size > 1 }, onDismiss = { editCurrency = false }) { c ->
        vm.setMainCurrency(c)
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
private fun BudgetDialog(ledger: Ledger, currency: String, onDismiss: () -> Unit, onSave: (Long, BudgetPeriod) -> Unit) {
    val b = ledger.settings.budgets[currency]
    var text by rememberSaveable { mutableStateOf(b?.let { Money.plain(it.amount) } ?: "") }
    var period by rememberSaveable { mutableStateOf(b?.period ?: BudgetPeriod.MONTHLY) }
    Alert(if (ledger.currencies().size == 1) "Spending budget" else "Budget in ${currency.trim()}", "Save", onConfirm = { onSave(Money.parse(text) ?: 0, period) }, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Segmented(listOf("Weekly", "Monthly"), if (period == BudgetPeriod.WEEKLY) 0 else 1) {
                period = if (it == 0) BudgetPeriod.WEEKLY else BudgetPeriod.MONTHLY
            }
            WellField(
                text, { v -> text = v.filter { it.isDigit() || it == '.' }.take(14) }, "Budget amount",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), prefix = currency,
            )
            Text("Leave it empty to turn the budget off.", style = MaterialTheme.typography.bodySmall, color = palette.secondaryLabel)
        }
    }
}

/**
 * Choosing the main currency. With several in use, it's one of them (the group Home opens on);
 * with one, any symbol, which relabels everything.
 */
@Composable
private fun CurrencyDialog(current: String, inUse: List<String>?, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(current) }
    Alert("Main currency", "Save", onConfirm = { onSave(text.ifBlank { current }) }, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (inUse != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    inUse.forEach { c -> Pill(c.trim(), text == c) { text = c } }
                }
                Text(
                    "Home and Reports open on this currency. Each account keeps its own.",
                    style = MaterialTheme.typography.bodySmall, color = palette.secondaryLabel,
                )
            } else {
                CurrencyPicker(text) { text = it }
                Text("Only the symbol changes; amounts aren't converted.", style = MaterialTheme.typography.bodySmall, color = palette.secondaryLabel)
            }
        }
    }
}

/** Common currency symbols as pills, plus a field for any other. */
@Composable
private fun CurrencyPicker(value: String, onChange: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Currencies.common.forEach { c -> Pill(c.trim(), value == c) { onChange(c) } }
    }
    // Empty while one of the pills is chosen; typing here picks any other symbol.
    WellField(value.takeIf { it !in Currencies.common }.orEmpty(), { onChange(it.take(5)) }, "Other symbol")
}

@Composable
fun AccountsScreen(vm: AppViewModel, ledger: Ledger, padding: PaddingValues) {
    var editing by remember { mutableStateOf<Account?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Account?>(null) }
    Column(Modifier.fillMaxSize().padding(padding)) {
        NavBar(
            title = "Accounts",
            leading = { BackAction("Settings") { vm.back() } },
            trailing = {
                Box(Modifier.size(44.dp).clip(CircleShape).tap { adding = true }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add, contentDescription = "New account", tint = palette.tint, modifier = Modifier.size(28.dp))
                }
            },
        )
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 32.dp)) {
            ledger.currencies().forEach { c ->
                val list = ledger.accounts(c)
                item(key = "h$c") {
                    SectionHeader(c.trim(), "Total ${Money.format(list.sumOf { ledger.balance(it.id) }, c)}")
                }
                item(key = "c$c") {
                    GroupCard {
                        list.forEachIndexed { i, a ->
                            if (i > 0) RowDivider(inset = 64.dp)
                            ListRow(a.name, value = balanceText(a, ledger.balance(a.id)), icon = { AccountIcon(a) }) { editing = a }
                        }
                    }
                }
            }
            item {
                SectionFooter(
                    "Each account keeps its own currency, and totals are only added up within a currency. " +
                        "Use Transfer to move money between accounts, including from one currency to another.",
                )
            }
        }
    }

    if (adding || editing != null) {
        AccountDialog(
            existing = editing,
            defaultCurrency = ledger.settings.currency,
            canRemove = editing != null && ledger.accounts.size > 1,
            onRemove = { deleting = editing; editing = null },
            onDismiss = { adding = false; editing = null },
        ) { a ->
            vm.saveAccount(a)
            adding = false
            editing = null
        }
    }
    deleting?.let { a ->
        val used = ledger.accountUsage(a.id)
        val others = ledger.accounts(a.currency).filter { it.id != a.id }
        var moveTo by remember(a.id) { mutableStateOf(others.firstOrNull()?.id) }
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = palette.card,
            title = { Text("Remove ${a.name}?", style = MaterialTheme.typography.titleMedium) },
            text = {
                when {
                    used == 0 -> Text("It isn't used by any entry.", style = MaterialTheme.typography.bodyMedium)
                    others.isEmpty() -> Text(
                        "Its $used ${if (used == 1) "entry needs" else "entries need"} another ${a.currency.trim()} account to move to. Add one first, or delete the entries.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Its $used ${if (used == 1) "entry moves" else "entries move"} to:", style = MaterialTheme.typography.bodyMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            others.forEach { o -> Pill("${o.type.emoji} ${o.name}", moveTo == o.id) { moveTo = o.id } }
                        }
                    }
                }
            },
            confirmButton = {
                TextAction("Remove", bold = true, color = palette.red, enabled = used == 0 || others.isNotEmpty()) {
                    vm.deleteAccount(a.id, moveTo)
                    deleting = null
                }
            },
            dismissButton = { TextAction("Cancel") { deleting = null } },
        )
    }
}

/** Adding ([existing] null) or changing an account: name, kind, currency and opening balance. */
@Composable
fun AccountDialog(
    existing: Account?,
    defaultCurrency: String,
    canRemove: Boolean = false,
    onRemove: () -> Unit = {},
    onDismiss: () -> Unit,
    onSave: (Account) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var type by rememberSaveable { mutableStateOf(existing?.type ?: AccountType.BANK) }
    var currency by rememberSaveable { mutableStateOf(existing?.currency ?: defaultCurrency) }
    // A card's opening balance is entered as what's owed, and kept as minus that.
    var openingText by rememberSaveable {
        mutableStateOf(existing?.opening?.takeIf { it != 0L }?.let { Money.plain(kotlin.math.abs(it)) } ?: "")
    }
    var limitText by rememberSaveable { mutableStateOf(existing?.limit?.takeIf { it > 0 }?.let { Money.plain(it) } ?: "") }
    var statementText by rememberSaveable { mutableStateOf(existing?.statementDay?.toString() ?: "") }
    var dueText by rememberSaveable { mutableStateOf(existing?.dueDay?.toString() ?: "") }
    fun day(text: String) = text.toIntOrNull()?.takeIf { it in 1..31 }
    // Both days or neither: a bill needs to know when it's made and when it's due.
    val daysOk = (statementText.isEmpty() && dueText.isEmpty()) || (day(statementText) != null && day(dueText) != null)
    Alert(
        if (existing == null) "New account" else "Edit account", "Save",
        confirmEnabled = name.isNotBlank() && currency.isNotBlank() && (type != AccountType.CARD || daysOk),
        onConfirm = {
            val v = Money.parse(openingText) ?: 0
            val card = type == AccountType.CARD
            onSave(
                (existing ?: Account(newId(), "", type, currency)).copy(
                    name = name.trim(), type = type, currency = currency, opening = if (card) -v else v,
                    limit = if (card) Money.parse(limitText) ?: 0 else 0,
                    statementDay = if (card) day(statementText) else null,
                    dueDay = if (card) day(dueText) else null,
                ),
            )
        },
        onDismiss = onDismiss,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            WellField(name, { name = it.take(30) }, "Name", label = "Name")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AccountType.entries.forEach { t -> Pill("${t.emoji} ${t.label}", type == t) { type = t } }
            }
            Text("Currency", style = MaterialTheme.typography.bodySmall, color = palette.secondaryLabel)
            CurrencyPicker(currency) { currency = it }
            WellField(
                openingText, { v -> openingText = v.filter { it.isDigit() || it == '.' }.take(14) },
                if (type == AccountType.CARD) "Amount owed now" else "Balance now",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), prefix = currency,
                label = if (type == AccountType.CARD) "Amount owed now" else "Balance now",
            )
            if (type == AccountType.CARD) {
                WellField(
                    limitText, { v -> limitText = v.filter { it.isDigit() || it == '.' }.take(14) }, "Credit limit (optional)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), prefix = currency,
                    label = "Credit limit",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        WellField(
                            statementText, { v -> statementText = v.filter { it.isDigit() }.take(2) }, "Statement day",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), label = "Statement day",
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        WellField(
                            dueText, { v -> dueText = v.filter { it.isDigit() }.take(2) }, "Due day",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), label = "Due day",
                        )
                    }
                }
                Text(
                    if (daysOk) "Days of the month, e.g. statement on the 15th, due on the 5th. They let Selavu show each bill and remind you."
                    else "Enter both days, from 1 to 31.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (daysOk) palette.secondaryLabel else palette.red,
                )
            }
            if (canRemove) {
                Text(
                    "Remove account", style = MaterialTheme.typography.bodyLarge, color = palette.red,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).tap(onRemove).padding(vertical = 6.dp),
                )
            }
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

/** A switch in iOS colours: green when on. */
@Composable
private fun IosSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
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
}
