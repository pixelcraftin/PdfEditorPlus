package com.pixelcraftin.pdfeditorplus.ui.documenteditor

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.pixelcraftin.pdfeditorplus.databinding.DialogSignaturePadBinding

class SignaturePadDialog : BottomSheetDialogFragment() {

    private var _binding: DialogSignaturePadBinding? = null
    private val binding get() = _binding!!

    var onSignatureApplied: ((Bitmap) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, com.pixelcraftin.pdfeditorplus.R.style.TransparentBottomSheetDialogTheme)
    }

    override fun onStart() {
        super.onStart()
        dialog?.let { dlg ->
            val bottomSheet = dlg.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.apply {
                background = null
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                outlineProvider = null
            }
            dlg.window?.apply {
                setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                setDimAmount(0.6f)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogSignaturePadBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnClearSignature.setOnClickListener {
            binding.signatureDrawingView.clear()
        }

        binding.btnCancelSignature.setOnClickListener {
            dismiss()
        }

        binding.btnApplySignature.setOnClickListener {
            val bitmap = binding.signatureDrawingView.getSignatureBitmap()
            if (bitmap != null) {
                onSignatureApplied?.invoke(bitmap)
                dismiss()
            } else {
                Toast.makeText(requireContext(), "Please draw a signature first", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): SignaturePadDialog = SignaturePadDialog()
    }
}
