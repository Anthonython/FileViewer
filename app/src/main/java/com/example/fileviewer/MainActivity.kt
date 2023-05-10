package com.example.fileviewer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.size
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fileviewer.FileModel.FileModel
import com.example.fileviewer.adapters.FileRecycleAdapter
import com.example.fileviewer.adapters.SpinnerAdapter
import com.example.fileviewer.database.AppDatabase
import com.example.fileviewer.database.FileEntity
import com.example.fileviewer.database.dbManager
import com.example.fileviewer.interfaces.Listener
import kotlinx.coroutines.*
import java.io.File
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity(), Listener {

    private val itemAdapter = FileRecycleAdapter(this, this)
    private var currentDir = Environment.getExternalStorageDirectory()
    private var fileList = currentDir.listFiles {file -> true}
    private val fileModels = ArrayList<FileModel>()
    private var sortType = 0         // По умолчанию список сортируется по названию
    private var hashFileList = ArrayList<FileModel>()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var hashCodes : ArrayList<Long>
    private lateinit var db: AppDatabase
    private val dbManager = dbManager()

    private lateinit var files: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        files = findViewById(R.id.recyclerView)
        val sortSpinner = findViewById<Spinner>(R.id.sort_spinner)
        val editSpinner = findViewById<Spinner>(R.id.edit_spinner)
        val sortAdapter = SpinnerAdapter(this, R.layout.itemtext, R.id.text1, resources.getStringArray(R.array.arraySpinner))
        val editAdapter = SpinnerAdapter(this, R.layout.itemtext, R.id.text1, resources.getStringArray(R.array.editSpinner))
        files.layoutManager = LinearLayoutManager(this)
        files.adapter = itemAdapter
        sortSpinner.adapter = sortAdapter
        editSpinner.adapter = editAdapter
        db = AppDatabase.appDatabase(this)

        init()

        sortSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                sortType = position
                itemAdapter.sort(sortType)
                if (files.size > 0) files.smoothScrollToPosition(0)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        editSpinner.onItemSelectedListener = object :AdapterView.OnItemSelectedListener{
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                when (position){
                    0 -> {
                        if (files.size > 0) files.smoothScrollToPosition(0)
                        init()
                        hashFileList.clear()
                    }
                    1 -> {
                        itemAdapter.clear()
                        if (files.size > 0) files.smoothScrollToPosition(0)
                        itemAdapter.add(hashFileList)
                        itemAdapter.sort(sortType)
                        hashFileList.clear()
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    override fun init() {
        if (permissionCheck()){
            val dir = File(Environment.getExternalStorageDirectory().path)
            coroutineScope.launch {
                hashCodes = dbManager.getHashCodesFromDBAsync(context = this@MainActivity).await()
                withContext(Dispatchers.Main) {
                    updateList(dir)
                }
            }
        }
    }

    fun setIcon(extension: String, isDirectory: Boolean): Int{
        return if (!isDirectory) {
            when (extension) {
                "jpg", "png", "jpeg", "gif" -> return R.drawable.imgpic
                "mp3", "wav", "flac" -> R.drawable.imgmusic
                "mp4", "avi", "mkv", "wmv" -> R.drawable.imgvideo
                "txt", "log" -> R.drawable.imgtext
                "pdf" -> R.drawable.imgfile
                "apk" -> R.drawable.imgapk
                else -> R.drawable.imgfile
            }
        } else return R.drawable.imgfolder
    }

    private fun checkHash(): ArrayList<FileModel> {
        val tempFileList = ArrayList<File>()
        for (file in fileList!!) {
            val tempHash = file.hashCode().toLong() + file.length().hashCode()
            if (!hashCodes.contains(tempHash)) {
                tempFileList.add(file)
            }
        }
        for (file in tempFileList){
            val fileModel = FileModel(
                id = file.hashCode().toLong(),
                name = file.name,
                size = file.length(),
                date = Date(file.lastModified()),
                extension = file.extension,
                icon = setIcon(file.extension, file.isDirectory),
                path = file.path,
                isDirectory = file.isDirectory
            )
            hashFileList.add(fileModel)
        }
        tempFileList.clear()
        return hashFileList
    }

    override fun updateList(dir: File) {
        files.smoothScrollToPosition(0)
        fileList = dir.listFiles {file -> true}
        fileModels.clear()


       if (fileList!= null) {
           checkHash()
            for (file in fileList!!) {
                val fileModel = FileModel(
                    id = file.hashCode().toLong(),
                    name = file.name,
                    size = file.length(),
                    date = Date(file.lastModified()),
                    extension = file.extension,
                    icon = setIcon(file.extension, file.isDirectory),
                    path = file.path,
                    isDirectory = file.isDirectory
                )

                val fileItem = FileEntity(null, file.name, file.hashCode().toLong() + file.length().hashCode())
                coroutineScope.launch {db.getDao().insert(fileItem)}
                fileModels.add(fileModel)
            }
            currentDir = dir
            itemAdapter.clear()
            itemAdapter.add(fileModels)
            itemAdapter.sort(sortType)
        }
        else {
            currentDir = dir
            itemAdapter.clear()
        }

    }

    override fun onBackPressed() {
        if (currentDir != Environment.getExternalStorageDirectory()) {
            val parentDir = currentDir.parentFile
            if (parentDir != null)
                updateList(parentDir)
        } else {
            Toast.makeText(this, "Нет", Toast.LENGTH_SHORT).show()
            super.onBackPressed()
        }

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || !Environment.isExternalStorageManager()) {

                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(
                                this,
                                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                                1
                            )
                        }
                    } else {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        val uri: Uri = Uri.fromParts("package", packageName, null)
                        Toast.makeText(this, "Выдайте разрешение на отображение всех файлов", Toast.LENGTH_SHORT).show()
                        intent.data = uri
                        startActivity(intent)
                    }
                }
            }
        }
    }

    private fun permissionCheck(): Boolean {
        return if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager())) {
            true
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
            false
        }
    }

    override fun onResume() {
        super.onResume()
        if (itemAdapter.itemCount == 0) init()
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }
}