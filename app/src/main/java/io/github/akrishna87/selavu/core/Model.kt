package io.github.akrishna87.selavu.core

import java.time.LocalDate

/** Spending, income, or money moved between two of your own accounts. */
enum class TxType { EXPENSE, INCOME, TRANSFER }

enum class AccountType(val label: String, val emoji: String) {
    BANK("Bank", "🏦"),
    CARD("Credit card", "💳"),
    CASH("Cash", "💵"),
    WALLET("Wallet", "📱"),
    OTHER("Other", "🗂️"),
}

/**
 * Where money is kept. Each account has its own [currency]; entries in it are in that currency,
 * and totals are only ever added up within one currency.
 * [opening] is the balance before the first entry; for a credit card it's minus what was owed.
 */
data class Account(
    val id: String,
    val name: String,
    val type: AccountType,
    val currency: String,
    val opening: Long = 0,
)

/** The account everything goes into until others are added (and that older data is moved into). */
const val DEFAULT_ACCOUNT = "cash"

data class Category(
    val id: String,
    val name: String,
    val emoji: String,
    val type: TxType,
    /** ARGB colour, used for the chart slice and the icon's background. */
    val color: Long,
)

/**
 * One expense, income or transfer. [amount] is in the smallest unit (paise, cents) of the
 * account's currency. A transfer moves [amount] out of [accountId] and [toAmount] (in the other
 * account's currency, which may differ) into [toAccountId]; it has no category.
 */
data class Transaction(
    val id: String,
    val type: TxType,
    val amount: Long,
    val categoryId: String,
    val date: LocalDate,
    val note: String = "",
    /** Set when this was logged by a [Recurring] item. */
    val recurringId: String? = null,
    val accountId: String = DEFAULT_ACCOUNT,
    val toAccountId: String? = null,
    val toAmount: Long = 0,
)

enum class Repeat(val label: String) {
    NONE("Never"),
    DAILY("Every day"),
    WEEKLY("Every week"),
    MONTHLY("Every month"),
    YEARLY("Every year"),
}

/**
 * A transaction that logs itself on a schedule. Occurrence n falls on [start] plus n periods,
 * worked out from [start] every time so a monthly item on the 31st lands on the last day of
 * short months without drifting to the 28th for good.
 */
data class Recurring(
    val id: String,
    val type: TxType,
    val amount: Long,
    val categoryId: String,
    val note: String,
    val repeat: Repeat,
    val start: LocalDate,
    /** How many occurrences have been logged so far. */
    val logged: Int = 0,
    val accountId: String = DEFAULT_ACCOUNT,
) {
    fun occurrence(n: Int): LocalDate = when (repeat) {
        Repeat.NONE -> start
        Repeat.DAILY -> start.plusDays(n.toLong())
        Repeat.WEEKLY -> start.plusWeeks(n.toLong())
        Repeat.MONTHLY -> start.plusMonths(n.toLong())
        Repeat.YEARLY -> start.plusYears(n.toLong())
    }

    val nextDate: LocalDate get() = occurrence(logged)
}

enum class BudgetPeriod { WEEKLY, MONTHLY }

data class Budget(val amount: Long, val period: BudgetPeriod = BudgetPeriod.MONTHLY)

data class Settings(
    /** The main currency: what Home opens on, and what new accounts start in. */
    val currency: String = "₹",
    /** A spending budget for each currency that has one. */
    val budgets: Map<String, Budget> = emptyMap(),
    val weekStartsMonday: Boolean = true,
)

data class Ledger(
    val categories: List<Category> = DefaultCategories.all,
    val transactions: List<Transaction> = emptyList(),
    val recurring: List<Recurring> = emptyList(),
    val settings: Settings = Settings(),
    val accounts: List<Account> = listOf(Account(DEFAULT_ACCOUNT, "Cash", AccountType.CASH, settings.currency)),
) {
    fun category(id: String): Category? = categories.firstOrNull { it.id == id }
    fun categories(type: TxType): List<Category> = categories.filter { it.type == type }
    fun account(id: String?): Account? = accounts.firstOrNull { it.id == id }

    /** The currency an entry is in: its account's (for a transfer, the account it left). */
    fun currencyOf(t: Transaction): String = account(t.accountId)?.currency ?: settings.currency

    /** Every currency with an account, the main one first. */
    fun currencies(): List<String> =
        (listOf(settings.currency) + accounts.map { it.currency }).distinct()
            .filter { c -> c == settings.currency || accounts.any { it.currency == c } }

    fun accounts(currency: String): List<Account> = accounts.filter { it.currency == currency }
}

/** Common currencies offered when choosing one; any other symbol can be typed. */
object Currencies {
    val common = listOf("₹", "$", "€", "£", "¥", "AED ", "S$", "RM ", "A$", "C$", "SAR ", "QAR ")
}

object CategoryColors {
    val all = listOf(
        0xFFE5534BL, 0xFFF0883EL, 0xFFE3B341L, 0xFF57AB5AL, 0xFF39C5BBL, 0xFF4493F8L,
        0xFF8E6BFFL, 0xFFDB61A2L, 0xFFA0785AL, 0xFF768390L,
    )
}

object DefaultCategories {
    private fun c(id: String, name: String, emoji: String, type: TxType, color: Long) =
        Category(id, name, emoji, type, color)

    val all = listOf(
        c("food", "Food & dining", "🍔", TxType.EXPENSE, 0xFFF0883E),
        c("groceries", "Groceries", "🛒", TxType.EXPENSE, 0xFF57AB5A),
        c("transport", "Transport", "🚌", TxType.EXPENSE, 0xFF4493F8),
        c("fuel", "Fuel", "⛽", TxType.EXPENSE, 0xFF768390),
        c("shopping", "Shopping", "🛍️", TxType.EXPENSE, 0xFFDB61A2),
        c("bills", "Bills & utilities", "💡", TxType.EXPENSE, 0xFFE3B341),
        c("rent", "Rent & home", "🏠", TxType.EXPENSE, 0xFFA0785A),
        c("health", "Health", "💊", TxType.EXPENSE, 0xFFE5534B),
        c("fun", "Entertainment", "🎬", TxType.EXPENSE, 0xFF8E6BFF),
        c("education", "Education", "📚", TxType.EXPENSE, 0xFF39C5BB),
        c("travel", "Travel", "✈️", TxType.EXPENSE, 0xFF4493F8),
        c("gifts", "Gifts", "🎁", TxType.EXPENSE, 0xFFDB61A2),
        c("personal", "Personal care", "💇", TxType.EXPENSE, 0xFF8E6BFF),
        c("other", "Other", "📦", TxType.EXPENSE, 0xFF768390),
        c("salary", "Salary", "💼", TxType.INCOME, 0xFF57AB5A),
        c("business", "Business", "🏪", TxType.INCOME, 0xFF39C5BB),
        c("interest", "Interest", "🏦", TxType.INCOME, 0xFF4493F8),
        c("gift-in", "Gifts received", "🎁", TxType.INCOME, 0xFFDB61A2),
        c("other-in", "Other income", "💰", TxType.INCOME, 0xFFE3B341),
    )
}
