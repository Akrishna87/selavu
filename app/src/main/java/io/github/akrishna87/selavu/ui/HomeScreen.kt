package io.github.akrishna87.selavu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.akrishna87.selavu.AppViewModel
import io.github.akrishna87.selavu.Screen
import io.github.akrishna87.selavu.core.BudgetPeriod
import io.github.akrishna87.selavu.core.BudgetStatus
import io.github.akrishna87.selavu.core.Ledger
import io.github.akrishna87.selavu.core.Money
import io.github.akrishna87.selavu.core.PeriodType
import io.github.akrishna87.selavu.core.Totals
import io.github.akrishna87.selavu.core.TxType
import io.github.akrishna87.selavu.core.budgetFor
import io.github.akrishna87.selavu.core.byDay
import io.github.akrishna87.selavu.core.dayHeading
import io.github.akrishna87.selavu.core.inPeriod
import io.github.akrishna87.selavu.core.totals
import java.time.LocalDate

@Composable
fun HomeScreen(vm: AppViewModel, ledger: Ledger, padding: PaddingValues) {
    val period = vm.period()
    val today = LocalDate.now()
    val currency = ledger.settings.currency
    val inPeriod = ledger.transactions.inPeriod(period)
    val totals = inPeriod.totals()
    val days = inPeriod.byDay()
    // The budget card shows when looking at the same kind of period the budget is set for,
    // and on a single day, where it says how much is left for that day.
    val budgetType = if (ledger.settings.budgetPeriod == BudgetPeriod.WEEKLY) PeriodType.WEEK else PeriodType.MONTH
    val budget = when (vm.periodType) {
        budgetType, PeriodType.DAY -> ledger.budgetFor(period.start, today)
        else -> null
    }

    Column(Modifier.fillMaxSize().padding(padding)) {
        PeriodHeader(vm.periodType, period, vm::choosePeriodType, vm::shiftPeriod, vm::backToToday)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)) {
            item { SummaryCard(totals, currency) }
            if (budget != null) {
                item { Spacer(Modifier.height(12.dp)) }
                item { BudgetCard(budget, currency, perDayOnly = vm.periodType == PeriodType.DAY) }
            }
            if (days.isEmpty()) {
                item {
                    EmptyState(
                        "👛", "Nothing logged ${if (today in period) "yet" else "here"}",
                        "Tap Expense or Income below to add one.",
                    )
                }
            }
            days.forEach { day ->
                item(key = "h" + day.date) {
                    SectionHeader(dayHeading(day.date, today), Money.format(day.net, currency, sign = true))
                }
                item(key = "d" + day.date) {
                    GroupCard {
                        day.transactions.forEachIndexed { i, t ->
                            if (i > 0) RowDivider(inset = 62.dp)
                            TransactionRow(t, ledger.category(t.categoryId), currency) {
                                vm.open(Screen.Edit(t.type, t.id))
                            }
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TintedButton("Expense", Icons.Rounded.Remove, palette.red, Modifier.weight(1f)) { vm.open(Screen.Edit(TxType.EXPENSE)) }
            TintedButton("Income", Icons.Rounded.Add, palette.green, Modifier.weight(1f)) { vm.open(Screen.Edit(TxType.INCOME)) }
        }
    }
}

/** iOS "tinted" button: a light wash of the colour, with the label in the colour itself. */
@Composable
private fun TintedButton(label: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(50.dp).clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = if (palette.dark) 0.22f else 0.13f)).tap(onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = color)
    }
}

@Composable
private fun SummaryCard(totals: Totals, currency: String) {
    GroupCard {
        Column(Modifier.padding(16.dp)) {
            Text("Balance", style = MaterialTheme.typography.bodyMedium, color = palette.secondaryLabel)
            Text(
                Money.format(totals.balance, currency),
                style = MaterialTheme.typography.displaySmall, color = palette.label,
            )
        }
        RowDivider()
        Row(Modifier.fillMaxWidth().padding(16.dp)) {
            Figure("Income", Money.format(totals.income, currency), palette.green, Modifier.weight(1f))
            Figure("Expenses", Money.format(totals.expense, currency), palette.red, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Figure(label: String, value: String, dot: Color, modifier: Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = palette.secondaryLabel)
        }
        Text(value, style = MaterialTheme.typography.titleMedium, color = palette.label)
    }
}

@Composable
private fun BudgetCard(b: BudgetStatus, currency: String, perDayOnly: Boolean) {
    val bar = when {
        b.fraction >= 1f -> palette.red
        b.fraction >= 0.8f -> palette.orange
        else -> palette.green
    }
    GroupCard {
        Column(Modifier.padding(16.dp)) {
            val which = if (b.period.type == PeriodType.WEEK) "week" else "month"
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(
                    if (b.over) "Over budget by ${Money.format(-b.left, currency)}" else "${Money.format(b.left, currency)} left",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (b.over) palette.red else palette.label,
                    modifier = Modifier.weight(1f),
                )
                Text("of ${Money.format(b.budget, currency)}", style = MaterialTheme.typography.bodyMedium, color = palette.secondaryLabel)
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(palette.fill)) {
                Box(Modifier.fillMaxWidth(b.fraction.coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(bar))
            }
            Spacer(Modifier.height(10.dp))
            val line = when {
                b.over -> "This $which's budget is used up."
                b.daysLeft > 0 -> "You can spend ${Money.format(b.perDay, currency)} a day for the next ${b.daysLeft} ${if (b.daysLeft == 1) "day" else "days"}."
                else -> "Spent ${Money.format(b.spent, currency)} of this $which's budget."
            }
            Text(
                if (perDayOnly && !b.over && b.daysLeft > 0) "${Money.format(b.perDay, currency)} a day keeps you within this $which's budget." else line,
                style = MaterialTheme.typography.bodySmall,
                color = palette.secondaryLabel,
            )
        }
    }
}
