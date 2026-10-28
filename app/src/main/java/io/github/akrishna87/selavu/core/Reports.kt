package io.github.akrishna87.selavu.core

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

data class Totals(val income: Long, val expense: Long) {
    val balance: Long get() = income - expense
}

fun List<Transaction>.inPeriod(period: Period) = filter { it.date in period }

fun List<Transaction>.totals() = Totals(
    income = filter { it.type == TxType.INCOME }.sumOf { it.amount },
    expense = filter { it.type == TxType.EXPENSE }.sumOf { it.amount },
)

data class DayGroup(val date: LocalDate, val transactions: List<Transaction>) {
    /** Income minus spending on this day. */
    val net: Long get() = transactions.sumOf {
        when (it.type) {
            TxType.INCOME -> it.amount
            TxType.EXPENSE -> -it.amount
            TxType.TRANSFER -> 0L
        }
    }
}

/** Newest day first; within a day, the most recently added first. */
fun List<Transaction>.byDay(): List<DayGroup> =
    withIndex()
        .groupBy { it.value.date }
        .toSortedMap(reverseOrder())
        .map { (date, items) -> DayGroup(date, items.sortedByDescending { it.index }.map { it.value }) }

data class CategoryShare(val category: Category, val total: Long, val count: Int, val share: Float)

fun Ledger.byCategory(transactions: List<Transaction>, type: TxType): List<CategoryShare> {
    val ofType = transactions.filter { it.type == type }
    val sum = ofType.sumOf { it.amount }
    if (sum == 0L) return emptyList()
    return ofType.groupBy { it.categoryId }
        .map { (id, items) ->
            val cat = category(id) ?: Category(id, "Deleted category", "❔", type, 0xFF768390)
            val total = items.sumOf { it.amount }
            CategoryShare(cat, total, items.size, total.toFloat() / sum)
        }
        .sortedByDescending { it.total }
}

data class Bucket(val label: String, val start: LocalDate, val end: LocalDate, val total: Long)

/** The period split into bars for the trend chart: days of a week or month, months of a year. */
fun trend(period: Period, transactions: List<Transaction>, type: TxType): List<Bucket> {
    val ofType = transactions.filter { it.type == type && it.date in period }
    return when (period.type) {
        PeriodType.DAY -> emptyList()
        PeriodType.WEEK, PeriodType.MONTH -> (0 until period.days).map { i ->
            val d = period.start.plusDays(i.toLong())
            val label = if (period.type == PeriodType.WEEK) d.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)).take(2)
            else d.dayOfMonth.toString()
            Bucket(label, d, d, ofType.filter { it.date == d }.sumOf { it.amount })
        }
        PeriodType.YEAR -> (0 until 12).map { m ->
            val s = period.start.plusMonths(m.toLong())
            val e = s.plusMonths(1).minusDays(1)
            Bucket(
                s.format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)).take(1),
                s, e,
                ofType.filter { it.date in Period(PeriodType.MONTH, s, e) }.sumOf { it.amount },
            )
        }
    }
}

/** Spending per day so far: over the days of [period] up to [today], or all of it if it's over. */
fun averagePerDay(period: Period, total: Long, today: LocalDate): Long {
    val last = if (today in period) today else period.end
    if (last.isBefore(period.start)) return 0
    val days = ChronoUnit.DAYS.between(period.start, last) + 1
    return total / days / 100 * 100 // whole rupees/dollars: "₹73 a day" reads better than "₹73.47"
}

data class BudgetStatus(
    val period: Period,
    val budget: Long,
    val spent: Long,
    /** Days left in the period, counting today; 0 when the period isn't the current one. */
    val daysLeft: Int,
) {
    val left: Long get() = budget - spent
    val fraction: Float get() = if (budget <= 0) 0f else spent.toFloat() / budget
    /** Rounded down to whole rupees/dollars, so spending it each day stays within budget. */
    val perDay: Long get() = if (daysLeft > 0 && left > 0) left / daysLeft / 100 * 100 else 0
    val over: Boolean get() = spent > budget
}

/**
 * The [currency]'s budget for the week or month containing [date], or null when that currency
 * has no budget. Only spending in that currency counts.
 */
fun Ledger.budgetFor(date: LocalDate, today: LocalDate, currency: String = settings.currency): BudgetStatus? {
    val budget = settings.budgets[currency]?.takeIf { it.amount > 0 } ?: return null
    val type = if (budget.period == BudgetPeriod.WEEKLY) PeriodType.WEEK else PeriodType.MONTH
    val period = Period.of(type, date, settings.weekStart())
    val spent = inCurrency(currency).inPeriod(period).filter { it.type == TxType.EXPENSE }.sumOf { it.amount }
    val daysLeft = if (today in period) (ChronoUnit.DAYS.between(today, period.end) + 1).toInt() else 0
    return BudgetStatus(period, budget.amount, spent, daysLeft)
}

