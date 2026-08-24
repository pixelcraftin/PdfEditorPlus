package com.pixelcraftin.pdfeditorplus.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pixelcraftin.pdfeditorplus.data.model.OpenSourceLibrary
import com.pixelcraftin.pdfeditorplus.databinding.ItemLibraryBinding

class LibraryAdapter : ListAdapter<OpenSourceLibrary, LibraryAdapter.LibraryViewHolder>(LibraryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LibraryViewHolder {
        val binding = ItemLibraryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return LibraryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LibraryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class LibraryViewHolder(
        private val binding: ItemLibraryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(lib: OpenSourceLibrary) {
            binding.tvLibName.text = lib.name
            binding.tvDescription.text = lib.description
            binding.tvLicense.text = lib.license
            binding.ivIcon.setImageResource(lib.iconRes)
            binding.ivIcon.imageTintList = ContextCompat.getColorStateList(
                binding.root.context, lib.iconTintRes
            )
            binding.iconContainer.background =
                ContextCompat.getDrawable(binding.root.context, lib.iconBgRes)
        }
    }

    class LibraryDiffCallback : DiffUtil.ItemCallback<OpenSourceLibrary>() {
        override fun areItemsTheSame(oldItem: OpenSourceLibrary, newItem: OpenSourceLibrary): Boolean {
            return oldItem.name == newItem.name
        }

        override fun areContentsTheSame(oldItem: OpenSourceLibrary, newItem: OpenSourceLibrary): Boolean {
            return oldItem == newItem
        }
    }
}
