package com.pixelcraftin.pdfeditorplus.ui.toolscreens

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.pixelcraftin.pdfeditorplus.R
import com.pixelcraftin.pdfeditorplus.data.db.HistoryDatabase
import com.pixelcraftin.pdfeditorplus.data.model.HistoryItem
import com.pixelcraftin.pdfeditorplus.data.prefs.AppPreferences
import com.pixelcraftin.pdfeditorplus.databinding.FragmentToolImageToPdfBinding
import com.pixelcraftin.pdfeditorplus.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.pixelcraftin.pdfeditorplus.util.PdfUtils
import java.io.File

class CompressPdfFragment : Fragment() {

    private var _binding: FragmentToolImageToPdfBinding? = null
    private val binding get() = _binding!!
    private var inputUri: Uri? = null
    private var inputFile: File? = null
    private var compressionLevel = 1

    private val pickPdf = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { onPdfSelected(it) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentToolImageToPdfBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvToolTitle.text = getString(R.string.tool_compress_pdf)
        binding.tvToolDesc.text = getString(R.string.desc_compress_pdf)
        setupOptions()
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.cardFilePicker.setOnClickListener { pickPdf.launch("application/pdf") }
        binding.btnProcess.setOnClickListener { processFile() }

        val preselectedUri = arguments?.getParcelable<Uri>("preselected_uri")
            ?: arguments?.getString("preselected_uri_string")?.let { Uri.parse(it) }
        preselectedUri?.let { onPdfSelected(it) }
    }

    private fun onPdfSelected(uri: Uri) {
        inputUri = uri
        inputFile = FileUtils.uriToFile(requireContext(), uri)
        binding.tvPickerHint.visibility = View.GONE
        binding.tvSelectedFileName.visibility = View.VISIBLE
        binding.tvSelectedFileName.text = FileUtils.getFileName(requireContext(), uri)
    }

    private fun setupOptions() {
        val container = binding.optionsContainer
        val label = TextView(requireContext()).apply {
            text = getString(R.string.compression_level)
            textSize = 14f
            setTextColor(resources.getColor(R.color.text_primary, null))
            setPadding(0, 0, 0, 8)
        }
        val radioGroup = RadioGroup(requireContext()).apply {
            orientation = RadioGroup.HORIZONTAL
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        
        val rbLow = RadioButton(requireContext()).apply {
            id = View.generateViewId()
            text = getString(R.string.low)
            setOnClickListener { compressionLevel = 0 }
        }
        val rbMedium = RadioButton(requireContext()).apply {
            id = View.generateViewId()
            text = getString(R.string.medium)
            isChecked = true
            setOnClickListener { compressionLevel = 1 }
        }
        val rbHigh = RadioButton(requireContext()).apply {
            id = View.generateViewId()
            text = getString(R.string.high)
            setOnClickListener { compressionLevel = 2 }
        }

        radioGroup.addView(rbLow)
        radioGroup.addView(rbMedium)
        radioGroup.addView(rbHigh)

        container.addView(label)
        container.addView(radioGroup)
    }

    private fun processFile() {
        if (inputFile == null) {
            Toast.makeText(requireContext(), R.string.err_no_file, Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            binding.btnProcess.isEnabled = false
            val prefs = AppPreferences(requireContext())
            val defaultAuthor = prefs.getDefaultAuthor().ifBlank { null }
            val outputFile = File(FileUtils.getOutputDir(requireContext()), FileUtils.generateOutputName("compressed"))
            val result = com.pixelcraftin.pdfeditorplus.util.PdfUtils.compressPdf(inputFile!!, outputFile, compressionLevel, defaultAuthor)
            binding.progressBar.visibility = View.GONE
            binding.btnProcess.isEnabled = true
            result.onSuccess {
                val origSize = FileUtils.formatSize(inputFile!!.length())
                val compSize = FileUtils.formatSize(it.length())
                binding.cardResult.visibility = View.VISIBLE
                binding.tvResultPath.text = "${getString(R.string.original_size, origSize)}\n${getString(R.string.compressed_size, compSize)}"

                binding.btnDownload.setOnClickListener {
                    FileUtils.saveFileToDownloads(requireContext(), outputFile).onSuccess { path ->
                        Toast.makeText(requireContext(), "Saved to $path", Toast.LENGTH_SHORT).show()
                    }.onFailure { e ->
                        Toast.makeText(requireContext(), "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }

                binding.btnShare.setOnClickListener { FileUtils.shareFile(requireContext(), outputFile) }
                binding.btnViewFile.setOnClickListener { FileUtils.openFile(requireContext(), outputFile) }
                HistoryDatabase.getInstance(requireContext()).historyDao().insert(
                    HistoryItem(fileName = it.name, filePath = it.absolutePath, toolName = "Compress PDF", fileSize = it.length())
                )
            }
            result.onFailure {
                Toast.makeText(requireContext(), getString(R.string.error_processing, it.message), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
