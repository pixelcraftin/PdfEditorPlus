package com.pixelcraftin.pdfeditorplus.ui.documenteditor

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.pixelcraftin.pdfeditorplus.R
import com.pixelcraftin.pdfeditorplus.databinding.ItemPageThumbBinding

class PageThumbnailAdapter(
    private val pages: List<DocumentPage>,
    private var currentPageIndex: Int = 0,
    private val onPageClicked: (Int) -> Unit
) : RecyclerView.Adapter<PageThumbnailAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemPageThumbBinding) : RecyclerView.ViewHolder(binding.root)

    fun setCurrentPage(index: Int) {
        val oldIndex = currentPageIndex
        currentPageIndex = index
        notifyItemChanged(oldIndex)
        notifyItemChanged(currentPageIndex)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPageThumbBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val page = pages[position]
        val isSelected = position == currentPageIndex
        val context = holder.binding.root.context

        holder.binding.tvPageNumber.text = "${position + 1}"
        holder.binding.cardPageThumb.strokeColor = if (isSelected) context.getColor(R.color.primary) else Color.TRANSPARENT

        holder.binding.ivPageThumb.rotation = page.rotationDegrees.toFloat()
        holder.binding.ivPageThumb.colorFilter = page.filterType.getColorFilter(page.brightnessAdjustment)
        holder.binding.ivPageThumb.load(page.uri) {
            crossfade(true)
            size(120, 160)
        }

        holder.binding.root.setOnClickListener {
            onPageClicked(position)
        }
    }

    override fun getItemCount(): Int = pages.size
}
