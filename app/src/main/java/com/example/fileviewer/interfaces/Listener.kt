package com.example.fileviewer.interfaces

import java.io.File

interface Listener {
    fun updateList(dir: File)
    fun init()
}