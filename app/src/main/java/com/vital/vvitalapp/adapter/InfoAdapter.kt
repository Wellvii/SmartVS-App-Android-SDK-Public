package com.vital.vvitalapp.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.vital.vvitalapp.bean.InfoBean
import com.vital.vvitalapp.databinding.RecyclerDeviceDetailItemBinding

class InfoAdapter(private var infoList: ArrayList<InfoBean>) :
    RecyclerView.Adapter<InfoAdapter.EventViewHolder>() {

    private var onItemClick: OnItemClick? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val binding = RecyclerDeviceDetailItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return EventViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val info = infoList[position]
        holder.binding.txtTitle.text = info.infoTitle
        holder.binding.txtVal.text = info.infoVal

        holder.binding.root.setOnClickListener {
            onItemClick?.onViewClick(position)
        }
    }

    override fun getItemCount(): Int = infoList.size

    fun setOnItemClick(onItemClick: OnItemClick) {
        this.onItemClick = onItemClick
    }

    interface OnItemClick {
        fun onViewClick(position: Int)
    }

    inner class EventViewHolder(val binding: RecyclerDeviceDetailItemBinding) :
        RecyclerView.ViewHolder(binding.root)
}