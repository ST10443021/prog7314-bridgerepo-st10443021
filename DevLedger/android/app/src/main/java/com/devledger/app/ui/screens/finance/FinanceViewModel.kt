package com.devledger.app.ui.screens.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devledger.app.data.local.entity.TransactionEntity
import com.devledger.app.data.repository.AuthRepository
import com.devledger.app.data.repository.SettingsRepository
import com.devledger.app.data.repository.TransactionRepository
import com.devledger.app.util.CurrencyConverter
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat

data class MonthlyStat(val total: Double, val percentChangeVsPrevMonth: Double?, val topCategories: List<Pair<String, Double>>)
data class DailyPoint(val label: String, val income: Double, val expenses: Double)

enum class SummaryRange { WEEK, MONTH, YEAR }

data class FinanceUiState(
    val selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val selectedMonth: Int = Calendar.getInstance().get(Calendar.MONTH), // 0-based, matches java.util.Calendar
    val incomeStat: MonthlyStat = MonthlyStat(0.0, null, emptyList()),
    val expenseStat: MonthlyStat = MonthlyStat(0.0, null, emptyList()),
    val netBalance: Double = 0.0,
    val netBalancePercentChange: Double? = null,
    val baseCurrency: String = "ZAR",
    val dailyPoints: List<DailyPoint> = emptyList(),
    val recentTransactions: List<TransactionEntity> = emptyList(),
    val summaryRange: SummaryRange = SummaryRange.MONTH,
) {
    val monthLabel: String
        get() = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault())
            .format(Calendar.getInstance().apply { set(selectedYear, selectedMonth, 1) }.time)
}

/** Backs the Financial Tracker dashboard — month selector, income/expense cards with
 *  month-over-month % change and top categories, a daily chart, and recent activity.
 *  Everything here is computed from the local RoomDB-backed transaction list rather
 *  than a dedicated report endpoint, so the dashboard stays accurate offline. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FinanceViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val settingsRepository: SettingsRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    private val selectedYearMonth = MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR) to Calendar.getInstance().get(Calendar.MONTH))
    private val summaryRange = MutableStateFlow(SummaryRange.MONTH)

    private val allTransactions = authRepository.currentUser.flatMapLatest { user ->
        if (user == null) flowOf(emptyList()) else transactionRepository.observeAllForUser(user.userId)
    }

    val uiState: StateFlow<FinanceUiState> = combine(
        allTransactions, 
        settingsRepository.baseCurrency, 
        selectedYearMonth,
        summaryRange
    ) { transactions, currency, (year, month), range ->
        buildState(transactions, currency, year, month, range)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FinanceUiState())

    fun onPreviousMonth() {
        val (year, month) = selectedYearMonth.value
        selectedYearMonth.value = if (month == 0) (year - 1) to 11 else year to (month - 1)
    }

    fun onNextMonth() {
        val (year, month) = selectedYearMonth.value
        selectedYearMonth.value = if (month == 11) (year + 1) to 0 else year to (month + 1)
    }
    
    fun onMonthSelected(year: Int, month: Int) {
        selectedYearMonth.value = year to month
    }
    
    fun onSummaryRangeChanged(range: SummaryRange) {
        summaryRange.value = range
    }

    private fun buildState(transactions: List<TransactionEntity>, currency: String, year: Int, month: Int, range: SummaryRange): FinanceUiState {
        val (monthStart, monthEnd) = monthBounds(year, month)
        val (prevStart, prevEnd) = monthBounds(if (month == 0) year - 1 else year, if (month == 0) 11 else month - 1)

        fun converted(list: List<TransactionEntity>) = list.sumOf { CurrencyConverter.convert(it.amount, it.currency, currency) }
        fun inMonth(start: Long, end: Long) = transactions.filter { it.transactionDate in start until end }

        val thisMonth = inMonth(monthStart, monthEnd)
        val prevMonth = inMonth(prevStart, prevEnd)

        fun stat(type: String): MonthlyStat {
            val current = converted(thisMonth.filter { it.type == type })
            val previous = converted(prevMonth.filter { it.type == type })
            val change = if (previous > 0.0) ((current - previous) / previous) * 100.0 else null
            val topCategories = thisMonth.filter { it.type == type }
                .groupBy { it.category }
                .mapValues { (_, list) -> converted(list) }
                .toList().sortedByDescending { it.second }.take(3)
            return MonthlyStat(current, change, topCategories)
        }

        val incomeStat = stat("INCOME")
        val expenseStat = stat("EXPENSE")
        val netBalance = incomeStat.total - expenseStat.total
        val prevNet = converted(prevMonth.filter { it.type == "INCOME" }) - converted(prevMonth.filter { it.type == "EXPENSE" })
        val netChange = if (prevNet != 0.0) ((netBalance - prevNet) / kotlin.math.abs(prevNet)) * 100.0 else null

        // Build summary chart points based on range
        val dailyPoints = when(range) {
            SummaryRange.WEEK -> {
                val cal = Calendar.getInstance().apply { timeInMillis = monthStart }
                (0..6).map { i ->
                    val dayStart = cal.timeInMillis + i * 24L * 60 * 60 * 1000
                    val dayEnd = dayStart + 24L * 60 * 60 * 1000
                    val dayTx = transactions.filter { it.transactionDate in dayStart until dayEnd }
                    DailyPoint("Day ${i+1}", converted(dayTx.filter { it.type == "INCOME" }), converted(dayTx.filter { it.type == "EXPENSE" }))
                }
            }
            SummaryRange.YEAR -> {
                (0..11).map { m ->
                    val (mStart, mEnd) = monthBounds(year, m)
                    val mTx = transactions.filter { it.transactionDate in mStart until mEnd }
                    DailyPoint(
                        SimpleDateFormat("MMM").format(Calendar.getInstance().apply { set(year, m, 1) }.time),
                               converted(mTx.filter { it.type == "INCOME" }), converted(mTx.filter { it.type == "EXPENSE" }))
                }
            }
            else -> { // MONTH
                val calendar = Calendar.getInstance().apply { set(year, month, 1, 0, 0, 0) }
                val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
                (1..daysInMonth).map { day ->
                    val dayStart = Calendar.getInstance().apply { set(year, month, day, 0, 0, 0) }.timeInMillis
                    val dayEnd = dayStart + 24L * 60 * 60 * 1000
                    val dayTx = thisMonth.filter { it.transactionDate in dayStart until dayEnd }
                    DailyPoint(day.toString(), converted(dayTx.filter { it.type == "INCOME" }), converted(dayTx.filter { it.type == "EXPENSE" }))
                }
            }
        }

        return FinanceUiState(
            selectedYear = year, selectedMonth = month, incomeStat = incomeStat, expenseStat = expenseStat,
            netBalance = netBalance, netBalancePercentChange = netChange, baseCurrency = currency,
            dailyPoints = dailyPoints, recentTransactions = transactions.sortedByDescending { it.transactionDate }.take(5),
            summaryRange = range
        )
    }

    private fun monthBounds(year: Int, month: Int): Pair<Long, Long> {
        val start = Calendar.getInstance().apply { set(year, month, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0) }
        val end = (start.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
        return start.timeInMillis to end.timeInMillis
    }
}