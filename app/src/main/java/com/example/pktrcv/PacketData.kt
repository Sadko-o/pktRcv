package com.example.pktrcv
data class PacketData(
    val distance: Float,
    val time: Float,
    val train_id: Int,
    val direction: Char,
    var timestamp: String=""
)