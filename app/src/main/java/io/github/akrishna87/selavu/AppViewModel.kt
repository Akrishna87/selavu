package io.github.akrishna87.selavu

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.akrishna87.selavu.core.Account
import io.github.akrishna87.selavu.core.Budget
import io.github.akrishna87.selavu.core.Category
import io.github.akrishna87.selavu.core.Backup
import io.github.akrishna87.selavu.core.Csv
import io.github.akrishna87.selavu.core.Xlsx
import io.github.akrishna87.selavu.core.Ledger
import io.github.akrishna87.selavu.core.Period
import io.github.akrishna87.selavu.core.PeriodType
import io.github.akrishna87.selavu.core.Repeat
import io.github.akrishna87.selavu.core.Settings
import io.github.akrishna87.selavu.core.Transaction
import io.github.akrishna87.selavu.core.TxType
import io.github.akrishna87.selavu.core.delete
import io.github.akrishna87.selavu.core.deleteAccount
import io.github.akrishna87.selavu.core.saveAccount
import io.github.akrishna87.selavu.core.setMainCurrency
import io.github.akrishna87.selavu.core.deleteCategory
import io.github.akrishna87.selavu.core.logDueRecurring
import io.github.akrishna87.selavu.core.save
import io.github.akrishna87.selavu.core.saveCategory
import io.github.akrishna87.selavu.core.stopRecurring
import io.github.akrishna87.selavu.core.weekStart
import io.github.akrishna87.selavu.data.BillReminderWorker
import io.github.akrishna87.selavu.data.Store
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What fills the screen above the bottom bar. */
sealed interface Screen {
    data object Home : Screen
    data object Reports : Screen
    data object Settings : Screen
    /** A new or existing entry; a new one can start with an account to pay into and an amount (Pay bill). */
    data class Edit(val type: TxType, val txId: String? = null, val toAccountId: String? = null, val amount: Long? = null) : Screen
    data class CategoryDetail(val categoryId: String) : Screen
    data object Categories : Screen
    data object Repeating : Screen
    data object Accounts : Screen
    data class AccountDetail(val accountId: String) : Screen
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    private val saveLock = Mutex()

    private val _ledger = MutableStateFlow<Ledger?>(null)
    /** Null until the file has been read. */
    val ledger: StateFlow<Ledger?> = _ledger

    var stack by mutableStateOf(listOf<Screen>(Screen.Home))
        private set
    val screen: Screen get() = stack.last()

    var periodType by mutableStateOf(PeriodType.MONTH)
        private set
    var anchor by mutableStateOf(LocalDate.now())
        private set

    var message by mutableStateOf<String?>(null)

    /** The currency group being looked at; null means the main currency. */
    private var chosenCurrency by mutableStateOf<String?>(null)

    /** The currency group Home and Reports show: the one chosen, if it still has accounts, else the main one. */
    fun currency(l: Ledger): String = chosenCurrency?.takeIf { it in l.currencies() } ?: l.settings.currency

    fun chooseCurrency(c: String) { chosenCurrency = c }

