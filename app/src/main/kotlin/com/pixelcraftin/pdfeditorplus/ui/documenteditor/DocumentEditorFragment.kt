package com.pixelcraftin.pdfeditorplus.ui.documenteditor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.pixelcraftin.pdfeditorplus.R
import com.pixelcraftin.pdfeditorplus.data.db.HistoryDatabase
import com.pixelcraftin.pdfeditorplus.data.model.HistoryItem
import com.pixelcraftin.pdfeditorplus.databinding.DialogTextAnnotationBinding
import com.pixelcraftin.pdfeditorplus.databinding.DialogWatermarkInputBinding
import com.pixelcraftin.pdfeditorplus.databinding.FragmentDocumentEditorBinding
import com.pixelcraftin.pdfeditorplus.util.FileUtils
import com.pixelcraftin.pdfeditorplus.util.PdfDocumentGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DocumentEditorFragment : Fragment() {

    private var _binding: FragmentDocumentEditorBinding? = null
    private val binding get() = _binding!!

    private val documentPages = mutableListOf<DocumentPage>()
    private lateinit var pageAdapter: DocumentPageAdapter
    private lateinit var filterAdapter: FilterThumbnailAdapter
    private lateinit var thumbnailAdapter: PageThumbnailAdapter

    private var currentPageIndex = 0
    private var isFilterPanelVisible = false
    private var isDrawingPanelVisible = false
    private var generatedPdfFile: File? = null

    // Multi-image picker to append additional images
    private val pickMoreImages = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (!uris.isNullOrEmpty()) {
            addImages(uris)
        }
    }

    // SAF (Storage Access Framework) file creator for exporting PDF to user-chosen location
    private val createSafDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { destUri ->
        if (destUri != null && generatedPdfFile != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    requireContext().contentResolver.openOutputStream(destUri)?.use { outStream ->
                        generatedPdfFile!!.inputStream().use { inStream ->
                            inStream.copyTo(outStream)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "PDF exported successfully.", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "PDF Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDocumentEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupInsets()
        setupCropResultListener()
        setupBackNavigation()
        extractInitialUris()
        setupViewPager()
        setupThumbnails()
        setupFilterStrip()
        setupDrawingControls()
        setupListeners()
        updateHeader()
    }

    private fun setupCropResultListener() {
        parentFragmentManager.setFragmentResultListener(
            com.pixelcraftin.pdfeditorplus.ui.documenteditor.crop.DocumentCropFragment.REQUEST_KEY_CROP,
            viewLifecycleOwner
        ) { _, bundle ->
            val croppedUri = bundle.getParcelable<Uri>(com.pixelcraftin.pdfeditorplus.ui.documenteditor.crop.DocumentCropFragment.RESULT_CROPPED_URI)
            val pageIdx = bundle.getInt(com.pixelcraftin.pdfeditorplus.ui.documenteditor.crop.DocumentCropFragment.RESULT_PAGE_INDEX, currentPageIndex)
            if (croppedUri != null && pageIdx in documentPages.indices) {
                val old = documentPages[pageIdx]
                documentPages[pageIdx] = old.copy(uri = croppedUri)
                pageAdapter.notifyItemChanged(pageIdx)
                thumbnailAdapter.notifyItemChanged(pageIdx)
                updateFilterPreview()
            }
        }
    }

    private fun setupBackNavigation() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigateBackToHome()
            }
        })
    }

    private fun navigateBackToHome() {
        val popped = findNavController().popBackStack(R.id.homeFragment, false)
        if (!popped) {
            findNavController().navigate(R.id.homeFragment)
        }
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomActionScrollView) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.updatePadding(bottom = navBars.bottom)
            insets
        }
    }

    private fun extractInitialUris() {
        val uriList = arguments?.getParcelableArrayList<Uri>(ARG_IMAGE_URIS)
        val uriStringList = arguments?.getStringArrayList(ARG_IMAGE_URI_STRINGS)

        val resolvedUris = when {
            !uriList.isNullOrEmpty() -> uriList
            !uriStringList.isNullOrEmpty() -> uriStringList.map { Uri.parse(it) }
            else -> emptyList()
        }

        if (resolvedUris.isNotEmpty()) {
            documentPages.clear()
            for (uri in resolvedUris) {
                documentPages.add(DocumentPage(uri))
            }
        } else {
            // Prompt image picker if none provided
            pickMoreImages.launch("image/*")
        }
    }

    private fun addImages(uris: List<Uri>) {
        val startIndex = documentPages.size
        for (uri in uris) {
            documentPages.add(DocumentPage(uri))
        }
        pageAdapter.notifyItemRangeInserted(startIndex, uris.size)
        thumbnailAdapter.notifyDataSetChanged()
        updateHeader()
    }

    private fun setupViewPager() {
        pageAdapter = DocumentPageAdapter(documentPages, viewLifecycleOwner.lifecycleScope)
        pageAdapter.onTextDoubleTapped = { textItem ->
            openTextAnnotationDialog(textItem)
        }
        pageAdapter.onTextDeleted = {
            val canvas = pageAdapter.getCanvasViewAt(currentPageIndex)
            canvas?.invalidate()
        }
        binding.viewPagerPages.adapter = pageAdapter
        binding.viewPagerPages.offscreenPageLimit = 2

        binding.viewPagerPages.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPageIndex = position
                thumbnailAdapter.setCurrentPage(position)
                binding.rvPageThumbs.smoothScrollToPosition(position)
                updateHeader()
                updateFilterPreview()
            }
        })
    }

    private fun setupThumbnails() {
        thumbnailAdapter = PageThumbnailAdapter(documentPages, currentPageIndex) { clickedPos ->
            binding.viewPagerPages.currentItem = clickedPos
        }
        binding.rvPageThumbs.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvPageThumbs.adapter = thumbnailAdapter
    }

    private fun setupFilterStrip() {
        filterAdapter = FilterThumbnailAdapter { selectedFilter ->
            val page = currentSelectedPage() ?: return@FilterThumbnailAdapter
            page.filterType = selectedFilter
            val canvas = pageAdapter.getCanvasViewAt(currentPageIndex)
            canvas?.invalidate()
            thumbnailAdapter.notifyItemChanged(currentPageIndex)
        }
        binding.rvFilters.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvFilters.adapter = filterAdapter

        binding.btnBrightnessLow.setOnClickListener {
            val page = currentSelectedPage() ?: return@setOnClickListener
            page.adjustBrightness(-25f)
            refreshPageFilter()
        }

        binding.btnBrightnessHigh.setOnClickListener {
            val page = currentSelectedPage() ?: return@setOnClickListener
            page.adjustBrightness(25f)
            refreshPageFilter()
        }
    }

    private fun refreshPageFilter() {
        val page = currentSelectedPage() ?: return
        val canvas = pageAdapter.getCanvasViewAt(currentPageIndex)
        canvas?.invalidate()
        thumbnailAdapter.notifyItemChanged(currentPageIndex)
        filterAdapter.setSelectedFilter(page.filterType, page.brightnessAdjustment)
    }

    private fun updateFilterPreview() {
        val page = currentSelectedPage() ?: return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val thumb = PdfDocumentGenerator.decodeSampledBitmapFromUri(requireContext(), page, 140, 140)
            withContext(Dispatchers.Main) {
                filterAdapter.setSelectedFilter(page.filterType, page.brightnessAdjustment)
                filterAdapter.setPreviewBitmap(thumb, page.brightnessAdjustment)
            }
        }
    }

    private fun setupDrawingControls() {
        binding.btnModePen.setOnClickListener {
            val canvas = pageAdapter.getCanvasViewAt(currentPageIndex)
            canvas?.toolMode = DocumentCanvasView.ToolMode.PEN
            canvas?.strokeColor = requireContext().getColor(R.color.primary)
            canvas?.strokeWidthPx = 8f
        }

        binding.btnModeHighlight.setOnClickListener {
            val canvas = pageAdapter.getCanvasViewAt(currentPageIndex)
            canvas?.toolMode = DocumentCanvasView.ToolMode.HIGHLIGHTER
            canvas?.strokeColor = android.graphics.Color.parseColor("#FFFF00")
            canvas?.strokeWidthPx = 24f
        }

        binding.btnModeEraser.setOnClickListener {
            val canvas = pageAdapter.getCanvasViewAt(currentPageIndex)
            canvas?.toolMode = DocumentCanvasView.ToolMode.ERASER
            canvas?.strokeWidthPx = 28f
        }

        binding.btnClearDrawings.setOnClickListener {
            val page = currentSelectedPage() ?: return@setOnClickListener
            page.clearDrawings()
            val canvas = pageAdapter.getCanvasViewAt(currentPageIndex)
            canvas?.invalidate()
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { navigateBackToHome() }

        binding.btnAddPages.setOnClickListener {
            pickMoreImages.launch("image/*")
        }

        binding.btnDeletePage.setOnClickListener {
            deleteCurrentPage()
        }

        binding.btnSavePdf.setOnClickListener {
            generatePdfAndSave()
        }

        // Bottom Action buttons
        binding.actionCrop.setOnClickListener {
            val page = currentSelectedPage() ?: return@setOnClickListener
            val bundle = Bundle().apply {
                putBoolean(com.pixelcraftin.pdfeditorplus.ui.documenteditor.crop.DocumentCropFragment.ARG_SINGLE_PAGE_MODE, true)
                putInt(com.pixelcraftin.pdfeditorplus.ui.documenteditor.crop.DocumentCropFragment.ARG_PAGE_INDEX, currentPageIndex)
                putParcelableArrayList(ARG_IMAGE_URIS, arrayListOf(page.uri))
                putStringArrayList(ARG_IMAGE_URI_STRINGS, arrayListOf(page.uri.toString()))
            }
            findNavController().navigate(R.id.documentCropFragment, bundle)
        }

        binding.actionFilter.setOnClickListener {
            isFilterPanelVisible = !isFilterPanelVisible
            binding.panelFilters.visibility = if (isFilterPanelVisible) View.VISIBLE else View.GONE
            binding.panelDrawing.visibility = View.GONE
            isDrawingPanelVisible = false
            if (isFilterPanelVisible) {
                updateFilterPreview()
            }
        }

        binding.actionRotate.setOnClickListener {
            val page = currentSelectedPage() ?: return@setOnClickListener
            page.rotateClockwise()
            val canvas = pageAdapter.getCanvasViewAt(currentPageIndex)
            canvas?.invalidate()
            thumbnailAdapter.notifyItemChanged(currentPageIndex)
        }

        binding.actionDraw.setOnClickListener {
            isDrawingPanelVisible = !isDrawingPanelVisible
            binding.panelDrawing.visibility = if (isDrawingPanelVisible) View.VISIBLE else View.GONE
            binding.panelFilters.visibility = View.GONE
            isFilterPanelVisible = false

            val canvas = pageAdapter.getCanvasViewAt(currentPageIndex)
            if (isDrawingPanelVisible) {
                binding.btnModePen.isChecked = true
                canvas?.toolMode = DocumentCanvasView.ToolMode.PEN
            } else {
                canvas?.toolMode = DocumentCanvasView.ToolMode.VIEW
            }
        }

        binding.actionSignature.setOnClickListener {
            openSignaturePad()
        }

        binding.actionWatermark.setOnClickListener {
            openWatermarkDialog()
        }

        binding.actionText.setOnClickListener {
            openTextAnnotationDialog()
        }
    }

    private fun currentSelectedPage(): DocumentPage? {
        if (currentPageIndex in documentPages.indices) {
            return documentPages[currentPageIndex]
        }
        return null
    }

    private fun updateHeader() {
        val total = documentPages.size
        binding.tvPageIndicator.text = if (total > 0) "Page ${currentPageIndex + 1} of $total" else "No pages"
        binding.btnSavePdf.isEnabled = total > 0
    }

    private fun deleteCurrentPage() {
        if (documentPages.isEmpty()) return

        if (documentPages.size == 1) {
            Toast.makeText(requireContext(), "Document must have at least one page", Toast.LENGTH_SHORT).show()
            return
        }

        val deletePos = currentPageIndex
        documentPages.removeAt(deletePos)
        pageAdapter.notifyItemRemoved(deletePos)
        thumbnailAdapter.notifyDataSetChanged()

        currentPageIndex = deletePos.coerceAtMost(documentPages.size - 1)
        binding.viewPagerPages.setCurrentItem(currentPageIndex, true)
        thumbnailAdapter.setCurrentPage(currentPageIndex)
        updateHeader()
        Toast.makeText(requireContext(), "Page ${deletePos + 1} deleted", Toast.LENGTH_SHORT).show()
    }

    private fun openSignaturePad() {
        val dialog = SignaturePadDialog.newInstance()
        dialog.onSignatureApplied = { signatureBitmap ->
            val page = currentSelectedPage()
            if (page != null) {
                page.signatureBitmap = signatureBitmap
                page.signatureNormRect = RectF(0.25f, 0.7f, 0.75f, 0.92f)
                val canvas = pageAdapter.getCanvasViewAt(currentPageIndex)
                canvas?.toolMode = DocumentCanvasView.ToolMode.SIGNATURE
                canvas?.invalidate()
                Toast.makeText(requireContext(), "Signature added! Drag to reposition.", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show(parentFragmentManager, "SignaturePadDialog")
    }

    private fun openWatermarkDialog() {
        val page = currentSelectedPage() ?: return
        val dialogBinding = DialogWatermarkInputBinding.inflate(layoutInflater)
        dialogBinding.etWatermarkText.setText(page.watermarkText ?: "")
        dialogBinding.sbWatermarkOpacity.progress = (page.watermarkOpacity * 100).toInt()
        dialogBinding.tvOpacityLabel.text = "Opacity: ${(page.watermarkOpacity * 100).toInt()}%"

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.sbWatermarkOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                dialogBinding.tvOpacityLabel.text = "Opacity: $progress%"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        dialogBinding.btnApplyWatermark.setOnClickListener {
            val text = dialogBinding.etWatermarkText.text?.toString()?.trim()
            page.watermarkText = if (!text.isNullOrBlank()) text else null
            page.watermarkOpacity = dialogBinding.sbWatermarkOpacity.progress / 100f
            val canvas = pageAdapter.getCanvasViewAt(currentPageIndex)
            canvas?.invalidate()
            dialog.dismiss()
        }

        dialogBinding.btnRemoveWatermark.setOnClickListener {
            page.watermarkText = null
            val canvas = pageAdapter.getCanvasViewAt(currentPageIndex)
            canvas?.invalidate()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun openTextAnnotationDialog(existingItem: TextItem? = null) {
        val page = currentSelectedPage() ?: return
        val dialog = TextAnnotationDialog.newInstance(existingItem)
        dialog.onTextApplied = { textItem ->
            if (existingItem == null) {
                page.textItems.add(textItem)
            }
            val canvas = pageAdapter.getCanvasViewAt(currentPageIndex)
            canvas?.toolMode = DocumentCanvasView.ToolMode.TEXT
            canvas?.invalidate()
        }
        dialog.onTextDeleted = { textItem ->
            page.textItems.remove(textItem)
            val canvas = pageAdapter.getCanvasViewAt(currentPageIndex)
            canvas?.invalidate()
        }
        dialog.show(parentFragmentManager, "TextAnnotationDialog")
    }

    private fun generatePdfAndSave() {
        if (documentPages.isEmpty()) {
            Toast.makeText(requireContext(), R.string.err_no_file, Toast.LENGTH_SHORT).show()
            return
        }

        val defaultName = "document_${System.currentTimeMillis()}"
        val input = android.widget.EditText(requireContext()).apply {
            setText(defaultName)
            setSelectAllOnFocus(true)
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_input_field)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
            textSize = 15f
        }

        val container = android.widget.FrameLayout(requireContext()).apply {
            val marginH = (22 * resources.displayMetrics.density).toInt()
            val marginV = (12 * resources.displayMetrics.density).toInt()
            setPadding(marginH, marginV, marginH, marginV)
            addView(input)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Save PDF Document 📄")
            .setMessage("Enter file name (extension .pdf will be added):")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val enteredName = input.text.toString().trim()
                val finalBaseName = if (enteredName.isNotBlank()) enteredName.removeSuffix(".pdf") else defaultName
                processAndExportPdf(finalBaseName)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun processAndExportPdf(customBaseName: String) {
        lifecycleScope.launch {
            binding.layoutProgress.visibility = View.VISIBLE
            binding.btnSavePdf.isEnabled = false

            val outputFile = File(
                FileUtils.getOutputDir(requireContext()),
                "$customBaseName.pdf"
            )

            val result = PdfDocumentGenerator.generatePdf(
                requireContext(),
                documentPages,
                outputFile
            ) { current, total ->
                lifecycleScope.launch(Dispatchers.Main) {
                    binding.tvProgressStatus.text = "Processing page $current of $total"
                }
            }

            binding.layoutProgress.visibility = View.GONE
            binding.btnSavePdf.isEnabled = true

            result.onSuccess { pdfFile ->
                generatedPdfFile = pdfFile
                val fileSize = pdfFile.length()

                // Save to Downloads directory
                FileUtils.saveFileToDownloads(requireContext(), pdfFile)

                // Record in History Database
                withContext(Dispatchers.IO) {
                    HistoryDatabase.getInstance(requireContext()).historyDao().insert(
                        HistoryItem(
                            fileName = pdfFile.name,
                            filePath = pdfFile.absolutePath,
                            toolName = "Document Editor",
                            fileSize = fileSize,
                            mimeType = "application/pdf"
                        )
                    )
                }

                showExportSuccessDialog(pdfFile)
            }.onFailure { error ->
                Toast.makeText(requireContext(), "PDF Generation failed: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showExportSuccessDialog(pdfFile: File) {
        val sizeStr = FileUtils.formatSize(pdfFile.length())
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("PDF Created Successfully! 📄")
            .setMessage("File: ${pdfFile.name}\nSize: $sizeStr (${documentPages.size} pages)\n\nSaved to Downloads/PdfEditor+ folder.")
            .setPositiveButton("Open PDF") { _, _ ->
                FileUtils.openFile(requireContext(), pdfFile)
            }
            .setNegativeButton("Share") { _, _ ->
                FileUtils.shareFile(requireContext(), pdfFile)
            }
            .setNeutralButton("Save PDF") { _, _ ->
                createSafDocument.launch(pdfFile.name)
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_IMAGE_URIS = "arg_image_uris"
        const val ARG_IMAGE_URI_STRINGS = "arg_image_uri_strings"

        fun createBundle(uris: List<Uri>): Bundle {
            return Bundle().apply {
                putParcelableArrayList(ARG_IMAGE_URIS, ArrayList(uris))
                putStringArrayList(ARG_IMAGE_URI_STRINGS, ArrayList(uris.map { it.toString() }))
            }
        }
    }
}
