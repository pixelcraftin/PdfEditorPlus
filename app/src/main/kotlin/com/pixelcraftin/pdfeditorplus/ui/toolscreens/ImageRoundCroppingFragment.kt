package com.pixelcraftin.pdfeditorplus.ui.toolscreens

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import coil.load
import com.google.android.material.button.MaterialButton
import com.pixelcraftin.pdfeditorplus.R
import com.pixelcraftin.pdfeditorplus.data.db.HistoryDatabase
import com.pixelcraftin.pdfeditorplus.data.model.HistoryItem
import com.pixelcraftin.pdfeditorplus.databinding.FragmentToolImageRoundCroppingBinding
import com.pixelcraftin.pdfeditorplus.util.FileUtils
import com.pixelcraftin.pdfeditorplus.util.ImageUtils
import kotlinx.coroutines.launch
import java.io.File

class ImageRoundCroppingFragment : Fragment() {

    private var _binding: FragmentToolImageRoundCroppingBinding? = null
    private val binding get() = _binding!!
    private var inputUri: Uri? = null
    private var isCircle = true
    private var cornerRadiusPercent = 25
    private lateinit var radiusLabel: TextView
    private lateinit var radiusSeekBar: SeekBar
    private var previewImageView: ImageView? = null
    private var discardButton: MaterialButton? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { onImageSelected(it) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentToolImageRoundCroppingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvToolTitle.text = getString(R.string.tool_image_round_cropping)
        binding.tvToolDesc.text = getString(R.string.desc_image_round_cropping)
        binding.tvPickerHint.text = getString(R.string.tap_to_pick_image)
        setupOptions()
        setupListeners()

        val preselectedUri = arguments?.getParcelable<Uri>("preselected_uri")
            ?: arguments?.getString("preselected_uri_string")?.let { Uri.parse(it) }
        preselectedUri?.let { onImageSelected(it) }
    }

    private fun onImageSelected(uri: Uri) {
        inputUri = uri
        binding.tvPickerHint.visibility = View.GONE
        binding.tvSelectedFileName.visibility = View.VISIBLE
        binding.tvSelectedFileName.text = FileUtils.getFileName(requireContext(), uri)
    }

    private var formatIndex = 0
    private val formatNames = listOf("WEBP (Recommended / Small Size)", "PNG (Lossless)", "JPG (Solid Background)")
    private val formatExtensions = listOf("webp", "png", "jpg")
    private val formatMimeTypes = listOf("image/webp", "image/png", "image/jpeg")

    private fun getSelectedFormat(): android.graphics.Bitmap.CompressFormat {
        return when (formatIndex) {
            1 -> android.graphics.Bitmap.CompressFormat.PNG
            2 -> android.graphics.Bitmap.CompressFormat.JPEG
            else -> ImageUtils.getDefaultWebpFormat()
        }
    }

    private fun setupOptions() {
        val container = binding.optionsContainer

        val shapeLabel = TextView(requireContext()).apply {
            text = getString(R.string.crop_shape)
            textSize = 14f
            setTextColor(resources.getColor(R.color.text_primary, null))
            setPadding(0, 0, 0, 8)
        }
        val radioGroup = RadioGroup(requireContext()).apply {
            orientation = RadioGroup.HORIZONTAL
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val circleRb = RadioButton(requireContext()).apply {
            text = getString(R.string.circle)
            id = View.generateViewId()
            isChecked = true
        }
        val roundedRb = RadioButton(requireContext()).apply {
            text = getString(R.string.rounded_rectangle)
            id = View.generateViewId()
        }
        radioGroup.addView(circleRb)
        radioGroup.addView(roundedRb)
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            isCircle = checkedId == circleRb.id
            radiusLabel.visibility = if (isCircle) View.GONE else View.VISIBLE
            radiusSeekBar.visibility = if (isCircle) View.GONE else View.VISIBLE
        }
        container.addView(shapeLabel)
        container.addView(radioGroup)

        radiusLabel = TextView(requireContext()).apply {
            text = "Corner Radius: ${cornerRadiusPercent}% (0-90%)"
            textSize = 14f
            setTextColor(resources.getColor(R.color.text_primary, null))
            setPadding(0, 16, 0, 8)
            visibility = View.GONE
        }
        radiusSeekBar = SeekBar(requireContext()).apply {
            max = 90
            progress = cornerRadiusPercent
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            visibility = View.GONE
        }
        container.addView(radiusLabel)
        container.addView(radiusSeekBar)
        radiusSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                cornerRadiusPercent = progress
                radiusLabel.text = "Corner Radius: ${progress}% (0-90%)"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // Output format selection
        val formatLabel = TextView(requireContext()).apply {
            text = getString(R.string.output_format)
            textSize = 14f
            setTextColor(resources.getColor(R.color.text_primary, null))
            setPadding(0, 16, 0, 8)
        }
        val formatSpinner = Spinner(requireContext()).apply {
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        container.addView(formatLabel)
        container.addView(formatSpinner)
        formatSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, formatNames)
        formatSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                formatIndex = pos
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.cardFilePicker.setOnClickListener { pickImage.launch("image/*") }
        binding.btnProcess.setOnClickListener { processFile() }
    }

