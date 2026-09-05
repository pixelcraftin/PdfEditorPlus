package com.pixelcraftin.pdfeditorplus.ui.toolscreens

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
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

class WatermarkFragment : Fragment() {

    private var _binding: FragmentToolImageToPdfBinding? = null
    private val binding get() = _binding!!
    private var inputUri: Uri? = null
    private var inputFile: File? = null
    private var opacity = 30
    private var fontSize = 48
    private var selectedAngle = 45f

    private val angleOptions = listOf(
        "45° (Diagonal)" to 45f,
        "0° (Horizontal)" to 0f,
        "-45° (Reverse Diagonal)" to -45f,
        "90°" to 90f,
        "-90° (Vertical)" to -90f
    )

    private var etWatermarkText: EditText? = null

    private val pickPdf = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            inputUri = it
            inputFile = FileUtils.uriToFile(requireContext(), it)
            binding.tvPickerHint.visibility = View.GONE
            binding.tvSelectedFileName.visibility = View.VISIBLE
            binding.tvSelectedFileName.text = FileUtils.getFileName(requireContext(), it)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentToolImageToPdfBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvToolTitle.text = getString(R.string.tool_watermark)
        binding.tvToolDesc.text = getString(R.string.desc_watermark)
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
        container.removeAllViews()

        val textLabel = TextView(requireContext()).apply {
            text = getString(R.string.watermark_text)
            textSize = 14f
            setTextColor(resources.getColor(R.color.text_primary, null))
            setPadding(0, 0, 0, 8)
        }
        val editText = EditText(requireContext()).apply {
            hint = getString(R.string.watermark_hint)
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        etWatermarkText = editText
        container.addView(textLabel)
        container.addView(editText)

        // Watermark Angle Dropdown
        val angleLabel = TextView(requireContext()).apply {
            text = getString(R.string.watermark_angle)
            textSize = 14f
            setTextColor(resources.getColor(R.color.text_primary, null))
            setPadding(0, 16, 0, 8)
        }
        val angleSpinner = android.widget.Spinner(requireContext()).apply {
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            val adapter = android.widget.ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                angleOptions.map { it.first }
            )
            this.adapter = adapter
            setSelection(0)
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, pos: Int, id: Long) {
                    selectedAngle = angleOptions[pos].second
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
        }
        container.addView(angleLabel)
        container.addView(angleSpinner)

        val opacityLabel = TextView(requireContext()).apply {
            text = "${getString(R.string.opacity)}: ${opacity}%"
            textSize = 14f
            setTextColor(resources.getColor(R.color.text_primary, null))
            setPadding(0, 16, 0, 8)
            id = View.generateViewId()
        }
        val opacitySeekBar = SeekBar(requireContext()).apply {
            max = 100
            progress = opacity
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        container.addView(opacityLabel)
        container.addView(opacitySeekBar)
        opacitySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                opacity = progress
                opacityLabel.text = "${getString(R.string.opacity)}: ${progress}%"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        val sizeLabel = TextView(requireContext()).apply {
            text = "${getString(R.string.font_size)}: ${fontSize}sp"
            textSize = 14f
            setTextColor(resources.getColor(R.color.text_primary, null))
            setPadding(0, 16, 0, 8)
            id = View.generateViewId()
        }
        val sizeSeekBar = SeekBar(requireContext()).apply {
            max = 48
            progress = fontSize - 24
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        container.addView(sizeLabel)
        container.addView(sizeSeekBar)
        sizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                fontSize = progress + 24
                sizeLabel.text = "${getString(R.string.font_size)}: ${fontSize}sp"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    private fun processFile() {
        val text = etWatermarkText?.text?.toString().orEmpty()
        if (inputFile == null) {
            Toast.makeText(requireContext(), R.string.err_no_file, Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            binding.btnProcess.isEnabled = false
            val prefs = com.pixelcraftin.pdfeditorplus.data.prefs.AppPreferences(requireContext())
            val defaultAuthor = prefs.getDefaultAuthor().ifBlank { null }
            val outputFile = File(FileUtils.getOutputDir(requireContext()), FileUtils.generateOutputName("watermark"))
            val result = PdfUtils.addWatermark(
                inputFile = inputFile!!,
                watermarkText = text,
                outputFile = outputFile,
                opacity = opacity / 100f,
                fontSize = fontSize.toFloat(),
                angleDegrees = selectedAngle,
                defaultAuthor = defaultAuthor
            )
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
                    HistoryItem(fileName = it.name, filePath = it.absolutePath, toolName = "Watermark", fileSize = it.length())
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
