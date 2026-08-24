package com.pixelcraftin.pdfeditorplus.ui.toolscreens

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textview.MaterialTextView
import com.pixelcraftin.pdfeditorplus.R
import com.pixelcraftin.pdfeditorplus.data.db.HistoryDatabase
import com.pixelcraftin.pdfeditorplus.data.model.HistoryItem
import com.pixelcraftin.pdfeditorplus.data.prefs.AppPreferences
import com.pixelcraftin.pdfeditorplus.databinding.FragmentToolImageToPdfBinding
import com.pixelcraftin.pdfeditorplus.ui.adapter.SelectedImageAdapter
import com.pixelcraftin.pdfeditorplus.ui.adapter.SelectedImageItem
import com.pixelcraftin.pdfeditorplus.util.FileUtils
import com.pixelcraftin.pdfeditorplus.util.PdfUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ImageToPdfFragment : Fragment() {

    private var _binding: FragmentToolImageToPdfBinding? = null
    private val binding get() = _binding!!

    private var selectedImagesList = mutableListOf<SelectedImageItem>()
    private lateinit var imageAdapter: SelectedImageAdapter

    private var selectedPageSize = "A4"
    private var fitToPage = true

    // Multi-image picker supporting 500+ images without limits
    private val pickImagesLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri>? ->
        if (!uris.isNullOrEmpty()) {
            addSelectedUris(uris)
        }
    }

    // Secondary fallback for file manager selection
    private val openDocsLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri>? ->
        if (!uris.isNullOrEmpty()) {
            addSelectedUris(uris)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentToolImageToPdfBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvToolTitle.text = getString(R.string.tool_image_to_pdf)
        binding.tvToolDesc.text = getString(R.string.desc_image_to_pdf)
        binding.tvPickerHint.text = getString(R.string.tap_to_pick_images)

        setupRecyclerView()
        setupOptions()
        setupListeners()

        // Handle preselected URI if passed via navigation
        arguments?.getString("preselected_uri")?.let { uriStr ->
            try {
                val uri = Uri.parse(uriStr)
                addSelectedUris(listOf(uri))
            } catch (_: Exception) {}
        }
    }

    private fun setupRecyclerView() {
        imageAdapter = SelectedImageAdapter { itemToRemove ->
            removeImageItem(itemToRemove)
        }

        binding.rvSelectedImages.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = imageAdapter
            setHasFixedSize(true)
            setItemViewCacheSize(20)
        }
    }

    private fun setupOptions() {
        val fitSwitch = SwitchMaterial(requireContext()).apply {
            text = getString(R.string.fit_to_page)
            isChecked = true
            setOnCheckedChangeListener { _, checked -> fitToPage = checked }
        }

        val sizes = arrayOf("A4", "Letter", "Only Image")
        val pageSizeSpinner = android.widget.Spinner(requireContext()).apply {
            id = View.generateViewId()
            adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, sizes)
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    selectedPageSize = sizes[position]
                    fitSwitch.visibility = if (selectedPageSize == "Only Image") View.GONE else View.VISIBLE
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
        }

        val labelPage = MaterialTextView(requireContext()).apply {
            text = getString(R.string.page_size)
            setTextColor(resources.getColor(R.color.text_primary, null))
            textSize = 14f
            setPadding(0, 8, 0, 4)
        }

        binding.optionsContainer.addView(labelPage)
        binding.optionsContainer.addView(pageSizeSpinner)
        binding.optionsContainer.addView(fitSwitch)
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }

        binding.cardFilePicker.setOnClickListener {
            launchImagePicker()
        }

        binding.btnClearAllImages.setOnClickListener {
            selectedImagesList.clear()
            imageAdapter.submitList(emptyList())
            updateUIState()
        }

        binding.btnProcess.setOnClickListener { processFiles() }
    }

    private fun launchImagePicker() {
        try {
            pickImagesLauncher.launch("image/*")
        } catch (_: Exception) {
            try {
                openDocsLauncher.launch(arrayOf("image/*"))
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Unable to open image picker: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun addSelectedUris(uris: List<Uri>) {
        viewLifecycleOwner.lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            val context = requireContext().applicationContext

            // Process image URIs and metadata (file name, size) asynchronously on Dispatchers.IO
            val newItems = withContext(Dispatchers.IO) {
                uris.map { uri ->
                    val name = FileUtils.getFileName(context, uri)
                    val size = FileUtils.getFileSize(context, uri)
                    SelectedImageItem(uri, name, size)
                }
            }

            val updatedList = selectedImagesList.toMutableList()
            // Avoid duplicate additions
            val existingUris = updatedList.map { it.uri }.toSet()
            val filteredNew = newItems.filter { it.uri !in existingUris }
            updatedList.addAll(filteredNew)

            selectedImagesList = updatedList
            imageAdapter.submitList(updatedList)
            updateUIState()

            binding.progressBar.visibility = View.GONE
        }
    }

    private fun removeImageItem(item: SelectedImageItem) {
        val updatedList = selectedImagesList.toMutableList()
        updatedList.remove(item)
        selectedImagesList = updatedList
        imageAdapter.submitList(updatedList)
        updateUIState()
    }

    private fun updateUIState() {
        val count = selectedImagesList.size
        if (count > 0) {
            binding.containerSelectedImages.visibility = View.VISIBLE
            binding.tvSelectedImagesCount.text = "$count ${if (count == 1) "image" else "images"} selected"
            binding.tvPickerHint.text = "+ Tap to select more images"
            binding.tvSelectedFileName.visibility = View.GONE
        } else {
            binding.containerSelectedImages.visibility = View.GONE
            binding.tvPickerHint.text = getString(R.string.tap_to_pick_images)
            binding.tvSelectedFileName.visibility = View.GONE
        }
    }

    private fun processFiles() {
        if (selectedImagesList.isEmpty()) {
            Toast.makeText(requireContext(), R.string.err_no_images, Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            binding.btnProcess.isEnabled = false
            binding.cardResult.visibility = View.GONE

            val uris = selectedImagesList.map { it.uri }
            val context = requireContext().applicationContext

            val tempFiles = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    FileUtils.uriToFile(context, uri, ".img")
                }
            }

            if (tempFiles.isEmpty()) {
                binding.progressBar.visibility = View.GONE
                binding.btnProcess.isEnabled = true
                Toast.makeText(requireContext(), R.string.err_no_images, Toast.LENGTH_SHORT).show()
                return@launch
            }

            val prefs = AppPreferences(requireContext())
            val defaultAuthor = prefs.getDefaultAuthor().ifBlank { null }

            val outputDir = FileUtils.getOutputDir(requireContext())
            val outputName = FileUtils.generateOutputName("image_to_pdf")
            val outputFile = File(outputDir, outputName)

            val result = PdfUtils.imagesToPdf(
                imageFiles = tempFiles,
                outputFile = outputFile,
                pageSizeOption = selectedPageSize,
                fitToPage = fitToPage,
                defaultAuthor = defaultAuthor
            )

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
                    toolName = "Image to PDF",
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
