package com.v2ray.ang.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class HandleRewardTime(
    @PrimaryKey val uid: Int,
    @ColumnInfo(name = "enterTime") val enterTime: Long ,
    @ColumnInfo(name = "exitTime") val exitTime: Long ,
    @ColumnInfo(name = "baseReward") val baseReward: Int ,
    @ColumnInfo(name = "currentReward") val currentReward: Int
)
