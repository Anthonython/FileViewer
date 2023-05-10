package com.example.fileviewer.database

import android.content.Context
import kotlinx.coroutines.*

class dbManager {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun getHashCodesFromDBAsync(context: Context): Deferred<ArrayList<Long>> = coroutineScope.async(Dispatchers.IO) {
        val db = AppDatabase.appDatabase(context)
        val fileItems = db.getDao().getAll()

        val hashCodes = ArrayList<Long>()
        for (fileItem in fileItems) {
            hashCodes.add(fileItem.hash)
        }
        return@async hashCodes
    }
}