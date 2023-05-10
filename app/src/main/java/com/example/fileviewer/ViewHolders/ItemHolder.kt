package com.example.fileviewer.ViewHolders

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.fileviewer.FileModel.FileModel
import com.example.fileviewer.R

class ItemHolder(item: View): RecyclerView.ViewHolder(item){

    private val filename: TextView = item.findViewById(R.id.FileName)
    private val filesize: TextView = item.findViewById(R.id.FileSize)
    private val filedata: TextView = item.findViewById(R.id.FileData)
    private val fileimg: ImageView = item.findViewById(R.id.imgFile)
    private val fileShare: ImageView = item.findViewById(R.id.imgShare)

    fun init(filemodel: FileModel) {
        filename.text = filemodel.name
        filesize.text = "${filemodel.size/1024} KB"
        filedata.text = filemodel.date.toString()
        Glide.with(itemView.context).load(filemodel.icon).into(fileimg)
        fileShare.visibility = if (filemodel.isDirectory) View.GONE else View.VISIBLE
    }
}