package com.pixelcraftin.pdfeditorplus.ui.common

import android.content.Context
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.card.MaterialCardView
import com.pixelcraftin.pdfeditorplus.R
import com.pixelcraftin.pdfeditorplus.databinding.DialogPdfActionPickerBinding
import com.pixelcraftin.pdfeditorplus.util.FileUtils

data class PdfActionOption(
    val id: String,
    val title: String,
    val description: String,
    val iconRes: Int,
    val bgRes: Int,
    val colorRes: Int,
    val destinationId: Int
)

class PdfActionBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogPdfActionPickerBinding? = null
    private val binding get() = _binding!!
    private var pdfUri: Uri? = null
    var onChangeFileRequested: (() -> Unit)? = null

    companion object {
        private const val ARG_URI = "arg_pdf_uri"

        fun newInstance(uri: Uri): PdfActionBottomSheet {
            return PdfActionBottomSheet().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_URI, uri)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pdfUri = arguments?.getParcelable(ARG_URI)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogPdfActionPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val uri = pdfUri
        if (uri != null) {
            val fileName = FileUtils.getFileName(requireContext(), uri)
            val fileSize = FileUtils.getFileSize(requireContext(), uri)
            binding.tvPickedFileName.text = fileName
            binding.tvPickedFileSize.text = "${FileUtils.formatSize(fileSize)} • Ready for operation"
        }

        binding.btnChangeFile.setOnClickListener {
            dismiss()
            onChangeFileRequested?.invoke()
        }

        setupActionsList()
    }

    private fun setupActionsList() {
        val actions = listOf(
            PdfActionOption(
                "merge",
                "Merge PDFs",
                "Combine with other PDF documents",
                R.drawable.ic_merge,
                R.drawable.bg_icon_red,
                R.color.icon_red,
                R.id.mergePdfFragment
            ),
            PdfActionOption(
                "sign",
                "Sign PDF",
                "Add your digital or hand-drawn signature",
                R.drawable.ic_signature,
                R.drawable.bg_icon_purple,
                R.color.icon_purple,
                R.id.signatureFragment
            ),
            PdfActionOption(
                "extract_text",
                "Extract Pages / Text",
                "Extract all text content from this document",
                R.drawable.ic_pdf_to_text,
                R.drawable.bg_icon_amber,
                R.color.icon_amber,
                R.id.pdfToTextFragment
            ),
            PdfActionOption(
                "page_numbers",
                "Add Page Numbers",
                "Insert custom header or footer page numbers",
                R.drawable.ic_page_numbers,
                R.drawable.bg_icon_blue,
                R.color.icon_blue,
                R.id.pageNumbersFragment
            ),
            PdfActionOption(
                "pdf_to_image",
                "PDF to Image",
                "Convert PDF pages into high-res JPG or PNG",
                R.drawable.ic_pdf_to_image,
                R.drawable.bg_icon_teal,
                R.color.icon_teal,
                R.id.pdfToImageFragment
            ),
            PdfActionOption(
                "compress",
                "Compress PDF",
                "Reduce file size while preserving quality",
                R.drawable.ic_compress,
                R.drawable.bg_icon_gold,
                R.color.icon_gold,
                R.id.compressPdfFragment
            ),
            PdfActionOption(
                "metadata",
                "Edit / Add Metadata",
                "Update title, author, subject and keywords",
                R.drawable.ic_metadata,
                R.drawable.bg_icon_teal,
                R.color.icon_teal,
                R.id.metadataFragment
            ),
            PdfActionOption(
                "protect",
                "Protect PDF",
                "Encrypt document with a strong password",
                R.drawable.ic_protect,
                R.drawable.bg_icon_purple,
                R.color.icon_purple,
                R.id.protectPdfFragment
            ),
            PdfActionOption(
                "unlock",
                "Unlock PDF",
                "Remove password and security restrictions",
                R.drawable.ic_unlock,
                R.drawable.bg_icon_orange,
                R.color.icon_orange,
                R.id.unlockPdfFragment
            ),
            PdfActionOption(
                "rotate",
                "Rotate PDF",
                "Change document or page orientation",
                R.drawable.ic_rotate,
                R.drawable.bg_icon_blue,
                R.color.icon_blue,
                R.id.rotatePdfFragment
            )
        )

        binding.rvPdfActions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPdfActions.adapter = PdfActionAdapter(actions) { action ->
            val uri = pdfUri
            dismiss()
            if (uri != null) {
                val bundle = Bundle().apply {
                    putParcelable("preselected_uri", uri)
                    putString("preselected_uri_string", uri.toString())
                }
                findNavController().navigate(action.destinationId, bundle)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class PdfActionAdapter(
        private val items: List<PdfActionOption>,
        private val onClick: (PdfActionOption) -> Unit
    ) : RecyclerView.Adapter<PdfActionAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val cardAction: MaterialCardView = view.findViewById(R.id.cardAction)
            val iconContainer: FrameLayout = view.findViewById(R.id.iconContainer)
            val ivActionIcon: ImageView = view.findViewById(R.id.ivActionIcon)
            val tvActionTitle: TextView = view.findViewById(R.id.tvActionTitle)
            val tvActionDesc: TextView = view.findViewById(R.id.tvActionDesc)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_pdf_action, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val context = holder.itemView.context
            holder.tvActionTitle.text = item.title
            holder.tvActionDesc.text = item.description
            holder.ivActionIcon.setImageResource(item.iconRes)
            holder.iconContainer.setBackgroundResource(item.bgRes)
            holder.ivActionIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, item.colorRes))
            holder.cardAction.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = items.size
    }
}
