package com.example.sleppify;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;

import androidx.annotation.NonNull;

import com.bumptech.glide.load.Key;
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class ArtworkTrimTransformation extends BitmapTransformation {

    private static final String ID = "com.example.sleppify.ArtworkTrimTransformation.v2";
    private static final byte[] ID_BYTES = ID.getBytes(StandardCharsets.UTF_8);
    private static final ArtworkTrimTransformation INSTANCE = new ArtworkTrimTransformation();

    private static final float MAX_TRIM_RATIO = 0.36f;
    private static final float BORDER_DARK_NEUTRAL_RATIO = 0.74f;
    private static final float MIN_CENTER_BORDER_LUMA_DELTA = 0.04f;
    private static final int MIN_DIMENSION_FOR_TRIM = 80;
    private static final int ROW_COL_SAMPLE_STEP = 2;
    private static final int LUMA_SAMPLE_STEP = 3;
    private static final int EXTRA_TRIM_PX = 1;
    private static final int ALPHA_BORDER_THRESHOLD = 180;

    @NonNull
    public static ArtworkTrimTransformation obtain() {
        return INSTANCE;
    }

    private ArtworkTrimTransformation() {
    }

    @Override
    protected Bitmap transform(
            @NonNull BitmapPool pool,
            @NonNull Bitmap toTransform,
            int outWidth,
            int outHeight
    ) {
        int width = toTransform.getWidth();
        int height = toTransform.getHeight();
        if (width < MIN_DIMENSION_FOR_TRIM || height < MIN_DIMENSION_FOR_TRIM) {
            return toTransform;
        }

        Insets insets = detectInsets(toTransform);
        if (insets.isEmpty()) {
            return toTransform;
        }

        insets = expandInsets(toTransform, insets);

        int croppedWidth = width - insets.left - insets.right;
        int croppedHeight = height - insets.top - insets.bottom;
        if (croppedWidth <= 0 || croppedHeight <= 0) {
            return toTransform;
        }

        if (croppedWidth < Math.round(width * 0.55f) || croppedHeight < Math.round(height * 0.55f)) {
            return toTransform;
        }

        if (!hasMeaningfulCenterContrast(toTransform, insets)) {
            return toTransform;
        }

        try {
            return Bitmap.createBitmap(toTransform, insets.left, insets.top, croppedWidth, croppedHeight);
        } catch (Exception ignored) {
            return toTransform;
        }
    }

    @NonNull
    private Insets detectInsets(@NonNull Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        int maxTopBottom = Math.max(1, Math.round(height * MAX_TRIM_RATIO));
        int maxLeftRight = Math.max(1, Math.round(width * MAX_TRIM_RATIO));

        int top = 0;
        while (top < maxTopBottom && isDarkNeutralRow(bitmap, top, 0, width)) {
            top++;
        }

        int bottom = 0;
        while (bottom < maxTopBottom && isDarkNeutralRow(bitmap, height - 1 - bottom, 0, width)) {
            bottom++;
        }

        int yStart = top;
        int yEnd = Math.max(yStart + 1, height - bottom);

        int left = 0;
        while (left < maxLeftRight && isDarkNeutralColumn(bitmap, left, yStart, yEnd)) {
            left++;
        }

        int right = 0;
        while (right < maxLeftRight && isDarkNeutralColumn(bitmap, width - 1 - right, yStart, yEnd)) {
            right++;
        }

        int totalTrim = top + bottom + left + right;
        if (totalTrim < 4) {
            return Insets.EMPTY;
        }

        boolean hasVerticalTrim = (top + bottom) >= 2;
        boolean hasHorizontalTrim = (left + right) >= 2;
        if (!hasVerticalTrim && !hasHorizontalTrim) {
            return Insets.EMPTY;
        }

        return new Insets(left, top, right, bottom);
    }

    @NonNull
    private Insets expandInsets(@NonNull Bitmap bitmap, @NonNull Insets insets) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        int left = insets.left;
        int top = insets.top;
        int right = insets.right;
        int bottom = insets.bottom;

        if (left > 0) {
            left = Math.min(left + EXTRA_TRIM_PX, Math.max(0, width - right - 2));
        }
        if (right > 0) {
            right = Math.min(right + EXTRA_TRIM_PX, Math.max(0, width - left - 2));
        }
        if (top > 0) {
            top = Math.min(top + EXTRA_TRIM_PX, Math.max(0, height - bottom - 2));
        }
        if (bottom > 0) {
            bottom = Math.min(bottom + EXTRA_TRIM_PX, Math.max(0, height - top - 2));
        }

        return new Insets(left, top, right, bottom);
    }

    private boolean isDarkNeutralRow(@NonNull Bitmap bitmap, int row, int xStart, int xEnd) {
        int matches = 0;
        int samples = 0;
        for (int x = xStart; x < xEnd; x += ROW_COL_SAMPLE_STEP) {
            samples++;
            if (isLikelyBorderColor(bitmap.getPixel(x, row))) {
                matches++;
            }
        }
        if (samples == 0) {
            return false;
        }
        return (matches / (float) samples) >= BORDER_DARK_NEUTRAL_RATIO;
    }

    private boolean isDarkNeutralColumn(@NonNull Bitmap bitmap, int column, int yStart, int yEnd) {
        int matches = 0;
        int samples = 0;
        for (int y = yStart; y < yEnd; y += ROW_COL_SAMPLE_STEP) {
            samples++;
            if (isLikelyBorderColor(bitmap.getPixel(column, y))) {
                matches++;
            }
        }
        if (samples == 0) {
            return false;
        }
        return (matches / (float) samples) >= BORDER_DARK_NEUTRAL_RATIO;
    }

    private boolean isLikelyBorderColor(int color) {
        int alpha = Color.alpha(color);
        if (alpha <= ALPHA_BORDER_THRESHOLD) {
            // Transparent/semi-transparent padding around artwork is treated as border.
            return true;
        }

        float r = Color.red(color) / 255f;
        float g = Color.green(color) / 255f;
        float b = Color.blue(color) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float saturation = max <= 0f ? 0f : ((max - min) / max);

        return saturation <= 0.35f && max <= 0.82f;
    }

    private boolean hasMeaningfulCenterContrast(@NonNull Bitmap bitmap, @NonNull Insets insets) {
        float borderLuma = sampleBorderLuma(bitmap, insets);

        int width = bitmap.getWidth() - insets.left - insets.right;
        int height = bitmap.getHeight() - insets.top - insets.bottom;
        if (width <= 0 || height <= 0) {
            return false;
        }

        int cx0 = insets.left + Math.round(width * 0.2f);
        int cx1 = insets.left + Math.round(width * 0.8f);
        int cy0 = insets.top + Math.round(height * 0.2f);
        int cy1 = insets.top + Math.round(height * 0.8f);

        Rect centerRect = new Rect(cx0, cy0, Math.max(cx0 + 1, cx1), Math.max(cy0 + 1, cy1));
        float centerLuma = sampleRegionLuma(bitmap, centerRect, LUMA_SAMPLE_STEP);

        return centerLuma >= (borderLuma + MIN_CENTER_BORDER_LUMA_DELTA);
    }

    private float sampleBorderLuma(@NonNull Bitmap bitmap, @NonNull Insets insets) {
        float sum = 0f;
        int count = 0;

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        int leftLimit = insets.left;
        int rightStart = Math.max(insets.left, width - insets.right);
        int topLimit = insets.top;
        int bottomStart = Math.max(insets.top, height - insets.bottom);

        for (int y = 0; y < topLimit; y += LUMA_SAMPLE_STEP) {
            for (int x = 0; x < width; x += LUMA_SAMPLE_STEP) {
                sum += luma(bitmap.getPixel(x, y));
                count++;
            }
        }

        for (int y = bottomStart; y < height; y += LUMA_SAMPLE_STEP) {
            for (int x = 0; x < width; x += LUMA_SAMPLE_STEP) {
                sum += luma(bitmap.getPixel(x, y));
                count++;
            }
        }

        for (int x = 0; x < leftLimit; x += LUMA_SAMPLE_STEP) {
            for (int y = topLimit; y < bottomStart; y += LUMA_SAMPLE_STEP) {
                sum += luma(bitmap.getPixel(x, y));
                count++;
            }
        }

        for (int x = rightStart; x < width; x += LUMA_SAMPLE_STEP) {
            for (int y = topLimit; y < bottomStart; y += LUMA_SAMPLE_STEP) {
                sum += luma(bitmap.getPixel(x, y));
                count++;
            }
        }

        if (count == 0) {
            Rect full = new Rect(0, 0, width, height);
            return sampleRegionLuma(bitmap, full, LUMA_SAMPLE_STEP);
        }

        return sum / count;
    }

    private float sampleRegionLuma(@NonNull Bitmap bitmap, @NonNull Rect region, int step) {
        float sum = 0f;
        int count = 0;
        for (int y = region.top; y < region.bottom; y += step) {
            for (int x = region.left; x < region.right; x += step) {
                sum += luma(bitmap.getPixel(x, y));
                count++;
            }
        }
        if (count == 0) {
            return 0f;
        }
        return sum / count;
    }

    private float luma(int color) {
        float r = Color.red(color) / 255f;
        float g = Color.green(color) / 255f;
        float b = Color.blue(color) / 255f;
        return (0.2126f * r) + (0.7152f * g) + (0.0722f * b);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ArtworkTrimTransformation;
    }

    @Override
    public int hashCode() {
        return ID.hashCode();
    }

    @Override
    public void updateDiskCacheKey(@NonNull MessageDigest messageDigest) {
        messageDigest.update(ID_BYTES);
    }

    private static final class Insets {
        static final Insets EMPTY = new Insets(0, 0, 0, 0);

        final int left;
        final int top;
        final int right;
        final int bottom;

        Insets(int left, int top, int right, int bottom) {
            this.left = Math.max(0, left);
            this.top = Math.max(0, top);
            this.right = Math.max(0, right);
            this.bottom = Math.max(0, bottom);
        }

        boolean isEmpty() {
            return left == 0 && top == 0 && right == 0 && bottom == 0;
        }
    }
}
