package com.example.android.codelabs.paging.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.android.codelabs.paging.model.Repo

@Database(
    entities = [Repo::class,RemoteKeys::class],
    version = 2, //migrating to new DB version because of the new 'query' column added to Repo DB
    exportSchema = false
)
abstract class RepoDatabase: RoomDatabase() {

    abstract fun reposDao():RepoDAO
    abstract fun remoteKeysDao():RemoteKeysDao

    companion object{
        @Volatile
        private var INSTANCE:RepoDatabase? = null

        //Creates RepoDatabase object if it doesn't exist (singleton [only 1 instance of DB will be created for this app])
        fun getInstance(context:Context):RepoDatabase =
            INSTANCE ?: synchronized(this){
                INSTANCE ?: buildDatabase(context).also{ INSTANCE = it}
            }

        private fun buildDatabase(context: Context) =
            Room.databaseBuilder(context.applicationContext,
            RepoDatabase::class.java, "Github.db")
                .addMigrations(MIGRATION_1_2) //migrate from version 1 to 2
                .build()


        //If there is a field/columns added to DB then a migration has to be done
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Execute this DB query on repos table to add the new query column
                database.execSQL("ALTER TABLE repos ADD COLUMN query TEXT NOT NULL DEFAULT ''")
            }
        }
    }


}