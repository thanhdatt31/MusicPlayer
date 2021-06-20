package com.example.musicplayer.adapter

import android.content.Context
import android.os.Build
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.RecyclerView
import com.example.musicplayer.R
import com.example.musicplayer.helper.Constants.getAlbumBitmap
import com.example.musicplayer.model.Audio
import java.io.Serializable
import java.util.concurrent.TimeUnit


class AudioListAdapter : RecyclerView.Adapter<AudioListAdapter.ViewHolder>(), Serializable {
    private var audioList: ArrayList<Audio> = arrayListOf()
    private lateinit var context: Context
    private var listener: OnItemClickListener? = null

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), Serializable {
        var imgThumb: ImageView = itemView.findViewById(R.id.img_thumb)
        var title: TextView = itemView.findViewById(R.id.tv_title)
        var duration: TextView = itemView.findViewById(R.id.tv_duration)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view: View = inflater.inflate(R.layout.item_audio, parent, false)
        context = parent.context
        return ViewHolder(view)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val audio = audioList[position]
        holder.title.text = audio.name
        holder.duration.text =
            DateUtils.formatElapsedTime(TimeUnit.MILLISECONDS.toSeconds(audio.duration.toLong()))
        holder.imgThumb.setImageBitmap(getAlbumBitmap(holder.itemView.context, audio.uri))
        holder.itemView.setOnClickListener {
            listener!!.onClicked(audio, position)
        }
    }

    override fun getItemCount(): Int {
        return audioList.size
    }

    fun setData(data: ArrayList<Audio>) {
        audioList = data
        notifyDataSetChanged()
    }

    fun setOnClickListener(listener1: OnItemClickListener) {
        listener = listener1
    }

    interface OnItemClickListener {
        fun onClicked(audio: Audio, pos: Int)
    }
}