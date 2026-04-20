package com.example.sleppify

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.Patterns
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.sleppify.CloudSyncManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Optimized Kotlin version of ScannerFragment.
 * Uses ML Kit and CameraX for fast barcode/QR detection.
 * Now using Coroutines for timing and UI state management.
 */
class ScannerFragment : Fragment() {

    companion object {
    }

    private var previewScanner: PreviewView? = null
    private var freezeOverlay: ImageView? = null
    private var actionImportImages: View? = null
    private var scannerRoot: View? = null
    private var scanArea: View? = null
    private var btnFlashlight: MaterialButton? = null
    private var cardScanResult: MaterialCardView? = null
    private var tvScanResultLabel: TextView? = null
    private var tvScanResultValue: TextView? = null
    private var tvScanResultMore: TextView? = null
    private var rowScanResultValue: View? = null
    private var ivScanResultChevron: ImageView? = null
    private var btnScanResultViewOptions: TextView? = null
    private var btnCancelScan: View? = null
    private var pendingScanForMenu: DetectedScanItem? = null
    private var pendingScanIsUrl: Boolean = false
    private var scanOptionsBottomSheet: BottomSheetDialog? = null

    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraExecutor: ExecutorService? = null
    private var zoomGestureDetector: ScaleGestureDetector? = null
    
    private val settingsPrefs: SharedPreferences? by lazy { 
        context?.getSharedPreferences(CloudSyncManager.PREFS_SETTINGS, Context.MODE_PRIVATE) 
    }
    
