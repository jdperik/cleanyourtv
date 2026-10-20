package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The ambient glow: a soft radial light in the focused app's colour. Drawn at the view's own size with
 * dithering, so the faint gradient does not band into rings the way an upscaled low-resolution mask does.
 */
class GlowDrawable extends Drawable {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);

    @Override
    protected void onBoundsChange(@NonNull Rect b) {
        float w = b.width(), h = b.height();
        // Same falloff as the earlier 480x270 mask, scaled to the view.
        paint.setShader(new RadialGradient(
                b.left + w * 0.32f, b.top + h * 0.42f, Math.max(1f, w * 0.62f),
                new int[]{0x5CFFFFFF, 0x26FFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.45f, 1f},
                Shader.TileMode.CLAMP));
    }

    /** The glow's colour; the gradient supplies the alpha. */
    void setColor(int color) {
        paint.setColorFilter(new PorterDuffColorFilter(0xFF000000 | color, PorterDuff.Mode.SRC_IN));
        invalidateSelf();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        canvas.drawRect(getBounds(), paint);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
