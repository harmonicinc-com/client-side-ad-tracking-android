package com.github.harmonicinc.csabdemo.layout

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.github.harmonicinc.csabdemo.R
import com.google.android.material.floatingactionbutton.FloatingActionButton

class EventFragment: Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.events_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val clearLogBtn = view.findViewById<FloatingActionButton>(R.id.clearLog)
        clearLogBtn.setOnClickListener {
            // TODO
        }
    }
}