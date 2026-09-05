package com.pixelcraftin.pdfeditorplus.ui.toolscreens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textview.MaterialTextView
import com.pixelcraftin.pdfeditorplus.R
import com.pixelcraftin.pdfeditorplus.data.db.HistoryDatabase
import com.pixelcraftin.pdfeditorplus.data.model.HistoryItem
import com.pixelcraftin.pdfeditorplus.data.prefs.AppPreferences
import com.pixelcraftin.pdfeditorplus.databinding.FragmentToolImageToPdfBinding
import com.pixelcraftin.pdfeditorplus.util.FileUtils
import com.itextpdf.kernel.pdf.*
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Image as PdfImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

class SignatureFragment : Fragment() {

    private var _binding: FragmentToolImageToPdfBinding? = null
    private val binding get() = _binding!!
    private var inputUri: Uri? = null
    private var inputFile: File? = null
    private var signatureUri: Uri? = null
    private var signatureFile: File? = null
    private var positionIndex = 6 // Default: Bottom Right
    private var applyToAllPages = false

    private val positions = listOf("Top Left", "Top Center", "Top Right", "Center", "Bottom Left", "Bottom Center", "Bottom Right")

    private var ivSignaturePreview: ImageView? = null
    private var tvSignatureName: TextView? = null
    private var cardSignaturePreview: View? = null
    private var btnAddSignature: MaterialButton? = null
    private var etPageNumbersInput: EditText? = null
    private var pageInputLayoutContainer: View? = null

