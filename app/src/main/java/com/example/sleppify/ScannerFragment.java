package com.example.sleppify;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.UnderlineSpan;
import android.util.Patterns;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.ZoomState;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.google.android.material.button.MaterialButton;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScannerFragment extends Fragment {

    private static final long DETECTION_FREEZE_MS = 5000L;
    private static final long SMART_TOAST_VISIBLE_MS = 10000L;

    @Nullable
    private PreviewView previewScanner;
    @Nullable
    private ImageView freezeOverlay;
    @Nullable
    private PopupWindow smartToastPopup;
    @Nullable
    private View actionImportImages;
    @Nullable
    private MaterialButton btnFlashlight;
    @Nullable
    private Camera camera;
    @Nullable
    private ProcessCameraProvider cameraProvider;
    @Nullable
    private ExecutorService cameraExecutor;
    @Nullable
    private ScaleGestureDetector zoomGestureDetector;
    private boolean torchEnabled;

    @NonNull
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    @NonNull
    private final AtomicBoolean detectionLock = new AtomicBoolean(false);
    @NonNull
    private final BarcodeScanner barcodeScanner = BarcodeScanning.getClient();

    @NonNull
    private final Runnable releaseDetectionLockRunnable = () -> {
        detectionLock.set(false);
        if (freezeOverlay != null) {
            freezeOverlay.setVisibility(View.GONE);
            freezeOverlay.setImageBitmap(null);
        }
    };

    @NonNull
    private final Runnable dismissSmartToastRunnable = this::dismissSmartToast;

    @NonNull
    private final ActivityResultLauncher<String> cameraPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> {
                if (granted) {
                    startCamera();
                    return;
                }
                showSimpleCenteredToast("Permiso de camara requerido para escanear");
            }
    );

    @NonNull
    private final ActivityResultLauncher<String> filesPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> {
                if (granted) {
                    openImagePicker();
                    return;
                }
                showSimpleCenteredToast("Permiso de archivos requerido para importar imagenes");
            }
    );

    @NonNull
    private final ActivityResultLauncher<String> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri == null) {
                    return;
                }
                analyzeImportedImage(uri);
            }
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_scanner, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        previewScanner = view.findViewById(R.id.previewScanner);
        freezeOverlay = view.findViewById(R.id.ivFreezeOverlay);
        actionImportImages = view.findViewById(R.id.actionImportImages);

        if (previewScanner != null) {
            zoomGestureDetector = new ScaleGestureDetector(requireContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(@NonNull ScaleGestureDetector detector) {
                    return applyPinchZoom(detector.getScaleFactor());
                }
            });
            previewScanner.setOnTouchListener((v, event) -> handlePreviewTouch(event));
        }

        btnFlashlight = view.findViewById(R.id.btnFlashlight);
        if (btnFlashlight != null) {
            btnFlashlight.setOnClickListener(v -> toggleTorch());
            btnFlashlight.setEnabled(false);
            updateTorchButtonVisualState();
        }
        if (actionImportImages != null) {
            actionImportImages.setOnClickListener(v -> requestImageImportPermissionAndPick());
        }

        if (cameraExecutor == null || cameraExecutor.isShutdown()) {
            cameraExecutor = Executors.newSingleThreadExecutor();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        ensureCameraPermissionAndStart();
    }

    @Override
    public void onPause() {
        stopCamera();
        mainHandler.removeCallbacks(releaseDetectionLockRunnable);
        mainHandler.removeCallbacks(dismissSmartToastRunnable);
        detectionLock.set(false);
        if (freezeOverlay != null) {
            freezeOverlay.setVisibility(View.GONE);
            freezeOverlay.setImageBitmap(null);
        }
        dismissSmartToast();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        stopCamera();
        mainHandler.removeCallbacks(releaseDetectionLockRunnable);
        mainHandler.removeCallbacks(dismissSmartToastRunnable);
        detectionLock.set(false);
        dismissSmartToast();

        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
            cameraExecutor = null;
        }

        if (previewScanner != null) {
            previewScanner.setOnTouchListener(null);
        }

        previewScanner = null;
        freezeOverlay = null;
        smartToastPopup = null;
        actionImportImages = null;
        btnFlashlight = null;
        zoomGestureDetector = null;
        super.onDestroyView();
    }

    private boolean handlePreviewTouch(@Nullable MotionEvent event) {
        if (event == null || zoomGestureDetector == null) {
            return false;
        }

        boolean handled = zoomGestureDetector.onTouchEvent(event);
        return handled || event.getPointerCount() > 1;
    }

    private boolean applyPinchZoom(float scaleFactor) {
        if (camera == null) {
            return false;
        }

        ZoomState zoomState = camera.getCameraInfo().getZoomState().getValue();
        if (zoomState == null) {
            return false;
        }

        float currentZoom = zoomState.getZoomRatio();
        float nextZoom = currentZoom * scaleFactor;
        float clampedZoom = Math.max(zoomState.getMinZoomRatio(), Math.min(nextZoom, zoomState.getMaxZoomRatio()));
        camera.getCameraControl().setZoomRatio(clampedZoom);
        return true;
    }

    private void requestImageImportPermissionAndPick() {
        if (!isAdded()) {
            return;
        }

        showSimpleCenteredToast("Abriendo selector de imagenes...");

        String permission = getImageReadPermission();
        if (permission == null || ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED) {
            openImagePicker();
            return;
        }

        filesPermissionLauncher.launch(permission);
    }

    @Nullable
    private String getImageReadPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return Manifest.permission.READ_MEDIA_IMAGES;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Manifest.permission.READ_EXTERNAL_STORAGE;
        }
        return null;
    }

    private void openImagePicker() {
        try {
            imagePickerLauncher.launch("image/*");
        } catch (Exception ignored) {
            showSimpleCenteredToast("No se pudo abrir el selector de imagenes");
        }
    }

    private void analyzeImportedImage(@NonNull Uri imageUri) {
        if (!isAdded() || previewScanner == null) {
            return;
        }

        if (!detectionLock.compareAndSet(false, true)) {
            showSimpleCenteredToast("Espera 5 segundos para el siguiente escaneo");
            return;
        }

        try {
            InputImage inputImage = InputImage.fromFilePath(requireContext(), imageUri);
            barcodeScanner.process(inputImage)
                    .addOnSuccessListener(this::onImportedImageBarcodesDetected)
                    .addOnFailureListener(error -> {
                        detectionLock.set(false);
                        showSimpleCenteredToast("No se pudo analizar la imagen");
                    });
        } catch (IOException ignored) {
            detectionLock.set(false);
            showSimpleCenteredToast("No se pudo abrir la imagen seleccionada");
        }
    }

    private void onImportedImageBarcodesDetected(@Nullable List<Barcode> barcodes) {
        if (!isAdded() || previewScanner == null) {
            detectionLock.set(false);
            return;
        }

        List<DetectedScanItem> detectedItems = extractDetectedItems(barcodes);
        if (detectedItems.isEmpty()) {
            detectionLock.set(false);
            showSimpleCenteredToast("No se detecto QR ni codigo de barras en la imagen");
            return;
        }

        float centerX = previewScanner.getWidth() / 2f;
        float centerY = previewScanner.getHeight() / 2f;
        showSmartDetectionToast(detectedItems, centerX, centerY);

        mainHandler.removeCallbacks(releaseDetectionLockRunnable);
        mainHandler.postDelayed(releaseDetectionLockRunnable, DETECTION_FREEZE_MS);
    }

    @Override
    public void onDestroy() {
        barcodeScanner.close();
        super.onDestroy();
    }

    private void ensureCameraPermissionAndStart() {
        if (!isAdded()) {
            return;
        }

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
            return;
        }

        cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
    }

    private void startCamera() {
        if (!isAdded() || previewScanner == null) {
            return;
        }

        ListenableFuture<ProcessCameraProvider> providerFuture = ProcessCameraProvider.getInstance(requireContext());
        providerFuture.addListener(() -> {
            try {
                cameraProvider = providerFuture.get();
                bindCameraUseCases();
            } catch (Exception ignored) {
                showSimpleCenteredToast("No se pudo iniciar la camara");
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null || previewScanner == null || cameraExecutor == null) {
            return;
        }

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewScanner.getSurfaceProvider());

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);

        CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

        cameraProvider.unbindAll();
        camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
        updateTorchButtonAvailability();
    }

    private void stopCamera() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
            camera = null;
        }
        torchEnabled = false;
        if (btnFlashlight != null) {
            btnFlashlight.setEnabled(false);
        }
        updateTorchButtonVisualState();
    }

    private void toggleTorch() {
        if (camera == null || !camera.getCameraInfo().hasFlashUnit()) {
            showSimpleCenteredToast("Este dispositivo no tiene linterna en esta camara");
            return;
        }

        torchEnabled = !torchEnabled;
        camera.getCameraControl().enableTorch(torchEnabled);
        updateTorchButtonVisualState();
    }

    private void updateTorchButtonAvailability() {
        if (btnFlashlight == null) {
            return;
        }

        boolean hasFlash = camera != null && camera.getCameraInfo().hasFlashUnit();
        btnFlashlight.setEnabled(hasFlash);
        if (!hasFlash) {
            torchEnabled = false;
        }
        updateTorchButtonVisualState();
    }

    private void updateTorchButtonVisualState() {
        if (btnFlashlight == null) {
            return;
        }

        if (!btnFlashlight.isEnabled()) {
            btnFlashlight.setText("Linterna no disponible");
            btnFlashlight.setTextColor(0xFF8A909D);
            btnFlashlight.setIconResource(R.drawable.ic_flashlight_off);
            btnFlashlight.setIconTint(ColorStateList.valueOf(0xFF8A909D));
            btnFlashlight.setBackgroundTintList(ColorStateList.valueOf(0xFF565B64));
            return;
        }

        if (torchEnabled) {
            btnFlashlight.setText("Apagar linterna");
            btnFlashlight.setTextColor(0xFFF4F8FF);
            btnFlashlight.setIconResource(R.drawable.ic_flashlight_on);
            btnFlashlight.setIconTint(ColorStateList.valueOf(0xFFF4F8FF));
            btnFlashlight.setBackgroundTintList(ColorStateList.valueOf(0xFF20A99B));
            return;
        }

        btnFlashlight.setText("Encender la linterna");
        btnFlashlight.setTextColor(0xFF8A909D);
        btnFlashlight.setIconResource(R.drawable.ic_flashlight_off);
        btnFlashlight.setIconTint(ColorStateList.valueOf(0xFF8A909D));
        btnFlashlight.setBackgroundTintList(ColorStateList.valueOf(0xFF565B64));
    }

    private void analyzeImage(@NonNull ImageProxy imageProxy) {
        if (detectionLock.get()) {
            imageProxy.close();
            return;
        }

        android.media.Image mediaImage = imageProxy.getImage();
        if (mediaImage == null) {
            imageProxy.close();
            return;
        }

        int imageWidth = imageProxy.getWidth();
        int imageHeight = imageProxy.getHeight();
        int rotation = imageProxy.getImageInfo().getRotationDegrees();
        InputImage inputImage = InputImage.fromMediaImage(mediaImage, rotation);

        barcodeScanner.process(inputImage)
                .addOnSuccessListener(barcodes -> onBarcodesDetected(barcodes, imageWidth, imageHeight, rotation))
                .addOnCompleteListener(task -> imageProxy.close());
    }

    private void onBarcodesDetected(
            @Nullable List<Barcode> barcodes,
            int imageWidth,
            int imageHeight,
            int rotation
    ) {
        List<DetectedScanItem> detectedItems = extractDetectedItems(barcodes);
        if (detectedItems.isEmpty()) {
            return;
        }

        if (!detectionLock.compareAndSet(false, true)) {
            return;
        }

        Barcode anchorBarcode = barcodes != null && !barcodes.isEmpty() ? barcodes.get(0) : null;
        mainHandler.post(() -> handleDetectedBarcodes(detectedItems, anchorBarcode, imageWidth, imageHeight, rotation));
    }

    private void handleDetectedBarcodes(
            @NonNull List<DetectedScanItem> detectedItems,
            @Nullable Barcode anchorBarcode,
            int imageWidth,
            int imageHeight,
            int rotation
    ) {
        if (!isAdded() || previewScanner == null) {
            detectionLock.set(false);
            return;
        }

        showFreezeFrame();

        Rect box = anchorBarcode != null ? anchorBarcode.getBoundingBox() : null;
        float[] mapped = mapDetectionCenterToPreview(box, imageWidth, imageHeight, rotation);
        showSmartDetectionToast(detectedItems, mapped[0], mapped[1]);

        mainHandler.removeCallbacks(releaseDetectionLockRunnable);
        mainHandler.postDelayed(releaseDetectionLockRunnable, DETECTION_FREEZE_MS);
    }

    private void showFreezeFrame() {
        if (previewScanner == null || freezeOverlay == null) {
            return;
        }

        Bitmap frame = previewScanner.getBitmap();
        if (frame == null) {
            return;
        }

        freezeOverlay.setImageBitmap(frame);
        freezeOverlay.setVisibility(View.VISIBLE);
    }

    @NonNull
    private float[] mapDetectionCenterToPreview(@Nullable Rect box, int imageWidth, int imageHeight, int rotation) {
        float centerX;
        float centerY;

        if (box == null) {
            centerX = imageWidth / 2f;
            centerY = imageHeight / 2f;
        } else {
            centerX = box.exactCenterX();
            centerY = box.exactCenterY();
        }

        float normalizedX;
        float normalizedY;
        switch (rotation) {
            case 90:
                normalizedX = centerY / Math.max(1f, imageHeight);
                normalizedY = 1f - (centerX / Math.max(1f, imageWidth));
                break;
            case 180:
                normalizedX = 1f - (centerX / Math.max(1f, imageWidth));
                normalizedY = 1f - (centerY / Math.max(1f, imageHeight));
                break;
            case 270:
                normalizedX = 1f - (centerY / Math.max(1f, imageHeight));
                normalizedY = centerX / Math.max(1f, imageWidth);
                break;
            case 0:
            default:
                normalizedX = centerX / Math.max(1f, imageWidth);
                normalizedY = centerY / Math.max(1f, imageHeight);
                break;
        }

        int viewWidth = previewScanner != null ? previewScanner.getWidth() : 0;
        int viewHeight = previewScanner != null ? previewScanner.getHeight() : 0;

        float mappedX = normalizedX * Math.max(1, viewWidth);
        float mappedY = normalizedY * Math.max(1, viewHeight);
        return new float[]{mappedX, mappedY};
    }

    @NonNull
    private List<DetectedScanItem> extractDetectedItems(@Nullable List<Barcode> barcodes) {
        List<DetectedScanItem> result = new ArrayList<>();
        if (barcodes == null || barcodes.isEmpty()) {
            return result;
        }

        Set<String> uniqueKeys = new LinkedHashSet<>();
        for (Barcode barcode : barcodes) {
            String rawValue = barcode.getRawValue();
            if (TextUtils.isEmpty(rawValue)) {
                rawValue = barcode.getDisplayValue();
            }
            if (TextUtils.isEmpty(rawValue)) {
                continue;
            }

            String normalized = rawValue.trim();
            if (normalized.isEmpty()) {
                continue;
            }

            String type = describeBarcodeType(barcode);
            String uniqueKey = type + "::" + normalized;
            if (!uniqueKeys.add(uniqueKey)) {
                continue;
            }

            result.add(new DetectedScanItem(normalized, type));
        }

        return result;
    }

    private void showSmartDetectionToast(@NonNull List<DetectedScanItem> detectedItems, float previewX, float previewY) {
        if (!isAdded() || previewScanner == null) {
            return;
        }

        if (detectedItems.isEmpty()) {
            return;
        }

        dismissSmartToast();

        boolean manyItems = detectedItems.size() > 1;
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundResource(R.drawable.bg_scanner_toast);
        int padH = dpToPx(12);
        int padV = dpToPx(8);
        content.setPadding(padH, padV, padH, padV);

        if (manyItems) {
            TextView header = new TextView(requireContext());
            header.setText("Escaneos detectados (" + detectedItems.size() + ")");
            header.setTextColor(0xFFFFFFFF);
            header.setTextSize(12f);
            header.setPadding(0, 0, 0, dpToPx(6));
            content.addView(header);
        }

        for (int i = 0; i < detectedItems.size(); i++) {
            DetectedScanItem item = detectedItems.get(i);
            boolean isLink = isLikelyWebLink(item.rawValue);
            String valueForUi = isLink ? item.rawValue.replaceFirst("^https?://", "") : item.rawValue;
            String wrappedValueForUi = addSoftWrapHints(valueForUi);
            String indexPrefix = manyItems ? (i + 1) + ". " : "";
            String linePrefix = isLink ? "Link (" + item.detectedType + "): " : item.detectedType + ": ";

            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            if (i < detectedItems.size() - 1) {
                row.setPadding(0, 0, 0, dpToPx(4));
            }

            TextView label = new TextView(requireContext());
            SpannableStringBuilder lineBuilder = new SpannableStringBuilder();
            lineBuilder.append(indexPrefix).append(linePrefix);
            int valueStart = lineBuilder.length();
            lineBuilder.append(wrappedValueForUi);
            int valueEnd = lineBuilder.length();
            lineBuilder.setSpan(new UnderlineSpan(), valueStart, valueEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            label.setText(lineBuilder);
            label.setSingleLine(false);
            label.setMaxLines(6);
            label.setEllipsize(null);
            label.setTextColor(0xFFFFFFFF);
            label.setTextSize(12f);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            label.setLayoutParams(textParams);
            label.setOnClickListener(v -> {
                if (isLink) {
                    openScannedValueInInternet(item.rawValue);
                } else {
                    copyToClipboard(item.rawValue);
                }
                dismissSmartToast();
            });
            row.addView(label);

            if (isLink || isAlphanumeric(item.rawValue)) {
                ImageView copyIcon = new ImageView(requireContext());
                copyIcon.setImageResource(R.drawable.ic_content_copy_18);
                copyIcon.setImageTintList(ColorStateList.valueOf(0xFFFFFFFF));
                copyIcon.setPadding(dpToPx(8), dpToPx(4), dpToPx(2), dpToPx(4));
                copyIcon.setOnClickListener(v -> {
                    copyToClipboard(item.rawValue);
                    dismissSmartToast();
                });
                row.addView(copyIcon);
            }

            content.addView(row);
        }

        int widthSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        content.measure(widthSpec, heightSpec);

        int[] location = new int[2];
        previewScanner.getLocationOnScreen(location);

        int margin = dpToPx(10);
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int popupWidth = Math.min(content.getMeasuredWidth(), screenWidth - (margin * 2));
        int popupHeight = content.getMeasuredHeight();

        int toastX = location[0] + Math.round(previewX) + dpToPx(14);
        int toastY = location[1] + Math.round(previewY) - dpToPx(16);

        toastX = Math.max(margin, Math.min(toastX, screenWidth - popupWidth - margin));
        toastY = Math.max(margin, Math.min(toastY, screenHeight - popupHeight - margin));

        PopupWindow popupWindow = new PopupWindow(
            content,
                popupWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );
        popupWindow.setTouchable(true);
        popupWindow.setOutsideTouchable(false);
        popupWindow.setBackgroundDrawable(new ColorDrawable(0x00000000));
        popupWindow.showAtLocation(previewScanner, Gravity.TOP | Gravity.START, toastX, toastY);
        smartToastPopup = popupWindow;

        mainHandler.removeCallbacks(dismissSmartToastRunnable);
        mainHandler.postDelayed(dismissSmartToastRunnable, SMART_TOAST_VISIBLE_MS);
    }

    private boolean isAlphanumeric(@NonNull String value) {
        return value.matches("^[A-Za-z0-9]+$");
    }

    @NonNull
    private String addSoftWrapHints(@NonNull String value) {
        if (value.length() <= 28) {
            return value;
        }

        StringBuilder wrapped = new StringBuilder(value.length() + 16);
        int runWithoutBreak = 0;

        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            wrapped.append(ch);
            runWithoutBreak++;

            boolean naturalBreak = ch == '/' || ch == '?' || ch == '&' || ch == '=' || ch == '-' || ch == '_' || ch == '.';
            if (naturalBreak) {
                wrapped.append('\n');
                runWithoutBreak = 0;
                continue;
            }

            if (runWithoutBreak >= 22) {
                wrapped.append('\n');
                runWithoutBreak = 0;
            }
        }

        return wrapped.toString();
    }

    private void copyToClipboard(@NonNull String value) {
        if (!isAdded()) {
            return;
        }

        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            showSimpleCenteredToast("No se pudo copiar");
            return;
        }

        ClipData clip = ClipData.newPlainText("escaneo", value);
        clipboard.setPrimaryClip(clip);
        showSimpleCenteredToast("Copiado");
    }

    @NonNull
    private String describeBarcodeType(@NonNull Barcode barcode) {
        switch (barcode.getFormat()) {
            case Barcode.FORMAT_QR_CODE:
                return "QR";
            case Barcode.FORMAT_AZTEC:
                return "Aztec";
            case Barcode.FORMAT_DATA_MATRIX:
                return "Data Matrix";
            case Barcode.FORMAT_PDF417:
                return "PDF417";
            case Barcode.FORMAT_CODABAR:
            case Barcode.FORMAT_CODE_39:
            case Barcode.FORMAT_CODE_93:
            case Barcode.FORMAT_CODE_128:
            case Barcode.FORMAT_EAN_8:
            case Barcode.FORMAT_EAN_13:
            case Barcode.FORMAT_ITF:
            case Barcode.FORMAT_UPC_A:
            case Barcode.FORMAT_UPC_E:
                return "Codigo de barras";
            default:
                return "Codigo";
        }
    }

    private void dismissSmartToast() {
        if (smartToastPopup != null) {
            smartToastPopup.dismiss();
            smartToastPopup = null;
        }
    }

    private boolean isLikelyWebLink(@NonNull String rawValue) {
        String value = rawValue.trim();
        if (value.isEmpty()) {
            return false;
        }
        if (value.startsWith("http://") || value.startsWith("https://") || value.startsWith("www.")) {
            return true;
        }
        return Patterns.WEB_URL.matcher(value).matches();
    }

    @NonNull
    private String normalizeWebUrl(@NonNull String rawValue) {
        String value = rawValue.trim();
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }
        return "https://" + value;
    }

    @NonNull
    private String buildInternetLookupUrl(@NonNull String rawValue) {
        String value = rawValue.trim();
        if (isLikelyWebLink(value)) {
            return normalizeWebUrl(value);
        }
        return "https://www.google.com/search?q=" + Uri.encode(value);
    }

    private void openScannedValueInInternet(@NonNull String rawValue) {
        if (!isAdded()) {
            return;
        }

        String value = rawValue.trim();
        if (value.isEmpty()) {
            return;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(buildInternetLookupUrl(value)));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException ignored) {
            showSimpleCenteredToast("No se encontro app para abrir internet");
        }
    }

    private void showSimpleCenteredToast(@NonNull String message) {
        // Toasts deshabilitados por requerimiento global.
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private static final class DetectedScanItem {
        @NonNull
        private final String rawValue;
        @NonNull
        private final String detectedType;

        private DetectedScanItem(@NonNull String rawValue, @NonNull String detectedType) {
            this.rawValue = rawValue;
            this.detectedType = detectedType;
        }
    }
}
