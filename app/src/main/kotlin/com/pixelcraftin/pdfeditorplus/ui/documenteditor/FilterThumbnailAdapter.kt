package com.pixelcraftin.pdfeditorplus.ui.documenteditor

import android.graphics.Bitmap
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.pixelcraftin.pdfeditorplus.R
import com.pixelcraftin.pdfeditorplus.databinding.ItemFilterPreviewBinding

class FilterThumbnailAdapter(
    private var selectedFilter: ColorFilterType = ColorFilterType.ORIGINAL,
    private val onFilterSelected: (ColorFilterType) -> Unit
) : RecyclerView.Adapter<FilterThumbnailAdapter.ViewHolder>() {

    private val filters = ColorFilterType.values()
    private var previewBitmap: Bitmap? = null
    var brightnessDelta: Float = 0f

    class ViewHolder(val binding: ItemFilterPreviewBinding) : RecyclerView.ViewHolder(binding.root)

    fun setPreviewBitmap(bitmap: Bitmap?, brightness: Float = 0f) {
        this.previewBitmap = bitmap
        this.brightnessDelta = brightness
        notifyDataSetChanged()
    }

    fun setSelectedFilter(filter: ColorFilterType, brightness: Float = 0f) {
        this.selectedFilter = filter
        this.brightnessDelta = brightness
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFilterPreviewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val filter = filters[position]
        val isSelected = filter == selectedFilter

        holder.binding.tvFilterName.text = filter.displayName
        val context = holder.binding.root.context

        if (isSelected) {
            holder.binding.cardFilterThumb.strokeColor = context.getColor(R.color.primary)
            holder.binding.tvFilterName.setTextColor(context.getColor(R.color.primary))
        } else {
            holder.binding.cardFilterThumb.strokeColor = Color.TRANSPARENT
            holder.binding.tvFilterName.setTextColor(context.getColor(R.color.text_secondary))
        }

        val bmp = previewBitmap
        if (bmp != null && !bmp.isRecycled) {
            holder.binding.ivFilterThumb.setImageBitmap(bmp)
            holder.binding.ivFilterThumb.colorFilter = filter.getColorFilter(brightnessDelta)
        } else {
            holder.binding.ivFilterThumb.setImageResource(R.drawable.ic_image_to_pdf)
            holder.binding.ivFilterThumb.colorFilter = filter.getColorFilter(brightnessDelta)
        }

        holder.binding.root.setOnClickListener {
            selectedFilter = filter
            notifyDataSetChanged()
            onFilterSelected(filter)
        }
    }

    override fun getItemCount(): Int = filters.size
}
