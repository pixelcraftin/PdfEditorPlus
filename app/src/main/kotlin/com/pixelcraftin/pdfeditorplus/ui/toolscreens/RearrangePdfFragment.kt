package com.pixelcraftin.pdfeditorplus.ui.toolscreens

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.pixelcraftin.pdfeditorplus.R
import com.pixelcraftin.pdfeditorplus.data.db.HistoryDatabase
import com.pixelcraftin.pdfeditorplus.data.model.HistoryItem
import com.pixelcraftin.pdfeditorplus.databinding.FragmentToolImageToPdfBinding
import com.pixelcraftin.pdfeditorplus.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class RearrangePdfFragment : Fragment() {

    private var _binding: FragmentToolImageToPdfBinding? = null
    private val binding get() = _binding!!

    private var selectedPdfUri: Uri? = null
    private val pageOrder = mutableListOf<Int>()
    private val pageThumbnails = mutableListOf<Bitmap>()
    private var totalPages = 0

    private val pdfPicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onPdfSelected(it) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentToolImageToPdfBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvToolTitle.text = getString(R.string.tool_rearrange_pdf)
        binding.tvToolDesc.text = getString(R.string.desc_rearrange_pdf)
        setupListeners()

        val preselectedUri = arguments?.getParcelable<Uri>("preselected_uri")
            ?: arguments?.getString("preselected_uri_string")?.let { Uri.parse(it) }
        preselectedUri?.let { onPdfSelected(it) }
    }

    private fun onPdfSelected(uri: Uri) {
        selectedPdfUri = uri
        binding.tvSelectedFileName.text = FileUtils.getFileName(requireContext(), uri)
        binding.tvSelectedFileName.visibility = View.VISIBLE
        binding.tvPickerHint.text = getString(R.string.pick_pdf)
        loadThumbnails(uri)
    }

    private fun loadThumbnails(uri: Uri) {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            withContext(Dispatchers.IO) {
                try {
                    val inputFile = FileUtils.uriToFile(requireContext(), uri) ?: return@withContext
                    val pfd = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = PdfRenderer(pfd)
                    totalPages = renderer.pageCount
                    pageOrder.clear()
                    pageOrder.addAll(0 until totalPages)

                    pageThumbnails.forEach { if (!it.isRecycled) it.recycle() }
                    pageThumbnails.clear()

                    for (i in 0 until totalPages) {
                        val page = renderer.openPage(i)
                        val w = page.width
                        val h = page.height
                        val scale = 160f / maxOf(w, h).coerceAtLeast(1)
                        val thumbW = (w * scale).toInt().coerceAtLeast(1)
                        val thumbH = (h * scale).toInt().coerceAtLeast(1)
                        val thumb = Bitmap.createBitmap(thumbW, thumbH, Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(thumb)
                        canvas.drawColor(android.graphics.Color.WHITE)
                        page.render(thumb, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        pageThumbnails.add(thumb)
                    }

                    renderer.close()
                    pfd.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            binding.progressBar.visibility = View.GONE
            showPageList()
        }
    }

    private fun showPageList() {
        binding.optionsContainer.removeAllViews()

        val title = TextView(requireContext()).apply {
            text = "Reorder pages (use up/down arrows):"
            textSize = 14f
            setTextColor(resources.getColor(R.color.text_primary, null))
            setPadding(0, 8, 0, 12)
        }
        binding.optionsContainer.addView(title)

        val listContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }

        pageOrder.forEachIndexed { displayIndex, pageIndex ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, 6, 0, 6)
            }

            val thumb = ImageView(requireContext()).apply {
                layoutParams = ViewGroup.LayoutParams(100, 130)
                scaleType = ImageView.ScaleType.FIT_CENTER
                if (pageIndex < pageThumbnails.size) {
                    setImageBitmap(pageThumbnails[pageIndex])
                }
            }

            val label = TextView(requireContext()).apply {
                text = "Page ${pageIndex + 1}"
                textSize = 14f
                setTextColor(resources.getColor(R.color.text_primary, null))
                setPadding(16, 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val upBtn = ImageButton(requireContext()).apply {
                setImageResource(android.R.drawable.arrow_up_float)
                background = null
                setOnClickListener {
                    if (displayIndex > 0) {
                        val temp = pageOrder[displayIndex]
                        pageOrder[displayIndex] = pageOrder[displayIndex - 1]
                        pageOrder[displayIndex - 1] = temp
                        showPageList()
                    }
                }
            }

            val downBtn = ImageButton(requireContext()).apply {
                setImageResource(android.R.drawable.arrow_down_float)
                background = null
                setOnClickListener {
                    if (displayIndex < pageOrder.size - 1) {
                        val temp = pageOrder[displayIndex]
                        pageOrder[displayIndex] = pageOrder[displayIndex + 1]
                        pageOrder[displayIndex + 1] = temp
                        showPageList()
                    }
                }
            }

            row.addView(thumb)
            row.addView(label)
            row.addView(upBtn)
            row.addView(downBtn)
            listContainer.addView(row)
        }

        binding.optionsContainer.addView(listContainer)
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.cardFilePicker.setOnClickListener { pdfPicker.launch("application/pdf") }
        binding.btnProcess.setOnClickListener { processFile() }
    }

    private fun processFile() {
        val uri = selectedPdfUri
        if (uri == null) {
            Toast.makeText(requireContext(), R.string.err_no_file, Toast.LENGTH_SHORT).show()
            return
        }

        if (pageOrder.isEmpty()) {
            Toast.makeText(requireContext(), R.string.err_no_file, Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            binding.btnProcess.isEnabled = false
            binding.cardResult.visibility = View.GONE

            val result = withContext(Dispatchers.IO) {
                try {
                    val inputFile = FileUtils.uriToFile(requireContext(), uri)
                        ?: return@withContext Result.failure(Exception("Cannot read PDF file"))
                    val reader = PdfReader(inputFile)
                    val src = PdfDocument(reader)
                    val outputDir = FileUtils.getOutputDir(requireContext())
                    val outputFile = File(outputDir, FileUtils.generateOutputName("rearranged_pdf"))
                    val writer = PdfWriter(outputFile)
                    val dest = PdfDocument(writer)

                    val reorderedPages = pageOrder.map { it + 1 }
                    src.copyPagesTo(reorderedPages, dest)

                    dest.close()
                    src.close()
                    Result.success(outputFile)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }

            binding.progressBar.visibility = View.GONE
            binding.btnProcess.isEnabled = true

            result.onSuccess { file ->
                showResult(file)
            }.onFailure { e ->
                Toast.makeText(requireContext(), getString(R.string.error_processing, e.message), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showResult(file: File) {
        binding.cardResult.visibility = View.VISIBLE
        binding.tvResultPath.text = file.absolutePath

        binding.btnDownload.setOnClickListener {
            FileUtils.promptSaveToDownloads(requireContext(), file)
        }

        binding.btnShare.setOnClickListener { FileUtils.shareFile(requireContext(), file) }
        binding.btnViewFile.setOnClickListener { FileUtils.openFile(requireContext(), file) }

        lifecycleScope.launch {
            val dao = HistoryDatabase.getInstance(requireContext()).historyDao()
            dao.insert(
                HistoryItem(
                    fileName = file.name,
                    filePath = file.absolutePath,
                    toolName = "Rearrange PDF",
                    fileSize = file.length()
                )
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pageThumbnails.forEach { if (!it.isRecycled) it.recycle() }
        pageThumbnails.clear()
        _binding = null
    }
}