    private val pickPdf = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { onPdfSelected(it) }
    }

    private val pickSignatureLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { onSignatureSelected(it) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentToolImageToPdfBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvToolTitle.text = getString(R.string.tool_signature)
        binding.tvToolDesc.text = getString(R.string.desc_signature)
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

    private fun onSignatureSelected(uri: Uri) {
        signatureUri = uri
        val file = FileUtils.uriToFile(requireContext(), uri, ".sig")
        signatureFile = file
        val fileName = FileUtils.getFileName(requireContext(), uri)

        tvSignatureName?.text = fileName
        cardSignaturePreview?.visibility = View.VISIBLE
        btnAddSignature?.text = getString(R.string.change_signature)

        file?.let {
            val bmp = BitmapFactory.decodeFile(it.absolutePath)
            if (bmp != null) {
                ivSignaturePreview?.setImageBitmap(bmp)
            }
        }
    }

    private fun setupOptions() {
        val container = binding.optionsContainer
        container.removeAllViews()

        // Signature Section Header
        val sigLabel = MaterialTextView(requireContext()).apply {
            text = getString(R.string.tool_signature)
            textSize = 14f
            setTextColor(resources.getColor(R.color.text_primary, null))
            setPadding(0, 0, 0, 8)
        }
        container.addView(sigLabel)

        // Add Signature Button
        btnAddSignature = MaterialButton(requireContext()).apply {
            text = getString(R.string.add_signature)
            icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_signature)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            iconPadding = 12
            cornerRadius = 16
            setBackgroundColor(resources.getColor(R.color.primary, null))
            setTextColor(resources.getColor(R.color.background, null))
            iconTint = ContextCompat.getColorStateList(requireContext(), R.color.background)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 10)
            }
            setOnClickListener { pickSignatureLauncher.launch("image/*") }
        }
        container.addView(btnAddSignature)

        // Signature Preview Card
        val previewCard = MaterialCardView(requireContext()).apply {
            radius = 16f
            cardElevation = 0f
            setCardBackgroundColor(resources.getColor(R.color.surface_variant, null))
            strokeWidth = 0
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 16)
            }
        }
        cardSignaturePreview = previewCard

        val previewLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 12, 16, 12)
        }

        ivSignaturePreview = ImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(56, 56).apply {
                setMargins(0, 0, 12, 0)
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(android.graphics.Color.WHITE)
            setPadding(4, 4, 4, 4)
        }

        tvSignatureName = MaterialTextView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            textSize = 13f
            setTextColor(resources.getColor(R.color.text_primary, null))
        }

        val btnChangeSig = MaterialButton(requireContext(), null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = getString(R.string.change_signature)
            textSize = 12f
            setTextColor(resources.getColor(R.color.primary, null))
            setOnClickListener { pickSignatureLauncher.launch("image/*") }
        }

        val btnRemoveSig = ImageButton(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(36, 36).apply {
                setMargins(6, 0, 0, 0)
            }
            setImageResource(R.drawable.ic_delete)
            background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_pill)
            setColorFilter(resources.getColor(R.color.icon_red, null))
            setOnClickListener {
                signatureUri = null
                signatureFile = null
                cardSignaturePreview?.visibility = View.GONE
                btnAddSignature?.visibility = View.VISIBLE
                btnAddSignature?.text = getString(R.string.add_signature)
            }
        }

        previewLayout.addView(ivSignaturePreview)
        previewLayout.addView(tvSignatureName)
        previewLayout.addView(btnChangeSig)
        previewLayout.addView(btnRemoveSig)
        previewCard.addView(previewLayout)
        container.addView(previewCard)

        // Apply Signature Pages Option
        val applyLabel = MaterialTextView(requireContext()).apply {
            text = getString(R.string.apply_signature_pages)
            textSize = 14f
            setTextColor(resources.getColor(R.color.text_primary, null))
            setPadding(0, 8, 0, 4)
        }
        container.addView(applyLabel)

        val pagesRadioGroup = RadioGroup(requireContext()).apply {
            orientation = RadioGroup.HORIZONTAL
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 8)
            }
        }

        val rbSpecific = RadioButton(requireContext()).apply {
            id = View.generateViewId()
            text = getString(R.string.apply_specific_pages)
            isChecked = true
        }

        val rbAll = RadioButton(requireContext()).apply {
            id = View.generateViewId()
            text = getString(R.string.apply_all_pages)
        }

        pagesRadioGroup.addView(rbSpecific)
        pagesRadioGroup.addView(rbAll)
        container.addView(pagesRadioGroup)

        val pageInputLayout = TextInputLayout(requireContext()).apply {
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setBoxCornerRadii(16f, 16f, 16f, 16f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 12)
            }
        }
        pageInputLayoutContainer = pageInputLayout

        val pageInput = TextInputEditText(requireContext()).apply {
            hint = getString(R.string.specific_pages_hint)
            setText("1")
            setTextColor(resources.getColor(R.color.text_primary, null))
        }
        etPageNumbersInput = pageInput
        pageInputLayout.addView(pageInput)
        container.addView(pageInputLayout)

        pagesRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == rbAll.id) {
                applyToAllPages = true
                pageInputLayout.visibility = View.GONE
            } else {
                applyToAllPages = false
                pageInputLayout.visibility = View.VISIBLE
            }
        }

        // Position Spinner
        val posLabel = MaterialTextView(requireContext()).apply {
            text = getString(R.string.position)
            textSize = 14f
            setTextColor(resources.getColor(R.color.text_primary, null))
            setPadding(0, 4, 0, 4)
        }
        val posSpinner = Spinner(requireContext()).apply {
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        container.addView(posLabel)
        container.addView(posSpinner)
        posSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, positions)
        posSpinner.setSelection(positionIndex)
        posSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) { positionIndex = pos }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
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
        if (inputFile == null) {
            Toast.makeText(requireContext(), R.string.err_no_file, Toast.LENGTH_SHORT).show()
            return
        }
        if (signatureFile == null) {
            Toast.makeText(requireContext(), R.string.err_no_signature, Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            binding.btnProcess.isEnabled = false
            binding.cardResult.visibility = View.GONE

            val outputFile = File(FileUtils.getOutputDir(requireContext()), FileUtils.generateOutputName("signature"))
            val pageInputText = etPageNumbersInput?.text?.toString().orEmpty()
            val result = overlaySignature(inputFile!!, signatureFile!!, outputFile, applyToAllPages, pageInputText, positionIndex)

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
                    HistoryItem(fileName = it.name, filePath = it.absolutePath, toolName = "Signature", fileSize = it.length())
                )
            }
            result.onFailure {
                Toast.makeText(requireContext(), getString(R.string.error_processing, it.message), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun overlaySignature(
        pdfFile: File,
        sigFile: File,
        outputFile: File,
        allPages: Boolean,
        pageRangeText: String,
        posIdx: Int
    ): Result<File> {
        return withContext(Dispatchers.IO) {
            try {
                val prefs = AppPreferences(requireContext())
                val defaultAuthor = prefs.getDefaultAuthor().ifBlank { null }
                val reader = PdfReader(pdfFile)
                val writer = PdfWriter(outputFile)
                val pdfDoc = PdfDocument(reader, writer)
                defaultAuthor?.let { pdfDoc.documentInfo.author = it }
                val document = Document(pdfDoc)

                val imgData = try {
                    com.itextpdf.io.image.ImageDataFactory.create(sigFile.absolutePath)
                } catch (_: Exception) {
                    val bmp = BitmapFactory.decodeFile(sigFile.absolutePath)
                    val bos = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
                    bmp.recycle()
                    com.itextpdf.io.image.ImageDataFactory.create(bos.toByteArray())
                }

                val totalPages = pdfDoc.numberOfPages
                val targetPages = if (allPages) {
                    (1..totalPages).toList()
                } else {
                    parsePageRanges(pageRangeText, totalPages)
                }

                for (pageNum in targetPages) {
                    if (pageNum !in 1..totalPages) continue
                    val sigImg = PdfImage(imgData)
                    val pageSize = pdfDoc.getPage(pageNum).pageSize
                    val imgW = pageSize.width * 0.28f
                    val imgH = sigImg.imageHeight * (imgW / sigImg.imageWidth)
                    val margin = 36f

                    // Positions: 0=Top Left, 1=Top Center, 2=Top Right, 3=Center, 4=Bottom Left, 5=Bottom Center, 6=Bottom Right
                    val x = when (posIdx) {
                        0, 4 -> margin // Left
                        1, 3, 5 -> (pageSize.width - imgW) / 2f // Center
                        else -> pageSize.width - imgW - margin // Right
                    }
                    val y = when (posIdx) {
                        0, 1, 2 -> pageSize.height - imgH - margin // Top
                        3 -> (pageSize.height - imgH) / 2f // Center
                        else -> margin // Bottom
                    }

                    sigImg.setFixedPosition(pageNum, x, y)
                    sigImg.scaleToFit(imgW, imgH)
                    document.add(sigImg)
                }

                document.close()
                Result.success(outputFile)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
