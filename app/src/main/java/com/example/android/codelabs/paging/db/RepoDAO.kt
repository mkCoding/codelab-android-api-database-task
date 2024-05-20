package com.example.android.codelabs.paging.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.android.codelabs.paging.model.Repo
import retrofit2.http.DELETE

@Dao
interface RepoDAO {
    //Insert list of Repo objects. If they already exist replace them
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(repos: List<Repo>) //abstract method

    //Sorts the results [Repo Table] descending order based on number of stars then alphabetically by name
    //Call this method to get these results from database
    @Query("Select * FROM repos WHERE `query`=:queryString AND "+ //only select records/repos related specifically to queryString
            "name LIKE :queryString OR description Like :queryString "+
            "ORDER BY stars DESC, name ASC")
    fun reposByName (queryString:String):PagingSource<Int,Repo>

    //Clear records in [Repos Table]
    @Query("DELETE FROM repos where `query`=:queryString") //only clear records related specifically to queryString
    suspend fun clearRepos(queryString: String)

    //this will check the DB and execute a query which will determine the count based on the given
    //query passed in by the user. I only am focused on the number of related records
    @Query("Select Count(*) FROM repos Where `query` =:queryString AND name " +
            "Like:queryString OR description Like:queryString")
    suspend fun getCountOfRepos(queryString: String):Int

    //this will be used to clear repos from DB if query entered by user already exist in DB
    @Query ("Delete from repos Where `query` = :queryString") //field that is not specifically not part part of api response
    suspend fun clearReposByQuery(queryString: String)



}