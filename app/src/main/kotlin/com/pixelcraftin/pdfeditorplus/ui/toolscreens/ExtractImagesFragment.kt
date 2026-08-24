package com.pixelcraftin.pdfeditorplus.ui.toolscreens

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
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

class ExtractImagesFragment : Fragment() {

    private var _binding: FragmentToolImageToPdfBinding? = null
    private val binding get() = _binding!!

    private var selectedPdfUri: Uri? = null
    private var lastExtractedFiles = listOf<File>()

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
        binding.tvToolTitle.text = getString(R.string.tool_extract_images)
        binding.tvToolDesc.text = getString(R.string.desc_extract_images)
        binding.cardOptions.visibility = View.GONE
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
                    val outputFiles = mutableListOf<File>()

                    for (pageIndex in 0 until pageCount) {
                        val page = renderer.openPage(pageIndex)
                        val densityScale = 2
                        val width = page.width * densityScale
                        val height = page.height * densityScale
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bitmap)
                        canvas.drawColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()

                        val outFile = File(outputDir, FileUtils.generateOutputName("extracted_img_${pageIndex + 1}", "png"))
                        FileOutputStream(outFile).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
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
                lastExtractedFiles = files
                val outputDir = FileUtils.getOutputDir(requireContext())
                binding.cardResult.visibility = View.VISIBLE
                binding.tvResultPath.text = "${files.size} image(s) extracted into ${outputDir.name}"

                binding.btnDownload.setOnClickListener {
                    var count = 0
                    for (f in lastExtractedFiles) {
                        FileUtils.saveFileToDownloads(requireContext(), f).onSuccess { count++ }
                    }
                    Toast.makeText(requireContext(), "Saved $count image(s) to Downloads/PdfEditor+", Toast.LENGTH_SHORT).show()
                }

                val firstFile = files.firstOrNull()
                if (firstFile != null) {
                    binding.btnShare.setOnClickListener { FileUtils.shareFile(requireContext(), firstFile) }
                    binding.btnViewFile.setOnClickListener { FileUtils.openFile(requireContext(), firstFile) }
                }

                lifecycleScope.launch {
                    val dao = HistoryDatabase.getInstance(requireContext()).historyDao()
                    dao.insert(
                        HistoryItem(
                            fileName = "extracted_images",
                            filePath = outputDir.absolutePath,
                            toolName = "Extract Images",
                            fileSize = files.sumOf { it.length() }
                        )
                    )
                }
            }.onFailure { e ->
                Toast.makeText(requireContext(), getString(R.string.error_processing, e.message), Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
