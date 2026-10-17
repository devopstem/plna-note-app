package by.smartplanogram.plnanote.ui.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import by.smartplanogram.plnanote.MainActivity
import by.smartplanogram.plnanote.R
import by.smartplanogram.plnanote.data.CatalogProduct
import by.smartplanogram.plnanote.data.DemoCatalog
import by.smartplanogram.plnanote.data.ItemOutcome
import by.smartplanogram.plnanote.data.OfflineRepository
import by.smartplanogram.plnanote.data.PurchaseHistoryRepository
import by.smartplanogram.plnanote.data.ShoppingItem
import by.smartplanogram.plnanote.data.ShoppingList
import by.smartplanogram.plnanote.data.ShoppingListRepository
import by.smartplanogram.plnanote.databinding.FragmentMapBinding
import by.smartplanogram.plnanote.nativebridge.NativeMapEngine
import by.smartplanogram.plnanote.ui.applyTopContentInset
import by.smartplanogram.plnanote.ui.map.pdr.FloorConstraint
import by.smartplanogram.plnanote.ui.map.pdr.PdrTracker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import org.json.JSONObject

class MapFragment : Fragment() {
    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private var view3d = false
    private var floorW = 30f
    private var floorH = 25f
    private var mapBearing = 0f
    private var activeListId: String? = null
    private var pickerShownThisVisit = false
    private var lastFaceSheetItemId: String? = null
    private var lastClosedItemId: String? = null
    private var activeRouteItem: ShoppingItem? = null
    private val racks = mutableListOf<RackFootprint>()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var entranceAnchor = Anchor(15f, 24.5f)
    private var cashAnchor = Anchor(15f, 1f)
    private var selectedAnchor = AnchorKind.ENTRANCE

    private val floorConstraint = FloorConstraint(30f, 25f)
    private var pdr: PdrTracker? = null
    private var pdrWanted = false
    private var relocateArmed = false

