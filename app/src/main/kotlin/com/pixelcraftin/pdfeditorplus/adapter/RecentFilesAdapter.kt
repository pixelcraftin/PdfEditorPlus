package com.pixelcraftin.pdfeditorplus.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pixelcraftin.pdfeditorplus.data.model.HistoryItem
import com.pixelcraftin.pdfeditorplus.databinding.ItemRecentFileBinding
import com.pixelcraftin.pdfeditorplus.util.FileUtils

class RecentFilesAdapter(
    private val onClick: (HistoryItem) -> Unit
) : ListAdapter<HistoryItem, RecentFilesAdapter.RecentFileViewHolder>(RecentFileDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecentFileViewHolder {
        val binding = ItemRecentFileBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return RecentFileViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecentFileViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class RecentFileViewHolder(
        private val binding: ItemRecentFileBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: HistoryItem) {
            binding.tvFileName.text = item.fileName
            binding.tvToolName.text = item.toolName
            binding.tvFileSize.text = FileUtils.formatSize(item.fileSize)

            binding.root.setOnClickListener {
                onClick(item)
            }
        }
    }

    class RecentFileDiffCallback : DiffUtil.ItemCallback<HistoryItem>() {
        override fun areItemsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean {
            return oldItem == newItem
        }
    }
}
