package com.example.android.codelabs.paging.data.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.Toast

/*
   A broadcast receiver is a component of android that constantly listens for system wide changes
 */


//class that is responsible for connectivity change events (BroadcastReceiver extended)
class NetworkChangeReceiver:BroadcastReceiver()
{
    //This method is called when phone receives an intent broadcast.
    // It constantly listens for system wide changes
    override fun onReceive(context: Context?, intent: Intent?) {

        //initialize connectivityManager variable that gets system service responsible for network connections
        val connectivityManager = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        //this retrieves the currently active network/wifi from connectivity manager
        val activeNetwork = connectivityManager.activeNetwork

        //gets the capabilities of the network
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)

        //This will check if network is capable of accessing the internet. will return true/false
        val isConnected:Boolean = networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        //handle connectivity change
        if(isConnected){
            Toast.makeText(context, "Connected to the internet", Toast.LENGTH_SHORT).show()
        }else{
            Toast.makeText(context, "Not connected to the internet", Toast.LENGTH_SHORT).show()
        }

    }
}