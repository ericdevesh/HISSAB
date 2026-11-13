package com.kharcha.app.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [TransactionEntity::class, DebtEntity::class, BudgetEntity::class, GoalEntity::class], version = 2, exportSchema = false)
abstract class KharchaDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun financeDao(): FinanceDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // v2 adds syncStatus to transactions; preserve existing transaction rows.
                db.execSQL("ALTER TABLE transactions ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'LOCAL_ONLY'")
                db.execSQL("CREATE TABLE IF NOT EXISTS debts (id TEXT NOT NULL PRIMARY KEY, person TEXT NOT NULL, phone TEXT NOT NULL, note TEXT NOT NULL, direction TEXT NOT NULL, totalPaise INTEGER NOT NULL, settledPaise INTEGER NOT NULL, createdAt INTEGER NOT NULL, dueAt INTEGER)")
                db.execSQL("CREATE TABLE IF NOT EXISTS budgets (id TEXT NOT NULL PRIMARY KEY, category TEXT NOT NULL, limitPaise INTEGER NOT NULL, monthKey TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS goals (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, targetPaise INTEGER NOT NULL, savedPaise INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
            }
        }
    }
}
