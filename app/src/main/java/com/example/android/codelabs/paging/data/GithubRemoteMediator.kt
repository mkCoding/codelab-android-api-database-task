package com.example.android.codelabs.paging.data

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.example.android.codelabs.paging.data.network.GithubService
import com.example.android.codelabs.paging.data.network.IN_QUALIFIER
import com.example.android.codelabs.paging.data.db.tables.RemoteKeys
import com.example.android.codelabs.paging.data.db.RepoDatabase
import com.example.android.codelabs.paging.data.db.tables.Repo
import retrofit2.HttpException
import java.io.IOException

/*
  Class will help load more data in DB from network, when DB is empty for specific query
 */

private const val GITHUB_STARTING_PAGE_INDEX = 1

@OptIn(ExperimentalPagingApi::class)
/*
   3 constructor params
   1) query - query string from user
   2.) service - to make network request
   3.) repoDatabase - save data retrieved from network call in DB
 */
class GithubRemoteMediator(
    private val query: String,
    private val service: GithubService,
    private val repoDatabase: RepoDatabase
) : RemoteMediator<Int, Repo>() {

    /*
    This method will be called whenever we need to load more data from the network
      loadType - info about pages loaded before, most recent accessed index and type of paging defined
      state - specifies if data should be loaded at end, beginning, if data was previously loaded or loaded
              for 1st time
     */
    override suspend fun load(loadType: LoadType, state: PagingState<Int, Repo>): MediatorResult {
        //Find out what page we need to load from the network, based on the LoadType.
        val page = when (loadType) {
            LoadType.REFRESH -> {
                val remoteKeys = getRemoteKeyClosestToCurrentPosition(state)
                remoteKeys?.nextKey?.minus(1) ?: GITHUB_STARTING_PAGE_INDEX
            }

            LoadType.PREPEND -> {
                val remoteKeys = getRemoteKeyForFirstItem(state)
                // If remoteKeys is null, that means the refresh result is not in the database yet.
                val prevKey = remoteKeys?.prevKey
                if (prevKey == null) {
                    return MediatorResult.Success(endOfPaginationReached = remoteKeys != null)
                }
                prevKey
            }

            //
            LoadType.APPEND -> {
                val remoteKeys = getRemoteKeyForLastItem(state)
                // If remoteKeys is null, that means the refresh result is not in the database yet.
                // We can return Success with endOfPaginationReached = false because Paging
                // will call this method again if RemoteKeys becomes non-null.
                // If remoteKeys is NOT NULL but its nextKey is null, that means we've reached
                // the end of pagination for append.
                val nextKey = remoteKeys?.nextKey
                if (nextKey == null) {
                    return MediatorResult.Success(endOfPaginationReached = remoteKeys != null)
                }
                nextKey
            }
        }

        //variable holding query user passes in
        val apiQuery = query + IN_QUALIFIER

        /*Task given over the weekend
        * 1.) Make an API call based on the user query to return all repos via network
          2.) Save response from API in Repo DB (Rooms)
          3.) If client makes the api call again, don't hit the API
          4.) Instead grab the data from the Repo DB instead.
          *
          * I used the below cachedRepos variable in an attempt to achieve this
         */

        //This variable holds the count of repos in the database based on the query passed in by user
        val cachedRepos = repoDatabase.reposDao().getCountOfRepos(apiQuery)

        //if there are cached results in the DB based on query passed by user, then retrieve data from DB rather than the API
        if(cachedRepos > 0){
            // Data is already cached, no need to make an API call get it from the DB
            return MediatorResult.Success(endOfPaginationReached = false)
        }

        //If no cached results are found in DB then make the API call and cache the results in DB
        try {
            //Make the API call
            val apiResponse = service.searchRepos(apiQuery, page, state.config.pageSize)


            //For each of the repos returned from API call update the query field in repo obj
            //and assign the apiQuery to the query field for each of the repos
              /*
               apiResponse.items - list of repos retrieved from API call
               .map - transforms each object to a list of repo objects
               it - current item in list being processed
               it.copy(query = apiQuery) - creates copy of data instance and provides option to change
               some of its properties.

                This ultimately ensures that each Repo object in the list has obj ref to query string
                used to fetch each repo by query
             */
            val repos = apiResponse.items.map {
                //in the query field that I added to the Repo
                it.copy(query = apiQuery) // Add the value of apiQuery to each of the repos
            }

            //end of pagination reached if repo retrieved from api is empty/contains no repos
            val endOfPaginationReached = repos.isEmpty()

            //return data specifically from the database
            repoDatabase.withTransaction {

                // clear all tables in the database when new query is entered
                if (loadType == LoadType.REFRESH) {

                    /*
                       1.) Whenever a query is sent from the UI a network call will be made
                       2.) A list of repos will be returned from tha API
                       3.) A remote key will be generated for each repo in the results
                       4.) All remote keys and repo data will be added to the database
                       4.) In this particular conditional statement if loadType is Refresh then this
                           means that a new query has been entered and entire DB should be cleared
                           (remote keys and db from previously entered query will be cleared from the DB)
                       5.) In order to ensure that entire DB is not cleared from DB when new query is entered,
                           I need to modify Repo, RepoDAO, RepoKeysDAO as well as this conditional statement
                           to ensure that only data related to the repeated query in UI is cleared.
                           Because it will eventually be added again to DB
                     */

                    //adding functionality to not clear the entire remote keys and repos during refresh

                    //only remove repos related to the current query only
                    repoDatabase.remoteKeysDao().clearRemoteKeysByQuery(apiQuery)
                    repoDatabase.reposDao().clearReposByQuery(apiQuery)

//This code initially cleared everything from DB when new query was entered [forcing a new API call if a repeated search was entered]
//                    repoDatabase.remoteKeysDao().clearRemoteKeys()
//                    repoDatabase.reposDao().clearRepos()

                }

                val prevKey = if (page.equals(GITHUB_STARTING_PAGE_INDEX)) null else page - 1
                val nextKey = if (endOfPaginationReached) null else page + 1
                val keys = repos.map {
                    RemoteKeys(repoId = it.id, prevKey = prevKey, nextKey = nextKey)
                }


                repoDatabase.remoteKeysDao().insertAll(keys) //insert all keys into the database
                repoDatabase.reposDao().insertAll(repos) //insert all repos in the database (retrieved from the api call)
            }



            return MediatorResult.Success(endOfPaginationReached = endOfPaginationReached)

        } catch (exception: IOException) {
            return MediatorResult.Error(exception)
        } catch (exception: HttpException) {
            return MediatorResult.Error(exception)
        }


    }

    private suspend fun getRemoteKeyForLastItem(state: PagingState<Int, Repo>): RemoteKeys? {
        // Get the last page that was retrieved, that contained items.
        // From that last page, get the last item

        //return value
        //
        return state.pages.lastOrNull() { it.data.isNotEmpty() }?.data?.lastOrNull()
            ?.let { repo ->
                // Get the remote keys of the last item retrieved
                repoDatabase.remoteKeysDao().remoteKeysRepoId(repo.id)
            }

    }


    private suspend fun getRemoteKeyForFirstItem(state: PagingState<Int, Repo>): RemoteKeys? {
        // Get the first page that was retrieved, that contained items.
        // From that first page, get the first item
        return state.pages.firstOrNull { it.data.isNotEmpty() }?.data?.firstOrNull()
            ?.let { repo ->
                // Get the remote keys of the first items retrieved
                repoDatabase.remoteKeysDao().remoteKeysRepoId(repo.id)
            }
    }

    private suspend fun getRemoteKeyClosestToCurrentPosition(
        state: PagingState<Int, Repo>
    ): RemoteKeys? {
        // The paging library is trying to load data after the anchor position
        // Get the item closest to the anchor position
        return state.anchorPosition?.let { position ->
            state.closestItemToPosition(position)?.id?.let { repoId ->
                repoDatabase.remoteKeysDao().remoteKeysRepoId(repoId)
            }
        }
    }


}