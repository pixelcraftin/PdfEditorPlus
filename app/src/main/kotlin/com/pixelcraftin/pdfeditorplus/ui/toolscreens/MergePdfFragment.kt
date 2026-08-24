package com.pixelcraftin.pdfeditorplus.ui.toolscreens

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.pixelcraftin.pdfeditorplus.R
import com.pixelcraftin.pdfeditorplus.data.db.HistoryDatabase
import com.pixelcraftin.pdfeditorplus.data.model.HistoryItem
import com.pixelcraftin.pdfeditorplus.data.prefs.AppPreferences
import com.pixelcraftin.pdfeditorplus.databinding.FragmentToolImageToPdfBinding
import com.pixelcraftin.pdfeditorplus.util.FileUtils
import com.pixelcraftin.pdfeditorplus.util.PdfUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class MergePdfFragment : Fragment() {

    private var _binding: FragmentToolImageToPdfBinding? = null
    private val binding get() = _binding!!

    private val selectedPdfUris = mutableListOf<Uri>()
    private val passwordsMap = mutableMapOf<String, String>()

    private val pdfPicker = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri>? ->
        if (!uris.isNullOrEmpty()) {
            selectedPdfUris.addAll(uris)
            updateFileList()
            checkProtectedFiles()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentToolImageToPdfBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvToolTitle.text = getString(R.string.tool_merge_pdf)
        binding.tvToolDesc.text = getString(R.string.desc_merge_pdf)
        setupOptions()
        setupListeners()

        val preselectedUri = arguments?.getParcelable<Uri>("preselected_uri")
            ?: arguments?.getString("preselected_uri_string")?.let { Uri.parse(it) }
        preselectedUri?.let {
            if (!selectedPdfUris.contains(it)) {
                selectedPdfUris.add(it)
                updateFileList()
                checkProtectedFiles()
            }
        }
    }

    private fun setupOptions() {
        val btnAddMore = MaterialButton(requireContext()).apply {
            text = getString(R.string.add_pdf)
            setOnClickListener { pdfPicker.launch("application/pdf") }
        }
        binding.optionsContainer.addView(btnAddMore)
        updateFileList()
    }

    private fun checkProtectedFiles() {
        lifecycleScope.launch {
            selectedPdfUris.forEach { uri ->
                val file = FileUtils.uriToFile(requireContext(), uri)
                if (file != null && PdfUtils.isPasswordProtected(file) && !passwordsMap.containsKey(file.absolutePath)) {
                    promptPasswordForFile(file)
                }
            }
        }
    }

    private fun promptPasswordForFile(file: File) {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Enter PDF Password"
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Password Required")
            .setMessage("The file \"${file.name}\" is password protected. Please enter its password to merge:")
            .setView(input)
            .setPositiveButton("Unlock") { _, _ ->
                val password = input.text.toString()
                if (PdfUtils.verifyPassword(file, password)) {
                    passwordsMap[file.absolutePath] = password
                    Toast.makeText(requireContext(), "Unlocked ${file.name}", Toast.LENGTH_SHORT).show()
                    updateFileList()
                } else {
                    Toast.makeText(requireContext(), "Incorrect password for ${file.name}", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateFileList() {
        val container = binding.optionsContainer
        container.removeAllViews()

        val btnAddMore = MaterialButton(requireContext()).apply {
            text = getString(R.string.add_pdf)
            setOnClickListener { pdfPicker.launch("application/pdf") }
        }
        container.addView(btnAddMore)

        selectedPdfUris.forEachIndexed { index, uri ->
            val name = FileUtils.getFileName(requireContext(), uri)
            val file = FileUtils.uriToFile(requireContext(), uri)
            val isProtected = file != null && PdfUtils.isPasswordProtected(file)
            val isUnlocked = file != null && passwordsMap.containsKey(file.absolutePath)

            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, 8, 0, 8)
            }

            val statusText = when {
                !isProtected -> ""
                isUnlocked -> " 🔓 Unlocked"
                else -> " 🔒 Locked (Tap to Unlock)"
            }

            val tv = TextView(requireContext()).apply {
                text = "${index + 1}. $name$statusText"
                textSize = 14f
                setTextColor(resources.getColor(R.color.text_primary, null))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                if (isProtected && !isUnlocked && file != null) {
                    setOnClickListener { promptPasswordForFile(file) }
                }
            }

            val removeBtn = ImageButton(requireContext()).apply {
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                background = null
                setOnClickListener {
                    file?.let { passwordsMap.remove(it.absolutePath) }
                    selectedPdfUris.removeAt(index)
                    updateFileList()
                }
            }

            row.addView(tv)
            row.addView(removeBtn)
            container.addView(row)
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.cardFilePicker.setOnClickListener { pdfPicker.launch("application/pdf") }
        binding.btnProcess.setOnClickListener { processFile() }
    }

    private fun processFile() {
        if (selectedPdfUris.size < 2) {
            Toast.makeText(requireContext(), R.string.err_no_pdfs, Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            binding.btnProcess.isEnabled = false
            binding.cardResult.visibility = View.GONE

            val inputFiles = selectedPdfUris.mapNotNull { uri ->
                FileUtils.uriToFile(requireContext(), uri)
            }

            if (inputFiles.size < 2) {
                binding.progressBar.visibility = View.GONE
                binding.btnProcess.isEnabled = true
                Toast.makeText(requireContext(), R.string.err_no_pdfs, Toast.LENGTH_SHORT).show()
                return@launch
            }

            // Check if any protected file is missing password
            for (file in inputFiles) {
                if (PdfUtils.isPasswordProtected(file) && !passwordsMap.containsKey(file.absolutePath)) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnProcess.isEnabled = true
                    promptPasswordForFile(file)
                    return@launch
                }
            }

            val prefs = AppPreferences(requireContext())
            val defaultAuthor = prefs.getDefaultAuthor().ifBlank { null }

            val outputDir = FileUtils.getOutputDir(requireContext())
            val outputFile = File(outputDir, FileUtils.generateOutputName("merged_pdf"))

            val result = PdfUtils.mergePdfs(inputFiles, outputFile, passwordsMap, defaultAuthor)

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
                    toolName = "Merge PDF",
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
