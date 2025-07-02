package org.ganquan.musictimer

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.ganquan.musictimer.tools.Utils
import org.ganquan.musictimer.tools.Utils.Companion.int2String

class NormalTimeAdapter(private val items: MutableList<List<Int>>) : RecyclerView.Adapter<MyViewHolder>() {

    val sharedPreferKey: String = "normalTimeList"

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        Utils.sharedPrefer(parent.context, sharedPreferKey, items)
        val view = LayoutInflater.from(parent.context).inflate(R.layout.normal_time_list_layout, parent, false)
        return MyViewHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        var startH: Int = items[position][0]
        var startM: Int = items[position][1]
        var endH: Int = startH
        var endM: Int = startM+items[position][2]
        if(endM > 60) {
            endH += 1
            endM -= 60
        }
        holder.normalTime.text = "${int2String(startH)}:${int2String(startM)} - ${int2String(startH)}:${int2String(endM)}"

        holder.deleteButton.setOnClickListener {
            items.removeAt(position)

            notifyItemRemoved(position)
            notifyItemRangeChanged(position, items.size - position)
        }
    }

    override fun getItemCount(): Int = items.size
}

class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val deleteButton: TextView = itemView.findViewById(R.id.normal_time_del)
    val normalTime: TextView = itemView.findViewById(R.id.normal_time)
}