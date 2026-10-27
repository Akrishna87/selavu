@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.github.akrishna87.selavu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.akrishna87.selavu.AppViewModel
import io.github.akrishna87.selavu.core.Ledger
import io.github.akrishna87.selavu.core.Money
import io.github.akrishna87.selavu.core.Repeat
import io.github.akrishna87.selavu.core.Transaction
import io.github.akrishna87.selavu.core.TxType
import io.github.akrishna87.selavu.core.lastAccount
import io.github.akrishna87.selavu.core.lastCategory
import io.github.akrishna87.selavu.core.newId
import io.github.akrishna87.selavu.core.shortDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The amount, large and centred with the currency symbol right before it. The field is sized to
 * what's typed, so the symbol and the digits stay together as the number grows.
 */
@Composable
private fun AmountField(value: String, onChange: (String) -> Unit, currency: String, modifier: Modifier, onDone: () -> Unit) {
    val style = MaterialTheme.typography.displayMedium
    val measurer = rememberTextMeasurer()
    val width = with(LocalDensity.current) {
        measurer.measure(value.ifEmpty { "0" }, style).size.width.toDp() + 4.dp
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(currency, style = style, color = if (value.isEmpty()) palette.tertiaryLabel else palette.label)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            modifier = modifier.width(width),
            textStyle = style.copy(color = palette.label),
            singleLine = true,
            cursorBrush = SolidColor(palette.tint),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text("0", style = style, color = palette.tertiaryLabel)
                    inner()
                }
            },
        )
    }
}

