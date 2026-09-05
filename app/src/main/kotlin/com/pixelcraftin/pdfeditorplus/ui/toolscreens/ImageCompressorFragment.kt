package com.pixelcraftin.pdfeditorplus.ui.toolscreens

import android.graphics.Bitmap
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.google.android.material.card.MaterialCardView
import com.pixelcraftin.pdfeditorplus.R
import com.pixelcraftin.pdfeditorplus.data.db.HistoryDatabase
import com.pixelcraftin.pdfeditorplus.data.model.HistoryItem
import com.pixelcraftin.pdfeditorplus.databinding.FragmentToolImageCompressorBinding
import com.pixelcraftin.pdfeditorplus.util.FileUtils
import com.pixelcraftin.pdfeditorplus.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class CompressImageItem(
    val uri: Uri,
    val name: String,
    val size: Long,
    var rotationDegrees: Int = 0
)

class ImageCompressorFragment : Fragment() {

    private var _binding: FragmentToolImageCompressorBinding? = null
    private val binding get() = _binding!!

    private val selectedImages = mutableListOf<CompressImageItem>()
    private lateinit var galleryAdapter: ImageCompressGalleryAdapter

    private var compressionPercent = 30
    private var formatIndex = 0
    private val formats = listOf(Bitmap.CompressFormat.JPEG, Bitmap.CompressFormat.PNG, Bitmap.CompressFormat.WEBP_LOSSY)
    private val formatNames = listOf("JPG", "PNG", "WEBP")
    private var lastCompressedFiles = listOf<File>()

    private val pickMultipleImages = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            addSelectedUris(uris)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentToolImageCompressorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvToolTitle.text = getString(R.string.tool_image_compressor)
        binding.tvToolDesc.text = getString(R.string.desc_image_compressor)
        binding.tvPickerHint.text = getString(R.string.tap_to_pick_image)

        setupGallery()
        setupOptions()
        setupListeners()

