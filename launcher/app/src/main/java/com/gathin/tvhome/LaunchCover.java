package com.gathin.tvhome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * Covers an app's first frames when it opens (config.xml launch_cover): the tile grows from where it
 * sits to the middle of the screen over a dark wash of its colour, holds while the app draws its first
 * frame (YouTube shows half a second of black even on a warm start), then fades out onto the app.
 *
 * Drawn in an overlay window so it stays above the app's window; needs "display over other apps"
 * (appops SYSTEM_ALERT_WINDOW), and without it the launch goes ahead with no cover. The window never
 * takes focus or touches, so the remote's keys reach the app the whole time.
 */
final class LaunchCover {
    private static final int GROW_MS = 220, WASH_MS = 140, FADE_MS = 260;
    /** Widest the grown tile gets, as a share of the screen width. */
    private static final float MAX_WIDTH = 0.34f;

    private LaunchCover() {
    }

    static boolean available(Context ctx) {
        return Settings.canDrawOverlays(ctx);
    }

    /**
     * Shows the cover for {@code card} (a tile on screen), lifting it after {@code holdMs} from now.
     * {@code artPx} is the width of the banner the tile shows as the app shipped it, 0 when unknown;
     * {@code fullArt}, when not null, is a sharper copy of that banner to draw the grown tile with.
     */
    static void show(Context ctx, View card, Drawable fullArt, int artPx, int color, float cornerRadius,
                     long holdMs) {
        WindowManager wm = ctx.getSystemService(WindowManager.class);
        int screenW = ctx.getResources().getDisplayMetrics().widthPixels;
        int screenH = ctx.getResources().getDisplayMetrics().heightPixels;
        int w = card.getWidth(), h = card.getHeight();
        if (wm == null || w == 0 || h == 0) return;

        // Grow no wider than the banner's own pixels: most apps ship a 320x180 banner, and stretching
        // it to a third of the screen (then the panel's 4K upscale) left it badly soft. A tile is never
        // shrunk below the size it is shown at, and an icon tile or a vector banner may use MAX_WIDTH.
        int shown = Math.round(w * card.getScaleX());
        int limit = Math.round(screenW * MAX_WIDTH);
        if (artPx > 0) limit = Math.min(limit, artPx);
        int tw = Math.max(shown, limit), th = Math.round(tw * (float) h / w);
        float k = tw / (float) w;
        Bitmap bmp = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.scale(k, k);
        ImageView banner = card.findViewById(R.id.banner);
        Drawable tileArt = banner != null ? banner.getDrawable() : null;
        if (fullArt != null && tileArt != null) banner.setImageDrawable(fullArt);
        card.draw(c);
        if (fullArt != null && tileArt != null) banner.setImageDrawable(tileArt);

        FrameLayout root = new FrameLayout(ctx);
        ColorDrawable wash = new ColorDrawable(blend(color, 0xFF0B0B10, 0.80f));
        wash.setAlpha(0);
        root.setBackground(wash);
        ImageView tile = new ImageView(ctx);
        tile.setImageBitmap(bmp);
        float radius = cornerRadius * k;
        tile.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        tile.setClipToOutline(true);
        tile.setElevation(24 * ctx.getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(tw, th);
        lp.leftMargin = (screenW - tw) / 2;
        lp.topMargin = (screenH - th) / 2;
        root.addView(tile, lp);

        // Start on the tile as it is drawn now (focus zoom included), then grow to the middle.
        int[] at = new int[2];
        card.getLocationOnScreen(at);
        float startScale = shown / (float) tw;
        float cx = at[0] + w / 2f, cy = at[1] + h / 2f;
        tile.setScaleX(startScale);
        tile.setScaleY(startScale);
        tile.setTranslationX(cx - screenW / 2f);
        tile.setTranslationY(cy - screenH / 2f);

        WindowManager.LayoutParams wp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        wp.windowAnimations = 0;
        try {
            wm.addView(root, wp);
        } catch (RuntimeException e) {   // permission revoked since the check: open the app uncovered
            bmp.recycle();
            return;
        }

        DecelerateInterpolator ease = new DecelerateInterpolator(2f);
        ValueAnimator washIn = ValueAnimator.ofInt(0, 255);
        washIn.setDuration(WASH_MS);
        washIn.addUpdateListener(a -> wash.setAlpha((int) a.getAnimatedValue()));
        washIn.start();
        tile.animate().scaleX(1f).scaleY(1f).translationX(0f).translationY(0f)
                .setDuration(GROW_MS).setInterpolator(ease).start();

        Handler main = new Handler(Looper.getMainLooper());
        main.postDelayed(() -> {
            tile.animate().scaleX(1.06f).scaleY(1.06f).setDuration(FADE_MS).setInterpolator(ease).start();
            // Fade the wash and the tile themselves, not the root: removing a window whose root is
            // mid-fade let one opaque frame of the wash through. The bitmap is left to the GC, since
            // the window may still draw it once after removeView.
            ValueAnimator fade = ValueAnimator.ofFloat(1f, 0f);
            fade.setDuration(FADE_MS);
            fade.addUpdateListener(a -> {
                float f = (float) a.getAnimatedValue();
                wash.setAlpha(Math.round(255 * f));
                tile.setAlpha(f);
            });
            fade.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    root.setVisibility(View.INVISIBLE);
                    main.postDelayed(() -> {
                        try {
                            wm.removeView(root);
                        } catch (RuntimeException ignored) {
                        }
                    }, 100);
                }
            });
            fade.start();
        }, Math.max(holdMs, GROW_MS));
    }

    private static int blend(int a, int b, float towardB) {
        return Color.rgb(
                Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * towardB),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * towardB),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * towardB));
    }
}
