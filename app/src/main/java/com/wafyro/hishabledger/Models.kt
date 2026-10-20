package com.wafyro.hishabledger

import java.util.*

enum class PartyType { SHOP, PERSON }

data class Shop(
    val id: String,
    val name: String,
    val phone: String = "",
    val address: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val type: PartyType = PartyType.SHOP,
    val deletedAt: Long = 0L
)

enum class TxType { DUE, PAYMENT, OPENING_BALANCE }

data class Tx(
    val id: String,
    val shopId: String,
    val amount: Long,
    val type: TxType,
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val deletedAt: Long = 0L,
    /** Date the transaction belongs to; defaults to the legacy timestamp for old backups. */
    val transactionDate: Long = timestamp
)