        val preselectedUri = arguments?.getParcelable<Uri>("preselected_uri")
            ?: arguments?.getString("preselected_uri_string")?.let { Uri.parse(it) }
        preselectedUri?.let { addSelectedUris(listOf(it)) }
    }

    private fun addSelectedUris(uris: List<Uri>) {
        for (uri in uris) {
            val name = FileUtils.getFileName(requireContext(), uri)
            val size = FileUtils.getFileSize(requireContext(), uri)
            selectedImages.add(CompressImageItem(uri, name, size, 0))
        }
        galleryAdapter.notifyDataSetChanged()
        updateSelectionUI()
    }

    private fun updateSelectionUI() {
        if (selectedImages.isNotEmpty()) {
            binding.tvPickerHint.visibility = View.GONE
            binding.tvSelectedFileName.visibility = View.VISIBLE
            binding.tvSelectedFileName.text = "${selectedImages.size} image(s) selected"
        } else {
            binding.tvPickerHint.visibility = View.VISIBLE
            binding.tvSelectedFileName.visibility = View.GONE
        }
    }

    private fun setupGallery() {
        galleryAdapter = ImageCompressGalleryAdapter(
            items = selectedImages,
            onRotate = { position ->
                val item = selectedImages[position]
                item.rotationDegrees = (item.rotationDegrees + 90) % 360
                galleryAdapter.notifyItemChanged(position)
            },
            onRemove = { position ->
                selectedImages.removeAt(position)
                galleryAdapter.notifyItemRemoved(position)
                galleryAdapter.notifyItemRangeChanged(position, selectedImages.size)
                updateSelectionUI()
            }
        )

        val galleryRv = RecyclerView(requireContext()).apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = galleryAdapter
            setPadding(0, 0, 0, 16)
            clipToPadding = false
        }
        binding.optionsContainer.addView(galleryRv, 0)
    }

    private fun setupOptions() {
        val container = binding.optionsContainer

        val compressionLabel = TextView(requireContext()).apply {
            text = formatCompressionLabel(compressionPercent)
            textSize = 14f
            setTextColor(resources.getColor(R.color.text_primary, null))
            setPadding(0, 8, 0, 8)
            id = View.generateViewId()
        }
        val seekBar = SeekBar(requireContext()).apply {
            max = 100
            progress = compressionPercent
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        container.addView(compressionLabel)
        container.addView(seekBar)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                compressionPercent = progress
                compressionLabel.text = formatCompressionLabel(progress)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        val formatLabel = TextView(requireContext()).apply {
            text = getString(R.string.output_format)
            textSize = 14f
            setTextColor(resources.getColor(R.color.text_primary, null))
            setPadding(0, 16, 0, 8)
        }
        val spinner = Spinner(requireContext()).apply {
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        container.addView(formatLabel)
        container.addView(spinner)
        spinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, formatNames)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) { formatIndex = pos }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun formatCompressionLabel(percent: Int): String {
        return when (percent) {
            0 -> "Compression: 0% (Original Quality)"
            100 -> "Compression: 100% (Smallest Size)"
            else -> "Compression: $percent%"
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.cardFilePicker.setOnClickListener { pickMultipleImages.launch("image/*") }
        binding.btnProcess.setOnClickListener { processFiles() }
    }

    private fun processFiles() {
        if (selectedImages.isEmpty()) {
            Toast.makeText(requireContext(), R.string.err_no_file, Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            binding.btnProcess.isEnabled = false
            binding.cardResult.visibility = View.GONE

            val ext = when (formats[formatIndex]) {
                Bitmap.CompressFormat.PNG -> ".png"
                Bitmap.CompressFormat.WEBP_LOSSY, Bitmap.CompressFormat.WEBP_LOSSLESS -> ".webp"
                else -> ".jpg"
            }
            val targetQuality = (100 - (compressionPercent * 0.9)).toInt().coerceIn(10, 100)
            val outputFiles = mutableListOf<File>()
            var totalOrigSize = 0L
            var totalCompressedSize = 0L

            val success = withContext(Dispatchers.IO) {
                try {
                    for (item in selectedImages) {
                        totalOrigSize += item.size
                        val outFile = File(
                            FileUtils.getOutputDir(requireContext()),
                            FileUtils.generateOutputName("compressed_${item.name.substringBeforeLast('.')}", ext.removePrefix("."))
                        )
                        val res = ImageUtils.compressImageWithRotation(
                            requireContext(),
                            item.uri,
                            outFile,
                            item.rotationDegrees,
                            targetQuality,
                            formats[formatIndex]
                        )
                        res.onSuccess { f ->
                            outputFiles.add(f)
                            totalCompressedSize += f.length()
                        }
                    }
                    outputFiles.isNotEmpty()
                } catch (e: Exception) {
                    false
                }
            }

            binding.progressBar.visibility = View.GONE
            binding.btnProcess.isEnabled = true

            if (success && outputFiles.isNotEmpty()) {
                lastCompressedFiles = outputFiles
                val firstFile = outputFiles.first()
                val percentSaved = if (totalOrigSize > 0) ((totalOrigSize - totalCompressedSize) * 100 / totalOrigSize).coerceAtLeast(0) else 0

                binding.cardResult.visibility = View.VISIBLE
                binding.tvResultTitle.text = "Compression Complete! (${outputFiles.size} images)"
                binding.tvResultPath.text = "Original: ${FileUtils.formatSize(totalOrigSize)} → Compressed: ${FileUtils.formatSize(totalCompressedSize)} ($percentSaved% reduced)"

                binding.btnDownload.setOnClickListener {
                    if (lastCompressedFiles.size == 1) {
                        FileUtils.promptSaveToDownloads(requireContext(), lastCompressedFiles.first())
                    } else {
                        FileUtils.promptSaveBatchToDownloads(requireContext(), lastCompressedFiles, "compressed_img")
                    }
                }

                binding.btnShare.setOnClickListener { FileUtils.shareFile(requireContext(), firstFile) }
                binding.btnViewFile.setOnClickListener { FileUtils.openFile(requireContext(), firstFile) }

                withContext(Dispatchers.IO) {
                    val dao = HistoryDatabase.getInstance(requireContext()).historyDao()
                    for (f in outputFiles) {
                        dao.insert(
                            HistoryItem(
                                fileName = f.name,
                                filePath = f.absolutePath,
                                toolName = "Image Compressor",
                                fileSize = f.length(),
                                mimeType = FileUtils.getMimeType(f.name)
                            )
                        )
                    }
                }
            } else {
                Toast.makeText(requireContext(), "Compression failed. Please try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class ImageCompressGalleryAdapter(
        private val items: List<CompressImageItem>,
        private val onRotate: (Int) -> Unit,
        private val onRemove: (Int) -> Unit
    ) : RecyclerView.Adapter<ImageCompressGalleryAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivThumb: ImageView = view.findViewById(R.id.ivThumb)
            val tvThumbName: TextView = view.findViewById(R.id.tvThumbName)
            val tvThumbSize: TextView = view.findViewById(R.id.tvThumbSize)
            val btnRotate: View = view.findViewById(R.id.btnRotate)
            val btnRemove: View = view.findViewById(R.id.btnRemove)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_image_compress_thumb, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvThumbName.text = item.name
            holder.tvThumbSize.text = "${FileUtils.formatSize(item.size)} • ${item.rotationDegrees}°"
            holder.ivThumb.rotation = item.rotationDegrees.toFloat()
            holder.ivThumb.load(item.uri)

            holder.btnRotate.setOnClickListener {
                val pos = holder.adapterPosition
                if (pos != RecyclerView.NO_POSITION) onRotate(pos)
            }
            holder.btnRemove.setOnClickListener {
                val pos = holder.adapterPosition
                if (pos != RecyclerView.NO_POSITION) onRemove(pos)
            }
        }

        override fun getItemCount() = items.size
    }
}