/** Adding a new expense, income or transfer, or changing one ([txId] set). */
@Composable
fun EditScreen(vm: AppViewModel, ledger: Ledger, startType: TxType, txId: String?, padding: PaddingValues) {
    val existing = remember(txId) { txId?.let { id -> ledger.transactions.firstOrNull { it.id == id } } }
    val today = LocalDate.now()
    var type by rememberSaveable { mutableStateOf(existing?.type ?: startType) }
    var amountText by rememberSaveable { mutableStateOf(existing?.let { Money.plain(it.amount) } ?: "") }
    var categoryId by rememberSaveable { mutableStateOf(existing?.categoryId ?: ledger.lastCategory(startType)) }
    var dateEpoch by rememberSaveable { mutableStateOf((existing?.date ?: vm.defaultDate()).toEpochDay()) }
    var note by rememberSaveable { mutableStateOf(existing?.note ?: "") }
    var repeat by rememberSaveable { mutableStateOf(Repeat.NONE) }
    // New entries start in the currency group being looked at.
    val group = vm.currency(ledger)
    fun startAccount(t: TxType): String {
        val last = ledger.lastAccount(t)
        return if (ledger.account(last)?.currency == group) last else ledger.accounts(group).firstOrNull()?.id ?: last
    }
    var accountId by rememberSaveable { mutableStateOf(existing?.accountId ?: startAccount(startType)) }
    var toAccountId by rememberSaveable {
        mutableStateOf(existing?.toAccountId ?: ledger.accounts.firstOrNull { it.id != accountId }?.id)
    }
    var toAmountText by rememberSaveable { mutableStateOf(existing?.takeIf { it.type == TxType.TRANSFER }?.let { Money.plain(it.toAmount) } ?: "") }
    var showDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var newCategory by remember { mutableStateOf(false) }
    var triedSave by remember { mutableStateOf(false) }
    val date = LocalDate.ofEpochDay(dateEpoch)
    val amount = Money.parse(amountText)
    val recurring = existing?.recurringId?.let { id -> ledger.recurring.firstOrNull { it.id == id } }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (existing == null) runCatching { focus.requestFocus() } }
    val noun = when (type) {
        TxType.EXPENSE -> "expense"
        TxType.INCOME -> "income"
        TxType.TRANSFER -> "transfer"
    }
    val account = ledger.account(accountId) ?: ledger.accounts.first()
    val toAccount = ledger.account(toAccountId)?.takeIf { it.id != account.id }
    // Between currencies, what arrived is typed separately; within one currency it's the same amount.
    val crossCurrency = toAccount != null && toAccount.currency != account.currency
    val toAmount = if (crossCurrency) Money.parse(toAmountText) else amount
    val toFocus = remember { FocusRequester() }

    fun save() {
        triedSave = true
        if (amount == null) return
        val tx = if (type == TxType.TRANSFER) {
            val to = toAccount ?: return
            val received = toAmount ?: return
            existing?.copy(
                type = type, amount = amount, categoryId = "", date = date, note = note.trim(),
                accountId = account.id, toAccountId = to.id, toAmount = received, recurringId = null,
            ) ?: Transaction(newId(), type, amount, "", date, note.trim(), accountId = account.id, toAccountId = to.id, toAmount = received)
        } else {
            val cat = categoryId?.takeIf { id -> ledger.category(id)?.type == type } ?: return
            existing?.copy(
                type = type, amount = amount, categoryId = cat, date = date, note = note.trim(),
                accountId = account.id, toAccountId = null, toAmount = 0,
            ) ?: Transaction(newId(), type, amount, cat, date, note.trim(), accountId = account.id)
        }
        vm.saveTransaction(tx, if (type == TxType.TRANSFER) Repeat.NONE else repeat)
        vm.back()
    }

    Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
        NavBar(
            title = (if (existing == null) "New " else "Edit ") + noun,
            leading = { TextAction("Cancel") { vm.back() } },
            trailing = { TextAction("Save", bold = true) { save() } },
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            val types = listOf(TxType.EXPENSE, TxType.INCOME, TxType.TRANSFER)
            Segmented(
                listOf("Expense", "Income", "Transfer"), types.indexOf(type),
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) { i ->
                val t = types[i]
                if (type != t) {
                    type = t
                    if (t != TxType.TRANSFER) categoryId = ledger.lastCategory(t)
                }
            }

            Spacer(Modifier.height(8.dp))
            GroupCard {
                Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (crossCurrency && type == TxType.TRANSFER) "Amount sent" else "Amount",
                        style = MaterialTheme.typography.bodySmall, color = palette.secondaryLabel,
                    )
                    AmountField(
                        amountText, { v -> amountText = v.filter { it.isDigit() || it == '.' || it == ',' }.take(16) },
                        account.currency, Modifier.focusRequester(focus),
                    ) { save() }
                }
            }
            if (triedSave && amount == null) SectionFooter("Enter an amount.")

            if (type == TxType.TRANSFER) {
                SectionHeader("From")
                AccountPills(ledger, accountId) { accountId = it }
                SectionHeader("To")
                if (ledger.accounts.size < 2) {
                    GroupCard {
                        ListRow("Add another account in Settings → Accounts to move money between them.", titleColor = palette.secondaryLabel)
                    }
                } else {
                    AccountPills(ledger, toAccount?.id, exclude = accountId) { toAccountId = it }
                    if (triedSave && toAccount == null) SectionFooter("Choose where the money went.")
                }
                if (crossCurrency) {
                    Spacer(Modifier.height(12.dp))
                    GroupCard {
                        Column(
                            Modifier.fillMaxWidth().tap { runCatching { toFocus.requestFocus() } }.padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("Amount received", style = MaterialTheme.typography.bodySmall, color = palette.secondaryLabel)
                            AmountField(
                                toAmountText, { v -> toAmountText = v.filter { it.isDigit() || it == '.' || it == ',' }.take(16) },
                                toAccount!!.currency, Modifier.focusRequester(toFocus),
                            ) { save() }
                        }
                    }
                    SectionFooter(
                        if (triedSave && toAmount == null) "Enter what arrived in ${toAccount!!.name}."
                        else "What arrived in ${toAccount!!.name}, in its currency. Nothing is converted.",
                    )
                }
            } else {
                SectionHeader("Account")
                AccountPills(ledger, accountId) { accountId = it }
            }

            if (type != TxType.TRANSFER) SectionHeader("Category")
            if (type != TxType.TRANSFER) GroupCard {
                FlowRow(
                    Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ledger.categories(type).forEach { c ->
                        Pill("${c.emoji}  ${c.name}", c.id == categoryId) { categoryId = c.id }
                    }
                    Pill("＋ New", false) { newCategory = true }
                }
            }

            SectionHeader("Date")
            GroupCard {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Today", date == today) { dateEpoch = today.toEpochDay() }
                    Pill("Yesterday", date == today.minusDays(1)) { dateEpoch = today.minusDays(1).toEpochDay() }
                    val other = date != today && date != today.minusDays(1)
                    Pill(
                        if (other) shortDate(date, today) else "Pick a date", other,
                        leading = {
                            Icon(
                                Icons.Rounded.CalendarMonth, contentDescription = null,
                                tint = if (other) Color.White else palette.tint, modifier = Modifier.size(18.dp).padding(end = 4.dp),
                            )
                        },
                    ) { showDate = true }
                }
            }

            SectionHeader("Note")
            GroupCard {
                PlainField(
                    value = note,
                    onValueChange = { note = it.take(200) },
                    placeholder = "Note (optional)",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                )
            }

            if (type == TxType.TRANSFER) {
                // Transfers are one-offs.
            } else if (existing?.type == TxType.TRANSFER) {
                // A transfer being turned into an expense or income doesn't start repeating.
            } else if (existing == null) {
                SectionHeader("Repeat")
                GroupCard {
                    FlowRow(
                        Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Repeat.entries.forEach { r -> Pill(r.label, repeat == r) { repeat = r } }
                    }
                }
                if (repeat != Repeat.NONE) {
                    SectionFooter(
                        "It will be logged ${repeat.label.lowercase()} on its own, starting " + when (date) {
                            today -> "today."
                            today.minusDays(1) -> "yesterday."
                            else -> "on ${shortDate(date, today)}."
                        },
                    )
                }
            } else if (recurring != null) {
                SectionHeader("Repeat")
                GroupCard {
                    ListRow(
                        "${recurring.repeat.label}, next on ${shortDate(recurring.nextDate, today)}",
                        icon = { Icon(Icons.Rounded.Repeat, contentDescription = null, tint = palette.tint) },
                    )
                    RowDivider()
                    ListRow("Stop repeating", titleColor = palette.red, chevron = false) { vm.stopRecurring(recurring.id) }
                }
                SectionFooter("Changes here apply to this entry only.")
            } else {
                SectionHeader("Repeat")
                GroupCard {
                    ListRow(if (existing.recurringId != null) "Logged by a repeat that has been stopped" else "Doesn't repeat", titleColor = palette.secondaryLabel)
                }
            }

            if (existing != null) {
                Spacer(Modifier.height(32.dp))
                GroupCard {
                    Text(
                        "Delete $noun",
                        style = MaterialTheme.typography.bodyLarge,
                        color = palette.red,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().tap { confirmDelete = true }.padding(vertical = 13.dp),
                    )
                }
            }
        }
    }

    if (showDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextAction("Done", bold = true) {
                    state.selectedDateMillis?.let {
                        dateEpoch = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    }
                    showDate = false
                }
            },
            dismissButton = { TextAction("Cancel") { showDate = false } },
        ) { DatePicker(state) }
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = palette.card,
            title = { Text("Delete this entry?", style = MaterialTheme.typography.titleMedium) },
            text = {
                Text(
                    if (recurring != null) "Only this entry is deleted. It will keep repeating; use Stop repeating for that."
                    else "This can't be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextAction("Delete", bold = true, color = palette.red) {
                    vm.deleteTransaction(existing.id)
                    confirmDelete = false
                    vm.back()
                }
            },
            dismissButton = { TextAction("Cancel") { confirmDelete = false } },
        )
    }

    if (newCategory) {
        CategoryDialog(null, type, onDismiss = { newCategory = false }) { c ->
            vm.saveCategory(c)
            categoryId = c.id
            newCategory = false
        }
    }
}

/** The accounts to pick from as pills; with more than one currency, each shows its currency. */
@Composable
private fun AccountPills(ledger: Ledger, selected: String?, exclude: String? = null, onPick: (String) -> Unit) {
    val showCurrency = ledger.currencies().size > 1
    GroupCard {
        FlowRow(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ledger.accounts.filter { it.id != exclude }.forEach { a ->
                Pill(
                    "${a.type.emoji}  ${a.name}" + if (showCurrency) "  ${a.currency.trim()}" else "",
                    a.id == selected,
                ) { onPick(a.id) }
            }
        }
    }
}
