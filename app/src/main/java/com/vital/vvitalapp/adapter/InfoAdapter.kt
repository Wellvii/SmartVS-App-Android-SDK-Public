package com.vital.vvitalapp.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.vital.vvitalapp.R
import com.vital.vvitalapp.bean.InfoBean
import kotlinx.android.synthetic.main.recycler_device_detail_item.view.*
import java.util.*

/**
 * SmartVSApp
 *
 * Created By Administrator on 10/10/2019
 *
 * Copyright Softweb Solutions Inc. 2019,  All rights reserved.
 */
class InfoAdapter(private var infoList: ArrayList<InfoBean>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var onItemClick: OnItemClick? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return EventViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.recycler_device_detail_item,
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is EventViewHolder) {
            holder.itemView.tag = position
            holder.txtTitle.text = infoList[position].infoTitle

            holder.txtVal.text = infoList[position].infoVal
        }
    }

    override fun getItemCount(): Int = infoList.size

    fun setOnItemClick(onItemClick: OnItemClick) {
        this.onItemClick = onItemClick
    }

    interface OnItemClick {
        fun onViewClick(position: Int)
    }

    private inner class EventViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtTitle = view.txtTitle!!
        val txtVal = view.txtVal!!

        init {
            if (onItemClick != null) {
                view.setOnClickListener { _view: View? ->
                    run {
                        onItemClick!!.onViewClick(_view!!.tag as Int)
                    }
                }
            }
        }
    }
}