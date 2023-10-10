package com.example.pktrcv

data class PacketData(
    val distance: Float,
    val time: Int,
    val trainId: Int,
    val direction: Char,
    val timestamp: String
)


/*
* data:
* distance
* time
* trainId
* direction
                        val distance = jsonObject.getDouble("distance").toFloat()
                        val time = jsonObject.getInt("time")
                        val trainId = jsonObject.getInt("train_id");
                        val direction = jsonObject.getString("direction")[0]
* */