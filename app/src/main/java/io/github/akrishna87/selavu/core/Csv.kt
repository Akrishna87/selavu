package io.github.akrishna87.selavu.core

import java.time.LocalDate

/**
 * Spreadsheet export and import. The columns are Date, Type, Category, Amount, Currency, Account,
 * To account, To currency, To amount, Note, which opens cleanly in Excel or Google Sheets and can be read back
 * in on another phone. Files with only the first five columns (from older versions) import too.
 */
object Csv {
    const val HEADER = "Date,Type,Category,Amount,Currency,Account,To account,To currency,To amount,Note"

    fun export(ledger: Ledger): String = buildString {
        append(HEADER).append("\r\n")
        ledger.transactions.sortedBy { it.date }.forEach { t ->
            val cat = ledger.category(t.categoryId)?.name ?: ""
            val to = ledger.account(t.toAccountId)
            append(t.date).append(',')
            append(
                when (t.type) {
                    TxType.INCOME -> "Income"
                    TxType.EXPENSE -> "Expense"
                    TxType.TRANSFER -> "Transfer"
                },
            ).append(',')
            append(quote(if (t.type == TxType.TRANSFER) "" else cat)).append(',')
            append(Money.plain(t.amount)).append(',')
            append(quote(ledger.currencyOf(t).trim())).append(',')
            append(quote(ledger.account(t.accountId)?.name ?: "")).append(',')
            append(quote(to?.name ?: "")).append(',')
            append(quote(to?.currency?.trim() ?: "")).append(',')
            append(if (t.type == TxType.TRANSFER) Money.plain(t.toAmount) else "").append(',')
            append(quote(t.note)).append("\r\n")
        }
    }

    data class Imported(val ledger: Ledger, val added: Int, val skipped: Int, val failed: Int)

    /**
     * Adds the rows of a CSV in the format [export] writes. Categories are matched by name and
     * accounts by name and currency, and either is created when missing; rows already in the
     * ledger (same date, type, category, amount, account and note) are skipped, so importing the
     * same file twice adds nothing. Rows without an account go into the main currency's first one.
     */
    fun import(ledger: Ledger, text: String, id: () -> String = ::newId): Imported {
        val rows = parse(text.removePrefix("﻿"))
        if (rows.isEmpty()) return Imported(ledger, 0, 0, 0)
        val header = rows.first().map { it.trim().lowercase() }
        val hasHeader = "date" in header
        fun col(name: String, fallback: Int) = if (hasHeader) header.indexOf(name).takeIf { it >= 0 } ?: -1 else fallback
        val iDate = col("date", 0)
        val iType = col("type", 1)
        val iCat = col("category", 2)
        val iAmount = col("amount", 3)
        val iNote = col("note", 4)
        val iCurrency = col("currency", -1)
        val iAccount = col("account", -1)
        val iTo = col("to account", -1)
        val iToCurrency = col("to currency", -1)
        val iToAmount = col("to amount", -1)
        fun List<String>.at(i: Int) = if (i < 0) "" else getOrNull(i)?.trim().orEmpty()

        var categories = ledger.categories
        var accounts = ledger.accounts
        fun accountFor(name: String, currency: String): Account {
            val cur = currency.ifEmpty { null }
            accounts.firstOrNull { it.name.equals(name, ignoreCase = true) && (cur == null || it.currency.trim() == cur) }?.let { return it }
            if (name.isEmpty()) {
                val c = cur?.let { want -> accounts.firstOrNull { it.currency.trim() == want }?.currency } ?: ledger.settings.currency
                accounts.firstOrNull { it.currency == c }?.let { return it }
            }
            val symbol = cur?.let { want -> Currencies.common.firstOrNull { it.trim() == want } ?: want } ?: ledger.settings.currency
            return Account(id(), name.ifEmpty { "Imported" }, AccountType.OTHER, symbol).also { accounts = accounts + it }
        }

        val seen = ledger.transactions.map { key(it, ledger.category(it.categoryId)?.name ?: "", ledger.account(it.accountId)?.name ?: "") }.toMutableSet()
        val added = mutableListOf<Transaction>()
        var skipped = 0
        var failed = 0
        for (row in if (hasHeader) rows.drop(1) else rows) {
            if (row.all { it.isBlank() }) continue
            val date = runCatching { LocalDate.parse(row.at(iDate)) }.getOrNull()
            val rawAmount = row.at(iAmount)
            val amount = Money.parse(rawAmount)
            if (date == null || amount == null) { failed++; continue }
            val typeText = row.at(iType).lowercase()
            val type = when {
                typeText.startsWith("tra") -> TxType.TRANSFER
                typeText.startsWith("inc") -> TxType.INCOME
                rawAmount.startsWith("+") -> TxType.INCOME
                else -> TxType.EXPENSE
            }
            val note = row.at(iNote)
            val account = accountFor(row.at(iAccount), row.at(iCurrency))
            val tx = if (type == TxType.TRANSFER) {
                val toName = row.at(iTo)
                if (toName.isEmpty()) { failed++; continue }
                val to = accountFor(toName, row.at(iToCurrency).ifEmpty { account.currency.trim() })
                if (to.id == account.id) { failed++; continue }
                val toAmount = Money.parse(row.at(iToAmount)) ?: amount
                Transaction(id(), type, amount, "", date, note, accountId = account.id, toAccountId = to.id, toAmount = toAmount)
            } else {
                val catName = row.at(iCat).ifEmpty { "Other" }
                val cat = categories.firstOrNull { it.type == type && it.name.equals(catName, ignoreCase = true) }
                    ?: Category(id(), catName, "🏷️", type, CategoryColors.all[categories.size % CategoryColors.all.size])
                        .also { categories = categories + it }
                Transaction(id(), type, amount, cat.id, date, note, accountId = account.id)
            }
            val catName = categories.firstOrNull { it.id == tx.categoryId }?.name ?: ""
            if (!seen.add(key(tx, catName, account.name))) { skipped++; continue }
            added += tx
        }
        return Imported(
            ledger.copy(categories = categories, accounts = accounts, transactions = ledger.transactions + added),
            added.size, skipped, failed,
        )
    }

    private fun key(t: Transaction, categoryName: String, accountName: String) =
        listOf(t.date.toString(), t.type.name, categoryName.lowercase(), t.amount.toString(), accountName.lowercase(), t.note.trim())
            .joinToString("\u0000")

    private fun quote(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' } || s.startsWith(" ") || s.endsWith(" "))
            "\"" + s.replace("\"", "\"\"") + "\""
        else s

    /** RFC 4180: quoted fields may hold commas, doubled quotes and line breaks. */
    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { field.append('"'); i++ } else quoted = false
                } else field.append(c)
            } else when (c) {
                '"' -> quoted = true
                ',' -> { row.add(field.toString()); field.clear() }
                '\r' -> {}
                '\n' -> { row.add(field.toString()); field.clear(); rows.add(row); row = mutableListOf() }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); rows.add(row) }
        return rows
    }
}
