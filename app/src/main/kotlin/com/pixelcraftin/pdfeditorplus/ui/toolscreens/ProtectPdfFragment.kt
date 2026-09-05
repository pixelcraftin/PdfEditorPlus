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

class ProtectPdfFragment : Fragment() {

    private var _binding: FragmentToolImageToPdfBinding? = null
    private val binding get() = _binding!!
    private var inputUri: Uri? = null
    private var inputFile: File? = null
    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText

    private val pickPdf = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { onPdfSelected(it) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentToolImageToPdfBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvToolTitle.text = getString(R.string.tool_protect_pdf)
        binding.tvToolDesc.text = getString(R.string.desc_protect_pdf)
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

        val pwLabel = TextView(requireContext()).apply {
            text = getString(R.string.set_password)
            textSize = 14f
            setTextColor(resources.getColor(R.color.text_primary, null))
            setPadding(0, 0, 0, 8)
        }
        etPassword = EditText(requireContext()).apply {
            hint = getString(R.string.password)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        container.addView(pwLabel)
        container.addView(etPassword)

        val confirmLabel = TextView(requireContext()).apply {
            text = getString(R.string.confirm_password)
            textSize = 14f
            setTextColor(resources.getColor(R.color.text_primary, null))
            setPadding(0, 16, 0, 8)
        }
        etConfirmPassword = EditText(requireContext()).apply {
            hint = getString(R.string.confirm_password)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        container.addView(confirmLabel)
        container.addView(etConfirmPassword)
    }

    private fun processFile() {
        val password = etPassword.text.toString()
        val confirm = etConfirmPassword.text.toString()
        if (inputFile == null) {
            Toast.makeText(requireContext(), R.string.err_no_file, Toast.LENGTH_SHORT).show()
            return
        }
        if (password.isEmpty()) {
            Toast.makeText(requireContext(), R.string.err_password_empty, Toast.LENGTH_SHORT).show()
            return
        }
        if (password != confirm) {
            Toast.makeText(requireContext(), R.string.passwords_mismatch, Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            binding.btnProcess.isEnabled = false
            val prefs = com.pixelcraftin.pdfeditorplus.data.prefs.AppPreferences(requireContext())
            val defaultAuthor = prefs.getDefaultAuthor().ifBlank { null }
            val outputFile = File(FileUtils.getOutputDir(requireContext()), FileUtils.generateOutputName("protected"))
            val result = PdfUtils.protectPdf(inputFile!!, password, outputFile, defaultAuthor)
            binding.progressBar.visibility = View.GONE
            binding.btnProcess.isEnabled = true
            result.onSuccess {
                binding.cardResult.visibility = View.VISIBLE
                binding.tvResultPath.text = it.absolutePath

                binding.btnDownload.setOnClickListener {
                    FileUtils.promptSaveToDownloads(requireContext(), outputFile)
                }

                binding.btnShare.setOnClickListener { FileUtils.shareFile(requireContext(), outputFile) }
                binding.btnViewFile.setOnClickListener { FileUtils.openFile(requireContext(), outputFile) }
                HistoryDatabase.getInstance(requireContext()).historyDao().insert(
                    HistoryItem(fileName = it.name, filePath = it.absolutePath, toolName = "Protect PDF", fileSize = it.length())
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