    init {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { store.load() }
            _ledger.value = loaded
            BillReminderWorker.schedule(app, loaded.settings.billReminders)
            refreshRecurring()
        }
    }

    fun period(): Period = Period.of(periodType, anchor, (_ledger.value?.settings ?: Settings()).weekStart())

    fun choosePeriodType(type: PeriodType) { periodType = type }
    fun shiftPeriod(n: Long) { anchor = period().shift(n).start }
    fun backToToday() { anchor = LocalDate.now() }

    /** New entries are dated today, or the day being looked at when that's another single day. */
    fun defaultDate(): LocalDate {
        val p = period()
        return if (p.type == PeriodType.DAY) p.start else LocalDate.now()
    }

    // --- Navigation

    fun open(s: Screen) {
        stack = if (s == Screen.Home || s == Screen.Reports || s == Screen.Settings) listOf(s) else stack + s
    }

    /** Returns false when there's nowhere to go back to, so the system can close the app. */
    fun back(): Boolean {
        if (stack.size > 1) { stack = stack.dropLast(1); return true }
        if (screen != Screen.Home) { stack = listOf(Screen.Home); return true }
        return false
    }

    // --- Changes

    private fun update(change: (Ledger) -> Ledger) {
        val current = _ledger.value ?: return
        val next = change(current)
        if (next == current) return
        _ledger.value = next
        // Each save writes the newest ledger, so saves that finish out of order can't lose a change.
        viewModelScope.launch(Dispatchers.IO) { saveLock.withLock { store.save(_ledger.value ?: next) } }
    }

    /** Logs recurring items that have come due; called on start and whenever the app is shown. */
    fun refreshRecurring() = update { it.logDueRecurring(LocalDate.now()) }

    fun saveTransaction(tx: Transaction, repeat: Repeat) = update { it.save(tx, repeat, LocalDate.now()) }
    fun deleteTransaction(id: String) = update { it.delete(id) }
    fun stopRecurring(id: String) = update { it.stopRecurring(id) }
    fun saveCategory(c: Category) = update { it.saveCategory(c) }
    fun deleteCategory(id: String, moveTo: String) = update { it.deleteCategory(id, moveTo) }
    fun updateSettings(change: (Settings) -> Settings) = update { it.copy(settings = change(it.settings)) }
    fun saveAccount(a: Account) = update { it.saveAccount(a) }
    fun setMainCurrency(c: String) = update { it.setMainCurrency(c) }

    fun setBillReminders(on: Boolean) {
        updateSettings { it.copy(billReminders = on) }
        BillReminderWorker.schedule(getApplication(), on)
    }
    fun deleteAccount(id: String, moveTo: String?) = update { it.deleteAccount(id, moveTo) }

    /** Sets (or, with an amount of 0, removes) a currency's budget. */
    fun setBudget(currency: String, budget: Budget) = updateSettings { s ->
        s.copy(budgets = if (budget.amount > 0) s.budgets + (currency to budget) else s.budgets - currency)
    }

    /** Saves the Excel workbook (Entries, Accounts and Budgets sheets) to a file the person chose. */
    fun exportWorkbook(uri: Uri) {
        val l = _ledger.value ?: return
        viewModelScope.launch {
            message = try {
                withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri, "wt")!!.use { it.write(Backup.workbook(l)) }
                }
                "Saved ${count(l.transactions.size, "entry", "entries")}, ${count(l.accounts.size, "account", "accounts")} and budgets to Excel"
            } catch (e: Exception) {
                "Couldn't save the file"
            }
        }
    }

    /**
     * Saves into a folder the person chose: the three CSV files (entries, accounts, budgets)
     * and/or the Excel workbook, each named with today's date.
     */
    fun exportToFolder(tree: Uri, csv: Boolean, excel: Boolean) {
        val l = _ledger.value ?: return
        viewModelScope.launch {
            message = try {
                val saved = withContext(Dispatchers.IO) {
                    val folder = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                    val day = LocalDate.now()
                    fun save(mime: String, name: String, bytes: ByteArray) {
                        val doc = DocumentsContract.createDocument(resolver, folder, mime, name) ?: error("Couldn't create $name")
                        resolver.openOutputStream(doc, "wt")!!.use { it.write(bytes) }
                    }
                    var files = 0
                    if (csv) Backup.csvFiles(l).forEach { (kind, text) ->
                        save("text/csv", "Selavu ${kind.title.lowercase()} $day.csv", text.toByteArray())
                        files++
                    }
                    if (excel) {
                        save(Xlsx.MIME, "Selavu $day.xlsx", Backup.workbook(l))
                        files++
                    }
                    files
                }
                "Saved $saved files: ${count(l.transactions.size, "entry", "entries")}, accounts and budgets"
            } catch (e: Exception) {
                "Couldn't save to that folder"
            }
        }
    }

    /**
     * Imports CSV files and Excel workbooks (any mix, any order): every sheet and file is
     * recognised by its header, accounts and budgets go in first, and entries already here are skipped.
     */
    fun importFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val tables = try {
                withContext(Dispatchers.IO) {
                    uris.flatMap { uri ->
                        val bytes = resolver.openInputStream(uri)!!.use { it.readBytes() }
                        // A workbook is a zip file: it starts with "PK".
                        if (bytes.size > 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
                            Xlsx.read(bytes).map { it.second }
                        } else {
                            listOf(Csv.parse(bytes.decodeToString().removePrefix("\uFEFF")))
                        }
                    }
                }
            } catch (e: Exception) {
                message = "Couldn't read that file. Choose a CSV or Excel (.xlsx) file."
                return@launch
            }
            val current = _ledger.value ?: return@launch
            val result = Backup.import(current, tables)
            update { result.ledger }
            message = result.summary()
        }
    }

    private val resolver get() = getApplication<Application>().contentResolver

    private fun count(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"
}
