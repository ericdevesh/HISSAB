package com.kharcha.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceDao {
    @Query("SELECT * FROM debts ORDER BY createdAt DESC") fun observeDebts(): Flow<List<DebtEntity>>
    @Query("SELECT * FROM debts ORDER BY createdAt DESC") suspend fun getAllDebtsForSync(): List<DebtEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveDebt(item: DebtEntity)
    @Query("UPDATE debts SET settledPaise = MIN(totalPaise, settledPaise + :amount) WHERE id = :id") suspend fun addDebtPayment(id: String, amount: Long)
    @Query("DELETE FROM debts WHERE id = :id") suspend fun deleteDebt(id: String)

    @Query("SELECT * FROM budgets ORDER BY monthKey DESC, category") suspend fun getAllBudgetsForSync(): List<BudgetEntity>
    @Query("SELECT * FROM budgets WHERE monthKey = :month ORDER BY category") fun observeBudgets(month: String): Flow<List<BudgetEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveBudget(item: BudgetEntity)
    @Query("DELETE FROM budgets WHERE id = :id") suspend fun deleteBudget(id: String)

    @Query("SELECT * FROM goals ORDER BY createdAt DESC") suspend fun getAllGoalsForSync(): List<GoalEntity>
    @Query("SELECT * FROM goals ORDER BY createdAt DESC") fun observeGoals(): Flow<List<GoalEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveGoal(item: GoalEntity)
    @Query("UPDATE goals SET savedPaise = savedPaise + :amount WHERE id = :id") suspend fun addGoalSaving(id: String, amount: Long)
    @Query("DELETE FROM goals WHERE id = :id") suspend fun deleteGoal(id: String)
}
