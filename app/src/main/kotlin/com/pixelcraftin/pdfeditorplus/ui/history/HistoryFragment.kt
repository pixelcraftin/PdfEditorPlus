package com.pixelcraftin.pdfeditorplus.ui.history

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.pixelcraftin.pdfeditorplus.R
import com.pixelcraftin.pdfeditorplus.adapter.HistoryAdapter
import com.pixelcraftin.pdfeditorplus.databinding.FragmentHistoryBinding
import com.pixelcraftin.pdfeditorplus.util.FileUtils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HistoryViewModel by viewModels()
    private lateinit var adapter: HistoryAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        com.pixelcraftin.pdfeditorplus.util.ViewUtils.startPulseGlow(binding.dotIndicator)
        setupRecyclerView()
        setupSearch()
        setupListeners()
        observeHistory()
    }

    private fun setupRecyclerView() {
        adapter = HistoryAdapter(
            onItemClick = { item -> FileUtils.openFile(requireContext(), File(item.filePath)) },
            onDownloadClick = { item ->
                val src = File(item.filePath)
                val dest = File(FileUtils.getPublicDownloadsDir(), item.fileName)
                if (src.exists()) {
                    src.copyTo(dest, overwrite = true)
                    com.google.android.material.snackbar.Snackbar.make(
                        binding.root, getString(R.string.msg_saved_to_downloads), 2000
                    ).show()
                }
            }
        )
        binding.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHistory.adapter = adapter
    }

    private fun setupSearch() {
        binding.etSearchHistory.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.setSearch(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupListeners() {
        binding.btnClearAll.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val count = viewModel.count()
                AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.clear_history_confirm))
                    .setMessage(getString(R.string.clear_history_confirm_msg, count))
                    .setPositiveButton(getString(R.string.ok)) { _, _ -> viewModel.clearAll() }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
            }
        }
    }

    private fun observeHistory() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.filteredItems.collectLatest { items ->
                adapter.submitList(items)
                val isEmpty = items.isEmpty()
                binding.rvHistory.visibility = if (isEmpty) View.GONE else View.VISIBLE
                binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
