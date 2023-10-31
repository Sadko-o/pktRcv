package com.example.pktrcv
data class PacketData(
    val distance: Int,
    val time: Float,
    val trainId: Int,
    val direction: Char,
    var timestamp: String=""
)