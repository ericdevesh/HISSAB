package com.kharcha.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "debts")
data class DebtEntity(
    @PrimaryKey val id: String,
    val person: String,
    val phone: String = "",
    val note: String = "",
    val direction: String, // GIVEN: they owe me; TAKEN: I owe them
    val totalPaise: Long,
    val settledPaise: Long = 0L,
    val createdAt: Long,
    val dueAt: Long? = null
)

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val id: String,
    val category: String,
    val limitPaise: Long,
    val monthKey: String
)

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey val id: String,
    val title: String,
    val targetPaise: Long,
    val savedPaise: Long = 0L,
    val createdAt: Long
)
