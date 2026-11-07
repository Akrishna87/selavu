package io.github.akrishna87.selavu.core

/**
 * Everything worth keeping, as three tables (entries, accounts, budgets), written as an Excel
 * workbook with a sheet for each, or as three CSV files, and read back from either. A file is
 * recognised by its header row, so files can be imported in any order and in any mix.
 */
object Backup {
    val ACCOUNTS_HEADER = listOf("Name", "Type", "Currency", "Opening balance", "Credit limit", "Statement day", "Due day")
    val BUDGETS_HEADER = listOf("Currency", "Period", "Amount")

    enum class Kind(val title: String) { ENTRIES("Entries"), ACCOUNTS("Accounts"), BUDGETS("Budgets") }

    /** Accounts, one per row. A credit card's opening balance is negative: minus what was owed. */
    fun accountRows(l: Ledger): List<List<Any?>> = listOf(ACCOUNTS_HEADER) + l.accounts.map { a ->
        listOf(
            a.name, a.type.label, a.currency.trim(), Csv.amount(kotlin.math.abs(a.opening)).let { if (a.opening < 0) it.negate() else it },
            if (a.limit > 0) Csv.amount(a.limit) else null, a.statementDay, a.dueDay,
        )
    }

    /** Budgets, one per currency that has one. */
    fun budgetRows(l: Ledger): List<List<Any?>> = listOf(BUDGETS_HEADER) + l.settings.budgets.map { (c, b) ->
        listOf(c.trim(), if (b.period == BudgetPeriod.WEEKLY) "Weekly" else "Monthly", Csv.amount(b.amount))
    }

    fun tables(l: Ledger): List<Pair<Kind, List<List<Any?>>>> =
        listOf(Kind.ENTRIES to Csv.entryRows(l), Kind.ACCOUNTS to accountRows(l), Kind.BUDGETS to budgetRows(l))

    fun workbook(l: Ledger): ByteArray = Xlsx.write(tables(l).map { (k, rows) -> Xlsx.Sheet(k.title, rows) })

    /** The three CSV files: name ending and text. */
    fun csvFiles(l: Ledger): List<Pair<Kind, String>> = tables(l).map { (k, rows) -> k to Csv.write(rows) }

    /** What a table holds, from its header row; null when it isn't one of these. */
    fun kind(rows: List<List<String>>): Kind? {
        val header = rows.firstOrNull()?.map { it.trim().lowercase() } ?: return null
        return when {
            "date" in header && "amount" in header -> Kind.ENTRIES
            "name" in header && "type" in header && "currency" in header -> Kind.ACCOUNTS
            "currency" in header && "period" in header && "amount" in header -> Kind.BUDGETS
            // An entries file without a header row, as very early versions wrote.
            rows.first().firstOrNull()?.let { Csv.date(it.trim()) } != null -> Kind.ENTRIES
            else -> null
        }
    }

    data class Result(
        val ledger: Ledger,
        val entries: Csv.Imported?,
        val accountsAdded: Int,
        val budgetsSet: Int,
        val unrecognised: Int,
    ) {
        /** "Added 12 entries, 2 accounts and 1 budget" and what was skipped. */
        fun summary(): String {
            val parts = mutableListOf<String>()
            entries?.let { parts += "${it.added} ${if (it.added == 1) "entry" else "entries"}" }
            if (accountsAdded > 0) parts += "$accountsAdded ${if (accountsAdded == 1) "account" else "accounts"}"
            if (budgetsSet > 0) parts += "$budgetsSet ${if (budgetsSet == 1) "budget" else "budgets"}"
            val sb = StringBuilder(if (parts.isEmpty()) "Nothing new to add" else "Added " + parts.joinToString(", ").replaceLast(", ", " and "))
            entries?.let {
                if (it.skipped > 0) sb.append(", ${it.skipped} already here")
                if (it.failed > 0) sb.append(", ${it.failed} rows not understood")
            }
            if (unrecognised > 0) sb.append(", $unrecognised ${if (unrecognised == 1) "file or sheet" else "files or sheets"} not recognised")
            return sb.toString()
        }

        private fun String.replaceLast(old: String, new: String): String {
            val i = lastIndexOf(old)
            return if (i < 0) this else substring(0, i) + new + substring(i + old.length)
        }
    }

