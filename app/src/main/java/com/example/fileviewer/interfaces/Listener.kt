package com.example.fileviewer.interfaces

import java.io.File

interface Listener {
    fun updateList(dir: File, fileList: Array<File>?)
    fun init()
}