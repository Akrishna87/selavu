package io.github.akrishna87.selavu.core

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {
    @Test fun parsesTypedAmounts() {
        assertEquals(25000L, Money.parse("250"))
        assertEquals(25050L, Money.parse("250.5"))
        assertEquals(9999L, Money.parse("₹ 99.99"))
        assertEquals(120000L, Money.parse("1,200"))
        assertEquals(50L, Money.parse(".5"))
        assertNull(Money.parse(""))
        assertNull(Money.parse("0"))
        assertNull(Money.parse("1.234"))
        assertNull(Money.parse("1.2.3"))
    }

    @Test fun formatsWithIndianGroupingForRupees() {
        assertEquals("₹1,25,000", Money.format(1_25_000_00, "₹"))
        assertEquals("₹999", Money.format(999_00, "₹"))
        assertEquals("₹12,34,56,789.05", Money.format(12_34_56_789_05, "₹"))
        assertEquals("$125,000.50", Money.format(125_000_50, "$"))
        assertEquals("−₹40", Money.format(-40_00, "₹"))
        assertEquals("+₹40", Money.format(40_00, "₹", sign = true))
    }

    @Test fun plainAmountsRoundTrip() {
        for (a in listOf(1L, 10L, 50L, 99L, 100L, 25050L, 123456789L)) assertEquals(a, Money.parse(Money.plain(a)))
        assertEquals("250.5", Money.plain(25050))
    }

    @Test fun compactAmounts() {
        assertEquals("₹1.2L", Money.compact(1_20_000_00, "₹"))
        assertEquals("₹45k", Money.compact(45_000_00, "₹"))
        assertEquals("₹800", Money.compact(800_00, "₹"))
        assertEquals("$3.4M", Money.compact(3_400_000_00, "$"))
    }
}

class PeriodTest {
    private val today = LocalDate.of(2026, 10, 17) // a Saturday

    @Test fun weeksFollowTheChosenFirstDay() {
        val mon = Period.of(PeriodType.WEEK, today, DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 10, 12), mon.start)
        assertEquals(LocalDate.of(2026, 10, 18), mon.end)
        val sun = Period.of(PeriodType.WEEK, today, DayOfWeek.SUNDAY)
        assertEquals(LocalDate.of(2026, 10, 11), sun.start)
        assertEquals(LocalDate.of(2026, 10, 4), sun.shift(-1).start)
    }

    @Test fun monthsAndYears() {
        val feb = Period.of(PeriodType.MONTH, LocalDate.of(2028, 2, 10), DayOfWeek.MONDAY)
        assertEquals(29, feb.days)
        assertEquals(LocalDate.of(2028, 3, 31), feb.shift(1).end)
        assertEquals("2026", Period.of(PeriodType.YEAR, today, DayOfWeek.MONDAY).label(today))
    }

    @Test fun labels() {
        val day = Period.of(PeriodType.DAY, today, DayOfWeek.MONDAY)
        assertEquals("Today", day.label(today))
        assertEquals("Yesterday", day.shift(-1).label(today))
        assertEquals("Mon, 5 Oct", day.shift(-12).label(today))
        val week = Period.of(PeriodType.WEEK, today, DayOfWeek.MONDAY)
        assertEquals("This week", week.label(today))
        assertEquals("12 – 18 Oct", week.subLabel(today))
        assertEquals("Last week", week.shift(-1).label(today))
        assertEquals("28 Sep – 4 Oct", week.shift(-2).label(today))
        assertEquals("October", Period.of(PeriodType.MONTH, today, DayOfWeek.MONDAY).label(today))
        assertEquals("December 2025", Period.of(PeriodType.MONTH, today, DayOfWeek.MONDAY).shift(-10).label(today))
    }
}

class LedgerTest {
    private val today = LocalDate.of(2026, 10, 17)
    private var n = 0
    private val id = { "id${n++}" }

    private fun tx(amount: Long, date: LocalDate, cat: String = "food", type: TxType = TxType.EXPENSE) =
        Transaction(id(), type, amount, cat, date)

