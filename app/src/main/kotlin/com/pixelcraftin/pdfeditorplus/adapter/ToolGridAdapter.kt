package com.pixelcraftin.pdfeditorplus.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pixelcraftin.pdfeditorplus.data.model.ToolItem
import com.pixelcraftin.pdfeditorplus.databinding.ItemToolGridBinding

class ToolGridAdapter(
    private val onClick: (ToolItem) -> Unit
) : ListAdapter<ToolItem, ToolGridAdapter.ToolGridViewHolder>(ToolGridDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ToolGridViewHolder {
        val binding = ItemToolGridBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ToolGridViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ToolGridViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ToolGridViewHolder(
        private val binding: ItemToolGridBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(tool: ToolItem) {
            binding.tvToolName.text = tool.name

            binding.ivToolIcon.setImageResource(tool.iconRes)
            binding.ivToolIcon.imageTintList = ContextCompat.getColorStateList(
                binding.root.context, tool.iconTintRes
            )

            binding.iconContainer.background =
                ContextCompat.getDrawable(binding.root.context, tool.iconBgRes)

            if (tool.subtitleLabel.isNotBlank()) {
                binding.tvSubtitle.visibility = View.VISIBLE
                binding.tvSubtitle.text = tool.subtitleLabel
            } else {
                binding.tvSubtitle.visibility = View.GONE
            }

            binding.root.setOnClickListener {
                onClick(tool)
            }
        }
    }

    class ToolGridDiffCallback : DiffUtil.ItemCallback<ToolItem>() {
        override fun areItemsTheSame(oldItem: ToolItem, newItem: ToolItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ToolItem, newItem: ToolItem): Boolean {
            return oldItem == newItem
        }
    }
}
