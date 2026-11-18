package com.gathin.tvhome;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * The now playing card under the top button: the cover, the app's name and state ("SPOTIFY · NOW PLAYING"), the
 * song and its artist, and a progress line with the time played and the song's length. The glass takes on the
 * cover's colour, strongest behind the cover and fading to the right. No playback buttons: the remote's own
 * play/pause keys reach the music app, and OK on the card opens the app's player screen. Up from the card goes
 * back to the top button and Down on to the dock (see MainActivity). Dimmed while paused. Hidden when nothing is
 * playing, and faded out while focus is on the rows below the dock, so the rows sliding up never pass under it.
 * Adapted from majnioui/CleanXiaomiTV (MIT), simplified: no playback buttons, no equalizer, no glass backdrop.
 */
final class NowPlayingCard extends LinearLayout {
    interface Exits {
        void up();

        void down();

        /** Menu on the card: hide it, and the cover behind Home, until another song plays. */
        void dismiss();
    }

    private static final int WIDTH = 156, PAD = 8, ART = 132, RADIUS = 22;
    private static final long TICK_MS = 1000;

    private final float density;
    private final GradientDrawable glass;
    private final ImageView art;
    /** The cover's colour across the glass; transparent without a cover. */
    private final GradientDrawable tint;
    private final TextView kicker, title, artist, elapsed, total;
    private final LinearLayout body;
    private final ProgressLine progress;
    private final LinearLayout times;
    private NowPlaying.Track track;
    /** The song the art on screen belongs to, and whether it is the real cover. */
    private String artKey;
    private boolean artIsCover;
    /** True while the card is held back (focus is on the rows below the dock); it comes back with the next dock focus. */
    private boolean suppressed;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            if (track != null && track.playing && getVisibility() == VISIBLE) postDelayed(this, TICK_MS);
        }
    };

    NowPlayingCard(Context ctx, Typeface font, Exits exits) {
        super(ctx);
        density = ctx.getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        setFocusable(true);
        setClickable(true);
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        setVisibility(GONE);
        setAlpha(0f);
        setPadding(dp(PAD), dp(PAD), dp(PAD), dp(PAD + 2));
        setOnClickListener(v -> {
            if (track != null) NowPlaying.open(getContext(), track);
        });
        setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    exits.up();
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    exits.down();
                    return true;
                case KeyEvent.KEYCODE_MENU:
                    exits.dismiss();
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    return true;    // the card is alone on its line: focus stays put
                default:
                    return false;
            }
        });
        setOnFocusChangeListener((v, focused) -> {
            setRing(focused);
            animate().scaleX(focused ? 1.02f : 1f).scaleY(focused ? 1.02f : 1f).setDuration(120);
        });

        glass = new GradientDrawable();
        glass.setColor(0xA6000000);
        glass.setCornerRadius(dp(RADIUS));
        setRing(false);
        tint = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0, 0});
        tint.setCornerRadius(dp(RADIUS));
        setBackground(new LayerDrawable(new Drawable[] {glass, tint}));

        art = new ImageView(ctx);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LayoutParams alp = new LayoutParams(dp(ART), dp(ART));
        alp.bottomMargin = dp(8);
        addView(art, alp);

        body = new LinearLayout(ctx);
        body.setOrientation(VERTICAL);
        kicker = text(ctx, font, 10.5f, 0x99FFFFFF);
        kicker.setLetterSpacing(0.12f);
        body.addView(kicker);
        title = text(ctx, font, 17, 0xFFFFFFFF);
        LayoutParams tlp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(3);
        body.addView(title, tlp);
        artist = text(ctx, font, 13, 0xA6FFFFFF);
        body.addView(artist);

        progress = new ProgressLine(ctx);
        LayoutParams plp = new LayoutParams(LayoutParams.MATCH_PARENT, dp(3));
        plp.topMargin = dp(9);
        body.addView(progress, plp);

        times = new LinearLayout(ctx);
        times.setOrientation(HORIZONTAL);
        elapsed = text(ctx, font, 10.5f, 0x99FFFFFF);
        total = text(ctx, font, 10.5f, 0x99FFFFFF);
        total.setGravity(Gravity.END);
        times.addView(elapsed, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        times.addView(total, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        LayoutParams tmlp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        tmlp.topMargin = dp(3);
        body.addView(times, tmlp);

        addView(body, new LayoutParams(dp(WIDTH - 2 * PAD), LayoutParams.WRAP_CONTENT));
    }

    /** Shows the track (dimmed while paused), or fades the card out for null. */
    void bind(NowPlaying.Track t) {
        track = t;
        removeCallbacks(tick);
        if (t == null) {
            if (getVisibility() == VISIBLE) {
                animate().alpha(0f).setDuration(200).withEndAction(() -> {
                    if (track == null) setVisibility(GONE);
                });
            }
            return;
        }
        title.setText(t.title);
        artist.setText(t.artist);
        artist.setVisibility(t.artist.isEmpty() ? GONE : VISIBLE);
        String state = getContext().getString(t.playing ? R.string.np_playing : R.string.np_paused);
        String app = appName(t.controller.getPackageName());
        kicker.setText(app.isEmpty() ? state : app + "  ·  " + state);
        // Apps like Spotify briefly send their own icon instead of the cover when play/pause changes:
        // keep the cover for the same song, and only take an icon when there is nothing better.
        String key = t.title + '\u0000' + t.artist;
        boolean sameSong = key.equals(artKey);
        if (t.art != null && (!sameSong || (t.artIsCover && !artIsCover))) {
            art.setImageBitmap(roundedSquare(t.art, dp(ART), dp(16)));
            art.setVisibility(VISIBLE);
            setTint(coverColor(t.art));
            artKey = key;
            artIsCover = t.artIsCover;
        } else if (t.art == null && !sameSong) {
            art.setVisibility(GONE);
            setTint(0);
            artKey = key;
            artIsCover = false;
        }
        float dim = t.playing ? 1f : 0.6f;
        body.setAlpha(dim);
        art.setAlpha(dim);
        updateProgress();
        if (suppressed) return;
        setVisibility(VISIBLE);
        animate().cancel();
        animate().alpha(1f).setDuration(200);
        if (t.playing) postDelayed(tick, TICK_MS);
    }

    /** Identifies the song shown (title and artist), or null; the same key MainActivity uses to keep a dismissed song hidden. */
    static String songKey(NowPlaying.Track t) {
        return t == null ? null : t.title + '\u0000' + t.artist;
    }

    String songKey() {
        return songKey(track);
    }

    /** The package of the song shown, or null: its app is spared when memory is freed. */
    String packageName() {
        return track == null ? null : track.controller.getPackageName();
    }

    /** True when the card is on screen and can take focus. */
    boolean canFocus() {
        return track != null && !suppressed && getVisibility() == VISIBLE && getAlpha() > 0.5f;
    }

    /**
     * Holds the card back while focus is on the rows below the dock, and brings it back when it returns to the
     * dock. The song is still tracked meanwhile, so it shows the right one when it comes back.
     */
    void setSuppressed(boolean hold) {
        if (hold == suppressed) return;
        suppressed = hold;
        if (track == null) return;
        animate().cancel();
        removeCallbacks(tick);
        if (hold) {
            animate().alpha(0f).setDuration(120).withEndAction(() -> {
                if (suppressed) setVisibility(INVISIBLE);
            });
        } else {
            setVisibility(VISIBLE);
            animate().alpha(1f).setDuration(150);
            if (track.playing) post(tick);
        }
    }

    /** Stops the clock that moves the progress line; call when Home leaves the screen. */
    void stop() {
        removeCallbacks(tick);
    }

    private void setRing(boolean focused) {
        glass.setStroke(focused ? dp(2) : Math.max(1, dp(1) / 2), focused ? 0xFFFFFFFF : 0x33FFFFFF);
    }

    private void updateProgress() {
        if (track == null || track.duration <= 0) {
            progress.setFraction(-1f);
            times.setVisibility(INVISIBLE);
            return;
        }
        long pos = track.position();
        if (pos < 0) {
            progress.setFraction(-1f);
            times.setVisibility(INVISIBLE);
            return;
        }
        progress.setFraction(Math.min(1f, (float) pos / track.duration));
        elapsed.setText(clock(pos));
        total.setText(clock(track.duration));
        times.setVisibility(VISIBLE);
    }

    private static String clock(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }

    /** The music app's name as the TV lists it, or "" when it can't be read. */
    private String appName(String pkg) {
        try {
            PackageManager pm = getContext().getPackageManager();
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0))).trim()
                    .toUpperCase(Locale.getDefault());
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return "";
        }
    }

    /** The cover's colour, from left to right: strong behind the cover, gone towards the end. */
    private void setTint(int color) {
        int c = color & 0x00FFFFFF;
        tint.setColors(color == 0 ? new int[] {0, 0}
                : new int[] {0x8C000000 | c, 0x40000000 | c, 0x0D000000 | c});
    }

    /**
     * A colour for the glass from the cover: its average, made a little more vivid and kept mid-bright,
     * so a dark or washed-out cover still gives a visible tint and a bright one doesn't glare.
     */
    private static int coverColor(Bitmap art) {
        Bitmap one = Bitmap.createScaledBitmap(art, 1, 1, true);
        int avg = one.getPixel(0, 0);
        if (one != art) one.recycle();
        float[] hsv = new float[3];
        Color.colorToHSV(avg, hsv);
        hsv[1] = Math.min(1f, hsv[1] * 1.4f);
        hsv[2] = Math.max(0.45f, Math.min(0.75f, hsv[2]));
        return Color.HSVToColor(hsv);
    }

    /** The picture cropped to a centred square, scaled to px, with rounded corners. */
    private static Bitmap roundedSquare(Bitmap src, int px, int radius) {
        int s = Math.min(src.getWidth(), src.getHeight());
        Bitmap square = Bitmap.createBitmap(src, (src.getWidth() - s) / 2, (src.getHeight() - s) / 2, s, s);
        Bitmap scaled = Bitmap.createScaledBitmap(square, px, px, true);
        Bitmap out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setShader(new BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        new Canvas(out).drawRoundRect(new RectF(0, 0, px, px), radius, radius, p);
        return out;
    }

    private static TextView text(Context ctx, Typeface font, float sp, int color) {
        TextView t = new TextView(ctx);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setIncludeFontPadding(false);
        if (font != null) t.setTypeface(font);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * density);
    }

    /** A thin track with the played part in white; hidden while the song's length or position is unknown. */
    private static final class ProgressLine extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private float fraction = -1f;

        ProgressLine(Context ctx) {
            super(ctx);
            track.setColor(0x40FFFFFF);
            fill.setColor(0xE6FFFFFF);
        }

        void setFraction(float f) {
            if (f == fraction) return;
            fraction = f;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            if (fraction < 0f) return;
            float h = getHeight(), r = h / 2f;
            rect.set(0, 0, getWidth(), h);
            c.drawRoundRect(rect, r, r, track);
            rect.set(0, 0, Math.max(h, getWidth() * fraction), h);
            c.drawRoundRect(rect, r, r, fill);
        }
    }
}