    private var torchEnabled = false
    private val detectionLock = AtomicBoolean(false)
    private val barcodeScanner: BarcodeScanner by lazy { BarcodeScanning.getClient() }

    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else showSimpleCenteredToast("Permiso de cámara requerido para escanear")
    }

    private val filesPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) openImagePicker() else showSimpleCenteredToast("Permiso de archivos requerido")
    }

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { analyzeImportedImage(it) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_scanner, container, false)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        previewScanner = view.findViewById(R.id.previewScanner)
        freezeOverlay = view.findViewById(R.id.ivFreezeOverlay)
        actionImportImages = view.findViewById(R.id.actionImportImages)
        scannerRoot = view.findViewById(R.id.scannerRoot)
        scanArea = view.findViewById(R.id.scanArea)
        btnFlashlight = view.findViewById(R.id.btnFlashlight)
        cardScanResult = view.findViewById(R.id.cardScanResult)
        tvScanResultLabel = view.findViewById(R.id.tvScanResultLabel)
        tvScanResultValue = view.findViewById(R.id.tvScanResultValue)
        tvScanResultMore = view.findViewById(R.id.tvScanResultMore)
        rowScanResultValue = view.findViewById(R.id.rowScanResultValue)
        ivScanResultChevron = view.findViewById(R.id.ivScanResultChevron)
        btnScanResultViewOptions = view.findViewById(R.id.btnScanResultViewOptions)
        btnCancelScan = view.findViewById(R.id.btnCancelScan)

        rowScanResultValue?.setOnClickListener { onScanResultRowClick() }
        btnScanResultViewOptions?.setOnClickListener { showScanResultOptionsMenu() }
        btnCancelScan?.setOnClickListener { resetDetectionState() }

        previewScanner?.let { pv ->
            zoomGestureDetector = ScaleGestureDetector(requireContext(), object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    return applyPinchZoom(detector.scaleFactor)
                }
            })
            pv.setOnTouchListener { _, event ->
                val handled = zoomGestureDetector?.onTouchEvent(event) ?: false
                handled || event.pointerCount > 1
            }
        }

        btnFlashlight?.apply {
            setOnClickListener { toggleTorch() }
            isEnabled = false
            updateTorchButtonVisualState()
        }

        actionImportImages?.setOnClickListener { requestImageImportPermissionAndPick() }

        // Tap anywhere outside the result card to dismiss it
        scannerRoot?.setOnClickListener {
            if (cardScanResult?.visibility == View.VISIBLE) {
                resetDetectionState()
            }
        }
        
        if (cameraExecutor == null || cameraExecutor?.isShutdown == true) {
            cameraExecutor = Executors.newSingleThreadExecutor()
        }
        
        applyScannerBackdropMode()
    }

    override fun onResume() {
        super.onResume()
        applyScannerBackdropMode()
        updateTorchButtonVisualState()
        ensureCameraPermissionAndStart()
    }

    override fun onPause() {
        stopCamera()
        // Do NOT dismiss scan result card here — keep it persistent
        super.onPause()
    }

    override fun onDestroyView() {
        stopCamera()
        resetDetectionState()
        hideScanResultCard()
        cameraExecutor?.shutdown()
        cameraExecutor = null
        
        previewScanner?.setOnTouchListener(null)
        previewScanner = null
        freezeOverlay = null
        cardScanResult = null
        tvScanResultLabel = null
        tvScanResultValue = null
        tvScanResultMore = null
        rowScanResultValue = null
        ivScanResultChevron = null
        btnScanResultViewOptions = null
        btnCancelScan = null
        actionImportImages = null
        scannerRoot = null
        scanArea = null
        btnFlashlight = null
        zoomGestureDetector = null
        scanOptionsBottomSheet?.dismiss()
        scanOptionsBottomSheet = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        barcodeScanner.close()
        super.onDestroy()
    }

    private fun applyScannerBackdropMode() {
        val amoled = isAmoledModeEnabled()
        scannerRoot?.setBackgroundColor(if (amoled) 0xFF000000.toInt() else 0xFF05060A.toInt())
        scanArea?.setBackgroundColor(if (amoled) 0xFF000000.toInt() else 0x120E171F)
        applyScanResultCardTheme()
    }

    private fun applyScanResultCardTheme() {
        val card = cardScanResult ?: return
        card.setCardBackgroundColor(scanResultPanelBackgroundColor())
    }

    /** Same fill as [cardScanResult] (scanner result card + options sheet). */
    private fun scanResultPanelBackgroundColor(): Int =
        if (isAmoledModeEnabled()) 0xFF1C1C1E.toInt() else 0xFF2A2D36.toInt()

    private fun applyPinchZoom(scaleFactor: Float): Boolean {
        val cam = camera ?: return false
        val zoomState = cam.cameraInfo.zoomState.value ?: return false
        val nextZoom = zoomState.zoomRatio * scaleFactor
        val clamped = nextZoom.coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
        cam.cameraControl.setZoomRatio(clamped)
        return true
    }

    private fun requestImageImportPermissionAndPick() {
        if (!isAdded) return
        showSimpleCenteredToast("Abriendo selector...")
        val permission = getImageReadPermission()
        if (permission == null || ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED) {
            openImagePicker()
        } else {
            filesPermissionLauncher.launch(permission)
        }
    }

    private fun getImageReadPermission(): String? {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> Manifest.permission.READ_MEDIA_IMAGES
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> Manifest.permission.READ_EXTERNAL_STORAGE
            else -> null
        }
    }

    private fun openImagePicker() {
        try { imagePickerLauncher.launch("image/*") } catch (e: Exception) {
            showSimpleCenteredToast("No se pudo abrir el selector")
        }
    }

    private fun analyzeImportedImage(uri: Uri) {
        if (!isAdded || previewScanner == null) return
        if (!detectionLock.compareAndSet(false, true)) {
            showSimpleCenteredToast("Espera 5 segundos")
            return
        }

        try {
            val inputImage = InputImage.fromFilePath(requireContext(), uri)
            barcodeScanner.process(inputImage)
                .addOnSuccessListener { barcodes -> onImportedBarcodes(barcodes) }
                .addOnFailureListener { detectionLock.set(false) }
        } catch (e: IOException) {
            detectionLock.set(false)
        }
    }

    private fun onImportedBarcodes(barcodes: List<Barcode>) {
        if (!isAdded || previewScanner == null) { detectionLock.set(false); return }
        val items = extractDetectedItems(barcodes)
        if (items.isEmpty()) {
            detectionLock.set(false)
            showSimpleCenteredToast("No se detectó nada")
            return
        }

        showScanResultBottomCard(items)
    }

    private fun ensureCameraPermissionAndStart() {
        if (!isAdded) return
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        if (!isAdded || previewScanner == null) return
        val providerFuture = ProcessCameraProvider.getInstance(requireContext())
        providerFuture.addListener({
            try {
                cameraProvider = providerFuture.get()
                bindCameraUseCases()
            } catch (e: Exception) {
                showSimpleCenteredToast("Error al iniciar cámara")
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        val pv = previewScanner ?: return
        val exec = cameraExecutor ?: return

        val preview = Preview.Builder().build().also { it.setSurfaceProvider(pv.surfaceProvider) }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build().also { it.setAnalyzer(exec) { proxy -> analyzeImage(proxy) } }
        
        provider.unbindAll()
        camera = provider.bindToLifecycle(viewLifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        updateTorchButtonAvailability()
    }

    private fun stopCamera() {
        cameraProvider?.unbindAll()
        camera = null
        torchEnabled = false
        btnFlashlight?.isEnabled = false
        updateTorchButtonVisualState()
    }

    private fun toggleTorch() {
        val cam = camera ?: return
        if (!cam.cameraInfo.hasFlashUnit()) return
        torchEnabled = !torchEnabled
        cam.cameraControl.enableTorch(torchEnabled)
        updateTorchButtonVisualState()
    }

    private fun updateTorchButtonAvailability() {
        val hasFlash = camera?.cameraInfo?.hasFlashUnit() ?: false
        btnFlashlight?.isEnabled = hasFlash
        if (!hasFlash) torchEnabled = false
        updateTorchButtonVisualState()
    }

    private fun updateTorchButtonVisualState() {
        val btn = btnFlashlight ?: return
        val amoled = isAmoledModeEnabled()
        val bgInactive = if (amoled) 0xFF000000.toInt() else 0xFF565B64.toInt()
        val strokeInactive = if (amoled) 0xFF2A2F39.toInt() else 0x00000000

        if (!btn.isEnabled) {
            btn.text = "Linterna no disponible"
            btn.setTextColor(0xFF8A909D.toInt())
            btn.setIconResource(R.drawable.ic_flashlight_off)
            btn.setIconTint(ColorStateList.valueOf(0xFF8A909D.toInt()))
            btn.backgroundTintList = ColorStateList.valueOf(bgInactive)
            btn.strokeWidth = if (amoled) dpToPx(1) else 0
            btn.strokeColor = ColorStateList.valueOf(strokeInactive)
            return
        }

        if (torchEnabled) {
            btn.setValues("Apagar linterna", 0xFFF4F8FF.toInt(), R.drawable.ic_flashlight_on, 0xFF20A99B.toInt(), 0)
        } else {
            btn.setValues("Encender la linterna", 0xFF8A909D.toInt(), R.drawable.ic_flashlight_off, bgInactive, if (amoled) dpToPx(1) else 0, strokeInactive)
        }
    }

    private fun MaterialButton.setValues(txt: String, color: Int, iconRes: Int, bg: Int, stroke: Int, strokeColorRes: Int = 0) {
        text = txt
        setTextColor(color)
        setIconResource(iconRes)
        setIconTint(ColorStateList.valueOf(color))
        backgroundTintList = ColorStateList.valueOf(bg)
        strokeWidth = stroke
        strokeColor = ColorStateList.valueOf(strokeColorRes)
    }

    private fun isAmoledModeEnabled() = settingsPrefs?.getBoolean(CloudSyncManager.KEY_AMOLED_MODE_ENABLED, false) ?: false

    @SuppressLint("UnsafeOptInUsageError")
    private fun analyzeImage(proxy: ImageProxy) {
        if (detectionLock.get()) { proxy.close(); return }
        val mediaImage = proxy.image ?: run { proxy.close(); return }
        
        val inputImage = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
        barcodeScanner.process(inputImage)
            .addOnSuccessListener { barcodes -> onBarcodesDetected(barcodes) }
            .addOnCompleteListener { proxy.close() }
    }

    private fun onBarcodesDetected(barcodes: List<Barcode>) {
        val items = extractDetectedItems(barcodes)
        if (items.isEmpty() || !detectionLock.compareAndSet(false, true)) return

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            if (!isAdded || previewScanner == null) { detectionLock.set(false); return@launch }
            showFreezeFrame()
            showScanResultBottomCard(items)
        }
    }

    private fun showFreezeFrame() {
        val pv = previewScanner ?: return
        val overlay = freezeOverlay ?: return
        pv.bitmap?.let { 
            overlay.setImageBitmap(it)
            overlay.visibility = View.VISIBLE
        }
    }

    private fun extractDetectedItems(barcodes: List<Barcode>): List<DetectedScanItem> {
        return barcodes.mapNotNull { b ->
            val raw = b.rawValue ?: b.displayValue ?: return@mapNotNull null
            val normalized = raw.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            DetectedScanItem(normalized, describeBarcodeType(b))
        }.distinctBy { "${it.detectedType}::${it.rawValue}" }
    }

    private fun showScanResultBottomCard(items: List<DetectedScanItem>) {
        if (!isAdded || items.isEmpty()) return
        val primary = items.first()
        val isUrl = isLikelyWebLink(primary.rawValue)
        pendingScanForMenu = primary
        pendingScanIsUrl = isUrl

        applyScanResultCardTheme()
        tvScanResultLabel?.text = if (isUrl) {
            getString(R.string.scan_result_web_address)
        } else {
            primary.detectedType
        }
        tvScanResultValue?.text = primary.rawValue
        if (items.size > 1) {
            tvScanResultMore?.visibility = View.VISIBLE
            tvScanResultMore?.text = getString(R.string.scan_result_more_items, items.size - 1)
        } else {
            tvScanResultMore?.visibility = View.GONE
        }
        ivScanResultChevron?.visibility = if (isUrl) View.VISIBLE else View.GONE

        cardScanResult?.visibility = View.VISIBLE
    }

    private fun hideScanResultCard() {
        scanOptionsBottomSheet?.dismiss()
        scanOptionsBottomSheet = null
        cardScanResult?.visibility = View.GONE
        pendingScanForMenu = null
        pendingScanIsUrl = false
    }

    private fun onScanResultRowClick() {
        val item = pendingScanForMenu ?: return
        if (pendingScanIsUrl) {
            openScannedInInternet(item.rawValue)
        } else {
            copyToClipboard(item.rawValue)
        }
        resetDetectionState()
    }

    /** Bottom sheet (same colors as the scan card). Do not use PopupMenu or R.menu.menu_scan_result_options here. */
    private fun showScanResultOptionsMenu() {
        if (!isAdded) return
        val item = pendingScanForMenu ?: return
        val isUrl = pendingScanIsUrl

        scanOptionsBottomSheet?.dismiss()
        val dialog = BottomSheetDialog(requireContext())
        val content = layoutInflater.inflate(R.layout.bottom_sheet_scan_options, null, false)
        val root = content.findViewById<LinearLayout>(R.id.sheetScanOptionsRoot)
        val corner = 22f * resources.displayMetrics.density
        root.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = floatArrayOf(corner, corner, corner, corner, 0f, 0f, 0f, 0f)
            setColor(scanResultPanelBackgroundColor())
        }

        content.findViewById<TextView>(R.id.tvSheetLabel).text =
            if (isUrl) getString(R.string.scan_result_web_address) else item.detectedType
        content.findViewById<TextView>(R.id.tvSheetValue).text = item.rawValue
        content.findViewById<ImageView>(R.id.ivSheetChevron).visibility =
            if (isUrl) View.VISIBLE else View.GONE

        val tvOpen = content.findViewById<TextView>(R.id.tvSheetOpenBrowser)
        tvOpen.visibility = if (isUrl) View.VISIBLE else View.GONE
        tvOpen.setOnClickListener {
            dialog.dismiss()
            openScannedInInternet(item.rawValue)
        }

        content.findViewById<TextView>(R.id.tvSheetCopy).setOnClickListener {
            dialog.dismiss()
            copyToClipboard(item.rawValue)
        }

        content.findViewById<TextView>(R.id.tvSheetCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialog.setContentView(content)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setOnShowListener {
            dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
                ?.setBackgroundColor(Color.TRANSPARENT)
        }
        dialog.setOnDismissListener { scanOptionsBottomSheet = null }
        scanOptionsBottomSheet = dialog
        dialog.show()
    }

    private fun copyToClipboard(v: String) {
        if (!isAdded) return
        val cb = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cb.setPrimaryClip(ClipData.newPlainText("escaneo", v))
        val root = scannerRoot ?: return
        Snackbar.make(root, getString(R.string.scan_result_copied), Snackbar.LENGTH_SHORT).show()
    }

    private fun describeBarcodeType(b: Barcode) = when (b.format) {
        Barcode.FORMAT_QR_CODE -> "QR"
        Barcode.FORMAT_AZTEC -> "Aztec"
        Barcode.FORMAT_DATA_MATRIX -> "Data Matrix"
        Barcode.FORMAT_PDF417 -> "PDF417"
        Barcode.FORMAT_CODABAR, Barcode.FORMAT_CODE_39, Barcode.FORMAT_CODE_93, Barcode.FORMAT_CODE_128,
        Barcode.FORMAT_EAN_8, Barcode.FORMAT_EAN_13, Barcode.FORMAT_ITF, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E -> "Código de barras"
        else -> "Código"
    }

    private fun isLikelyWebLink(s: String) = s.trim().let { 
        it.startsWith("http://") || it.startsWith("https://") || it.startsWith("www.") || Patterns.WEB_URL.matcher(it).matches()
    }

    private fun openScannedInInternet(s: String) {
        if (!isAdded) return
        val url = s.trim().let { if (isLikelyWebLink(it)) (if (it.startsWith("http")) it else "https://$it") else "https://www.google.com/search?q=${Uri.encode(it)}" }
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) {
            showSimpleCenteredToast("No se encontró app de internet")
        }
    }

    private fun showSimpleCenteredToast(m: String) { /* Global disabled toasts */ }

    private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()

    private fun resetDetectionState() {
        detectionLock.set(false)
        freezeOverlay?.visibility = View.GONE
        freezeOverlay?.setImageBitmap(null)
        hideScanResultCard()
    }

    private data class DetectedScanItem(val rawValue: String, val detectedType: String)
}
