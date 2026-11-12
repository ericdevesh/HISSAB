package com.kharcha.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val category: String,
    val amountPaise: Long,
    val type: String,
    val createdAt: Long,
    val syncStatus: String = "LOCAL_ONLY"
)
