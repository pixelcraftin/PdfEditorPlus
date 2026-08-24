package com.pixelcraftin.pdfeditorplus.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.pixelcraftin.pdfeditorplus.data.model.ToolCategory
import com.pixelcraftin.pdfeditorplus.data.model.ToolItem
import com.pixelcraftin.pdfeditorplus.databinding.ItemSectionHeaderBinding
import com.pixelcraftin.pdfeditorplus.databinding.ItemToolListBinding

class ToolListAdapter(
    private val onClick: (ToolItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_SECTION_HEADER = 0
        private const val TYPE_TOOL = 1
        private const val TYPE_FOOTER = 2
    }

    private var items: List<ListItem> = emptyList()

    sealed class ListItem {
        data class SectionHeader(val category: ToolCategory) : ListItem()
        data class Tool(val tool: ToolItem) : ListItem()
        data class Footer(val versionText: String) : ListItem()
    }

    fun submitCategorized(tools: List<ToolItem>) {
        val grouped = tools.groupBy { it.category }
        val result = mutableListOf<ListItem>()
        for (category in grouped.keys) {
            result.add(ListItem.SectionHeader(category))
            grouped[category]?.forEach { tool ->
                result.add(ListItem.Tool(tool))
            }
        }
        result.add(ListItem.Footer("PDFEDITOR+ V1.0.1"))
        items = result
        notifyDataSetChanged()
    }

    fun submitFiltered(tools: List<ToolItem>) {
        val result = tools.map { ListItem.Tool(it) }.toMutableList<ListItem>()
        result.add(ListItem.Footer("PDFEDITOR+ V1.0.1"))
        items = result
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is ListItem.SectionHeader -> TYPE_SECTION_HEADER
            is ListItem.Tool -> TYPE_TOOL
            is ListItem.Footer -> TYPE_FOOTER
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_SECTION_HEADER -> {
                val binding = ItemSectionHeaderBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                SectionHeaderViewHolder(binding)
            }
            TYPE_FOOTER -> {
                val binding = com.pixelcraftin.pdfeditorplus.databinding.ItemToolFooterBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                FooterViewHolder(binding)
            }
            else -> {
                val binding = ItemToolListBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                ToolViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ListItem.SectionHeader -> (holder as SectionHeaderViewHolder).bind(item.category)
            is ListItem.Tool -> (holder as ToolViewHolder).bind(item.tool)
            is ListItem.Footer -> (holder as FooterViewHolder).bind(item.versionText)
        }
    }

    inner class FooterViewHolder(
        private val binding: com.pixelcraftin.pdfeditorplus.databinding.ItemToolFooterBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(versionText: String) {
            binding.tvFooterVersion.text = versionText
        }
    }

    inner class SectionHeaderViewHolder(
        private val binding: ItemSectionHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(category: ToolCategory) {
            binding.tvSectionName.text = category.displayName
            binding.ivSectionIcon.setImageResource(category.iconRes)
            binding.ivSectionIcon.imageTintList = ContextCompat.getColorStateList(
                binding.root.context, com.pixelcraftin.pdfeditorplus.R.color.primary
            )
        }
    }

    inner class ToolViewHolder(
        private val binding: ItemToolListBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(tool: ToolItem) {
            binding.tvToolName.text = tool.name
            binding.tvToolDesc.text = tool.description
            binding.ivToolIcon.setImageResource(tool.iconRes)
            binding.ivToolIcon.imageTintList = ContextCompat.getColorStateList(
                binding.root.context, tool.iconTintRes
            )
            binding.iconContainer.background =
                ContextCompat.getDrawable(binding.root.context, tool.iconBgRes)

            binding.root.setOnClickListener {
                onClick(tool)
            }
        }
    }
}
