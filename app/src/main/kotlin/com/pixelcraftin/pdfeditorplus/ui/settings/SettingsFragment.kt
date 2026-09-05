package com.pixelcraftin.pdfeditorplus.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.pixelcraftin.pdfeditorplus.MainActivity
import com.pixelcraftin.pdfeditorplus.R
import com.pixelcraftin.pdfeditorplus.data.db.HistoryDatabase
import com.pixelcraftin.pdfeditorplus.data.prefs.AppPreferences
import com.pixelcraftin.pdfeditorplus.databinding.FragmentSettingsBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: AppPreferences

    private val themeOptions = arrayOf("System", "Light", "Dark")
    private val limitOptions = arrayOf("10 Files", "25 Files", "50 Files", "100 Files", "Unlimited")
    private val limitValues = intArrayOf(10, 25, 50, 100, 0)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        com.pixelcraftin.pdfeditorplus.util.ViewUtils.startPulseGlow(binding.dotIndicator)
        prefs = AppPreferences(requireContext())
        setupThemeSpinner()
        setupHistoryLimitSpinner()
        observePrefs()
        setupListeners()
    }

    private var isUserInteractingWithTheme = false

    private fun setupThemeSpinner() {
        val currentTheme = prefs.getThemeSync()
        val currentIdx = themeOptions.indexOf(currentTheme).coerceAtLeast(0)

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, themeOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerTheme.adapter = adapter
        binding.spinnerTheme.setSelection(currentIdx, false)

        binding.spinnerTheme.setOnTouchListener { _, _ ->
            isUserInteractingWithTheme = true
            false
        }

        binding.spinnerTheme.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!isUserInteractingWithTheme) return
                val theme = themeOptions[position]
                if (theme != prefs.getThemeSync()) {
                    lifecycleScope.launch {
                        prefs.setTheme(theme)
                        (requireActivity() as? MainActivity)?.applyTheme(theme)
                    }
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun setupHistoryLimitSpinner() {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, limitOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerHistoryLimit.adapter = adapter
        binding.spinnerHistoryLimit.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                lifecycleScope.launch { prefs.setHistoryLimit(limitValues[position]) }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun observePrefs() {
        viewLifecycleOwner.lifecycleScope.launch {
            prefs.autoWipeFlow.collectLatest { binding.switchAutoWipe.isChecked = it }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            prefs.historyLimitFlow.collectLatest { limit ->
                val idx = limitValues.indexOf(limit)
                if (idx >= 0) binding.spinnerHistoryLimit.setSelection(idx)
            }
        }
    }

    private fun setupListeners() {
        binding.switchAutoWipe.setOnCheckedChangeListener { _, checked ->
            lifecycleScope.launch { prefs.setAutoWipe(checked) }
        }
        binding.rowClearHistory.setOnClickListener {
            lifecycleScope.launch {
                val dao = HistoryDatabase.getInstance(requireContext()).historyDao()
                val count = dao.count()
                AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.clear_history_confirm))
                    .setMessage(getString(R.string.clear_history_confirm_msg, count))
                    .setPositiveButton(getString(R.string.ok)) { _, _ ->
                        lifecycleScope.launch { dao.deleteAll() }
                    }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
            }
        }
        binding.rowAbout.setOnClickListener {
            findNavController().navigate(R.id.aboutFragment)
        }
        binding.rowOtherApps.setOnClickListener {
            val url = "https://play.google.com/store/apps/dev?id=4643660879459504423"
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: Exception) {
                android.widget.Toast.makeText(requireContext(), "Could not open browser", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        binding.rowPrivacyPolicy.setOnClickListener {
            val url = getString(R.string.privacy_policy_url)
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: Exception) {
                android.widget.Toast.makeText(requireContext(), "Could not open browser", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        binding.rowReportIssue.setOnClickListener {
            val url = getString(R.string.report_issue_url)
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: Exception) {
                android.widget.Toast.makeText(requireContext(), "Could not open browser", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
