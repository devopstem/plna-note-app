package by.smartplanogram.plnanote.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import by.smartplanogram.plnanote.R
import by.smartplanogram.plnanote.data.CatalogProduct
import by.smartplanogram.plnanote.databinding.DialogQuantityBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip

class QuantityBottomSheet : BottomSheetDialogFragment() {

    fun interface Listener {
        fun onQuantityChosen(product: CatalogProduct, quantityLabel: String)
    }

    private var _binding: DialogQuantityBinding? = null
    private val binding get() = _binding!!
    private lateinit var product: CatalogProduct
    private var listener: Listener? = null
    private var selectedHint: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        product = CatalogProduct(
            id = requireArguments().getString(ARG_ID).orEmpty(),
            name = requireArguments().getString(ARG_NAME).orEmpty(),
            rackId = requireArguments().getString(ARG_RACK).orEmpty(),
            shelf = requireArguments().getInt(ARG_SHELF, 1),
            x = requireArguments().getFloat(ARG_X),
            y = requireArguments().getFloat(ARG_Y),
            unitHints = requireArguments().getStringArrayList(ARG_HINTS).orEmpty()
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogQuantityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        listener = parentFragment as? Listener ?: activity as? Listener
        binding.quantitySubtitle.text = getString(R.string.quantity_subtitle, product.name)

        val hints = product.unitHints.ifEmpty { listOf("1 шт", "1л", "1кг") }
        hints.forEachIndexed { index, hint ->
            val chip = Chip(requireContext()).apply {
                text = hint
                isCheckable = true
                isClickable = true
                setOnClickListener {
                    selectedHint = hint
                    binding.quantityCustom.setText("")
                }
            }
            binding.quantityChips.addView(chip)
            if (index == 0) {
                chip.isChecked = true
                selectedHint = hint
            }
        }

        binding.btnConfirmQuantity.setOnClickListener {
            val custom = binding.quantityCustom.text?.toString()?.trim().orEmpty()
            val label = custom.ifBlank { selectedHint.orEmpty() }
            if (label.isBlank()) {
                Toast.makeText(requireContext(), R.string.quantity_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            listener?.onQuantityChosen(product, label)
            dismiss()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "QuantityBottomSheet"
        private const val ARG_ID = "id"
        private const val ARG_NAME = "name"
        private const val ARG_RACK = "rack"
        private const val ARG_SHELF = "shelf"
        private const val ARG_X = "x"
        private const val ARG_Y = "y"
        private const val ARG_HINTS = "hints"

        fun newInstance(product: CatalogProduct): QuantityBottomSheet {
            return QuantityBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_ID, product.id)
                    putString(ARG_NAME, product.name)
                    putString(ARG_RACK, product.rackId)
                    putInt(ARG_SHELF, product.shelf)
                    putFloat(ARG_X, product.x)
                    putFloat(ARG_Y, product.y)
                    putStringArrayList(ARG_HINTS, ArrayList(product.unitHints))
                }
            }
        }
    }
}
