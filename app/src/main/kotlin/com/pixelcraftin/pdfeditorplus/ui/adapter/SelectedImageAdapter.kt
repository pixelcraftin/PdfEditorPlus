package com.pixelcraftin.pdfeditorplus.ui.adapter

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.pixelcraftin.pdfeditorplus.databinding.ItemSelectedImageBinding
import com.pixelcraftin.pdfeditorplus.util.FileUtils

data class SelectedImageItem(
    val uri: Uri,
    val name: String,
    val size: Long
)

class SelectedImageAdapter(
    private val onRemove: (SelectedImageItem) -> Unit
) : ListAdapter<SelectedImageItem, SelectedImageAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSelectedImageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemSelectedImageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SelectedImageItem) {
            binding.tvFileName.text = item.name
            binding.tvFileSize.text = if (item.size > 0) FileUtils.formatSize(item.size) else ""

            binding.ivThumbnail.load(item.uri) {
                crossfade(true)
                size(120, 120)
                transformations(RoundedCornersTransformation(12f))
            }

            binding.btnRemove.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION && pos < itemCount) {
                    onRemove(getItem(pos))
                }
            }
        }
    }

    companion object {
        val DiffCallback = object : DiffUtil.ItemCallback<SelectedImageItem>() {
            override fun areItemsTheSame(oldItem: SelectedImageItem, newItem: SelectedImageItem): Boolean {
                return oldItem.uri == newItem.uri
            }

            override fun areContentsTheSame(oldItem: SelectedImageItem, newItem: SelectedImageItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}
