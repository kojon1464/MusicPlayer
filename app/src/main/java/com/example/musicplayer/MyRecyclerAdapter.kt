package com.example.musicplayer

import android.support.v4.media.MediaBrowserCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView


class MyRecyclerAdapter(private val songs: List<MediaBrowserCompat.MediaItem>, private val callback: (MediaBrowserCompat.MediaItem) -> Unit) :
    RecyclerView.Adapter<MyRecyclerAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView

        init {
            title = view.findViewById(R.id.title)
        }
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.item, viewGroup, false)

        return ViewHolder(view)
    }


    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        viewHolder.title.text = songs[position].description.title
        viewHolder.title.setOnClickListener { callback(songs[position]) }
    }

    override fun getItemCount() = songs.size

}