    @Test fun totalsAndGrouping() {
        val l = Ledger(transactions = listOf(
            tx(100_00, today), tx(50_00, today.minusDays(1)), tx(1000_00, today, "salary", TxType.INCOME),
            tx(30_00, today.minusDays(40)),
        ))
        val month = Period.of(PeriodType.MONTH, today, DayOfWeek.MONDAY)
        val t = l.transactions.inPeriod(month).totals()
        assertEquals(150_00L, t.expense)
        assertEquals(850_00L, t.balance)
        val days = l.transactions.inPeriod(month).byDay()
        assertEquals(listOf(today, today.minusDays(1)), days.map { it.date })
        assertEquals(900_00L, days.first().net)
        // The entry added last comes first within its day.
        assertEquals(TxType.INCOME, days.first().transactions.first().type)
    }

    @Test fun categoryShares() {
        val l = Ledger(transactions = listOf(tx(300_00, today), tx(100_00, today, "groceries"), tx(100_00, today)))
        val shares = l.byCategory(l.transactions, TxType.EXPENSE)
        assertEquals("food", shares[0].category.id)
        assertEquals(0.8f, shares[0].share, 0.001f)
        assertEquals(2, shares[0].count)
    }

    @Test fun budgetLeftAndPerDay() {
        val l = Ledger(
            transactions = listOf(tx(4000_00, today), tx(1000_00, today.minusDays(3)), tx(999_00, today.minusMonths(1))),
            settings = Settings(budgets = mapOf("₹" to Budget(20000_00, BudgetPeriod.MONTHLY))),
        )
        val b = l.budgetFor(today, today)!!
        assertEquals(5000_00L, b.spent)
        assertEquals(15000_00L, b.left)
        assertEquals(15, b.daysLeft) // 17th..31st
        assertEquals(1000_00L, b.perDay)
        assertEquals(1250_00L, l.copy(transactions = listOf(tx(1249_00, today))).budgetFor(today, today)!!.perDay) // 1250.07 → 1250
        assertEquals(0, l.budgetFor(today.minusMonths(1), today)!!.daysLeft)
        assertNull(l.copy(settings = Settings()).budgetFor(today, today))
    }

    @Test fun trendBuckets() {
        val month = Period.of(PeriodType.MONTH, today, DayOfWeek.MONDAY)
        val b = trend(month, listOf(tx(10_00, today), tx(5_00, today)), TxType.EXPENSE)
        assertEquals(31, b.size)
        assertEquals(15_00L, b[16].total)
        val year = trend(Period.of(PeriodType.YEAR, today, DayOfWeek.MONDAY), listOf(tx(10_00, today)), TxType.EXPENSE)
        assertEquals(12, year.size)
        assertEquals(10_00L, year[9].total)
        assertEquals(1_00L, averagePerDay(month, 17_00, today))
    }

    @Test fun recurringMonthlyLogsEachMonthWithoutDrift() {
        val start = LocalDate.of(2026, 5, 31)
        val l = Ledger().save(Transaction("t", TxType.EXPENSE, 500_00, "rent", start, "Rent"), Repeat.MONTHLY, today, id)
        val dates = l.transactions.map { it.date }
        assertEquals(
            listOf("2026-05-31", "2026-06-30", "2026-07-31", "2026-08-31", "2026-09-30").map(LocalDate::parse),
            dates,
        )
        assertTrue(l.transactions.all { it.recurringId == l.recurring.single().id })
        assertEquals(LocalDate.of(2026, 10, 31), l.recurring.single().nextDate)
        // Nothing new until the next one is due, then exactly one.
        assertEquals(5, l.logDueRecurring(today).transactions.size)
        assertEquals(6, l.logDueRecurring(LocalDate.of(2026, 10, 31)).transactions.size)
        assertEquals(5, l.stopRecurring(l.recurring.single().id).logDueRecurring(LocalDate.of(2027, 1, 1)).transactions.size)
    }

    @Test fun futureRecurringWaitsForItsDate() {
        val l = Ledger().save(Transaction("t", TxType.INCOME, 1_00, "salary", today.plusDays(3)), Repeat.WEEKLY, today, id)
        assertEquals(0, l.transactions.size)
        assertEquals(1, l.logDueRecurring(today.plusDays(3)).transactions.size)
    }

    @Test fun catchUpIsCapped() {
        val l = Ledger().save(Transaction("t", TxType.EXPENSE, 1_00, "food", today.minusYears(5)), Repeat.DAILY, today, id)
        assertEquals(MAX_CATCH_UP + 1, l.transactions.size)
    }

    @Test fun editingKeepsTheSameEntry() {
        val l = Ledger().save(Transaction("t", TxType.EXPENSE, 1_00, "food", today), Repeat.NONE, today, id)
        val edited = l.save(l.transactions.single().copy(amount = 2_00), Repeat.NONE, today, id)
        assertEquals(2_00L, edited.transactions.single().amount)
        assertEquals(0, edited.delete("t").transactions.size)
    }

