package com.pixelcraftin.pdfeditorplus.ui.tools

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.pixelcraftin.pdfeditorplus.R
import com.pixelcraftin.pdfeditorplus.adapter.ToolListAdapter
import com.pixelcraftin.pdfeditorplus.data.model.ToolCategory
import com.pixelcraftin.pdfeditorplus.data.model.ToolItem
import com.pixelcraftin.pdfeditorplus.databinding.FragmentToolsBinding

class ToolsFragment : Fragment() {

    private var _binding: FragmentToolsBinding? = null
    private val binding get() = _binding!!

    private val allTools: List<ToolItem> by lazy { buildToolList() }
    private lateinit var adapter: ToolListAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentToolsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        com.pixelcraftin.pdfeditorplus.util.ViewUtils.startPulseGlow(binding.dotIndicator)
        setupRecyclerView()
        setupSearch()
    }

    private fun setupRecyclerView() {
        adapter = ToolListAdapter { tool -> findNavController().navigate(tool.navActionId) }
        binding.rvTools.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTools.adapter = adapter
        adapter.submitCategorized(allTools)
    }

    private fun setupSearch() {
        binding.etSearchTools.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                if (query.isEmpty()) adapter.submitCategorized(allTools)
                else adapter.submitFiltered(allTools.filter {
                    it.name.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true)
                })
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun buildToolList(): List<ToolItem> = listOf(
        // CONVERT
        ToolItem("pdf_to_image","PDF to Image",getString(R.string.desc_pdf_to_image),ToolCategory.CONVERT,R.drawable.ic_pdf_to_image,R.drawable.bg_icon_teal,R.color.icon_teal,R.id.pdfToImageFragment),
        ToolItem("image_to_pdf","Image to PDF",getString(R.string.desc_image_to_pdf),ToolCategory.CONVERT,R.drawable.ic_image_to_pdf,R.drawable.bg_icon_teal,R.color.icon_teal,R.id.imageToPdfFragment),
        ToolItem("extract_images","Extract Images",getString(R.string.desc_extract_images),ToolCategory.CONVERT,R.drawable.ic_extract,R.drawable.bg_icon_orange,R.color.icon_orange,R.id.extractImagesFragment),
        ToolItem("pdf_to_text","PDF to Text",getString(R.string.desc_pdf_to_text),ToolCategory.CONVERT,R.drawable.ic_pdf_to_text,R.drawable.bg_icon_teal,R.color.icon_teal,R.id.pdfToTextFragment),
        // EDIT
        ToolItem("document_editor","Document Editor","Edit images with filters, drawings, signature & export to A4 PDF",ToolCategory.EDIT,R.drawable.ic_brush,R.drawable.bg_icon_blue,R.color.icon_blue,R.id.documentEditorFragment),
        ToolItem("merge_pdf","Merge PDF",getString(R.string.desc_merge_pdf),ToolCategory.EDIT,R.drawable.ic_merge,R.drawable.bg_icon_blue,R.color.icon_blue,R.id.mergePdfFragment),
        ToolItem("split_pdf","Split PDF",getString(R.string.desc_split_pdf),ToolCategory.EDIT,R.drawable.ic_split,R.drawable.bg_icon_orange,R.color.icon_orange,R.id.splitPdfFragment),
        ToolItem("rotate_pdf","Rotate PDF",getString(R.string.desc_rotate_pdf),ToolCategory.EDIT,R.drawable.ic_rotate,R.drawable.bg_icon_blue,R.color.icon_blue,R.id.rotatePdfFragment),
        ToolItem("rearrange_pdf","Rearrange PDF",getString(R.string.desc_rearrange_pdf),ToolCategory.EDIT,R.drawable.ic_rearrange,R.drawable.bg_icon_blue,R.color.icon_blue,R.id.rearrangePdfFragment),
        ToolItem("page_numbers","Page Numbers",getString(R.string.desc_page_numbers),ToolCategory.EDIT,R.drawable.ic_page_numbers,R.drawable.bg_icon_blue,R.color.icon_blue,R.id.pageNumbersFragment),
        ToolItem("watermark","Watermark",getString(R.string.desc_watermark),ToolCategory.EDIT,R.drawable.ic_watermark,R.drawable.bg_icon_blue,R.color.icon_blue,R.id.watermarkFragment),
        ToolItem("signature","Signature",getString(R.string.desc_signature),ToolCategory.EDIT,R.drawable.ic_signature,R.drawable.bg_icon_blue,R.color.icon_blue,R.id.signatureFragment),
        // OPTIMIZE
        ToolItem("compress_pdf","Compress PDF",getString(R.string.desc_compress_pdf),ToolCategory.OPTIMIZE,R.drawable.ic_compress,R.drawable.bg_icon_amber,R.color.icon_amber,R.id.compressPdfFragment),
        ToolItem("grayscale","Grayscale",getString(R.string.desc_grayscale),ToolCategory.OPTIMIZE,R.drawable.ic_grayscale,R.drawable.bg_icon_gold,R.color.icon_gold,R.id.grayscaleFragment),
        ToolItem("image_compressor","Image Compressor",getString(R.string.desc_image_compressor),ToolCategory.OPTIMIZE,R.drawable.ic_image_compress,R.drawable.bg_icon_orange,R.color.icon_orange,R.id.imageCompressorFragment),
        ToolItem("round_crop","Image Round Cropping",getString(R.string.desc_image_round_cropping),ToolCategory.OPTIMIZE,R.drawable.ic_round_crop,R.drawable.bg_icon_orange,R.color.icon_orange,R.id.imageRoundCroppingFragment),
        // SECURE
        ToolItem("protect_pdf","Protect PDF",getString(R.string.desc_protect_pdf),ToolCategory.SECURE,R.drawable.ic_protect,R.drawable.bg_icon_purple,R.color.icon_purple,R.id.protectPdfFragment),
        ToolItem("unlock_pdf","Unlock PDF",getString(R.string.desc_unlock_pdf),ToolCategory.SECURE,R.drawable.ic_unlock,R.drawable.bg_icon_purple,R.color.icon_purple,R.id.unlockPdfFragment),
        ToolItem("metadata","Metadata",getString(R.string.desc_metadata),ToolCategory.SECURE,R.drawable.ic_metadata,R.drawable.bg_icon_purple,R.color.icon_purple,R.id.metadataFragment)
    )

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
