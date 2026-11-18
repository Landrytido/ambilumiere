package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.widget.ImageView;

/**
 * The playing song's cover as a layer over a wallpaper: Home's and the clock screensaver's. It fades in while a
 * cover is available and out when the song is gone, so the wallpaper underneath comes back.
 * The layer is the cover blurred (shrunk to a few dozen pixels and stretched back) over the whole 16:9 screen, and
 * optionally the cover itself, sharp and whole, on top of it at coverShare of the screen's height, centred at
 * coverCenter of its width. Home leaves the sharp cover to its now playing card; the screensaver shows it.
 */
final class ArtBackdrop {
    private static final int SMALL = 24;
    /** The layer is drawn at 720p and stretched to the screen: sharp enough, and 3.7 MB. */
    private static final int WIDTH = 1280, HEIGHT = 720;
    /** A light black over the blurred fill only (never the sharp cover), so text on top stays readable. */
    private static final int SHADE = 0x33000000;
    private static final long FADE_MS = 700L;

    interface Listener {
        /** The sharp cover came up (true) or went away (false). Main thread. */
        void onCover(boolean up);
    }

    private final ImageView view;
    private final float coverShare, coverCenter;
    private final Listener listener;
    /** The cover the layer was built from, to skip rebuilding it on every metadata update. */
    private Bitmap shown;
    private boolean up;

    /** coverShare 0: blurred fill only. listener may be null. */
    ArtBackdrop(Context ctx, float coverShare, float coverCenter, Listener listener) {
        this.coverShare = coverShare;
        this.coverCenter = coverCenter;
        this.listener = listener;
        view = new ImageView(ctx);
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        view.setFocusable(false);
        view.setAlpha(0f);
        view.setVisibility(View.INVISIBLE);
    }

    ImageView view() {
        return view;
    }

    /** True while the sharp cover is on screen or fading in. */
    boolean coverUp() {
        return up && coverShare > 0f;
    }

    /** track null or without a real cover: fade out. Otherwise show its cover, rebuilding only when it changed. */
    void show(NowPlaying.Track track) {
        Bitmap art = track != null && track.artIsCover ? track.art : null;
        if (art == null) {
            hide();
            return;
        }
        if (art != shown) {
            Bitmap built = build(art);
            if (built == null) {
                hide();
                return;
            }
            view.setImageBitmap(built);
            shown = art;
        }
        view.animate().cancel();
        view.setVisibility(View.VISIBLE);
        view.animate().alpha(1f).setDuration(FADE_MS).start();
        setUp(true);
    }

    void hide() {
        view.animate().cancel();
        setUp(false);
        if (view.getVisibility() != View.VISIBLE) return;
        view.animate().alpha(0f).setDuration(FADE_MS)
                .withEndAction(() -> {
                    if (up) return;   // a song came back during the fade
                    view.setVisibility(View.INVISIBLE);
                    view.setImageDrawable(null);
                    shown = null;
                }).start();
    }

    /** Immediately gone, e.g. when the screen using it is stopped. */
    void clear() {
        view.animate().cancel();
        view.setAlpha(0f);
        view.setVisibility(View.INVISIBLE);
        view.setImageDrawable(null);
        shown = null;
        up = false;
    }

    private void setUp(boolean now) {
        if (up == now) return;
        up = now;
        if (listener != null && coverShare > 0f) listener.onCover(now);
    }

    private Bitmap build(Bitmap art) {
        try {
            Bitmap source = art.getConfig() == Bitmap.Config.HARDWARE ? art.copy(Bitmap.Config.ARGB_8888, false) : art;
            if (source == null) return null;
            int w = source.getWidth(), h = source.getHeight();
            if (w <= 0 || h <= 0) return null;
            int side = Math.min(w, h);
            Rect square = new Rect((w - side) / 2, (h - side) / 2, (w - side) / 2 + side, (h - side) / 2 + side);
            Paint smooth = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
            Bitmap small = Bitmap.createBitmap(SMALL, SMALL, Bitmap.Config.ARGB_8888);
            new Canvas(small).drawBitmap(source, square, new Rect(0, 0, SMALL, SMALL), smooth);
            Bitmap out = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(out);
            canvas.drawBitmap(small, null, new Rect(0, 0, WIDTH, HEIGHT), smooth);
            canvas.drawColor(SHADE);
            if (coverShare > 0f) {
                int cover = Math.round(HEIGHT * coverShare);
                int left = Math.round(WIDTH * coverCenter) - cover / 2, top = (HEIGHT - cover) / 2;
                Path round = new Path();
                round.addRoundRect(new RectF(left, top, left + cover, top + cover), cover * 0.04f, cover * 0.04f,
                        Path.Direction.CW);
                canvas.save();
                canvas.clipPath(round);
                canvas.drawBitmap(source, square, new Rect(left, top, left + cover, top + cover), smooth);
                canvas.restore();
            }
            small.recycle();
            if (source != art) source.recycle();
            return out;
        } catch (RuntimeException | OutOfMemoryError failure) {
            return null;
        }
    }
}