    private val activityPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        ensurePdr()
        if (granted) {
            pdr?.forceAccelSteps = false
            startPdrInternal()
            return@registerForActivityResult
        }
        // без разрешения — шаги через акселерометр
        if (pdr?.hasSensors() == true) {
            pdr?.forceAccelSteps = true
            startPdrInternal()
        } else {
            binding.switchPdr.isChecked = false
            Toast.makeText(requireContext(), R.string.map_pdr_need_permission, Toast.LENGTH_LONG).show()
        }
    }

    private data class RackFootprint(
        val id: String,
        val name: String,
        val x: Float,
        val y: Float,
        val w: Float,
        val d: Float,
        val shelves: Int
    )

    private data class Anchor(val x: Float, val y: Float)

    private enum class AnchorKind { ENTRANCE, CASH }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.mapRoot.applyTopContentInset(10)
        activeListId = arguments?.getString(ARG_LIST_ID)
            ?: requireActivity().intent?.getStringExtra(EXTRA_ROUTE_LIST_ID)

        selectedAnchor = loadSavedAnchor()

        binding.viewModeToggle.check(R.id.btn_view_2d)
        binding.viewModeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            view3d = checkedId == R.id.btn_view_3d
            NativeMapEngine.nativeSetView3D(view3d)
            refreshDrawList()
        }

        binding.anchorToggle.check(
            if (selectedAnchor == AnchorKind.CASH) R.id.btn_anchor_cash
            else R.id.btn_anchor_entrance
        )
        binding.anchorToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            selectedAnchor = if (checkedId == R.id.btn_anchor_cash) {
                AnchorKind.CASH
            } else {
                AnchorKind.ENTRANCE
            }
            saveAnchor(selectedAnchor)
            applyStartMarker()
            // сменили якорь — точка «я» стартует оттуда
            pdr?.resetTo(currentStart().x, currentStart().y)
            activeRouteItem?.let { focusItem(it) }
            refreshDrawList()
        }

        binding.switchPdr.setOnCheckedChangeListener { _, checked ->
            if (checked) enablePdr() else disablePdr()
        }
        binding.btnPdrRelocate.setOnClickListener { armRelocate() }

        binding.btnZoomIn.setOnClickListener { binding.storeMapView.zoomIn() }
        binding.btnZoomOut.setOnClickListener { binding.storeMapView.zoomOut() }
        binding.btnRotateLeft.setOnClickListener { binding.storeMapView.rotateBy(-45f) }
        binding.btnRotateRight.setOnClickListener { binding.storeMapView.rotateBy(45f) }
        binding.btnFitMap.setOnClickListener { binding.storeMapView.resetView() }
        binding.btnGoLists.setOnClickListener {
            (activity as? MainActivity)?.openTab(R.id.nav_lists)
        }
        binding.btnChangeNote.setOnClickListener { showNotePicker(force = true) }
        binding.btnMarkBought.setOnClickListener { markCurrent(ItemOutcome.BOUGHT) }
        binding.btnMarkSkip.setOnClickListener { markCurrent(ItemOutcome.SKIPPED) }
        binding.btnMarkMissing.setOnClickListener { markCurrent(ItemOutcome.MISSING) }
        binding.btnShowShelf.setOnClickListener { openShelfFace() }
        binding.mapTargetLabel.setOnLongClickListener {
            undoLastClosed()
            true
        }
        binding.mapProgressLabel.setOnLongClickListener {
            undoLastClosed()
            true
        }
    }

    private fun loadMap() {
        viewLifecycleOwner.lifecycleScope.launch {
            val offline = OfflineRepository.get(requireContext())
            val json = offline.mapJsonForSelectedStore()
                ?: requireContext().assets.open("store_map_sample.json")
                    .bufferedReader().use { it.readText() }
            NativeMapEngine.nativeLoadStoreMapJson(json)
            NativeMapEngine.nativeSetView3D(view3d)
            parseMapMeta(json)

            val floor = NativeMapEngine.nativeFloorSize()
            floorW = if (floor.size >= 2) floor[0] else 30f
            floorH = if (floor.size >= 2) floor[1] else 25f

            resolveNoteAndRoute()
            applyStartMarker()
            pdr?.mapBearingDeg = mapBearing
            if (pdrWanted && pdr?.enabled != true) {
                startPdrInternal()
            } else if (pdr?.enabled == true) {
                binding.storeMapView.setMe(pdr!!.x, pdr!!.y, pdr!!.mapHeadingDeg)
            }
            refreshDrawList()
        }
    }

    private fun parseMapMeta(json: String) {
        racks.clear()
        runCatching {
            val root = JSONObject(json)
            val layout = root.optJSONObject("layout")
            mapBearing = layout?.optDouble("bearingDegrees", 0.0)?.toFloat() ?: 0f
            floorW = layout?.optDouble("widthMeters", floorW.toDouble())?.toFloat() ?: floorW
            floorH = layout?.optDouble("heightMeters", floorH.toDouble())?.toFloat() ?: floorH

            for (key in listOf("racks", "coolers", "stands")) {
                val arr = root.optJSONArray(key) ?: continue
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val w = o.optDouble("widthMeters", 1.2).toFloat()
                    val d = o.optDouble("lengthMeters", 0.7).toFloat()
                    val x = o.optDouble("posX", o.optDouble("rx")).toFloat()
                    val y = o.optDouble("posY", o.optDouble("ry")).toFloat()
                    racks += RackFootprint(
                        id = o.optString("id"),
                        name = o.optString("displayName", o.optString("id")),
                        x = x + w * 0.5f,
                        y = y + d * 0.5f,
                        w = w,
                        d = d,
                        shelves = o.optInt("shelfCount", 4)
                    )
                }
            }
            floorConstraint.setFloor(floorW, floorH)
            floorConstraint.setObstacles(emptyList())
            racks.forEach { r ->
                floorConstraint.addRackCentered(r.x, r.y, r.w, r.d)
            }

            val entrances = mutableListOf<Anchor>()
            val cashes = mutableListOf<Anchor>()
            val markers = root.optJSONArray("markers")
            if (markers != null) {
                for (i in 0 until markers.length()) {
                    val o = markers.getJSONObject(i)
                    val type = o.optString("type")
                    val w = o.optDouble("widthMeters", 1.0).toFloat()
                    val d = o.optDouble("depthMeters", 0.6).toFloat()
                    val cx = o.optDouble("posX").toFloat() + w * 0.5f
                    val cy = o.optDouble("posY").toFloat() + d * 0.5f
                    when (type) {
                        "entrance" -> entrances += Anchor(cx, cy)
                        "cash", "cash_register" -> cashes += Anchor(cx, cy)
                    }
                }
            }

            entranceAnchor = entrances.firstOrNull()
                ?: layoutEntrance(layout)
            cashAnchor = if (cashes.isNotEmpty()) {
                Anchor(
                    cashes.map { it.x }.average().toFloat(),
                    cashes.map { it.y }.average().toFloat()
                )
            } else {
                layoutCash(layout)
            }
        }
    }

    /** Fallback из layout.entranceSide / entranceOffsetM, если нет маркера. */
    private fun layoutEntrance(layout: JSONObject?): Anchor {
        val side = layout?.optString("entranceSide", "north") ?: "north"
        val offset = layout?.optDouble("entranceOffsetM", floorW * 0.5)?.toFloat()
            ?: floorW * 0.5f
        val width = layout?.optDouble("entranceWidthM", 5.0)?.toFloat() ?: 5f
        val cx = offset + width * 0.5f
        return when (side.lowercase()) {
            "south" -> Anchor(cx.coerceIn(0.5f, floorW - 0.5f), 0.6f)
            "east" -> Anchor(floorW - 0.6f, offset.coerceIn(0.5f, floorH - 0.5f))
            "west" -> Anchor(0.6f, offset.coerceIn(0.5f, floorH - 0.5f))
            else -> Anchor(cx.coerceIn(0.5f, floorW - 0.5f), floorH - 0.6f)
        }
    }

    private fun layoutCash(layout: JSONObject?): Anchor {
        val side = layout?.optString("cashSide", "south") ?: "south"
        val midX = floorW * 0.5f
        return when (side.lowercase()) {
            "north" -> Anchor(midX, floorH - 0.8f)
            "east" -> Anchor(floorW - 0.8f, floorH * 0.5f)
            "west" -> Anchor(0.8f, floorH * 0.5f)
            else -> Anchor(midX, 0.8f)
        }
    }

    private fun currentStart(): Anchor =
        if (selectedAnchor == AnchorKind.CASH) cashAnchor else entranceAnchor

    private fun applyStartMarker() {
        val start = currentStart()
        val label = getString(
            if (selectedAnchor == AnchorKind.CASH) R.string.map_anchor_cash
            else R.string.map_anchor_entrance
        )
        binding.storeMapView.setStart(start.x, start.y, label)
    }

    private fun routeFrom(): Anchor {
        val tracker = pdr
        if (tracker != null && tracker.enabled) {
            return Anchor(tracker.x, tracker.y)
        }
        return currentStart()
    }

    private fun enablePdr() {
        pdrWanted = true
        ensurePdr()
        val tracker = pdr
        if (tracker != null &&
            tracker.needsActivityRecognition() &&
            Build.VERSION.SDK_INT >= 29
        ) {
            val ok = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
            if (!ok) {
                activityPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                return
            }
        }
        startPdrInternal()
    }

    private fun startPdrInternal() {
        if (_binding == null) return
        ensurePdr()
        val tracker = pdr ?: return
        if (!tracker.hasSensors()) {
            binding.switchPdr.isChecked = false
            binding.pdrStatus.setText(R.string.map_pdr_no_sensors)
            Toast.makeText(requireContext(), R.string.map_pdr_no_sensors, Toast.LENGTH_LONG).show()
            return
        }
        tracker.mapBearingDeg = mapBearing
        val wasOff = !tracker.enabled
        if (wasOff) {
            tracker.resetTo(currentStart().x, currentStart().y)
        }
        tracker.start()
        binding.btnPdrRelocate.visibility = View.VISIBLE
        binding.storeMapView.setMe(tracker.x, tracker.y, tracker.mapHeadingDeg)
        binding.pdrStatus.text = getString(R.string.map_pdr_active, tracker.stepCount)
        if (wasOff) {
            activeRouteItem?.let { focusItem(it) }
            refreshDrawList()
        }
    }

    private fun disablePdr() {
        pdrWanted = false
        relocateArmed = false
        pdr?.stop()
        binding.storeMapView.clearMe()
        binding.storeMapView.setRelocateMode(false)
        binding.btnPdrRelocate.visibility = View.GONE
        binding.pdrStatus.setText(R.string.map_pdr_hint)
        applyStartMarker()
        activeRouteItem?.let { focusItem(it) }
    }

    private fun ensurePdr() {
        if (pdr != null) return
        pdr = PdrTracker(requireContext(), floorConstraint) { x, y, heading, steps ->
            if (_binding == null) return@PdrTracker
            binding.storeMapView.setMe(x, y, heading)
            binding.pdrStatus.text = getString(R.string.map_pdr_active, steps)
            // лёгкий пересчёт маршрута от живой точки (не на каждый шаг — раз в 3 шага)
            if (steps % 3 == 0) {
                activeRouteItem?.let { item ->
                    NativeMapEngine.calculateRoute(x, y, item.x, item.y)
                    refreshDrawList()
                }
            }
        }
    }

    private fun armRelocate() {
        if (pdr?.enabled != true) return
        relocateArmed = true
        binding.pdrStatus.setText(R.string.map_pdr_relocate_hint)
        binding.storeMapView.setRelocateMode(true) { mx, my ->
            pdr?.setPosition(mx, my)
            relocateArmed = false
            binding.storeMapView.setRelocateMode(false)
            Toast.makeText(requireContext(), R.string.map_pdr_relocated, Toast.LENGTH_SHORT).show()
            activeRouteItem?.let { focusItem(it) }
            refreshDrawList()
        }
    }

    private fun nearestRack(x: Float, y: Float): RackFootprint? {
        return racks.minByOrNull { hypot2(it.x - x, it.y - y) }
    }

    private fun hypot2(a: Float, b: Float): Float = a * a + b * b

    /**
     * Логика:
     * — нет заметок с товарами → баннер «создайте заметку»
     * — одна заметка → сразу маршрут
     * — несколько и не выбранa → диалог выбора
     * — уже выбранa (из «Показать на карте» или диалога) → маршрут
     */
    private fun resolveNoteAndRoute() {
        val repo = ShoppingListRepository.get(requireContext())
        val usable = repo.all().filter { it.items.any { item -> !item.isDone() } }

        binding.mapEmptyBanner.visibility = View.GONE
        binding.mapRouteBar.visibility = View.GONE
        binding.mapTargetBar.visibility = View.GONE

        if (usable.isEmpty()) {
            activeListId = null
            activeRouteItem = null
            binding.storeMapView.clearHighlight()
            binding.mapEmptyBanner.visibility = View.VISIBLE
            binding.mapHint.visibility = View.GONE
            return
        }

        val preferred = activeListId?.let { id -> usable.firstOrNull { it.id == id } }
        when {
            preferred != null -> applyRoute(preferred)
            usable.size == 1 -> applyRoute(usable.first())
            else -> {
                binding.mapHint.visibility = View.VISIBLE
                binding.mapHint.setText(R.string.map_hint_pick_note)
                binding.mapRouteBar.visibility = View.VISIBLE
                binding.activeNoteLabel.text = getString(R.string.map_note_not_selected)
                if (!pickerShownThisVisit) {
                    pickerShownThisVisit = true
                    showNotePicker(force = false)
                }
            }
        }
    }

    private fun applyRoute(list: ShoppingList) {
        activeListId = list.id
        arguments = (arguments ?: Bundle()).apply { putString(ARG_LIST_ID, list.id) }

        binding.mapEmptyBanner.visibility = View.GONE
        binding.mapHint.visibility = View.GONE
        binding.mapRouteBar.visibility = View.VISIBLE
        updateProgress(list)
        val left = list.items.count { !it.isDone() }
        binding.activeNoteLabel.text = getString(
            R.string.map_active_note_progress,
            list.title,
            left,
            list.items.size
        )

        val first = list.items.firstOrNull { !it.isDone() }
        if (first != null) {
            focusItem(first)
        } else {
            activeRouteItem = null
            binding.mapTargetBar.visibility = View.GONE
            binding.storeMapView.clearHighlight()
        }
        refreshDrawList()
    }

    private fun markCurrent(outcome: ItemOutcome) {
        val item = activeRouteItem ?: return
        val listId = activeListId ?: return
        dismissShelfFace()
        val repo = ShoppingListRepository.get(requireContext())
        val updated = repo.setOutcome(listId, item.id, outcome) ?: return
        lastClosedItemId = item.id
        if (outcome == ItemOutcome.BOUGHT) {
            lightTick()
            viewLifecycleOwner.lifecycleScope.launch {
                runCatching {
                    val storeId = OfflineRepository.get(requireContext()).selectedStoreId()
                    PurchaseHistoryRepository.get(requireContext())
                        .recordChecked(updated, listId, storeId)
                }
            }
        }
        val list = repo.get(listId) ?: return
        updateProgress(list)
        val next = list.items.firstOrNull { !it.isDone() }
        if (next != null) {
            // только карта и маршрут — полку не открываем (пока не нажмут «Показать полку»)
            focusItem(item = next, routeFromX = item.x, routeFromY = item.y)
            binding.activeNoteLabel.text = getString(
                R.string.map_active_note_progress,
                list.title,
                list.items.count { !it.isDone() },
                list.items.size
            )
            refreshDrawList()
        } else {
            activeRouteItem = null
            binding.mapTargetBar.visibility = View.GONE
            binding.storeMapView.clearHighlight()
            binding.activeNoteLabel.text = getString(R.string.map_note_done, list.title)
            refreshDrawList()
        }
    }

    /** Долгий тап: вернуть последний закрытый товар в открытые. */
    private fun undoLastClosed() {
        val listId = activeListId ?: return
        val repo = ShoppingListRepository.get(requireContext())
        val list = repo.get(listId) ?: return
        val undoId = lastClosedItemId
        val lastClosed = list.items.firstOrNull { it.id == undoId && it.isDone() }
            ?: list.items.lastOrNull { it.isDone() }
            ?: run {
                Toast.makeText(requireContext(), R.string.map_undo_empty, Toast.LENGTH_SHORT).show()
                return
            }
        repo.setOutcome(listId, lastClosed.id, ItemOutcome.OPEN)
        lastClosedItemId = null
        val refreshed = repo.get(listId) ?: return
        updateProgress(refreshed)
        binding.activeNoteLabel.text = getString(
            R.string.map_active_note_progress,
            refreshed.title,
            refreshed.items.count { !it.isDone() },
            refreshed.items.size
        )
        focusItem(lastClosed)
        refreshDrawList()
        Toast.makeText(
            requireContext(),
            getString(R.string.item_reopened_named, lastClosed.name),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun updateProgress(list: ShoppingList) {
        val total = list.items.size.coerceAtLeast(1)
        val done = list.items.count { it.isDone() }
        val left = total - done
        binding.mapProgress.max = total
        binding.mapProgress.progress = done
        binding.mapProgressLabel.text = getString(R.string.map_progress_label, done, total, left)
    }

    private fun lightTick() {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                val mgr = requireContext().getSystemService(VibratorManager::class.java)
                mgr?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                requireContext().getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createOneShot(28, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(28)
            }
        }
    }

    private fun focusItem(
        item: ShoppingItem,
        routeFromX: Float? = null,
        routeFromY: Float? = null
    ) {
        activeRouteItem = item
        lastFaceSheetItemId = null
        mainHandler.removeCallbacksAndMessages(null)
        val origin = routeFrom()
        val fromX = routeFromX ?: origin.x
        val fromY = routeFromY ?: origin.y
        NativeMapEngine.calculateRoute(fromX, fromY, item.x, item.y)

        val rack = nearestRack(item.x, item.y)
            ?: racks.firstOrNull { it.id == item.rackId }
        val label = rack?.id?.ifBlank { item.rackId } ?: item.rackId
        binding.storeMapView.setHighlight(
            x = rack?.x ?: item.x,
            y = rack?.y ?: item.y,
            rackW = rack?.w ?: 1.5f,
            rackD = rack?.d ?: 0.7f,
            label = label
        )
        binding.mapTargetBar.visibility = View.VISIBLE
        binding.mapTargetLabel.text = getString(
            R.string.map_target_label,
            item.displayName(),
            item.rackId,
            item.shelf
        )
    }

    /** Полка по запросу — когда уже у стеллажа и не видно товар. */
    private fun openShelfFace() {
        val item = activeRouteItem ?: return
        if (lastFaceSheetItemId == item.id) {
            // уже открыт — не дублируем
            val existing = parentFragmentManager.findFragmentByTag(RackFaceBottomSheet.TAG)
            if (existing != null) return
        }
        lastFaceSheetItemId = item.id
        val rack = nearestRack(item.x, item.y)
            ?: racks.firstOrNull { it.id == item.rackId }
        val catalog = DemoCatalog.all(requireContext())
        val demo = catalog.firstOrNull { it.rackId == item.rackId && it.name == item.name }
            ?: catalog.firstOrNull { it.rackId == item.rackId }
        val product = demo ?: CatalogProduct(
            id = item.id,
            name = item.name,
            rackId = item.rackId,
            shelf = item.shelf,
            x = item.x,
            y = item.y,
            rackName = rack?.name ?: item.rackId,
            shelfCount = rack?.shelves ?: 5,
            bay = 2,
            isDemo = false
        )
        RackFaceBottomSheet.newInstance(product)
            .show(parentFragmentManager, RackFaceBottomSheet.TAG)
    }

    private fun dismissShelfFace() {
        mainHandler.removeCallbacksAndMessages(null)
        lastFaceSheetItemId = null
        (parentFragmentManager.findFragmentByTag(RackFaceBottomSheet.TAG) as? RackFaceBottomSheet)
            ?.dismissAllowingStateLoss()
    }

    private fun showNotePicker(force: Boolean) {
        val usable = ShoppingListRepository.get(requireContext())
            .all()
            .filter { it.items.any { item -> !item.isDone() } }
        if (usable.isEmpty()) {
            binding.mapEmptyBanner.visibility = View.VISIBLE
            return
        }
        if (usable.size == 1 && !force) {
            applyRoute(usable.first())
            return
        }

        val labels = usable.map { list ->
            val left = list.items.count { !it.isDone() }
            getString(R.string.map_note_option, list.title, left)
        }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.map_pick_note_title)
            .setItems(labels) { _, which ->
                applyRoute(usable[which])
            }
            .setNegativeButton(R.string.map_pick_cancel, null)
            .show()
    }

    private fun refreshDrawList() {
        binding.storeMapView.setScene(
            NativeMapEngine.nativeBuildDrawList(),
            floorW,
            floorH,
            view3d,
            mapBearing
        )
    }

    private fun prefs() =
        requireContext().applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun loadSavedAnchor(): AnchorKind {
        val raw = prefs().getString(KEY_ANCHOR, AnchorKind.ENTRANCE.name)
        return runCatching { AnchorKind.valueOf(raw!!) }.getOrDefault(AnchorKind.ENTRANCE)
    }

    private fun saveAnchor(kind: AnchorKind) {
        prefs().edit().putString(KEY_ANCHOR, kind.name).apply()
    }

    override fun onPause() {
        pdr?.stop()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        loadMap()
        if (pdrWanted && racks.isNotEmpty()) {
            ensurePdr()
            val needsPerm = pdr?.needsActivityRecognition() == true && Build.VERSION.SDK_INT >= 29
            val granted = !needsPerm || ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) startPdrInternal()
        }
    }

    override fun onDestroyView() {
        mainHandler.removeCallbacksAndMessages(null)
        pdr?.stop()
        pdr = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_LIST_ID = "list_id"
        const val EXTRA_ROUTE_LIST_ID = "route_list_id"
        private const val PREFS = "plna_map"
        private const val KEY_ANCHOR = "start_anchor"

        fun withRoute(listId: String): MapFragment {
            return MapFragment().apply {
                arguments = Bundle().apply { putString(ARG_LIST_ID, listId) }
            }
        }
    }
}
