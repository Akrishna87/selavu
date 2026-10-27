package io.github.akrishna87.selavu.data

import android.content.Context
import android.util.Log
import io.github.akrishna87.selavu.core.Account
import io.github.akrishna87.selavu.core.AccountType
import io.github.akrishna87.selavu.core.Budget
import io.github.akrishna87.selavu.core.BudgetPeriod
import io.github.akrishna87.selavu.core.DEFAULT_ACCOUNT
import io.github.akrishna87.selavu.core.Category
import io.github.akrishna87.selavu.core.Ledger
import io.github.akrishna87.selavu.core.Recurring
import io.github.akrishna87.selavu.core.Repeat
import io.github.akrishna87.selavu.core.Settings
import io.github.akrishna87.selavu.core.Transaction
import io.github.akrishna87.selavu.core.TxType
import java.io.File
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

/**
 * Keeps the whole ledger in one JSON file in the app's private storage. Writes go to a
 * temporary file that then replaces the real one, so a crash mid-save can't corrupt it.
 * The previous version is kept as a backup and used if the main file can't be read.
 */
class Store(context: Context) {
    private val file = File(context.filesDir, "ledger.json")
    private val backup = File(context.filesDir, "ledger.backup.json")

    fun load(): Ledger {
        for (f in listOf(file, backup)) {
            if (!f.exists()) continue
            try {
                return decode(JSONObject(f.readText()))
            } catch (e: Exception) {
                Log.w("Selavu", "Couldn't read ${f.name}", e)
            }
        }
        return Ledger()
    }

    @Synchronized
    fun save(ledger: Ledger) {
        val tmp = File(file.parentFile, "ledger.json.tmp")
        tmp.writeText(encode(ledger).toString())
        if (file.exists()) file.copyTo(backup, overwrite = true)
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
    }

    companion object {
        private const val VERSION = 2

        fun encode(l: Ledger): JSONObject = JSONObject().apply {
            put("version", VERSION)
            put("settings", JSONObject().apply {
                put("currency", l.settings.currency)
                put("budgets", JSONObject().apply {
                    l.settings.budgets.forEach { (currency, b) ->
                        put(currency, JSONObject().apply { put("amount", b.amount); put("period", b.period.name) })
                    }
                })
                put("weekStartsMonday", l.settings.weekStartsMonday)
            })
            put("accounts", JSONArray().apply {
                l.accounts.forEach { a ->
                    put(JSONObject().apply {
                        put("id", a.id); put("name", a.name); put("type", a.type.name)
                        put("currency", a.currency); put("opening", a.opening)
                    })
                }
            })
            put("categories", JSONArray().apply {
                l.categories.forEach { c ->
                    put(JSONObject().apply {
                        put("id", c.id); put("name", c.name); put("emoji", c.emoji)
                        put("type", c.type.name); put("color", c.color)
                    })
                }
            })
            put("transactions", JSONArray().apply {
                l.transactions.forEach { t ->
                    put(JSONObject().apply {
                        put("id", t.id); put("type", t.type.name); put("amount", t.amount)
                        put("category", t.categoryId); put("date", t.date.toString())
                        if (t.note.isNotEmpty()) put("note", t.note)
                        t.recurringId?.let { put("recurring", it) }
                        put("account", t.accountId)
                        t.toAccountId?.let { put("toAccount", it); put("toAmount", t.toAmount) }
                    })
                }
            })
            put("recurring", JSONArray().apply {
                l.recurring.forEach { r ->
                    put(JSONObject().apply {
                        put("id", r.id); put("type", r.type.name); put("amount", r.amount)
                        put("category", r.categoryId); put("note", r.note); put("repeat", r.repeat.name)
                        put("start", r.start.toString()); put("logged", r.logged)
                        put("account", r.accountId)
                    })
                }
            })
        }

        fun decode(o: JSONObject): Ledger {
            val s = o.optJSONObject("settings")
            val currency = s?.optString("currency", "₹") ?: "₹"
            val budgets = mutableMapOf<String, Budget>()
            s?.optJSONObject("budgets")?.let { b ->
                b.keys().forEach { c ->
                    val o = b.getJSONObject(c)
                    budgets[c] = Budget(o.getLong("amount"), enumOr(o.optString("period"), BudgetPeriod.MONTHLY))
                }
            }
            // Version 1 had a single budget, in the one currency there was.
            if (s != null && !s.has("budgets") && s.optLong("budget", 0) > 0) {
                budgets[currency] = Budget(s.getLong("budget"), enumOr(s.optString("budgetPeriod"), BudgetPeriod.MONTHLY))
            }
            val settings = Settings(currency, budgets, s?.optBoolean("weekStartsMonday", true) ?: true)
            // Version 1 had no accounts: everything goes into one Cash account in its currency.
            val accounts = o.optJSONArray("accounts").objects().map { a ->
                Account(
                    a.getString("id"), a.getString("name"), enumOr(a.optString("type"), AccountType.OTHER),
                    a.optString("currency", currency), a.optLong("opening", 0),
                )
            }.ifEmpty { listOf(Account(DEFAULT_ACCOUNT, "Cash", AccountType.CASH, currency)) }
            val categories = o.optJSONArray("categories").objects().map { c ->
                Category(
                    c.getString("id"), c.getString("name"), c.optString("emoji", "🏷️"),
                    enumOr(c.optString("type"), TxType.EXPENSE), c.optLong("color", 0xFF768390),
                )
            }
            val transactions = o.optJSONArray("transactions").objects().map { t ->
                Transaction(
                    t.getString("id"), enumOr(t.optString("type"), TxType.EXPENSE), t.getLong("amount"),
                    t.getString("category"), LocalDate.parse(t.getString("date")), t.optString("note", ""),
                    if (t.has("recurring")) t.getString("recurring") else null,
                    t.optString("account", DEFAULT_ACCOUNT),
                    if (t.has("toAccount")) t.getString("toAccount") else null,
                    t.optLong("toAmount", 0),
                )
            }
            val recurring = o.optJSONArray("recurring").objects().map { r ->
                Recurring(
                    r.getString("id"), enumOr(r.optString("type"), TxType.EXPENSE), r.getLong("amount"),
                    r.getString("category"), r.optString("note", ""), enumOr(r.optString("repeat"), Repeat.MONTHLY),
                    LocalDate.parse(r.getString("start")), r.optInt("logged", 0),
                    r.optString("account", DEFAULT_ACCOUNT),
                )
            }
            return Ledger(categories.ifEmpty { Ledger().categories }, transactions, recurring, settings, accounts)
        }

        private fun JSONArray?.objects(): List<JSONObject> =
            if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

        private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
            enumValues<E>().firstOrNull { it.name == name } ?: fallback
    }
}
