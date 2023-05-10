package com.example.fileviewer.adapters

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import com.example.fileviewer.FileModel.FileModel
import com.example.fileviewer.R
import com.example.fileviewer.ViewHolders.ItemHolder
import com.example.fileviewer.interfaces.Listener
import java.io.File
import java.util.*


class FileRecycleAdapter(private val context: Context, private val listener: Listener): RecyclerView.Adapter<ItemHolder>() {
    private val listitem = ArrayList<FileModel>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemHolder {
        val layoutInflater = LayoutInflater.from(parent.context).inflate(R.layout.itemfile, parent, false)
        return ItemHolder(layoutInflater)
    }

    override fun onBindViewHolder(holder: ItemHolder, position: Int) {
        val item = listitem[position]
        val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", File(item.path))

        holder.init(item)
        holder.itemView.setOnClickListener {
            if (item.isDirectory)  listener.updateList(File(item.path))
            else context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndTypeAndNormalize(contentUri, item.getMimeType())
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        }

        holder.itemView.findViewById<ImageView>(R.id.imgShare).setOnClickListener {
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                type = item.getMimeType()
            }, "Поделиться файлом"))
        }
    }


    fun sort(type: Int){
        when(type){
            0 ->   listitem.sortBy { it.name.lowercase(Locale.getDefault()) }
            1 ->   listitem.sortBy { it.size }
            2 ->   listitem.sortByDescending { it.size }
            3 ->   listitem.sortBy { it.extension }
            4 ->   listitem.sortBy { it.date }
        }
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        return listitem.size
    }

    fun add(item: ArrayList<FileModel>){
        listitem.addAll(item)
        notifyDataSetChanged()
    }

    fun clear(){
        listitem.clear()
        notifyDataSetChanged()
    }
}