package com.pixelcraftin.pdfeditorplus.ui.documenteditor.crop

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.isInvisible
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.pixelcraftin.pdfeditorplus.R
import com.pixelcraftin.pdfeditorplus.databinding.FragmentDocumentCropBinding
import com.pixelcraftin.pdfeditorplus.ui.documenteditor.DocumentEditorFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class DocumentCropFragment : Fragment() {

    private var _binding: FragmentDocumentCropBinding? = null
    private val binding get() = _binding!!

    private val inputUris = mutableListOf<Uri>()
    private val outputCroppedUris = mutableListOf<Uri>()
    private var currentIndex = 0
    private var isSinglePageMode = false
    private var singlePageIndex = 0
    private var currentBitmap: Bitmap? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDocumentCropBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupInsets()
        extractUris()
        setupListeners()
        loadCurrentImage()
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.cropBottomContainer) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.updatePadding(bottom = navBars.bottom)
            insets
        }
    }

    private fun extractUris() {
        isSinglePageMode = arguments?.getBoolean(ARG_SINGLE_PAGE_MODE, false) ?: false
        singlePageIndex = arguments?.getInt(ARG_PAGE_INDEX, 0) ?: 0

        val uriList = arguments?.getParcelableArrayList<Uri>(DocumentEditorFragment.ARG_IMAGE_URIS)
        val uriStringList = arguments?.getStringArrayList(DocumentEditorFragment.ARG_IMAGE_URI_STRINGS)

        val resolved = when {
            !uriList.isNullOrEmpty() -> uriList
            !uriStringList.isNullOrEmpty() -> uriStringList.map { Uri.parse(it) }
            else -> emptyList()
        }

        inputUris.clear()
        outputCroppedUris.clear()
        inputUris.addAll(resolved)

        if (inputUris.isEmpty()) {
            findNavController().popBackStack()
        }
    }

    private fun setupListeners() {
        binding.btnCropBack.setOnClickListener {
            if (!isSinglePageMode && currentIndex > 0) {
                currentIndex--
                if (outputCroppedUris.isNotEmpty()) {
                    outputCroppedUris.removeAt(outputCroppedUris.size - 1)
                }
                loadCurrentImage()
            } else {
                findNavController().popBackStack()
            }
        }

        binding.btnCropPrev.setOnClickListener {
            if (currentIndex > 0) {
                currentIndex--
                if (outputCroppedUris.isNotEmpty()) {
                    outputCroppedUris.removeAt(outputCroppedUris.size - 1)
                }
                loadCurrentImage()
            }
        }

        binding.btnAutoCrop.setOnClickListener {
            binding.documentCropView.autoDetectCropBounds()
            Toast.makeText(requireContext(), "Auto-Crop detected", Toast.LENGTH_SHORT).show()
        }

        binding.btnFullCrop.setOnClickListener {
            binding.documentCropView.resetToFull()
            Toast.makeText(requireContext(), "Reset to full bounds", Toast.LENGTH_SHORT).show()
        }

        binding.btnCropRotate.setOnClickListener {
            binding.documentCropView.rotationAngle += 90
        }

        binding.btnSkipCrop.setOnClickListener {
            if (isSinglePageMode) {
                findNavController().popBackStack()
            } else {
                outputCroppedUris.add(inputUris[currentIndex])
                advanceToNextOrFinish()
            }
        }

        binding.btnNextOrDone.setOnClickListener {
            applyCropAndAdvance()
        }
    }

    private fun loadCurrentImage() {
        if (currentIndex !in inputUris.indices) return

        val uri = inputUris[currentIndex]
        val total = inputUris.size

        if (isSinglePageMode) {
            binding.tvCropTitle.text = "Crop Page ${singlePageIndex + 1}"
            binding.tvCropPageIndicator.text = "Adjust page boundaries"
            binding.btnCropPrev.visibility = View.GONE
            binding.btnSkipCrop.visibility = View.GONE
            binding.btnNextOrDone.setIconResource(R.drawable.ic_check)
            binding.btnNextOrDone.contentDescription = "Apply Crop"
        } else {
            binding.tvCropTitle.text = "Crop Document"
            binding.tvCropPageIndicator.text = "Page ${currentIndex + 1} of $total"
            binding.btnCropPrev.visibility = View.VISIBLE
            binding.btnCropPrev.isInvisible = (currentIndex == 0)
            binding.btnSkipCrop.visibility = View.VISIBLE
            if (currentIndex == total - 1) {
                binding.btnNextOrDone.setIconResource(R.drawable.ic_check)
                binding.btnNextOrDone.contentDescription = "Done & Edit"
            } else {
                binding.btnNextOrDone.setIconResource(R.drawable.ic_chevron_right)
                binding.btnNextOrDone.contentDescription = "Next Page"
            }
        }

        lifecycleScope.launch {
            binding.cropProgressBar.visibility = View.VISIBLE
            val bitmap = withContext(Dispatchers.IO) {
                decodeBitmapFromUri(uri)
            }
            binding.cropProgressBar.visibility = View.GONE

            currentBitmap?.recycle()
            currentBitmap = bitmap

            binding.documentCropView.rotationAngle = 0
            binding.documentCropView.setImageBitmap(bitmap)
        }
    }

    private fun applyCropAndAdvance() {
        val bmp = currentBitmap ?: return
        val corners = binding.documentCropView.getCorners()
        val rotation = binding.documentCropView.rotationAngle

        lifecycleScope.launch {
            binding.cropProgressBar.visibility = View.VISIBLE
            binding.btnNextOrDone.isEnabled = false

            val croppedUri = withContext(Dispatchers.IO) {
                try {
                    val croppedBmp = EdgeDetectionUtils.cropPerspective(bmp, corners, rotation)
                    val cacheFile = File(requireContext().cacheDir, "crop_${System.currentTimeMillis()}_$currentIndex.jpg")
                    FileOutputStream(cacheFile).use { out ->
                        croppedBmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    croppedBmp.recycle()
                    Uri.fromFile(cacheFile)
                } catch (e: Exception) {
                    e.printStackTrace()
                    inputUris[currentIndex]
                }
            }

            binding.cropProgressBar.visibility = View.GONE
            binding.btnNextOrDone.isEnabled = true

            outputCroppedUris.add(croppedUri)
            advanceToNextOrFinish()
        }
    }

    private fun advanceToNextOrFinish() {
        if (isSinglePageMode) {
            val lastCropped = outputCroppedUris.lastOrNull() ?: inputUris[0]
            setFragmentResult(
                REQUEST_KEY_CROP,
                bundleOf(
                    RESULT_CROPPED_URI to lastCropped,
                    RESULT_PAGE_INDEX to singlePageIndex
                )
            )
            findNavController().popBackStack()
        } else {
            if (currentIndex < inputUris.size - 1) {
                currentIndex++
                loadCurrentImage()
            } else {
                // Done with all batch images -> navigate to DocumentEditorFragment
                val bundle = DocumentEditorFragment.createBundle(outputCroppedUris)
                findNavController().navigate(R.id.documentEditorFragment, bundle)
            }
        }
    }

    private fun decodeBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            requireContext().contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
            val reqW = 1440
            val reqH = 2560
            var sample = 1
            if (options.outHeight > reqH || options.outWidth > reqW) {
                val halfH = options.outHeight / 2
                val halfW = options.outWidth / 2
                while ((halfH / sample) >= reqH && (halfW / sample) >= reqW) {
                    sample *= 2
                }
            }
            options.inSampleSize = sample.coerceAtLeast(1)
            options.inJustDecodeBounds = false
            requireContext().contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        currentBitmap?.recycle()
        currentBitmap = null
        _binding = null
    }

    companion object {
        const val ARG_SINGLE_PAGE_MODE = "arg_single_page_mode"
        const val ARG_PAGE_INDEX = "arg_page_index"
        const val REQUEST_KEY_CROP = "request_key_crop"
        const val RESULT_CROPPED_URI = "result_cropped_uri"
        const val RESULT_PAGE_INDEX = "result_page_index"
    }
}