    @Test fun deletingACategoryMovesItsEntries() {
        val l = Ledger(transactions = listOf(tx(1_00, today, "fuel")))
        val after = l.deleteCategory("fuel", "transport")
        assertNull(after.category("fuel"))
        assertEquals("transport", after.transactions.single().categoryId)
        val onlyOne = Ledger(categories = listOf(DefaultCategories.all.first()))
        assertEquals(onlyOne, onlyOne.deleteCategory("food", "x"))
    }

    @Test fun lastCategoryIsPreselected() {
        val l = Ledger(transactions = listOf(tx(1, today, "groceries"), tx(1, today, "salary", TxType.INCOME)))
        assertEquals("groceries", l.lastCategory(TxType.EXPENSE))
        assertEquals("salary", l.lastCategory(TxType.INCOME))
        assertEquals("food", Ledger().lastCategory(TxType.EXPENSE))
    }
}

class CsvTest {
    private val today = LocalDate.of(2026, 10, 17)

    @Test fun exportAndImportRoundTrip() {
        val l = Ledger(transactions = listOf(
            Transaction("a", TxType.EXPENSE, 250_50, "food", today, "Lunch, with \"team\""),
            Transaction("b", TxType.INCOME, 50000_00, "salary", today.minusDays(2), "Line one\nline two"),
        ))
        val csv = Csv.export(l)
        assertTrue(csv.startsWith(Csv.HEADER))
        val back = Csv.import(Ledger(), csv)
        assertEquals(2, back.added)
        assertEquals(
            l.transactions.map { listOf(it.type, it.amount, it.categoryId, it.date, it.note) }.toSet(),
            back.ledger.transactions.map { listOf(it.type, it.amount, it.categoryId, it.date, it.note) }.toSet(),
        )
        // The same file again adds nothing.
        val again = Csv.import(back.ledger, csv)
        assertEquals(0, again.added)
        assertEquals(2, again.skipped)
    }

    @Test fun unknownCategoriesAreCreatedAndBadRowsCounted() {
        val csv = "Date,Type,Category,Amount,Note\n2026-10-01,Expense,Pets,1200,Vet\nnot a date,Expense,Food,10,\n2026-10-02,Expense,Food,abc,\n"
        val r = Csv.import(Ledger(), csv, { "new" })
        assertEquals(1, r.added)
        assertEquals(2, r.failed)
        assertEquals("Pets", r.ledger.category(r.ledger.transactions.single().categoryId)!!.name)
    }
}

class AccountsTest {
    private val today = LocalDate.of(2026, 10, 27)
    private val inr = Account("hdfc", "HDFC", AccountType.BANK, "₹", 10_000_00)
    private val card = Account("amex", "Amex", AccountType.CARD, "₹", -2_000_00)
    private val usd = Account("chase", "Chase", AccountType.BANK, "$")
    private val base = Ledger(accounts = listOf(Account(DEFAULT_ACCOUNT, "Cash", AccountType.CASH, "₹"), inr, card, usd))

    private fun tx(type: TxType, amount: Long, account: String, cat: String = if (type == TxType.INCOME) "salary" else "food") =
        Transaction(newId(), type, amount, cat, today, accountId = account)

    private fun transfer(from: String, amount: Long, to: String, toAmount: Long) =
        Transaction(newId(), TxType.TRANSFER, amount, "", today, accountId = from, toAccountId = to, toAmount = toAmount)

    @Test fun olderLedgersGetACashAccount() {
        val l = Ledger(settings = Settings(currency = "$"))
        assertEquals("$", l.accounts.single().currency)
        assertEquals(DEFAULT_ACCOUNT, Transaction("t", TxType.EXPENSE, 1, "food", today).accountId)
    }

    @Test fun currenciesAreGroupsMainFirst() {
        assertEquals(listOf("₹", "$"), base.currencies())
        assertEquals(listOf("$", "₹"), base.copy(settings = Settings(currency = "$")).currencies())
    }

