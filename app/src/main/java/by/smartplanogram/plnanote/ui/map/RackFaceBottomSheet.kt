package by.smartplanogram.plnanote.ui.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import by.smartplanogram.plnanote.R
import by.smartplanogram.plnanote.data.CatalogProduct
import by.smartplanogram.plnanote.databinding.BottomSheetRackFaceBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class RackFaceBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetRackFaceBinding? = null
    private val binding get() = _binding!!
    private lateinit var product: CatalogProduct

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val a = requireArguments()
        product = CatalogProduct(
            id = a.getString(ARG_ID).orEmpty(),
            name = a.getString(ARG_NAME).orEmpty(),
            rackId = a.getString(ARG_RACK).orEmpty(),
            shelf = a.getInt(ARG_SHELF, 1),
            x = a.getFloat(ARG_X),
            y = a.getFloat(ARG_Y),
            rackName = a.getString(ARG_RACK_NAME).orEmpty(),
            shelfCount = a.getInt(ARG_SHELVES, 5),
            bay = a.getInt(ARG_BAY, 1),
            rackLengthM = a.getFloat(ARG_LEN, 1.5f),
            rackHeightM = a.getFloat(ARG_HEIGHT, 1.8f),
            rackDepthM = a.getFloat(ARG_DEPTH, 0.65f),
            isDemo = a.getBoolean(ARG_DEMO, true)
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetRackFaceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.rackTitle.text = product.rackName.ifBlank { product.rackId }
        binding.rackSubtitle.text = getString(
            R.string.rack_face_subtitle,
            product.name,
            product.rackId,
            product.shelf,
            product.bay
        )
        binding.rackFace.showProduct(product)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "RackFaceBottomSheet"
        private const val ARG_ID = "id"
        private const val ARG_NAME = "name"
        private const val ARG_RACK = "rack"
        private const val ARG_RACK_NAME = "rack_name"
        private const val ARG_SHELF = "shelf"
        private const val ARG_SHELVES = "shelves"
        private const val ARG_BAY = "bay"
        private const val ARG_X = "x"
        private const val ARG_Y = "y"
        private const val ARG_LEN = "len"
        private const val ARG_HEIGHT = "height"
        private const val ARG_DEPTH = "depth"
        private const val ARG_DEMO = "demo"

        fun newInstance(product: CatalogProduct): RackFaceBottomSheet {
            return RackFaceBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_ID, product.id)
                    putString(ARG_NAME, product.name)
                    putString(ARG_RACK, product.rackId)
                    putString(ARG_RACK_NAME, product.rackName)
                    putInt(ARG_SHELF, product.shelf)
                    putInt(ARG_SHELVES, product.shelfCount)
                    putInt(ARG_BAY, product.bay)
                    putFloat(ARG_X, product.x)
                    putFloat(ARG_Y, product.y)
                    putFloat(ARG_LEN, product.rackLengthM)
                    putFloat(ARG_HEIGHT, product.rackHeightM)
                    putFloat(ARG_DEPTH, product.rackDepthM)
                    putBoolean(ARG_DEMO, product.isDemo)
                }
            }
        }
    }
}
