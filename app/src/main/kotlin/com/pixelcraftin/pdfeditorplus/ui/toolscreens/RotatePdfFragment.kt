package com.pixelcraftin.pdfeditorplus.ui.toolscreens

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

class RotatePdfFragment : Fragment() {

    private var _binding: FragmentToolImageToPdfBinding? = null
    private val binding get() = _binding!!

    private var selectedPdfUri: Uri? = null
    private var rotationDegrees = 90
    private var applyTo = PdfUtils.ApplyTo.ALL

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
        binding.tvToolTitle.text = getString(R.string.tool_rotate_pdf)
        binding.tvToolDesc.text = getString(R.string.desc_rotate_pdf)
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

    private val rotationOptions = listOf(
        "Right (90° Clockwise)" to 90,
        "Left (90° Counter-Clockwise / 270°)" to 270,
        "Bottom / Upside Down (180°)" to 180,
        "Up / Reset to Standard (0°)" to 0
    )

    private val applyToOptions = listOf(
        "All Pages" to PdfUtils.ApplyTo.ALL,
        "Custom Range" to PdfUtils.ApplyTo.CUSTOM,
        "Odd Pages Only" to PdfUtils.ApplyTo.ODD,
        "Even Pages Only" to PdfUtils.ApplyTo.EVEN
    )

    private var etCustomRange: EditText? = null
    private var customRangeContainer: View? = null

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.cardFilePicker.setOnClickListener { pdfPicker.launch("application/pdf") }
        binding.btnProcess.setOnClickListener { processFile() }
    }

    private fun setupOptions() {
        val container = binding.optionsContainer
        container.removeAllViews()

        // 1. Rotation Dropdown Label & Menu
        val angleLabel = MaterialTextView(requireContext()).apply {
            text = getString(R.string.rotation_angle)
            setTextColor(resources.getColor(R.color.text_primary, null))
            textSize = 14f
            setPadding(0, 4, 0, 8)
        }
        container.addView(angleLabel)

        val rotationSpinner = android.widget.Spinner(requireContext()).apply {
            id = View.generateViewId()
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 14)
            }
            val adapter = android.widget.ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                rotationOptions.map { it.first }
            )
            this.adapter = adapter
            setSelection(0)
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, pos: Int, id: Long) {
                    rotationDegrees = rotationOptions[pos].second
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
        }
        container.addView(rotationSpinner)

        // 2. Apply To Dropdown Label & Menu
        val applyLabel = MaterialTextView(requireContext()).apply {
            text = getString(R.string.apply_to)
            setTextColor(resources.getColor(R.color.text_primary, null))
            textSize = 14f
            setPadding(0, 8, 0, 8)
        }
        container.addView(applyLabel)

        val applySpinner = android.widget.Spinner(requireContext()).apply {
            id = View.generateViewId()
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 10)
            }
            val adapter = android.widget.ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                applyToOptions.map { it.first }
            )
            this.adapter = adapter
            setSelection(0)
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, pos: Int, id: Long) {
                    applyTo = applyToOptions[pos].second
                    customRangeContainer?.visibility = if (applyTo == PdfUtils.ApplyTo.CUSTOM) View.VISIBLE else View.GONE
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
        }
        container.addView(applySpinner)

        // 3. Custom Range Input (Revealed when "Custom Range" is selected)
        val customRangeInputLayout = com.google.android.material.textfield.TextInputLayout(requireContext()).apply {
            boxBackgroundMode = com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE
            setBoxCornerRadii(16f, 16f, 16f, 16f)
            visibility = View.GONE
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 4, 0, 12)
            }
        }
        customRangeContainer = customRangeInputLayout

        val customRangeInput = com.google.android.material.textfield.TextInputEditText(requireContext()).apply {
            hint = "e.g. 1-3, 5"
            setText("1")
            setTextColor(resources.getColor(R.color.text_primary, null))
        }
        etCustomRange = customRangeInput
        customRangeInputLayout.addView(customRangeInput)
        container.addView(customRangeInputLayout)
    }

    private fun parsePageRanges(input: String, maxPage: Int): List<Int> {
        val pages = mutableSetOf<Int>()
        val parts = input.split(",", " ", ";").filter { it.isNotBlank() }
        for (part in parts) {
            if (part.contains("-")) {
                val range = part.split("-").mapNotNull { it.trim().toIntOrNull() }
                if (range.size == 2) {
                    val start = range[0].coerceIn(1, maxPage)
                    val end = range[1].coerceIn(1, maxPage)
                    val (from, to) = if (start <= end) Pair(start, end) else Pair(end, start)
                    for (p in from..to) pages.add(p)
                }
            } else {
                part.trim().toIntOrNull()?.let { p ->
                    if (p in 1..maxPage) pages.add(p)
                }
            }
        }
        return if (pages.isEmpty()) listOf(1) else pages.sorted()
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
            val outputFile = File(outputDir, FileUtils.generateOutputName("rotated_pdf"))

            val customPages = if (applyTo == PdfUtils.ApplyTo.CUSTOM) {
                val rangeText = etCustomRange?.text?.toString().orEmpty()
                parsePageRanges(rangeText, 100000)
            } else {
                emptyList()
            }

            val result = PdfUtils.rotatePdf(inputFile, rotationDegrees, outputFile, applyTo, customPages, defaultAuthor)

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
                    toolName = "Rotate PDF",
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
