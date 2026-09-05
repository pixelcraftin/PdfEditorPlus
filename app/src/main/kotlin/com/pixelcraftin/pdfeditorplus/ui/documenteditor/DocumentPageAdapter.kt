package com.pixelcraftin.pdfeditorplus.ui.documenteditor

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.pixelcraftin.pdfeditorplus.databinding.ItemDocumentPageBinding
import com.pixelcraftin.pdfeditorplus.util.PdfDocumentGenerator
import kotlinx.coroutines.*

class DocumentPageAdapter(
    private val pages: List<DocumentPage>,
    private val coroutineScope: CoroutineScope
) : RecyclerView.Adapter<DocumentPageAdapter.PageViewHolder>() {

    private val boundViews = mutableMapOf<Int, DocumentCanvasView>()

    var onTextDoubleTapped: ((TextItem) -> Unit)? = null
    var onTextDeleted: ((TextItem) -> Unit)? = null

    class PageViewHolder(val binding: ItemDocumentPageBinding) : RecyclerView.ViewHolder(binding.root) {
        var loadJob: Job? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val binding = ItemDocumentPageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val page = pages[position]
        val canvasView = holder.binding.documentCanvas
        boundViews[position] = canvasView

        canvasView.onTextDoubleTapped = onTextDoubleTapped
        canvasView.onTextDeleted = onTextDeleted

        holder.loadJob?.cancel()
        holder.loadJob = coroutineScope.launch(Dispatchers.IO) {
            val bitmap = PdfDocumentGenerator.decodeSampledBitmapFromUri(
                canvasView.context,
                page,
                1080,
                1920
            )
            withContext(Dispatchers.Main) {
                canvasView.bind(bitmap, page)
            }
        }
    }

    override fun onViewRecycled(holder: PageViewHolder) {
        super.onViewRecycled(holder)
        holder.loadJob?.cancel()
        val pos = holder.adapterPosition
        if (pos != RecyclerView.NO_POSITION) {
            boundViews.remove(pos)
        }
    }

    fun getCanvasViewAt(position: Int): DocumentCanvasView? {
        return boundViews[position]
    }

    override fun getItemCount(): Int = pages.size
}
