package com.pixelcraftin.pdfeditorplus.ui.toolscreens

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.pixelcraftin.pdfeditorplus.R
import com.pixelcraftin.pdfeditorplus.data.db.HistoryDatabase
import com.pixelcraftin.pdfeditorplus.data.model.HistoryItem
import com.pixelcraftin.pdfeditorplus.databinding.FragmentToolImageToPdfBinding
import com.pixelcraftin.pdfeditorplus.util.FileUtils
import com.pixelcraftin.pdfeditorplus.util.PdfUtils
import kotlinx.coroutines.launch
import java.io.File

class MetadataFragment : Fragment() {

    private var _binding: FragmentToolImageToPdfBinding? = null
    private val binding get() = _binding!!
    private var inputUri: Uri? = null
    private var inputFile: File? = null
    private lateinit var etTitle: EditText
    private lateinit var etAuthor: EditText
    private lateinit var etSubject: EditText
    private lateinit var etKeywords: EditText
    private lateinit var etCreator: EditText

    private val pickPdf = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            inputUri = it
            inputFile = FileUtils.uriToFile(requireContext(), it)
            binding.tvPickerHint.visibility = View.GONE
            binding.tvSelectedFileName.visibility = View.VISIBLE
            binding.tvSelectedFileName.text = FileUtils.getFileName(requireContext(), it)
            loadMetadata()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentToolImageToPdfBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvToolTitle.text = getString(R.string.tool_metadata)
        binding.tvToolDesc.text = getString(R.string.desc_metadata)
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
        loadMetadata()
    }

    private fun setupOptions() {
        val container = binding.optionsContainer
        val fields = listOf(
            R.string.title to "title",
            R.string.author to "author",
            R.string.subject to "subject",
            R.string.keywords to "keywords",
            R.string.creator to "creator"
        )
        fields.forEachIndexed { index, (labelRes, _) ->
            val label = TextView(requireContext()).apply {
                text = getString(labelRes)
                textSize = 14f
                setPadding(0, if (index == 0) 0 else 16, 0, 8)
            }
            val edit = EditText(requireContext()).apply {
                hint = getString(labelRes)
                id = View.generateViewId()
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            when (labelRes) {
                R.string.title -> etTitle = edit
                R.string.author -> etAuthor = edit
                R.string.subject -> etSubject = edit
                R.string.keywords -> etKeywords = edit
                R.string.creator -> etCreator = edit
            }
            container.addView(label)
            container.addView(edit)
        }
    }

    private fun loadMetadata() {
        inputFile?.let { file ->
            val meta = PdfUtils.readMetadata(file)
            etTitle.setText(meta["title"].orEmpty())
            etSubject.setText(meta["subject"].orEmpty())
            etKeywords.setText(meta["keywords"].orEmpty())
            etCreator.setText(meta["creator"].orEmpty())
            lifecycleScope.launch {
                val author = meta["author"]
                if (!author.isNullOrBlank()) {
                    etAuthor.setText(author)
                } else {
                    val defaultAuthor = com.pixelcraftin.pdfeditorplus.data.prefs.AppPreferences(requireContext()).getDefaultAuthor()
                    etAuthor.setText(defaultAuthor)
                }
            }
        }
    }

    private fun processFile() {
        if (inputFile == null) {
            Toast.makeText(requireContext(), R.string.err_no_file, Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            binding.btnProcess.isEnabled = false
            val prefs = com.pixelcraftin.pdfeditorplus.data.prefs.AppPreferences(requireContext())
            val defaultAuthor = prefs.getDefaultAuthor().ifBlank { null }
            val outputFile = File(FileUtils.getOutputDir(requireContext()), FileUtils.generateOutputName("metadata"))
            val finalAuthor = etAuthor.text.toString().ifBlank { defaultAuthor }
            val result = PdfUtils.editMetadata(
                inputFile!!,
                title = etTitle.text.toString().ifBlank { null },
                author = finalAuthor,
                subject = etSubject.text.toString().ifBlank { null },
                keywords = etKeywords.text.toString().ifBlank { null },
                creator = etCreator.text.toString().ifBlank { null },
                outputFile = outputFile
            )
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
                    HistoryItem(fileName = it.name, filePath = it.absolutePath, toolName = "Metadata", fileSize = it.length())
                )
            }
            result.onFailure {
                Toast.makeText(requireContext(), getString(R.string.error_processing, it.message), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
