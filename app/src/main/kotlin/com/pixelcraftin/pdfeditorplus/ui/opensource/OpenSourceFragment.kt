package com.pixelcraftin.pdfeditorplus.ui.opensource

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.pixelcraftin.pdfeditorplus.R
import com.pixelcraftin.pdfeditorplus.adapter.LibraryAdapter
import com.pixelcraftin.pdfeditorplus.data.model.OpenSourceLibrary
import com.pixelcraftin.pdfeditorplus.databinding.FragmentOpensourceBinding

class OpenSourceFragment : Fragment() {

    private var _binding: FragmentOpensourceBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOpensourceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }

        val libraries = listOf(
            OpenSourceLibrary(
                "iText 7 Community", "PDF manipulation engine (merge, split, rotate, watermark, protect, metadata)",
                "AGPL v3", "https://itextpdf.com",
                R.drawable.ic_merge, R.drawable.bg_icon_blue, R.color.icon_blue
            ),
            OpenSourceLibrary(
                "PdfiumAndroid", "PDF page rendering for thumbnails and rearrange",
                "Apache 2.0", "https://github.com/nicbell/PdfiumAndroid",
                R.drawable.ic_pdf_to_image, R.drawable.bg_icon_teal, R.color.icon_teal
            ),
            OpenSourceLibrary(
                "ML Kit Text Recognition", "On-device OCR for PDF to Text",
                "Apache 2.0", "https://developers.google.com/ml-kit",
                R.drawable.ic_pdf_to_text, R.drawable.bg_icon_orange, R.color.icon_orange
            ),
            OpenSourceLibrary(
                "uCrop", "Image cropping for round/rounded corner crop",
                "Apache 2.0", "https://github.com/Yalantis/uCrop",
                R.drawable.ic_round_crop, R.drawable.bg_icon_orange, R.color.icon_orange
            ),
            OpenSourceLibrary(
                "Coil", "Image loading library for Android",
                "Apache 2.0", "https://coil-kt.github.io/coil/",
                R.drawable.ic_image_to_pdf, R.drawable.bg_icon_teal, R.color.icon_teal
            ),
            OpenSourceLibrary(
                "AndroidX Navigation", "Fragment navigation component",
                "Apache 2.0", "https://developer.android.com/jetpack/androidx/releases/navigation",
                R.drawable.ic_nav_tools, R.drawable.bg_icon_blue, R.color.icon_blue
            ),
            OpenSourceLibrary(
                "Room Database", "Local persistence for history",
                "Apache 2.0", "https://developer.android.com/training/data-storage/room",
                R.drawable.ic_clock, R.drawable.bg_icon_purple, R.color.icon_purple
            ),
            OpenSourceLibrary(
                "DataStore Preferences", "Settings persistence",
                "Apache 2.0", "https://developer.android.com/topic/libraries/architecture/datastore",
                R.drawable.ic_settings, R.drawable.bg_icon_blue, R.color.icon_blue
            ),
            OpenSourceLibrary(
                "Material Components", "Material Design UI widgets",
                "Apache 2.0", "https://github.com/material-components/material-components-android",
                R.drawable.ic_info, R.drawable.bg_icon_blue, R.color.icon_blue
            ),
            OpenSourceLibrary(
                "Kotlin Coroutines", "Asynchronous processing",
                "Apache 2.0", "https://kotlinlang.org/docs/coroutines-guide.html",
                R.drawable.ic_file, R.drawable.bg_icon_purple, R.color.icon_purple
            )
        )

        val adapter = LibraryAdapter()
        binding.rvLibraries.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLibraries.adapter = adapter
        adapter.submitList(libraries)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