    /**
     * Adds what [tables] hold: accounts first (so entries find them), then budgets, then entries.
     * Accounts already here (same name and currency) are kept as they are; a budget replaces the
     * one for its currency; entries already here are skipped.
     */
    fun import(ledger: Ledger, tables: List<List<List<String>>>, id: () -> String = ::newId): Result {
        var l = ledger
        var accountsAdded = 0
        var budgetsSet = 0
        var unrecognised = 0
        val byKind = tables.groupBy { kind(it) }
        unrecognised += byKind[null]?.size ?: 0

        byKind[Kind.ACCOUNTS].orEmpty().forEach { rows ->
            val h = rows.first().map { it.trim().lowercase() }
            fun List<String>.at(name: String) = h.indexOf(name).takeIf { it >= 0 }?.let { getOrNull(it)?.trim() }.orEmpty()
            rows.drop(1).forEach { r ->
                val name = r.at("name")
                if (name.isEmpty()) return@forEach
                val currency = symbol(r.at("currency").ifEmpty { l.settings.currency.trim() })
                if (l.accounts.any { it.name.equals(name, ignoreCase = true) && it.currency.trim() == currency.trim() }) return@forEach
                val type = AccountType.entries.firstOrNull { it.label.equals(r.at("type"), ignoreCase = true) || it.name.equals(r.at("type"), ignoreCase = true) }
                    ?: AccountType.OTHER
                val opening = r.at("opening balance").let { t -> Money.parse(t)?.let { if (t.trim().startsWith("-") || t.trim().startsWith("−")) -it else it } } ?: 0
                l = l.saveAccount(
                    Account(
                        id(), name, type, currency, opening,
                        limit = Money.parse(r.at("credit limit")) ?: 0,
                        statementDay = r.at("statement day").toDoubleOrNull()?.toInt()?.takeIf { it in 1..31 },
                        dueDay = r.at("due day").toDoubleOrNull()?.toInt()?.takeIf { it in 1..31 },
                    ),
                )
                accountsAdded++
            }
        }

        byKind[Kind.BUDGETS].orEmpty().forEach { rows ->
            val h = rows.first().map { it.trim().lowercase() }
            fun List<String>.at(name: String) = h.indexOf(name).takeIf { it >= 0 }?.let { getOrNull(it)?.trim() }.orEmpty()
            rows.drop(1).forEach { r ->
                val amount = Money.parse(r.at("amount")) ?: return@forEach
                val currency = symbol(r.at("currency").ifEmpty { l.settings.currency.trim() })
                val period = if (r.at("period").lowercase().startsWith("week")) BudgetPeriod.WEEKLY else BudgetPeriod.MONTHLY
                l = l.copy(settings = l.settings.copy(budgets = l.settings.budgets + (currency to Budget(amount, period))))
                budgetsSet++
            }
        }

        var entries: Csv.Imported? = null
        byKind[Kind.ENTRIES].orEmpty().forEach { rows ->
            val r = Csv.importRows(l, rows, id)
            l = r.ledger
            entries = entries?.let { Csv.Imported(l, it.added + r.added, it.skipped + r.skipped, it.failed + r.failed) } ?: r
        }
        return Result(l, entries, accountsAdded, budgetsSet, unrecognised)
    }

    /** A currency as written in a file ("AED") back to the symbol the app uses ("AED "). */
    private fun symbol(text: String): String = Currencies.common.firstOrNull { it.trim() == text.trim() } ?: text.trim()
}
