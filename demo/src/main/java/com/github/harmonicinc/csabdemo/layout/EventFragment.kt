package com.github.harmonicinc.csabdemo.layout

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.harmonicinc.csabdemo.R
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.harmonicinc.clientsideadtracking.tracking.util.Constants.OMSDK_INTENT_LOG_ACTION
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EventFragment: Fragment() {
    private val events = mutableListOf<String>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    private val recyclerViewAdapter = object: RecyclerView.Adapter<ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.event_log_fragment, parent, false)
            return ViewHolder(view)
        }

        override fun getItemCount(): Int {
            return events.size
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
//            holder.mContentView.text = events[events.size - position - 1]
            holder.mContentView.text = events[position]
        }

    }
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.events_fragment, container, false)

        // Set the adapter
        val recyclerView = v.findViewById<RecyclerView>(R.id.event_list)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = recyclerViewAdapter

        val clearLogBtn = v.findViewById<FloatingActionButton>(R.id.clearLog)
        clearLogBtn.setOnClickListener {
            val oldEventsLength = events.size
            events.clear()
            recyclerViewAdapter.notifyItemRangeRemoved(0, oldEventsLength)
        }
        return v
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onResume() {
        super.onResume()
        activity?.registerReceiver(broadcastReceiver, IntentFilter(OMSDK_INTENT_LOG_ACTION))
    }

    override fun onPause() {
        super.onPause()
        activity?.unregisterReceiver(broadcastReceiver)
    }

    private val broadcastReceiver = object: BroadcastReceiver() {
        override fun onReceive(p0: Context?, p1: Intent?) {
            val action = p1?.action
            if (action == OMSDK_INTENT_LOG_ACTION) {
                val msg = p1.getStringExtra("message")
                if (msg != null) {
                    pushEventLog(msg)
                }
            }
        }
    }

    private fun pushEventLog(msg: String) {
        activity?.runOnUiThread {
            events.add(dateFormat.format(Date()) + "  " + msg)
            recyclerViewAdapter.notifyItemInserted(events.size - 1)
            if (events.size > 100) {
                events.removeAt(0)
                recyclerViewAdapter.notifyItemRemoved(0)
            }
        }
    }

    inner class ViewHolder(mView: View) : RecyclerView.ViewHolder(mView) {
        val mContentView: TextView = mView.findViewById(R.id.content)
        override fun toString(): String {
            return super.toString() + " '" + mContentView.text + "'"
        }
    }
}