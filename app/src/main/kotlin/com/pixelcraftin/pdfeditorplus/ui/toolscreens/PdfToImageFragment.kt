package com.pixelcraftin.pdfeditorplus.ui.toolscreens

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textview.MaterialTextView
import com.pixelcraftin.pdfeditorplus.R
import com.pixelcraftin.pdfeditorplus.data.db.HistoryDatabase
import com.pixelcraftin.pdfeditorplus.data.model.HistoryItem
import com.pixelcraftin.pdfeditorplus.databinding.FragmentToolImageToPdfBinding
import com.pixelcraftin.pdfeditorplus.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class PdfToImageFragment : Fragment() {

    private var _binding: FragmentToolImageToPdfBinding? = null
    private val binding get() = _binding!!

    private var selectedPdfUri: Uri? = null
    private var selectedFormat = "JPG"
    private var allPages = true
    private var lastGeneratedFiles = listOf<File>()

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
        binding.tvToolTitle.text = getString(R.string.tool_pdf_to_image)
        binding.tvToolDesc.text = getString(R.string.desc_pdf_to_image)
        setupOptions()
        setupListeners()

        // Handle preselected URI from '+' action picker or navigation args
        val preselectedUri = arguments?.getParcelable<Uri>("preselected_uri")
            ?: arguments?.getString("preselected_uri_string")?.let { Uri.parse(it) }
        preselectedUri?.let { onPdfSelected(it) }
    }

    private fun onPdfSelected(uri: Uri) {
        selectedPdfUri = uri
        binding.tvSelectedFileName.text = FileUtils.getFileName(requireContext(), uri)
        binding.tvSelectedFileName.visibility = View.VISIBLE
        binding.tvPickerHint.text = getString(R.string.pick_pdf)
    }

    private fun setupOptions() {
        val formatLabel = MaterialTextView(requireContext()).apply {
            text = getString(R.string.image_format)
            setTextColor(resources.getColor(R.color.text_primary, null))
            textSize = 14f
            setPadding(0, 8, 0, 4)
        }

        val formatSpinner = android.widget.Spinner(requireContext()).apply {
            id = View.generateViewId()
            val formats = arrayOf("JPG", "PNG")
            adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, formats)
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    selectedFormat = formats[position]
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
        }

        val allPagesSwitch = SwitchMaterial(requireContext()).apply {
            text = getString(R.string.all_pages)
            isChecked = true
            setOnCheckedChangeListener { _, checked -> allPages = checked }
        }

        binding.optionsContainer.addView(formatLabel)
        binding.optionsContainer.addView(formatSpinner)
        binding.optionsContainer.addView(allPagesSwitch)
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

        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            binding.btnProcess.isEnabled = false
            binding.cardResult.visibility = View.GONE

            val result = withContext(Dispatchers.IO) {
                try {
                    val inputFile = FileUtils.uriToFile(requireContext(), uri)
                        ?: return@withContext Result.failure(Exception("Cannot read PDF file"))

                    val pfd = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = PdfRenderer(pfd)
                    val pageCount = renderer.pageCount
                    val outputDir = FileUtils.getOutputDir(requireContext())
                    val pagesToRender = if (allPages) 0 until pageCount else listOf(0)
                    val outputFiles = mutableListOf<File>()

                    for (pageIndex in pagesToRender) {
                        val page = renderer.openPage(pageIndex)
                        // Render at 2x resolution for high crispness
                        val densityScale = 2
                        val width = page.width * densityScale
                        val height = page.height * densityScale
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        // Fill white background for transparent PDF rendering
                        val canvas = android.graphics.Canvas(bitmap)
                        canvas.drawColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()

                        val ext = if (selectedFormat == "PNG") "png" else "jpg"
                        val format = if (selectedFormat == "PNG") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                        val outFile = File(outputDir, FileUtils.generateOutputName("page_${pageIndex + 1}", ext))
                        FileOutputStream(outFile).use { out ->
                            bitmap.compress(format, 95, out)
                        }
                        bitmap.recycle()
                        outputFiles.add(outFile)
                    }

                    renderer.close()
                    pfd.close()
                    Result.success(outputFiles)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }

            binding.progressBar.visibility = View.GONE
            binding.btnProcess.isEnabled = true

            result.onSuccess { files ->
                lastGeneratedFiles = files
                val firstFile = files.first()
                showResult(firstFile, files.size)
            }.onFailure { e ->
                Toast.makeText(requireContext(), getString(R.string.error_processing, e.message), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showResult(file: File, count: Int) {
        binding.cardResult.visibility = View.VISIBLE
        binding.tvResultPath.text = "$count image(s) created in ${file.parentFile?.name}"

        binding.btnDownload.setOnClickListener {
            if (lastGeneratedFiles.size == 1) {
                FileUtils.promptSaveToDownloads(requireContext(), lastGeneratedFiles.first())
            } else {
                FileUtils.promptSaveBatchToDownloads(requireContext(), lastGeneratedFiles, "pdf_page")
            }
        }

        binding.btnShare.setOnClickListener { FileUtils.shareFile(requireContext(), file) }
        binding.btnViewFile.setOnClickListener { FileUtils.openFile(requireContext(), file) }

        lifecycleScope.launch {
            val dao = HistoryDatabase.getInstance(requireContext()).historyDao()
            dao.insert(
                HistoryItem(
                    fileName = file.name,
                    filePath = file.absolutePath,
                    toolName = "PDF to Image",
                    fileSize = file.length()
                )
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
