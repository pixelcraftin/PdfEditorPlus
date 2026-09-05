package com.pixelcraftin.pdfeditorplus.ui.documenteditor

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.pixelcraftin.pdfeditorplus.R
import com.pixelcraftin.pdfeditorplus.databinding.DialogTextAnnotationBinding

class TextAnnotationDialog : BottomSheetDialogFragment() {

    private var _binding: DialogTextAnnotationBinding? = null
    private val binding get() = _binding!!

    var existingTextItem: TextItem? = null
    var onTextApplied: ((TextItem) -> Unit)? = null
    var onTextDeleted: ((TextItem) -> Unit)? = null

    private var selectedColor: Int = Color.BLACK
    private var selectedSizeSp: Float = 24f

    private val paletteColors = listOf(
        Color.parseColor("#000000"), // Black
        Color.parseColor("#FFFFFF"), // White
        Color.parseColor("#EF5350"), // Red
        Color.parseColor("#5C7CFA"), // Blue
        Color.parseColor("#00C9A7"), // Teal
        Color.parseColor("#66BB6A"), // Green
        Color.parseColor("#FFA040"), // Orange
        Color.parseColor("#AB47BC"), // Purple
        Color.parseColor("#FFD600"), // Yellow
        Color.parseColor("#37474F")  // Dark Slate
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.TransparentBottomSheetDialogTheme)
    }

    override fun onStart() {
        super.onStart()
        dialog?.let { dlg ->
            val bottomSheet = dlg.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.apply {
                background = null
                setBackgroundColor(Color.TRANSPARENT)
                outlineProvider = null
            }
            dlg.window?.apply {
                setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
                setDimAmount(0.6f)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogTextAnnotationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val item = existingTextItem
        if (item != null) {
            binding.tvDialogTitle.text = "Edit Text"
            binding.etAnnotationText.setText(item.text)
            selectedColor = item.textColor
            selectedSizeSp = item.textSize
            binding.btnDeleteText.visibility = View.VISIBLE
        } else {
            binding.tvDialogTitle.text = "Add Text"
            binding.btnDeleteText.visibility = View.GONE
        }

        binding.seekTextSize.progress = selectedSizeSp.toInt().coerceIn(12, 72)
        binding.tvTextSizeLabel.text = "Size: ${selectedSizeSp.toInt()}sp"

        binding.seekTextSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                selectedSizeSp = progress.toFloat().coerceAtLeast(12f)
                binding.tvTextSizeLabel.text = "Size: ${progress}sp"
                updatePreview()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.etAnnotationText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updatePreview()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        setupColorPalette()
        updatePreview()

        binding.btnCancelAnnotation.setOnClickListener { dismiss() }

        binding.btnDeleteText.setOnClickListener {
            item?.let { onTextDeleted?.invoke(it) }
            dismiss()
        }

        binding.btnAddAnnotation.setOnClickListener {
            val text = binding.etAnnotationText.text?.toString()?.trim()
            if (text.isNullOrBlank()) {
                Toast.makeText(requireContext(), "Please enter some text", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val resultItem = item?.apply {
                this.text = text
                this.textSize = selectedSizeSp
                this.textColor = selectedColor
                this.isSelected = true
            } ?: TextItem(
                text = text,
                textSize = selectedSizeSp,
                textColor = selectedColor,
                x = 0.5f,
                y = 0.5f,
                isSelected = true
            )

            onTextApplied?.invoke(resultItem)
            dismiss()
        }

        binding.etAnnotationText.requestFocus()
    }

    private fun updatePreview() {
        val currentText = binding.etAnnotationText.text?.toString()
        binding.tvTextPreview.text = if (currentText.isNullOrBlank()) "Preview: Text" else currentText
        binding.tvTextPreview.setTextColor(selectedColor)
        binding.tvTextPreview.textSize = (selectedSizeSp * 0.8f).coerceIn(14f, 32f)

        // Ensure preview background has high contrast with selectedColor
        val isColorDark = getLuminance(selectedColor) < 0.35f
        if (isColorDark) {
            binding.previewContainer.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F1F5F9"))
        } else {
            binding.previewContainer.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1E293B"))
        }
    }

    private fun getLuminance(color: Int): Float {
        val r = Color.red(color) / 255f
        val g = Color.green(color) / 255f
        val b = Color.blue(color) / 255f
        return 0.299f * r + 0.587f * g + 0.114f * b
    }

    private fun setupColorPalette() {
        val container = binding.colorPaletteContainer
        container.removeAllViews()

        for (color in paletteColors) {
            val sizePx = (36 * resources.displayMetrics.density).toInt()
            val marginPx = (4 * resources.displayMetrics.density).toInt()

            val frame = FrameLayout(requireContext()).apply {
                layoutParams = ViewGroup.MarginLayoutParams(sizePx, sizePx).apply {
                    setMargins(marginPx, marginPx, marginPx, marginPx)
                }
            }

            val circleView = View(requireContext()).apply {
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    setStroke((2 * resources.displayMetrics.density).toInt(), if (color == Color.WHITE) Color.LTGRAY else Color.TRANSPARENT)
                }
            }
            frame.addView(circleView)

            if (color == selectedColor) {
                val check = ImageView(requireContext()).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        (20 * resources.displayMetrics.density).toInt(),
                        (20 * resources.displayMetrics.density).toInt()
                    ).apply {
                        gravity = android.view.Gravity.CENTER
                    }
                    setImageResource(R.drawable.ic_check)
                    setColorFilter(if (color == Color.WHITE || color == Color.parseColor("#FFD600")) Color.BLACK else Color.WHITE)
                }
                frame.addView(check)
            }

            frame.setOnClickListener {
                selectedColor = color
                setupColorPalette()
                updatePreview()
            }

            container.addView(frame)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(item: TextItem? = null): TextAnnotationDialog {
            return TextAnnotationDialog().apply {
                existingTextItem = item
            }
        }
    }
}
