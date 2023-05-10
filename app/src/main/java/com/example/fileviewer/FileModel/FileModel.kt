package com.example.fileviewer.FileModel

import java.util.*

data class FileModel(
    val id: Long,
    val name: String,
    val size: Long,
    val date: Date,
    val extension: String,
    val icon: Int,
    val path: String,
    val isDirectory: Boolean
){
    // добавить метод в FileModel
    fun getMimeType(): String {
        return when (this.path.substringAfterLast(".")) {
            "jpg", "png", "jpeg", "gif" -> "image/*"
            "mp3", "wav", "flac" -> "audio/*"
            "mp4", "avi", "mkv", "wmv" -> "video/*"
            "txt", "log" -> "text/plain"
            "pdf" -> "application/pdf"
            else -> "*/*"
        }
    }
}


