package com.kharcha.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY createdAt DESC") fun observeAll(): Flow<List<TransactionEntity>>
    @Query("SELECT * FROM transactions ORDER BY createdAt DESC") suspend fun getAllForSync(): List<TransactionEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(item: TransactionEntity)
    @Query("DELETE FROM transactions WHERE id = :id") suspend fun delete(id: String)
    @Query("SELECT COALESCE(SUM(amountPaise),0) FROM transactions WHERE type='INCOME'") fun observeIncome(): Flow<Long>
    @Query("SELECT COALESCE(SUM(amountPaise),0) FROM transactions WHERE type='EXPENSE'") fun observeExpenses(): Flow<Long>
    @Query("SELECT COALESCE(SUM(amountPaise),0) FROM transactions WHERE type='EXPENSE' AND category = :category AND createdAt >= :start AND createdAt < :end") fun observeCategoryExpense(category: String, start: Long, end: Long): Flow<Long>
}
