package com.v2ray.ang.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [HandleRewardTime::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rwDao(): HandleRewardDao
}