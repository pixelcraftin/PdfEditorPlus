package com.pixelcraftin.pdfeditorplus.ui.toolscreens

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
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
import com.pixelcraftin.pdfeditorplus.util.PdfUtils
import kotlinx.coroutines.launch
import java.io.File

class UnlockPdfFragment : Fragment() {

    private var _binding: FragmentToolImageToPdfBinding? = null
    private val binding get() = _binding!!
    private var inputUri: Uri? = null
    private var inputFile: File? = null
    private lateinit var etPassword: EditText

    private val pickPdf = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { onPdfSelected(it) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentToolImageToPdfBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvToolTitle.text = getString(R.string.tool_unlock_pdf)
        binding.tvToolDesc.text = getString(R.string.desc_unlock_pdf)
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

        inputFile?.let { file ->
            val isProtected = PdfUtils.isPdfPasswordProtected(file)
            if (isProtected) {
                etPassword.requestFocus()
            }
        }
    }

    private fun setupOptions() {
        val container = binding.optionsContainer

        val label = TextView(requireContext()).apply {
            text = "Enter Password (User or Statement Password)"
            textSize = 14f
            setTextColor(resources.getColor(R.color.text_primary, null))
            setPadding(0, 0, 0, 8)
        }
        etPassword = EditText(requireContext()).apply {
            hint = "e.g. DOB/PAN/Statement password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        container.addView(label)
        container.addView(etPassword)
    }

    private fun processFile() {
        val password = etPassword.text.toString()
        if (inputFile == null) {
            Toast.makeText(requireContext(), R.string.err_no_file, Toast.LENGTH_SHORT).show()
            return
        }
        if (password.isEmpty()) {
            Toast.makeText(requireContext(), R.string.err_password_empty, Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            binding.btnProcess.isEnabled = false
            val outputFile = File(FileUtils.getOutputDir(requireContext()), FileUtils.generateOutputName("unlocked_statement"))
            val result = PdfUtils.unlockPdf(inputFile!!, password, outputFile)
            binding.progressBar.visibility = View.GONE
            binding.btnProcess.isEnabled = true
            result.onSuccess {
                binding.cardResult.visibility = View.VISIBLE
                binding.tvResultPath.text = "Decrypted: ${it.name}"

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
                    HistoryItem(fileName = it.name, filePath = it.absolutePath, toolName = "Unlock PDF", fileSize = it.length())
                )
            }
            result.onFailure {
                Toast.makeText(requireContext(), "Decryption failed. Please check password (AES-128/256 or RC4 statement password).", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