/**
 * The entries that belong to a currency group: those in its accounts, plus transfers that
 * arrived in one of its accounts from another currency.
 */
fun Ledger.inCurrency(currency: String): List<Transaction> = transactions.filter { t ->
    currencyOf(t) == currency || (t.type == TxType.TRANSFER && account(t.toAccountId)?.currency == currency)
}

/**
 * What an entry did to the money in [currency]: minus for spending or money sent out,
 * plus for income or money received, null for a transfer between two accounts of that currency.
 */
fun Ledger.flow(t: Transaction, currency: String): Long? = when (t.type) {
    TxType.EXPENSE -> -t.amount
    TxType.INCOME -> t.amount
    TxType.TRANSFER -> {
        val from = currencyOf(t)
        val to = account(t.toAccountId)?.currency
        when {
            from == to -> null
            from == currency -> -t.amount
            else -> t.toAmount
        }
    }
}

/** An account's balance now: its opening balance plus everything in and out of it. */
fun Ledger.balance(accountId: String): Long = balanceOn(accountId, LocalDate.MAX)

/** An account's balance at the end of [date]: the opening balance plus everything dated up to then. */
fun Ledger.balanceOn(accountId: String, date: LocalDate): Long {
    val opening = account(accountId)?.opening ?: 0
    return opening + transactions.filter { !it.date.isAfter(date) }.sumOf { change(it, accountId) }
}

/** What [t] did to the balance of [accountId]. */
fun change(t: Transaction, accountId: String): Long = when {
    t.type == TxType.TRANSFER && t.accountId == accountId && t.toAccountId == accountId -> 0L
    t.type == TxType.TRANSFER && t.accountId == accountId -> -t.amount
    t.type == TxType.TRANSFER && t.toAccountId == accountId -> t.toAmount
    t.accountId != accountId -> 0L
    t.type == TxType.INCOME -> t.amount
    else -> -t.amount
}

/**
 * A credit card's current bill: what was owed on the last statement date, what has been paid
 * into the card since, and when it's due.
 */
data class CardBill(
    val statementDate: LocalDate,
    val dueDate: LocalDate,
    val statementOwed: Long,
    val paid: Long,
) {
    val remaining: Long get() = (statementOwed - paid).coerceAtLeast(0)
    fun overdue(today: LocalDate) = remaining > 0 && today.isAfter(dueDate)
    fun daysLeft(today: LocalDate): Long = ChronoUnit.DAYS.between(today, dueDate)
}

/** The bill of a credit card with statement and due days set, as of [today]; null otherwise. */
fun Ledger.cardBill(accountId: String, today: LocalDate): CardBill? {
    val a = account(accountId) ?: return null
    val statementDay = a.statementDay ?: return null
    val dueDay = a.dueDay ?: return null
    if (a.type != AccountType.CARD) return null
    val statement = onOrBefore(today, statementDay)
    val due = onOrAfter(statement.plusDays(1), dueDay)
    val owed = (-balanceOn(accountId, statement)).coerceAtLeast(0)
    val paid = transactions.filter { it.date.isAfter(statement) && !it.date.isAfter(today) }
        .sumOf { change(it, accountId).coerceAtLeast(0) }
    return CardBill(statement, due, owed, paid)
}

/** The day [day] of [month]'s month, or its last day when the month is shorter. */
private fun dayIn(month: LocalDate, day: Int): LocalDate = month.withDayOfMonth(minOf(day, month.lengthOfMonth()))

/** The latest date on or before [date] falling on day [day] of its month. */
fun onOrBefore(date: LocalDate, day: Int): LocalDate {
    val thisMonth = dayIn(date, day)
    return if (!thisMonth.isAfter(date)) thisMonth else dayIn(date.minusMonths(1), day)
}

/** The earliest date on or after [date] falling on day [day] of its month. */
fun onOrAfter(date: LocalDate, day: Int): LocalDate {
    val thisMonth = dayIn(date, day)
    return if (!thisMonth.isBefore(date)) thisMonth else dayIn(date.plusMonths(1), day)
}

/** Cards with something still to pay that is due within [days] days (or already overdue). */
fun Ledger.billsDueWithin(days: Long, today: LocalDate): List<Pair<Account, CardBill>> =
    accounts.mapNotNull { a -> cardBill(a.id, today)?.let { a to it } }
        .filter { (_, b) -> b.remaining > 0 && b.daysLeft(today) <= days }

/** The account used most recently for [type] (new entries start there), else the main currency's first. */
fun Ledger.lastAccount(type: TxType): String =
    transactions.lastOrNull { it.type == type && account(it.accountId) != null }?.accountId
        ?: accounts(settings.currency).firstOrNull()?.id
        ?: accounts.first().id

/** The category used most recently for [type], to pre-select it for the next entry. */
fun Ledger.lastCategory(type: TxType): String? =
    transactions.lastOrNull { it.type == type && category(it.categoryId) != null }?.categoryId
        ?: categories(type).firstOrNull()?.id
