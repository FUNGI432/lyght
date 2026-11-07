package com.aman.melklight.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.aman.melklight.R
import com.aman.melklight.ble.Commander
import com.aman.melklight.protocol.Effect
import com.aman.melklight.protocol.EffectCatalog
import com.aman.melklight.protocol.Protocol

class EffectsFragment : Fragment(R.layout.fragment_effects) {

    private lateinit var adapter: EffectAdapter

    private val groupIcons = mapOf(
        "Basic" to R.drawable.ic_group_basic,
        "Curtain" to R.drawable.ic_group_curtain,
        "Trans" to R.drawable.ic_group_trans,
        "Water" to R.drawable.ic_group_water,
        "Flow" to R.drawable.ic_group_flow,
        "Tail" to R.drawable.ic_group_tail,
        "Run" to R.drawable.ic_group_run,
        "RunBack" to R.drawable.ic_group_runback,
        "Scenes" to R.drawable.ic_group_scenes,
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val chipGroup = view.findViewById<com.google.android.material.chip.ChipGroup>(R.id.groupChipGroup)
        val list = view.findViewById<RecyclerView>(R.id.effectList)
        val speedSeek = view.findViewById<SeekBar>(R.id.speedSeek)

        list.layoutManager = LinearLayoutManager(requireContext())
        adapter = EffectAdapter { effect, isScene ->
            val context = requireContext()
            val command = if (isScene) Protocol.scene(effect.code) else Protocol.effectMode(effect.code)
            Commander.send(context, command)
        }
        list.adapter = adapter

        EffectCatalog.groups.forEachIndexed { index, group ->
            val chip = Chip(requireContext())
            chip.text = group.label
            chip.isCheckable = true
            chip.id = View.generateViewId()
            groupIcons[group.label]?.let { iconRes ->
                chip.chipIcon = androidx.core.content.ContextCompat.getDrawable(requireContext(), iconRes)
                chip.isChipIconVisible = true
                chip.isCheckedIconVisible = false
            }
            chip.setOnClickListener {
                adapter.submit(group.effects, group.label == "Scenes")
            }
            chipGroup.addView(chip)
            if (index == 0) {
                chip.isChecked = true
                adapter.submit(group.effects, group.label == "Scenes")
            }
        }

        speedSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                Commander.send(requireContext(), Protocol.effectSpeed(seekBar?.progress ?: 0))
            }
        })
    }

    private class EffectAdapter(
        private val onPick: (Effect, Boolean) -> Unit
    ) : RecyclerView.Adapter<EffectAdapter.ViewHolder>() {

        private var items: List<Effect> = emptyList()
        private var isScene = false
        private var selectedPosition = -1

        fun submit(effects: List<Effect>, scene: Boolean) {
            items = effects
            isScene = scene
            selectedPosition = -1
            notifyDataSetChanged()
        }

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val text: TextView = view.findViewById(R.id.effectName)
            val mark: ImageView = view.findViewById(R.id.selectedMark)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_effect, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val effect = items[position]
            holder.text.text = EffectNameColorizer.colorize(effect.name)
            holder.mark.visibility = if (position == selectedPosition) View.VISIBLE else View.INVISIBLE
            holder.itemView.setOnClickListener {
                val previous = selectedPosition
                selectedPosition = position
                notifyItemChanged(previous)
                notifyItemChanged(position)
                onPick(effect, isScene)
            }
        }

        override fun getItemCount() = items.size
    }
}
