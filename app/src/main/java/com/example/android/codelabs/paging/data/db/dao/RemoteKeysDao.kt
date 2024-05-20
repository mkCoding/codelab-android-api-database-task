package com.example.android.codelabs.paging.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.android.codelabs.paging.data.db.tables.RemoteKeys

@Dao
interface RemoteKeysDao {
    //when we get Repos from API generate remote keys for them
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(remoteKey: List<RemoteKeys>)

    //get RemoteKey based on repo Id (remote key and repo id are linked)
    @Query("SELECT * FROM remote_keys WHERE repoId = :repoId")
    suspend fun remoteKeysRepoId(repoId: Long): RemoteKeys?

    //clear remote keys from DB
    @Query("DELETE FROM remote_keys")
    suspend fun clearRemoteKeys()

    //Only remove remote keys form repo that are associated with query string
    //this will ensure that all remote keys will not be removed from the DB but rather only specific remote keys
    //specifically associated with query passed in
    @Query("DELETE FROM remote_keys where repoId IN" +
            " (Select id from repos where `query`=:queryString)")
    suspend fun clearRemoteKeysByQuery(queryString: String)
}