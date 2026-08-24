package com.pixelcraftin.pdfeditorplus.ui.toolscreens

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.textview.MaterialTextView
import com.pixelcraftin.pdfeditorplus.R
import com.pixelcraftin.pdfeditorplus.data.db.HistoryDatabase
import com.pixelcraftin.pdfeditorplus.data.model.HistoryItem
import com.pixelcraftin.pdfeditorplus.databinding.FragmentToolImageToPdfBinding
import com.pixelcraftin.pdfeditorplus.util.FileUtils
import com.pixelcraftin.pdfeditorplus.util.PdfUtils
import kotlinx.coroutines.launch
import java.io.File

class SplitPdfFragment : Fragment() {

    private var _binding: FragmentToolImageToPdfBinding? = null
    private val binding get() = _binding!!

    private var selectedPdfUri: Uri? = null
    private lateinit var etPageRange: EditText

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
        binding.tvToolTitle.text = getString(R.string.tool_split_pdf)
        binding.tvToolDesc.text = getString(R.string.desc_split_pdf)
        setupOptions()
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

    private fun setupOptions() {
        val label = MaterialTextView(requireContext()).apply {
            text = getString(R.string.page_range_hint)
            setTextColor(resources.getColor(R.color.text_primary, null))
            textSize = 14f
            setPadding(0, 8, 0, 4)
        }

        etPageRange = EditText(requireContext()).apply {
            hint = "e.g. 1-3, 5"
            setPadding(32, 24, 32, 24)
            setBackgroundColor(resources.getColor(R.color.card_background, null))
            setTextColor(resources.getColor(R.color.text_primary, null))
        }

        binding.optionsContainer.addView(label)
        binding.optionsContainer.addView(etPageRange)
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.cardFilePicker.setOnClickListener { pdfPicker.launch("application/pdf") }
        binding.btnProcess.setOnClickListener { processFile() }
    }

    private fun parsePageRange(rangeStr: String): IntRange? {
        return try {
            val parts = rangeStr.split(",").map { it.trim() }
            val pages = mutableListOf<Int>()
            for (part in parts) {
                if (part.contains("-")) {
                    val (start, end) = part.split("-").map { it.trim().toInt() }
                    pages.addAll(start..end)
                } else {
                    pages.add(part.toInt())
                }
            }
            if (pages.isEmpty()) null else IntRange(pages.min(), pages.max())
        } catch (e: Exception) {
            null
        }
    }

    private fun processFile() {
        val uri = selectedPdfUri
        if (uri == null) {
            Toast.makeText(requireContext(), R.string.err_no_file, Toast.LENGTH_SHORT).show()
            return
        }

        val rangeStr = etPageRange.text.toString()
        if (rangeStr.isBlank()) {
            Toast.makeText(requireContext(), R.string.err_invalid_range, Toast.LENGTH_SHORT).show()
            return
        }

        val range = parsePageRange(rangeStr)
        if (range == null) {
            Toast.makeText(requireContext(), R.string.err_invalid_range, Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            binding.btnProcess.isEnabled = false
            binding.cardResult.visibility = View.GONE

            val inputFile = FileUtils.uriToFile(requireContext(), uri)
            if (inputFile == null) {
                binding.progressBar.visibility = View.GONE
                binding.btnProcess.isEnabled = true
                Toast.makeText(requireContext(), R.string.err_no_file, Toast.LENGTH_SHORT).show()
                return@launch
            }

            val prefs = com.pixelcraftin.pdfeditorplus.data.prefs.AppPreferences(requireContext())
            val defaultAuthor = prefs.getDefaultAuthor().ifBlank { null }
            val outputDir = FileUtils.getOutputDir(requireContext())
            val outputFile = File(outputDir, FileUtils.generateOutputName("split_pdf"))

            val result = PdfUtils.splitPdf(inputFile, range, outputFile, defaultAuthor = defaultAuthor)

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
            FileUtils.saveFileToDownloads(requireContext(), file).onSuccess { path ->
                Toast.makeText(requireContext(), "Saved to $path", Toast.LENGTH_SHORT).show()
            }.onFailure { e ->
                Toast.makeText(requireContext(), "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
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
                    toolName = "Split PDF",
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
