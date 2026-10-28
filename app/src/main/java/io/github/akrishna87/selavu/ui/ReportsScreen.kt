package io.github.akrishna87.selavu.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.akrishna87.selavu.AppViewModel
import io.github.akrishna87.selavu.Screen
import io.github.akrishna87.selavu.core.Bucket
import io.github.akrishna87.selavu.core.CategoryShare
import io.github.akrishna87.selavu.core.AccountType
import io.github.akrishna87.selavu.core.Ledger
import io.github.akrishna87.selavu.core.Transaction
import io.github.akrishna87.selavu.core.Money
import io.github.akrishna87.selavu.core.PeriodType
import io.github.akrishna87.selavu.core.TxType
import io.github.akrishna87.selavu.core.averagePerDay
import io.github.akrishna87.selavu.core.balance
import io.github.akrishna87.selavu.core.byCategory
import io.github.akrishna87.selavu.core.cardBill
import io.github.akrishna87.selavu.core.change
import io.github.akrishna87.selavu.core.shortDate
import io.github.akrishna87.selavu.core.dayHeading
import io.github.akrishna87.selavu.core.inCurrency
import io.github.akrishna87.selavu.core.inPeriod
import io.github.akrishna87.selavu.core.trend
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun ReportsScreen(vm: AppViewModel, ledger: Ledger, padding: PaddingValues) {
    var type by rememberSaveable { mutableStateOf(TxType.EXPENSE) }
    val period = vm.period()
    val today = LocalDate.now()
    val currency = vm.currency(ledger)
    val inPeriod = ledger.inCurrency(currency).inPeriod(period)
    val shares = ledger.byCategory(inPeriod, type)
    val total = shares.sumOf { it.total }
    val buckets = trend(period, inPeriod, type)

    Column(Modifier.fillMaxSize().padding(padding)) {
        CurrencySwitcher(ledger.currencies(), currency, vm::chooseCurrency)
        PeriodHeader(vm.periodType, period, vm::choosePeriodType, vm::shiftPeriod, vm::backToToday)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Segmented(
                    listOf("Spending", "Income"), if (type == TxType.EXPENSE) 0 else 1,
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) { type = if (it == 0) TxType.EXPENSE else TxType.INCOME }
            }
            if (shares.isEmpty()) {
                item {
                    EmptyState(
                        "📊", if (type == TxType.EXPENSE) "No spending in this period" else "No income in this period",
                        "Charts appear here once there's something to show.",
                    )
                }
                return@LazyColumn
            }
            item {
                Spacer(Modifier.height(8.dp))
            }
            item {
                GroupCard {
                    Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                        Donut(shares, Modifier.size(190.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                if (type == TxType.EXPENSE) "Spent" else "Earned",
                                style = MaterialTheme.typography.bodySmall, color = palette.secondaryLabel,
                            )
                            Text(Money.format(total, currency), style = MaterialTheme.typography.titleLarge, color = palette.label)
                            if (period.type != PeriodType.DAY) {
                                Text(
                                    "${Money.format(averagePerDay(period, total, today), currency)} a day",
                                    style = MaterialTheme.typography.bodySmall, color = palette.secondaryLabel,
                                )
                            }
                        }
                    }
                }
            }
            item { SectionHeader("By category") }
            item {
                GroupCard {
                    shares.forEachIndexed { i, s ->
                        if (i > 0) RowDivider(inset = 62.dp)
                        ShareRow(s, currency) { vm.open(Screen.CategoryDetail(s.category.id)) }
                    }
                }
            }
            if (buckets.isNotEmpty()) {
                item { SectionHeader(if (period.type == PeriodType.YEAR) "By month" else "By day") }
                item {
                    GroupCard {
                        Bars(buckets, currency, if (type == TxType.EXPENSE) palette.tint else palette.green, Modifier.padding(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Donut(shares: List<CategoryShare>, modifier: Modifier) {
    val track = palette.fill
    val gapColor = palette.card
    val colors = shares.map { categoryColor(it.category.color) }
    Canvas(modifier.semantics { contentDescription = "Chart by category" }) {
        val stroke = size.minDimension * 0.13f
        val d = size.minDimension - stroke
        val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
        drawArc(track, 0f, 360f, false, topLeft, Size(d, d), style = Stroke(stroke))
        var start = -90f
        shares.forEachIndexed { i, s ->
            val sweep = s.share * 360f
            drawArc(colors[i], start, sweep, false, topLeft, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Butt))
            start += sweep
        }
        // Thin gaps between slices in the card's colour, unless one category is everything.
        if (shares.size > 1) {
            start = -90f
            shares.forEach { s ->
                drawArc(gapColor, start - 0.8f, 1.6f, false, topLeft, Size(d, d), style = Stroke(stroke + 2f))
                start += s.share * 360f
            }
        }
    }
}

@Composable
private fun ShareRow(s: CategoryShare, currency: String, onClick: () -> Unit) {
    val color = categoryColor(s.category.color)
    Row(
        Modifier.fillMaxWidth().tap(onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIcon(s.category)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(s.category.name, style = MaterialTheme.typography.bodyLarge, color = palette.label, modifier = Modifier.weight(1f))
                Text(Money.format(s.total, currency), style = MaterialTheme.typography.bodyLarge, color = palette.label)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(palette.fill)) {
                    Box(Modifier.fillMaxWidth(s.share.coerceIn(0f, 1f)).height(4.dp).clip(RoundedCornerShape(2.dp)).background(color))
                }
                Text(
                    "${(s.share * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.secondaryLabel,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(44.dp),
                )
            }
        }
    }
}

@Composable
private fun Bars(buckets: List<Bucket>, currency: String, color: Color, modifier: Modifier) {
    val max = buckets.maxOf { it.total }.coerceAtLeast(1)
    val track = palette.fill
    val today = LocalDate.now()
    Column(modifier) {
        Text("Highest", style = MaterialTheme.typography.bodySmall, color = palette.secondaryLabel)
        Text(Money.format(max, currency), style = MaterialTheme.typography.titleMedium, color = palette.label)
        Spacer(Modifier.height(12.dp))
        Canvas(Modifier.fillMaxWidth().height(130.dp)) {
            val slot = size.width / buckets.size
            val bar = (slot * 0.56f).coerceAtLeast(2f)
            val r = CornerRadius(minOf(bar / 2, 4.dp.toPx()))
            buckets.forEachIndexed { i, b ->
                val h = size.height * b.total / max
                val x = i * slot + (slot - bar) / 2
                val isNow = !today.isBefore(b.start) && !today.isAfter(b.end)
                drawRoundRect(track, Offset(x, 0f), Size(bar, size.height), r)
                if (b.total > 0) {
                    drawRoundRect(if (isNow) color else color.copy(alpha = 0.55f), Offset(x, size.height - h), Size(bar, h), r)
                }
            }
        }
        // Labels: every bar for a week or year, about every fifth day for a month.
        val step = if (buckets.size > 12) 5 else 1
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            buckets.forEachIndexed { i, b ->
                Text(
                    if (i % step == 0) b.label else "",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.secondaryLabel,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/** Everything in one category for the period being looked at. */
@Composable
fun CategoryDetailScreen(vm: AppViewModel, ledger: Ledger, categoryId: String, padding: PaddingValues) {
    val category = ledger.category(categoryId)
    val period = vm.period()
    val today = LocalDate.now()
    val currency = vm.currency(ledger)
    val entries = ledger.inCurrency(currency).inPeriod(period).filter { it.categoryId == categoryId }.sortedByDescending { it.date }
    Column(Modifier.fillMaxSize().padding(padding)) {
        NavBar(
            title = "${category?.emoji ?: ""} ${category?.name ?: "Category"}".trim(),
            leading = { BackAction("Reports") { vm.back() } },
        )
        PeriodHeader(vm.periodType, period, vm::choosePeriodType, vm::shiftPeriod, vm::backToToday)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                SectionHeader(
                    "${entries.size} ${if (entries.size == 1) "entry" else "entries"}",
                    Money.format(entries.sumOf { it.amount }, currency),
                )
            }
            if (entries.isEmpty()) {
                item { EmptyState("🗂️", "Nothing in this period", "Try another period with the arrows above.") }
            } else {
                item {
                    GroupCard {
                        entries.forEachIndexed { i, t ->
                            if (i > 0) RowDivider(inset = 62.dp)
                            TransactionRow(t, ledger, currency, subtitle = dayHeading(t.date, today)) {
                                vm.open(Screen.Edit(t.type, t.id))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Everything in and out of one account, newest first, under its balance. */
@Composable
fun AccountDetailScreen(vm: AppViewModel, ledger: Ledger, accountId: String, padding: PaddingValues) {
    val account = ledger.account(accountId) ?: return
    val today = LocalDate.now()
    val entries = ledger.transactions.filter { it.accountId == accountId || it.toAccountId == accountId }
        .withIndex().sortedWith(compareByDescending<IndexedValue<Transaction>> { it.value.date }.thenByDescending { it.index })
        .map { it.value }
    Column(Modifier.fillMaxSize().padding(padding)) {
        NavBar(title = account.name, leading = { BackAction("Back") { vm.back() } })
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
            item {
                GroupCard {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "${account.type.label} · ${account.currency.trim()}",
                            style = MaterialTheme.typography.bodyMedium, color = palette.secondaryLabel,
                        )
                        Text(balanceText(account, ledger.balance(accountId)), style = MaterialTheme.typography.displaySmall, color = palette.label)
                        if (account.opening != 0L) {
                            Text(
                                (if (account.type == AccountType.CARD && account.opening < 0) "Owed at the start: " + Money.format(-account.opening, account.currency)
                                else "Opening balance: " + Money.format(account.opening, account.currency)),
                                style = MaterialTheme.typography.bodySmall, color = palette.secondaryLabel,
                            )
                        }
                    }
                }
            }
            if (account.type == AccountType.CARD) item { CardSection(vm, ledger, account, today) }
            item { SectionHeader("${entries.size} ${if (entries.size == 1) "entry" else "entries"}") }
            if (entries.isEmpty()) {
                item { EmptyState(account.type.emoji, "Nothing in this account yet", "Pick it when adding an expense, income or transfer.") }
            } else {
                item {
                    GroupCard {
                        entries.forEachIndexed { i, t ->
                            if (i > 0) RowDivider(inset = 62.dp)
                            // Amounts are shown as they changed this account's balance, in its currency.
                            TransactionRow(t, ledger, account.currency, subtitle = dayHeading(t.date, today), change = change(t, accountId)) {
                                vm.open(Screen.Edit(t.type, t.id))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A credit card's limit, its current bill, and a button to pay it. */
@Composable
private fun CardSection(vm: AppViewModel, ledger: Ledger, card: io.github.akrishna87.selavu.core.Account, today: LocalDate) {
    val owed = (-ledger.balance(card.id)).coerceAtLeast(0)
    val bill = ledger.cardBill(card.id, today)
    Column {
        if (card.limit > 0) {
            SectionHeader("Credit")
            GroupCard {
                ListRow("Credit limit", value = Money.format(card.limit, card.currency))
                RowDivider()
                ListRow("Available", value = Money.format(card.limit - owed, card.currency))
            }
        }
        SectionHeader("Bill")
        GroupCard {
            if (bill != null) {
                ListRow("Statement on ${shortDate(bill.statementDate, today)}", value = Money.format(bill.statementOwed, card.currency))
                RowDivider()
                ListRow("Paid since", value = Money.format(bill.paid, card.currency))
                RowDivider()
                ListRow(
                    "Due ${shortDate(bill.dueDate, today)}",
                    value = Money.format(bill.remaining, card.currency),
                    subtitle = billLine(bill, card.currency, today),
                    subtitleColor = billColor(bill, today),
                )
                RowDivider()
            }
            val toPay = bill?.remaining?.takeIf { it > 0 } ?: owed.takeIf { it > 0 }
            ListRow("Pay bill", titleColor = palette.tint, chevron = false) {
                vm.open(Screen.Edit(TxType.TRANSFER, toAccountId = card.id, amount = toPay))
            }
        }
        if (bill == null) SectionFooter("Set the statement and due days for this card in Settings → Accounts to see each bill and when it's due.")
    }
}