    @Test fun totalsNeverMixCurrencies() {
        val l = base.copy(transactions = listOf(
            tx(TxType.INCOME, 5_000_00, "chase"), tx(TxType.EXPENSE, 100_00, "chase"),
            tx(TxType.EXPENSE, 300_00, "hdfc"), tx(TxType.INCOME, 1_000_00, DEFAULT_ACCOUNT),
        ))
        val usdTotals = l.inCurrency("$").totals()
        assertEquals(5_000_00L, usdTotals.income)
        assertEquals(100_00L, usdTotals.expense)
        val inrTotals = l.inCurrency("₹").totals()
        assertEquals(1_000_00L, inrTotals.income)
        assertEquals(300_00L, inrTotals.expense)
    }

    @Test fun transfersMoveMoneyButAreNotSpendingOrIncome() {
        // $1,000 sent home arrives as ₹84,000; then ₹5,000 pays off part of the card.
        val l = base.copy(transactions = listOf(
            tx(TxType.INCOME, 2_000_00, "chase"),
            transfer("chase", 1_000_00, "hdfc", 84_000_00),
            transfer("hdfc", 5_000_00, "amex", 5_000_00),
        ))
        assertEquals(1_000_00L, l.balance("chase"))
        assertEquals(10_000_00L + 84_000_00 - 5_000_00, l.balance("hdfc"))
        assertEquals(-2_000_00L + 5_000_00, l.balance("amex"))
        assertEquals(0L, l.inCurrency("₹").totals().income)
        assertEquals(0L, l.inCurrency("$").totals().expense)
        // The cross-currency transfer shows in both groups, the same-currency one only in ₹.
        assertEquals(2, l.inCurrency("$").size)
        assertEquals(2, l.inCurrency("₹").size)
        val sent = l.transactions[1]
        assertEquals(-1_000_00L, l.flow(sent, "$"))
        assertEquals(84_000_00L, l.flow(sent, "₹"))
        assertNull(l.flow(l.transactions[2], "₹"))
        assertEquals(0L, l.inCurrency("₹").byDay().single().net)
    }

    @Test fun budgetsArePerCurrency() {
        val l = base.copy(
            transactions = listOf(tx(TxType.EXPENSE, 100_00, "chase"), tx(TxType.EXPENSE, 400_00, "hdfc")),
            settings = Settings(budgets = mapOf("$" to Budget(1_000_00), "₹" to Budget(20_000_00, BudgetPeriod.WEEKLY))),
        )
        assertEquals(100_00L, l.budgetFor(today, today, "$")!!.spent)
        val inr = l.budgetFor(today, today, "₹")!!
        assertEquals(400_00L, inr.spent)
        assertEquals(PeriodType.WEEK, inr.period.type)
        assertNull(l.budgetFor(today, today, "€"))
    }

    @Test fun repeatsLogIntoTheirAccount() {
        val l = base.save(tx(TxType.INCOME, 3_000_00, "chase").copy(date = today.minusMonths(1)), Repeat.MONTHLY, today)
        assertEquals(2, l.transactions.size)
        assertTrue(l.transactions.all { it.accountId == "chase" })
    }

    @Test fun deletingAnAccountMovesItsEntriesWithinTheCurrency() {
        val l = base.copy(transactions = listOf(tx(TxType.EXPENSE, 1_00, "hdfc"), transfer("hdfc", 1_00, "amex", 1_00)))
        // Not into another currency, and not with nowhere to go.
        assertEquals(l, l.deleteAccount("hdfc", "chase"))
        assertEquals(l, l.deleteAccount("hdfc", null))
        val after = l.deleteAccount("hdfc", "amex")
        assertNull(after.account("hdfc"))
        assertEquals(1, after.transactions.size) // the transfer would now be Amex → Amex
        assertEquals("amex", after.transactions.single().accountId)
        // An unused account just goes; the last one stays.
        assertNull(l.deleteAccount("chase", null).account("chase"))
        val one = Ledger()
        assertEquals(one, one.deleteAccount(DEFAULT_ACCOUNT, null))
    }

    @Test fun mainCurrency() {
        // One currency: relabels it everywhere.
        val one = Ledger(settings = Settings(budgets = mapOf("₹" to Budget(5_00))))
        val relabelled = one.setMainCurrency("$")
        assertEquals("$", relabelled.accounts.single().currency)
        assertEquals(Budget(5_00), relabelled.settings.budgets["$"])
        // Several: only picks the main group.
        val several = base.setMainCurrency("$")
        assertEquals("$", several.settings.currency)
        assertEquals("₹", several.account("hdfc")!!.currency)
    }

