package io.github.akrishna87.selavu.core

import java.time.LocalDate
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

/** Most occurrences a recurring item logs in one go, so a years-old daily item can't freeze the app. */
const val MAX_CATCH_UP = 400

/**
 * Logs every occurrence of every recurring item that has come due by [today].
 */
fun Ledger.logDueRecurring(today: LocalDate, id: () -> String = ::newId): Ledger {
    if (recurring.none { !it.nextDate.isAfter(today) }) return this
    val added = mutableListOf<Transaction>()
    val updated = recurring.map { r ->
        var n = r.logged
        var count = 0
        while (count < MAX_CATCH_UP && !r.occurrence(n).isAfter(today)) {
            added += Transaction(id(), r.type, r.amount, r.categoryId, r.occurrence(n), r.note, r.id, r.accountId)
            n++
            count++
        }
        r.copy(logged = n)
    }
    return copy(transactions = transactions + added, recurring = updated)
}

/**
 * Saves a new or edited transaction. For a new one with a [repeat], a recurring item is set up
 * starting on its date, and its first occurrence is logged now (if that date has come).
 */
fun Ledger.save(tx: Transaction, repeat: Repeat, today: LocalDate, id: () -> String = ::newId): Ledger {
    val exists = transactions.any { it.id == tx.id }
    if (exists) return copy(transactions = transactions.map { if (it.id == tx.id) tx else it })
    // Transfers don't repeat.
    if (repeat == Repeat.NONE || tx.type == TxType.TRANSFER) return copy(transactions = transactions + tx)
    val r = Recurring(id(), tx.type, tx.amount, tx.categoryId, tx.note, repeat, tx.date, accountId = tx.accountId)
    return if (tx.date.isAfter(today)) copy(recurring = recurring + r)
    else copy(
        transactions = transactions + tx.copy(recurringId = r.id),
        recurring = recurring + r.copy(logged = 1),
    ).logDueRecurring(today, id)
}

fun Ledger.delete(txId: String): Ledger = copy(transactions = transactions.filterNot { it.id == txId })

/** Stops a recurring item. What it already logged stays in the history. */
fun Ledger.stopRecurring(recurringId: String): Ledger = copy(recurring = recurring.filterNot { it.id == recurringId })

fun Ledger.saveCategory(category: Category): Ledger =
    if (categories.any { it.id == category.id }) copy(categories = categories.map { if (it.id == category.id) category else it })
    else copy(categories = categories + category)

/**
 * Removes a category, moving its transactions (and recurring items) to [moveTo].
 * The last category of a type can't be removed.
 */
fun Ledger.deleteCategory(categoryId: String, moveTo: String): Ledger {
    val cat = category(categoryId) ?: return this
    if (categories(cat.type).size <= 1 || moveTo == categoryId) return this
    return copy(
        categories = categories.filterNot { it.id == categoryId },
        transactions = transactions.map { if (it.categoryId == categoryId) it.copy(categoryId = moveTo) else it },
        recurring = recurring.map { if (it.categoryId == categoryId) it.copy(categoryId = moveTo) else it },
    )
}

fun Ledger.usage(categoryId: String): Int = transactions.count { it.categoryId == categoryId }

fun Ledger.saveAccount(account: Account): Ledger =
    if (accounts.any { it.id == account.id }) copy(accounts = accounts.map { if (it.id == account.id) account else it })
    else copy(accounts = accounts + account)

/** How many entries (including transfers in or out) and repeats use an account. */
fun Ledger.accountUsage(accountId: String): Int =
    transactions.count { it.accountId == accountId || it.toAccountId == accountId } +
        recurring.count { it.accountId == accountId }

/**
 * Removes an account, moving its entries and repeats to [moveTo], which must be in the same
 * currency (amounts aren't converted). A transfer that would then go from an account to itself
 * is dropped. The last account can't be removed.
 */
fun Ledger.deleteAccount(accountId: String, moveTo: String?): Ledger {
    val acc = account(accountId) ?: return this
    if (accounts.size <= 1) return this
    val target = account(moveTo)
    if (accountUsage(accountId) > 0 && (target == null || target.id == accountId || target.currency != acc.currency)) return this
    val to = target?.id ?: accountId
    val moved = transactions.map { t ->
        t.copy(
            accountId = if (t.accountId == accountId) to else t.accountId,
            toAccountId = if (t.toAccountId == accountId) to else t.toAccountId,
        )
    }.filterNot { it.type == TxType.TRANSFER && it.accountId == it.toAccountId }
    var settings = settings
    if (settings.currency == acc.currency && accounts.none { it.id != accountId && it.currency == acc.currency }) {
        settings = settings.copy(currency = accounts.first { it.id != accountId }.currency)
    }
    return copy(
        accounts = accounts.filterNot { it.id == accountId },
        transactions = moved,
        recurring = recurring.map { if (it.accountId == accountId) it.copy(accountId = to) else it },
        settings = settings,
    )
}

/**
 * Makes [currency] the main one. With only one currency in use this just relabels it, accounts
 * and budget included (amounts aren't converted); with several it picks which group Home opens on.
 */
fun Ledger.setMainCurrency(currency: String): Ledger {
    val old = settings.currency
    if (currency == old) return this
    if (currencies().size > 1) return copy(settings = settings.copy(currency = currency))
    val budgets = settings.budgets[old]?.let { (settings.budgets - old) + (currency to it) } ?: settings.budgets
    return copy(
        accounts = accounts.map { it.copy(currency = currency) },
        settings = settings.copy(currency = currency, budgets = budgets),
    )
}
