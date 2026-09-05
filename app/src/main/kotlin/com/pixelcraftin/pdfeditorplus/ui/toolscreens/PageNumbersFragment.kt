package com.pixelcraftin.pdfeditorplus.ui.toolscreens

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
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

class PageNumbersFragment : Fragment() {

    private var _binding: FragmentToolImageToPdfBinding? = null
    private val binding get() = _binding!!

    private var selectedPdfUri: Uri? = null
    private var selectedPosition = PdfUtils.NumberPosition.BOTTOM_CENTER
    private var startFrom = 1
    private lateinit var etStartFrom: EditText

    private val positionLabels = mapOf(
        "Top Left" to PdfUtils.NumberPosition.TOP_LEFT,
        "Top Center" to PdfUtils.NumberPosition.TOP_CENTER,
        "Top Right" to PdfUtils.NumberPosition.TOP_RIGHT,
        "Bottom Left" to PdfUtils.NumberPosition.BOTTOM_LEFT,
        "Bottom Center" to PdfUtils.NumberPosition.BOTTOM_CENTER,
        "Bottom Right" to PdfUtils.NumberPosition.BOTTOM_RIGHT
    )

    private val pdfPicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedPdfUri = it
            binding.tvSelectedFileName.text = FileUtils.getFileName(requireContext(), it)
            binding.tvSelectedFileName.visibility = View.VISIBLE
            binding.tvPickerHint.text = getString(R.string.pick_pdf)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentToolImageToPdfBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvToolTitle.text = getString(R.string.tool_page_numbers)
        binding.tvToolDesc.text = getString(R.string.desc_page_numbers)
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
        val posLabel = MaterialTextView(requireContext()).apply {
            text = getString(R.string.number_position)
            setTextColor(resources.getColor(R.color.text_primary, null))
            textSize = 14f
            setPadding(0, 8, 0, 4)
        }

        val positions = positionLabels.keys.toTypedArray()
        val posSpinner = android.widget.Spinner(requireContext()).apply {
            id = View.generateViewId()
            adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, positions)
            setSelection(4)
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    selectedPosition = positionLabels[positions[position]] ?: PdfUtils.NumberPosition.BOTTOM_CENTER
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
        }

        val startLabel = MaterialTextView(requireContext()).apply {
            text = getString(R.string.start_from)
            setTextColor(resources.getColor(R.color.text_primary, null))
            textSize = 14f
            setPadding(0, 16, 0, 4)
        }

        etStartFrom = EditText(requireContext()).apply {
            hint = "1"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("1")
            setPadding(32, 24, 32, 24)
            setBackgroundColor(resources.getColor(R.color.card_background, null))
            setTextColor(resources.getColor(R.color.text_primary, null))
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    startFrom = s.toString().toIntOrNull() ?: 1
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }

        binding.optionsContainer.addView(posLabel)
        binding.optionsContainer.addView(posSpinner)
        binding.optionsContainer.addView(startLabel)
        binding.optionsContainer.addView(etStartFrom)
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

            val prefs = com.pixelcraftin.pdfeditorplus.data.prefs.AppPreferences(requireContext())
            val defaultAuthor = prefs.getDefaultAuthor().ifBlank { null }
            val outputDir = FileUtils.getOutputDir(requireContext())
            val outputFile = File(outputDir, FileUtils.generateOutputName("numbered_pdf"))

            val result = PdfUtils.addPageNumbers(inputFile, outputFile, startFrom, selectedPosition, defaultAuthor)

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
                    toolName = "Page Numbers",
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
