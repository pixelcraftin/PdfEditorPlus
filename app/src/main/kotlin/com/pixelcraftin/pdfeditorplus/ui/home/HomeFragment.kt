package com.pixelcraftin.pdfeditorplus.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.pixelcraftin.pdfeditorplus.R
import com.pixelcraftin.pdfeditorplus.adapter.RecentFilesAdapter
import com.pixelcraftin.pdfeditorplus.adapter.ToolGridAdapter
import com.pixelcraftin.pdfeditorplus.databinding.FragmentHomeBinding
import com.pixelcraftin.pdfeditorplus.util.FileUtils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        com.pixelcraftin.pdfeditorplus.util.ViewUtils.startPulseGlow(binding.dotIndicator)
        setupRecentFiles()
        setupQuickTools()
        setupCoreTools()
        setupListeners()
    }

    private fun setupRecentFiles() {
        val adapter = RecentFilesAdapter { item ->
            FileUtils.openFile(requireContext(), java.io.File(item.filePath))
        }
        binding.rvRecentFiles.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRecentFiles.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.recentFiles.collectLatest { files ->
                adapter.submitList(files)
                if (files.isEmpty()) {
                    binding.tvNoRecentFiles.visibility = View.VISIBLE
                    binding.rvRecentFiles.visibility = View.GONE
                } else {
                    binding.tvNoRecentFiles.visibility = View.GONE
                    binding.rvRecentFiles.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun setupQuickTools() {
        val adapter = ToolGridAdapter { tool ->
            findNavController().navigate(tool.navActionId)
        }
        binding.rvQuickTools.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvQuickTools.adapter = adapter
        adapter.submitList(viewModel.quickTools)
    }

    private fun setupCoreTools() {
        val adapter = ToolGridAdapter { tool ->
            findNavController().navigate(tool.navActionId)
        }
        binding.rvCoreTools.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvCoreTools.adapter = adapter
        adapter.submitList(viewModel.coreTools)
    }

    private fun setupListeners() {
        binding.tvViewAll.setOnClickListener {
            findNavController().navigate(R.id.historyFragment)
        }
        binding.cardMoreTools.setOnClickListener {
            findNavController().navigate(R.id.toolsFragment)
        }
        binding.btnThemeToggle.setOnClickListener {
            // Toggle theme via SettingsViewModel / prefs
            val isDark = resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            (requireActivity() as? com.pixelcraftin.pdfeditorplus.MainActivity)?.apply {
                lifecycleScope.launch {
                    val prefs = com.pixelcraftin.pdfeditorplus.data.prefs.AppPreferences(requireContext())
                    val newTheme = if (isDark) "Light" else "Dark"
                    prefs.setTheme(newTheme)
                    applyTheme(newTheme)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
