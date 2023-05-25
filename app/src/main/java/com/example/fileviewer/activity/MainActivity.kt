package com.example.fileviewer.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fileviewer.FileModel.FileModel
import com.example.fileviewer.R
import com.example.fileviewer.adapters.FileRecycleAdapter
import com.example.fileviewer.adapters.SpinnerAdapter
import com.example.fileviewer.adapters.setIcon
import com.example.fileviewer.database.AppDatabase
import com.example.fileviewer.database.FileEntity
import com.example.fileviewer.database.dbManager
import com.example.fileviewer.interfaces.Listener
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity(), Listener {

    private val itemAdapter = FileRecycleAdapter(this, this) // Адаптер для списка файлов
    private var currentDir = Environment.getExternalStorageDirectory() // Получаем корневую директорию
    private var fileList: Array<File>? = null // Список файлов в текущей директории
    private val fileModels = ArrayList<FileModel>() // Модели файлов для отображения в RecyclerView
    private var sortType = 0 // По умолчанию список сортируется по названию
    private val hashFileList = ArrayList<FileModel>() // Список файлов, прошедших проверку на хэш
    private lateinit var hashCodes: ArrayList<Long> // Список хеш-сумм файлов в базе данных
    private lateinit var db: AppDatabase // БД хэш-кодов
    private val dbManager = dbManager()
    private val setIcon = setIcon() // Утилита для установки иконок файлов
    private val hashFileListLock = Mutex() // Mutex для блокировки доступа к списку hashFileList
    private var isTaskRunning = false // Флаг, указывающий, выполняется ли задача

    private lateinit var files: RecyclerView // RecyclerView для отображения файлов

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Инициализация RecyclerView
        files = findViewById(R.id.recyclerView)
        files.layoutManager = LinearLayoutManager(this)
        files.adapter = itemAdapter

        // Инициализация Spinner'ов
        val sortSpinner = findViewById<Spinner>(R.id.sort_spinner)
        val editSpinner = findViewById<Spinner>(R.id.edit_spinner)
        val sortAdapter = SpinnerAdapter(this, R.layout.itemtext, R.id.text1, resources.getStringArray(R.array.arraySpinner))
        val editAdapter = SpinnerAdapter(this, R.layout.itemtext, R.id.text1, resources.getStringArray(R.array.editSpinner))
        sortSpinner.adapter = sortAdapter
        editSpinner.adapter = editAdapter

        // Инициализация базы данных
        db = AppDatabase.appDatabase(this)

        init()

        // Обработчик выбора элемента в Spinner'е с типом сортировки
        sortSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                sortType = position
                itemAdapter.sort(sortType)
                if (itemAdapter.itemCount > 0) files.smoothScrollToPosition(0)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Обработчик выбора элемента в Spinner'е для просмотра последних отредактированных файлов
        editSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                when (position) {
                    0 -> {
                        if (itemAdapter.itemCount > 0) files.smoothScrollToPosition(0)
                        init()
                        hashFileList.clear()
                    }
                    1 -> {
                        itemAdapter.clear()
                        if (itemAdapter.itemCount > 0) files.smoothScrollToPosition(0)
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
        if (permissionCheck() && !isTaskRunning) { // Проверка флага перед выполнением задачи
            val dir = File(Environment.getExternalStorageDirectory().path)
            isTaskRunning = true
            coroutineScope.launch {
                try {
                    // Получение списка файлов в директории
                    val fileList = withContext(Dispatchers.IO) { dir.listFiles { file -> true } }
                    // Получение списка хеш-сумм файлов из базы данных
                    hashCodes = withContext(Dispatchers.IO) { dbManager.getHashCodesFromDBAsync(context = this@MainActivity).await() }
                    // Обновление списка файлов и хеш-сумм
                    updateList(dir, fileList)
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error in init(): ${e.message}")
                } finally {
                    isTaskRunning = false // Сброс флага после выполнения задачи
                }
            }
        }
    }

    private suspend fun checkHashAsync(fileList: Array<File>): ArrayList<FileModel> = withContext(Dispatchers.Default) {
        val tempFileList = ArrayList<File>()
        for (file in fileList) {
            val tempHash = file.hashCode().toLong() + file.length().hashCode()
            if (!hashCodes.contains(tempHash)) {
                tempFileList.add(file)
            }
        }

        val tempHashFileList = ArrayList<FileModel>()

        for (file in tempFileList) {
            val fileModel = FileModel(
                id = file.hashCode().toLong(),
                name = file.name,
                size = file.length(),
                date = Date(file.lastModified()),
                extension = file.extension,
                icon = setIcon.setIcon(file.extension, file.isDirectory),
                path = file.path,
                isDirectory = file.isDirectory
            )
            tempHashFileList.add(fileModel)
        }

        tempHashFileList
    }

    override fun updateList(dir: File, fileList: Array<File>?) {
        files.smoothScrollToPosition(0)
        this.fileList = fileList
        fileModels.clear()
        if (fileList != null) {
            coroutineScope.launch {
                try {
                    val tempHashFileList = checkHashAsync(fileList)
                    hashFileListLock.withLock {
                        hashFileList.clear()
                        hashFileList.addAll(tempHashFileList)
                    }

                    for (file in fileList) {
                        val fileModel = FileModel(
                            id = file.hashCode().toLong(),
                            name = file.name,
                            size = file.length(),
                            date = Date(file.lastModified()),
                            extension = file.extension,
                            icon = setIcon.setIcon(file.extension, file.isDirectory),
                            path = file.path,
                            isDirectory = file.isDirectory
                        )

                        val fileItem = FileEntity(null, file.name, file.hashCode().toLong() + file.length().hashCode())
                        withContext(Dispatchers.IO) { db.getDao().insert(fileItem) }
                        fileModels.add(fileModel)
                    }
                    currentDir = dir
                    itemAdapter.clear()
                    itemAdapter.add(fileModels)
                    itemAdapter.sort(sortType)
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error in updateList(): ${e.message}")
                }
            }
        } else {
            currentDir = dir
            itemAdapter.clear()
        }
    }

    override fun onBackPressed() {
        if (currentDir != Environment.getExternalStorageDirectory()) {
            coroutineScope.launch {
                try {
                    val parentDir = currentDir.parentFile
                    if (parentDir != null) {
                        val fileList = withContext(Dispatchers.IO) { parentDir.listFiles { file -> true } }
                        hashFileListLock.withLock {
                            updateList(parentDir, fileList)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error in onBackPressed(): ${e.message}")
                }
            }
        } else {
            coroutineScope.launch {
                hashFileListLock.withLock {
                    super.onBackPressed()
                }
            }
        }
    }

    // Запрашиваем разрешения на доступ к памяти.
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
                        if (ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.READ_EXTERNAL_STORAGE
                            ) != PackageManager.PERMISSION_GRANTED ||
                            ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            ActivityCompat.requestPermissions(
                                this@MainActivity,
                                arrayOf(
                                    Manifest.permission.READ_EXTERNAL_STORAGE,
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                                ),
                                1
                            )
                        }
                    } else {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        val uri: Uri = Uri.fromParts("package", packageName, null)
                        Toast.makeText(
                            this@MainActivity,
                            "Выдайте разрешение на отображение всех файлов",
                            Toast.LENGTH_SHORT
                        ).show()
                        intent.data = uri
                        startActivity(intent)
                    }
                }
            }
        }
    }

    // Проверяем, есть ли у приложения права. Если нет -- запрашиваем.
    private fun permissionCheck(): Boolean {
        return if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager())
        ) {
            true
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                1
            )
            false
        }
    }

    // Если список файлов пуст из-за отсутствия разрешения, запрашиваем список снова
    override fun onResume() {
        super.onResume()
        if (itemAdapter.itemCount == 0) init()
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
        db.close()
    }
}
