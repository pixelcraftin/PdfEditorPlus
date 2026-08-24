package com.pixelcraftin.pdfeditorplus.ui.about

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.pixelcraftin.pdfeditorplus.R
import com.pixelcraftin.pdfeditorplus.databinding.FragmentAboutBinding

class AboutFragment : Fragment() {

    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvAboutDesc.text = androidx.core.text.HtmlCompat.fromHtml(
            getString(R.string.about_desc),
            androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
        )
        binding.tvZeroCloudDesc.text = androidx.core.text.HtmlCompat.fromHtml(
            getString(R.string.zero_cloud_full),
            androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
        )
        binding.tvInstantOfflineDesc.text = androidx.core.text.HtmlCompat.fromHtml(
            getString(R.string.instant_offline_full),
            androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
        )

        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.cardGitHub.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.github_url))))
        }
        binding.cardTwitter.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.twitter_url))))
        }
        binding.rowOpenSource.setOnClickListener { findNavController().navigate(R.id.openSourceFragment) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
