package com.v2ray.ang.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface HandleRewardDao {
    @Query("SELECT * FROM HandleRewardTime")
    fun getData(): HandleRewardTime

    @Update(entity = HandleRewardTime::class)
    fun updateData(obj: HandleRewardTime)

    @Insert
    fun insertData(handleRewardTime: HandleRewardTime)

}