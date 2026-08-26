package com.pixelcraftin.pdfeditorplus.ui.toolscreens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.pixelcraftin.pdfeditorplus.R
import com.pixelcraftin.pdfeditorplus.data.db.HistoryDatabase
import com.pixelcraftin.pdfeditorplus.data.model.HistoryItem
import com.pixelcraftin.pdfeditorplus.databinding.FragmentToolImageToPdfBinding
import com.pixelcraftin.pdfeditorplus.ocr.TextRecognizerProvider
import com.pixelcraftin.pdfeditorplus.util.FileUtils
import com.pixelcraftin.pdfeditorplus.util.PdfUtils
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class PdfToTextFragment : Fragment() {

    private var _binding: FragmentToolImageToPdfBinding? = null
    private val binding get() = _binding!!

    private var selectedPdfUri: Uri? = null
    private var extractedTextContent = ""

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
        binding.tvToolTitle.text = getString(R.string.tool_pdf_to_text)
        binding.tvToolDesc.text = getString(R.string.desc_pdf_to_text)
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

            val inputFile = FileUtils.uriToFile(requireContext(), uri)
            if (inputFile == null) {
                binding.progressBar.visibility = View.GONE
                binding.btnProcess.isEnabled = true
                Toast.makeText(requireContext(), R.string.err_no_file, Toast.LENGTH_SHORT).show()
                return@launch
            }

            val result = TextRecognizerProvider.instance.extractTextFromPdf(requireContext(), inputFile)

            binding.progressBar.visibility = View.GONE
            binding.btnProcess.isEnabled = true

            result.onSuccess { extractedText ->
                extractedTextContent = extractedText
                binding.cardResult.visibility = View.VISIBLE
                binding.tvResultPath.text = extractedText.ifEmpty { getString(R.string.msg_no_text_found) }

                val textFile = File(FileUtils.getOutputDir(requireContext()), FileUtils.generateOutputName("extracted_text", "txt"))
                FileOutputStream(textFile).use { out -> out.write(extractedText.toByteArray()) }

                binding.btnDownload.setOnClickListener {
                    FileUtils.saveFileToDownloads(requireContext(), textFile).onSuccess { path ->
                        Toast.makeText(requireContext(), "Saved text to $path", Toast.LENGTH_SHORT).show()
                    }.onFailure { e ->
                        Toast.makeText(requireContext(), "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }

                binding.btnShare.setOnClickListener { FileUtils.shareFile(requireContext(), textFile) }
                binding.btnViewFile.setOnClickListener {
                    val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("extracted_text", extractedText)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(requireContext(), "Copied text to clipboard!", Toast.LENGTH_SHORT).show()
                }

                lifecycleScope.launch {
                    val dao = HistoryDatabase.getInstance(requireContext()).historyDao()
                    dao.insert(
                        HistoryItem(
                            fileName = textFile.name,
                            filePath = textFile.absolutePath,
                            toolName = "PDF to Text",
                            fileSize = textFile.length()
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