    @Test fun lastAccountIsPreselected() {
        val l = base.copy(transactions = listOf(tx(TxType.INCOME, 1, "chase")))
        assertEquals("chase", l.lastAccount(TxType.INCOME))
        assertEquals(DEFAULT_ACCOUNT, l.lastAccount(TxType.EXPENSE))
    }

    @Test fun csvKeepsAccountsCurrenciesAndTransfers() {
        val l = base.copy(transactions = listOf(
            tx(TxType.INCOME, 2_000_00, "chase"),
            transfer("chase", 1_000_00, "hdfc", 84_000_00),
            tx(TxType.EXPENSE, 250_00, "amex"),
        ))
        val csv = Csv.export(l)
        assertTrue(csv.contains("Transfer,,1000,$,Chase,HDFC,₹,84000,"))
        val back = Csv.import(Ledger(), csv).ledger
        assertEquals(3, back.transactions.size)
        val chase = back.accounts.first { it.name == "Chase" }
        assertEquals("$", chase.currency)
        assertEquals(1_000_00L, back.balance(chase.id))
        val t = back.transactions.first { it.type == TxType.TRANSFER }
        assertEquals("HDFC", back.account(t.toAccountId)!!.name)
        assertEquals(84_000_00L, t.toAmount)
        assertEquals(0, Csv.import(back, csv).added)
    }
}

class CardBillTest {
    private val card = Account("amex", "Amex", AccountType.CARD, "₹", opening = -1_000_00, limit = 50_000_00, statementDay = 15, dueDay = 5)
    private val bank = Account("hdfc", "HDFC", AccountType.BANK, "₹", 100_000_00)
    private val base = Ledger(accounts = listOf(bank, card))

    private fun spend(amount: Long, date: LocalDate) = Transaction(newId(), TxType.EXPENSE, amount, "food", date, accountId = "amex")
    private fun pay(amount: Long, date: LocalDate) =
        Transaction(newId(), TxType.TRANSFER, amount, "", date, accountId = "hdfc", toAccountId = "amex", toAmount = amount)

    @Test fun daysOfTheMonth() {
        assertEquals(LocalDate.of(2026, 10, 15), onOrBefore(LocalDate.of(2026, 10, 20), 15))
        assertEquals(LocalDate.of(2026, 9, 15), onOrBefore(LocalDate.of(2026, 10, 14), 15))
        assertEquals(LocalDate.of(2026, 2, 28), onOrBefore(LocalDate.of(2026, 3, 1), 31))
        assertEquals(LocalDate.of(2026, 11, 5), onOrAfter(LocalDate.of(2026, 10, 16), 5))
        assertEquals(LocalDate.of(2026, 10, 25), onOrAfter(LocalDate.of(2026, 10, 16), 25))
    }

    @Test fun billIsWhatWasOwedOnTheStatementDateLessPayments() {
        val today = LocalDate.of(2026, 10, 28)
        val l = base.copy(transactions = listOf(
            spend(4_000_00, LocalDate.of(2026, 10, 10)), // on the 15 Oct statement
            spend(700_00, LocalDate.of(2026, 10, 20)), // on the next one
            pay(2_000_00, LocalDate.of(2026, 10, 22)),
        ))
        val bill = l.cardBill("amex", today)!!
        assertEquals(LocalDate.of(2026, 10, 15), bill.statementDate)
        assertEquals(LocalDate.of(2026, 11, 5), bill.dueDate)
        assertEquals(5_000_00L, bill.statementOwed)
        assertEquals(2_000_00L, bill.paid)
        assertEquals(3_000_00L, bill.remaining)
        assertEquals(8L, bill.daysLeft(today))
        assertEquals(listOf("amex"), l.billsDueWithin(10, today).map { it.first.id })
        assertTrue(l.billsDueWithin(3, today).isEmpty())
        // Paid in full, and then overdue when it isn't.
        assertEquals(0L, l.copy(transactions = l.transactions + pay(3_000_00, today)).cardBill("amex", today)!!.remaining)
        assertTrue(bill.overdue(LocalDate.of(2026, 11, 6)))
        assertEquals(3_700_00L, -l.balance("amex"))
    }

    @Test fun onlyCardsWithDaysSetHaveBills() {
        assertNull(base.cardBill("hdfc", LocalDate.of(2026, 10, 28)))
        assertNull(base.copy(accounts = listOf(card.copy(dueDay = null))).cardBill("amex", LocalDate.of(2026, 10, 28)))
    }
}