    private fun processFile() {
        if (inputUri == null) {
            Toast.makeText(requireContext(), R.string.err_no_file, Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            binding.btnProcess.isEnabled = false
            binding.cardResult.visibility = View.GONE

            val ext = formatExtensions[formatIndex]
            val mime = formatMimeTypes[formatIndex]
            val format = getSelectedFormat()

            val inputFile = FileUtils.uriToFile(requireContext(), inputUri!!, ".$ext") ?: run {
                binding.progressBar.visibility = View.GONE
                binding.btnProcess.isEnabled = true
                return@launch
            }
            val outputFile = File(FileUtils.getOutputDir(requireContext()), FileUtils.generateOutputName("round_crop", ext))
            val result = ImageUtils.cropToRound(inputFile, outputFile, isCircle, cornerRadiusPercent.toFloat(), format, 90)
            binding.progressBar.visibility = View.GONE
            binding.btnProcess.isEnabled = true
            result.onSuccess {
                val origSize = FileUtils.getFileSize(requireContext(), inputUri!!)
                val newSize = it.length()
                binding.cardResult.visibility = View.VISIBLE
                binding.tvResultTitle.text = "Cropping Complete!"
                binding.tvResultPath.text = "Original: ${FileUtils.formatSize(origSize)} • Output: ${FileUtils.formatSize(newSize)}"

                // Display visual preview of circular / rounded cropped image
                displayCroppedPreview(outputFile)

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
                    HistoryItem(fileName = it.name, filePath = it.absolutePath, toolName = "Round Crop", fileSize = newSize, mimeType = mime)
                )
            }
            result.onFailure {
                Toast.makeText(requireContext(), getString(R.string.error_processing, it.message), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun displayCroppedPreview(file: File) {
        val resultContainer = binding.tvResultPath.parent as? ViewGroup ?: return

        if (previewImageView == null) {
            previewImageView = ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (220 * resources.displayMetrics.density).toInt()
                ).apply {
                    topMargin = (12 * resources.displayMetrics.density).toInt()
                    bottomMargin = (12 * resources.displayMetrics.density).toInt()
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
                background = resources.getDrawable(R.drawable.bg_card, null)
                setPadding(12, 12, 12, 12)
            }
            val index = resultContainer.indexOfChild(binding.tvResultPath)
            resultContainer.addView(previewImageView, index + 1)
        }

        previewImageView?.load(file)

        if (discardButton == null) {
            discardButton = MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "Discard & Try Another"
                textSize = 13f
                setTextColor(resources.getColor(R.color.icon_red, null))
                strokeColor = android.content.res.ColorStateList.valueOf(resources.getColor(R.color.icon_red, null))
                cornerRadius = (12 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (44 * resources.displayMetrics.density).toInt()).apply {
                    topMargin = (8 * resources.displayMetrics.density).toInt()
                }
                setOnClickListener {
                    binding.cardResult.visibility = View.GONE
                    if (file.exists()) file.delete()
                    Toast.makeText(requireContext(), "Discarded", Toast.LENGTH_SHORT).show()
                }
            }
            resultContainer.addView(discardButton)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        previewImageView = null
        discardButton = null
        _binding = null
    }
}
