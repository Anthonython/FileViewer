package com.example.fileviewer.adapters

import com.example.fileviewer.R

class setIcon {
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
}