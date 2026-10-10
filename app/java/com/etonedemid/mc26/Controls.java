package com.etonedemid.mc26;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import org.libsdl.app.SDLActivity;

// On-screen controls over SDL's game view, as input the game can't tell from a keyboard and mouse: keys and mouse go in
// through SDL's own entry points (SDLActivity.onNativeKeyDown/Up, onNativeMouse), the way a real device's would.
// The same layout as the 1.12.2 build's touch.h:
//   in game (the game has captured the mouse):
//     left:   D-pad (W A S D; double-tap forward to sprint), sneak toggle in its middle
//     right:  jump, use (right button held: place, eat, draw a bow), drop; inventory beside the hotbar;
//             pause, camera view (F5) and debug screen (F3) at the top right; chat (with the keyboard) at the top left
//     hotbar: tap a slot to select it
//     elsewhere: drag to look, short tap to attack (left click), hold still to break (left button held)
//   menus: touches go straight to the game (SDL makes them the mouse); keyboard and settings (render resolution) buttons
//     at the top left; over a screen
//     opened from the game, a close button and a move button (edit the layout)
//   layout editor: every control shows, drag them (the D-pad moves as one), Done saves, Reset puts them back; offsets in
//     screen heights, kept in the app's preferences
public class Controls extends View {
    static native void nativeTextInput(boolean on);   // glue.c: SDL_StartTextInput/StopTextInput on SDL's thread

    // --- glyphs: 8x8 (doubled) or 16x16, '#' set ---
    static final String G_UP = "...##..." + "..####.." + ".######." + "########" + "...##..." + "...##..." + "...##..." + "...##...";
    static final String G_DOWN = "...##..." + "...##..." + "...##..." + "...##..." + "########" + ".######." + "..####.." + "...##...";
    static final String G_LEFT = "...#...." + "..##...." + ".###...." + "########" + "########" + ".###...." + "..##...." + "...#....";
    static final String G_RIGHT = "....#..." + "....##.." + "....###." + "########" + "########" + "....###." + "....##.." + "....#...";
    static final String G_JUMP = "...##..." + "..####.." + ".##..##." + "##....##" + "...##..." + "..####.." + ".##..##." + "##....##";
    static final String G_SNEAK =
        "..........#####." + "..........#####." + "..........#####." + "..........#####." + "..........#####." +
        "........#####..." + ".......#######.." + "......####.###.." + ".....####..###.." + "....####...###.." +
        "....###....##..." + "....###........." + "....###........." + "....###........." + "....###........." + "....###.........";
    static final String G_DROP = "...##..." + "...##..." + "...##..." + ".######." + "..####.." + "...##..." + "........" + "########";
    static final String G_INV = "########" + "#......#" + "#.####.#" + "########" + "#......#" + "#......#" + "#......#" + "########";
    static final String G_PAUSE = "........" + ".##..##." + ".##..##." + ".##..##." + ".##..##." + ".##..##." + ".##..##." + "........";
    static final String G_CAM = "........" + "..####.." + ".#....#." + "#..##..#" + "#..##..#" + ".#....#." + "..####.." + "........";
    static final String G_USE = "########" + "#......#" + "#..##..#" + "#.####.#" + "#.####.#" + "#..##..#" + "#......#" + "########";
    static final String G_KEYB = "........" + "########" + "#.#.#.##" + "########" + "##.#.#.#" + "########" + "#.####.#" + "########";
    static final String G_CHAT = "########" + "#......#" + "#.####.#" + "#......#" + "#.###..#" + "#......#" + "########" + ".##.....";
    static final String G_CLOSE = "##....##" + "###..###" + ".######." + "..####.." + "..####.." + ".######." + "###..###" + "##....##";
    static final String G_MOVE = "...##..." + "..####.." + ".#.##.#." + "########" + "########" + ".#.##.#." + "..####.." + "...##...";
    static final String G_CHECK = "........" + ".......#" + "......##" + "#....##." + "##..##.." + ".####..." + "..##...." + "........";
    static final String G_RESET = "..####.." + ".#....##" + "#....###" + "#......." + "#......#" + "#......#" + ".#....#." + "..####..";
    static final String G_F3 =
        "................" + "................" + "................" + "................" + "................" +
        "................" + ".#####...#####.." + ".#.......#...#.." + ".#...........#.." + ".####......##..." +
        ".#...........#.." + ".#.......#...#.." + ".#.......#####.." + "................" + "................" + "................";

    static final int ACT_KEY = 0, ACT_TOGGLE = 1, ACT_MOUSE = 2;
    static final class Button {
        final String glyph; final int action, key; RectF r = new RectF(); boolean pressed;
        Button(String glyph, int action, int key) { this.glyph = glyph; this.action = action; this.key = key; }
    }
    static final int B_SNEAK = 0, B_JUMP = 1, B_USE = 2, B_DROP = 3, B_INV = 4, B_PAUSE = 5, B_CAM = 6, B_CHAT = 7, B_DEBUG = 8, NB = 9;
    final Button[] buttons = {
        new Button(G_SNEAK, ACT_TOGGLE, KeyEvent.KEYCODE_SHIFT_LEFT), new Button(G_JUMP, ACT_KEY, KeyEvent.KEYCODE_SPACE),
        new Button(G_USE, ACT_MOUSE, 2 /* BUTTON_SECONDARY */), new Button(G_DROP, ACT_KEY, KeyEvent.KEYCODE_Q),
        new Button(G_INV, ACT_KEY, KeyEvent.KEYCODE_E), new Button(G_PAUSE, ACT_KEY, KeyEvent.KEYCODE_ESCAPE),
        new Button(G_CAM, ACT_KEY, KeyEvent.KEYCODE_F5), new Button(G_CHAT, ACT_KEY, KeyEvent.KEYCODE_T),
        new Button(G_F3, ACT_KEY, KeyEvent.KEYCODE_F3),
    };
    static final String G_GEAR = "..#..#.." + ".######." + ".##..##." + "###..###" + "###..###" + ".##..##." + ".######." + "..#..#..";
    final Button gearB = new Button(G_GEAR, ACT_KEY, 0);
    final Button closeB = new Button(G_CLOSE, ACT_KEY, KeyEvent.KEYCODE_ESCAPE), keybB = new Button(G_KEYB, ACT_KEY, 0),
        moveB = new Button(G_MOVE, ACT_KEY, 0), doneB = new Button(G_CHECK, ACT_KEY, 0), resetB = new Button(G_RESET, ACT_KEY, 0);
    final float[][] offs = new float[NB][2];   // layout: offsets in screen heights; offs[B_SNEAK] moves the D-pad
    float padCx, padCy, padB, hbX, hbY, hbSlot;
    volatile boolean inGame, screenFromGame;
    boolean editMode, keyboardOn;
    final SharedPreferences prefs;
    final File gameDir;

    Controls(Context c, File gameDir) {
        super(c);
        this.gameDir = gameDir;
        prefs = c.getSharedPreferences("controls", Context.MODE_PRIVATE);
        for (int i = 0; i < NB; i++) { offs[i][0] = prefs.getFloat("x" + i, 0); offs[i][1] = prefs.getFloat("y" + i, 0); }
    }

    // The game freed or captured the mouse (SDL's thread): in game vs a menu, and whether a menu was opened from the game.
    void setInGame(boolean g) {
        if (g == inGame) return;
        if (!g) screenFromGame = true; else { screenFromGame = false; if (keyboardOn) { keyboardOn = false; nativeTextInput(false); } }
        inGame = g;
        post(() -> { releaseAll(); invalidate(); });
    }

    // --- layout ---
    int guiScale() {   // the game's GUI scale: the largest that keeps 320x240 GUI pixels, capped by the option
        int opt = 0;
        try (BufferedReader r = new BufferedReader(new FileReader(new File(gameDir, "options.txt")))) {
            for (String l; (l = r.readLine()) != null; ) if (l.startsWith("guiScale:")) opt = Integer.parseInt(l.substring(9).trim());
        } catch (Exception e) { }
        int w = renderW(), h = renderH(), s = 1;
        while ((opt == 0 || s < opt) && w / (s + 1) >= 320 && h / (s + 1) >= 240) s++;
        return s;
    }
    // the game's own window size: the render resolution (McActivity.renderHeight), or the view's at native
    int renderH() { int h = ((McActivity) getContext()).renderHeight(); return h > 0 && h < getHeight() ? h : getHeight(); }
    int renderW() { return (int) ((long) getWidth() * renderH() / Math.max(1, getHeight())); }
    int cachedScale; long scaleAt;
    void layout() {
        float W = getWidth(), H = getHeight(), u = H / 720f, b = 92 * u, m = 48 * u, tb = 0.7f * b;
        padB = b; padCx = m + 1.5f * b + 48 * u + offs[B_SNEAK][0] * H; padCy = H - m - 1.5f * b - 40 * u + offs[B_SNEAK][1] * H;
        buttons[B_SNEAK].r.set(padCx - b / 2, padCy - b / 2, padCx + b / 2, padCy + b / 2);
        set(buttons[B_JUMP], W - m - 1.2f * b, padCy - 0.6f * b, 1.2f * b);
        set(buttons[B_USE], W - m - 2.2f * b, padCy - 1.6f * b, b);
        set(buttons[B_DROP], W - m - 0.95f * b, padCy - 1.9f * b, 0.7f * b);
        long now = System.currentTimeMillis();
        if (now - scaleAt > 2000) { cachedScale = guiScale(); scaleAt = now; }
        float s = cachedScale * H / renderH();   // GUI pixels of the game's window, in view pixels
        hbSlot = 20 * s; hbX = W / 2f - 91 * s; hbY = H - 22 * s;
        set(buttons[B_INV], hbX + 182 * s + 6 * u, H - 22 * s, 22 * s);
        set(buttons[B_PAUSE], W - m / 2 - tb, m / 2, tb);
        set(buttons[B_CAM], W - m / 2 - 2 * tb - 12 * u, m / 2, tb);
        set(buttons[B_DEBUG], W - m / 2 - 3 * tb - 24 * u, m / 2, tb);
        set(buttons[B_CHAT], m / 2, m / 2, tb);
        for (int i = 0; i < NB; i++) if (i != B_SNEAK) buttons[i].r.offset(offs[i][0] * H, offs[i][1] * H);
        set(closeB, W - m / 2 - tb, m / 2, tb);
        set(keybB, m / 2, m / 2, tb);
        set(gearB, m / 2 + tb + 12 * u, m / 2, tb);
        set(moveB, m / 2 + 2 * tb + 24 * u, m / 2, tb);
        set(doneB, W / 2f + 6 * u, m / 2, tb);
        set(resetB, W / 2f - tb - 6 * u, m / 2, tb);
    }
    static void set(Button b, float x, float y, float size) { b.r.set(x, y, x + size, y + size); }
    void saveLayout() {
        SharedPreferences.Editor e = prefs.edit();
        for (int i = 0; i < NB; i++) e.putFloat("x" + i, offs[i][0]).putFloat("y" + i, offs[i][1]);
        e.apply();
    }

    // --- input to the game ---
    final boolean[] keys = new boolean[512];
    int mouseMask;
    void key(int code, boolean down) {
        if (keys[code] == down) return;
        keys[code] = down;
        if (down) SDLActivity.onNativeKeyDown(code); else SDLActivity.onNativeKeyUp(code);
    }
    void tapKey(int code) { key(code, true); key(code, false); }
    void mouse(int button, boolean down) {   // SDL wants the whole button state with each press/release
        int m = down ? mouseMask | button : mouseMask & ~button;
        if (m == mouseMask) return;
        mouseMask = m;
        SDLActivity.onNativeMouse(m, down ? 0 /* ACTION_DOWN */ : 1 /* ACTION_UP */, 0, 0, true);
    }
    void look(float dx, float dy) { SDLActivity.onNativeMouse(mouseMask, 2 /* ACTION_MOVE */, dx, dy, true); }
    void releaseAll() {
        for (int k = 0; k < keys.length; k++) if (keys[k] && !(k == KeyEvent.KEYCODE_SHIFT_LEFT && buttons[B_SNEAK].pressed)) key(k, false);
        mouse(1, false); mouse(2, false);
        for (Button b : buttons) if (b.action != ACT_TOGGLE) b.pressed = false;
        padDirs = 0; sprint = false;
        for (Finger f : fingers) f.role = R_NONE;
    }

    // --- fingers ---
    static final int R_NONE = 0, R_LOOK = 1, R_PAD = 2, R_BUTTON = 3, R_DRAG = 4, R_PASS = 5;
    static final class Finger { int id = -1, role, button; boolean moved, breaking; float x, y, sx, sy; long t; }
    final Finger[] fingers = new Finger[10];
    { for (int i = 0; i < fingers.length; i++) fingers[i] = new Finger(); }
    int padDirs; boolean sprint; long fwdLast;
    Finger finger(int id) { for (Finger f : fingers) if (f.role != R_NONE && f.id == id) return f; return null; }

    void padUpdate(float x, float y) {
        float dx = x - padCx, dy = y - padCy, dz = padB * 0.35f;
        int d = 0;
        if (Math.abs(dx) > dz || Math.abs(dy) > dz) {   // 8 directions: diagonals between two arrows
            int sector = (int) Math.round(Math.atan2(-dy, dx) / (Math.PI / 4)) & 7;
            d = new int[] { 8, 8 | 1, 1, 1 | 4, 4, 4 | 2, 2, 2 | 8 }[sector];
        }
        if ((d & 1) != 0 && (padDirs & 1) == 0) {   // forward pressed twice within 0.3 s: sprint
            long t = System.currentTimeMillis();
            if (t - fwdLast < 300) sprint = true;
            fwdLast = t;
        }
        if ((d & 1) == 0) sprint = false;
        padDirs = d;
        key(KeyEvent.KEYCODE_W, (d & 1) != 0); key(KeyEvent.KEYCODE_S, (d & 2) != 0);
        key(KeyEvent.KEYCODE_A, (d & 4) != 0); key(KeyEvent.KEYCODE_D, (d & 8) != 0);
        key(KeyEvent.KEYCODE_CTRL_LEFT, sprint);
        invalidate();
    }
    boolean onPad(float x, float y) { return Math.abs(x - padCx) < 1.7f * padB && Math.abs(y - padCy) < 1.7f * padB; }
    static boolean in(Button b, float x, float y, float pad) {
        return x >= b.r.left - pad && x < b.r.right + pad && y >= b.r.top - pad && y < b.r.bottom + pad;
    }

    int dragItem = -1; float dragX, dragY;
    boolean editTouch(int act, float x, float y) {
        if (act == MotionEvent.ACTION_DOWN || act == MotionEvent.ACTION_POINTER_DOWN) {
            if (in(doneB, x, y, 8)) { editMode = false; saveLayout(); invalidate(); return true; }
            if (in(resetB, x, y, 8)) { for (float[] o : offs) { o[0] = 0; o[1] = 0; } saveLayout(); layout(); invalidate(); return true; }
            dragItem = Math.abs(x - padCx) < 1.5f * padB && Math.abs(y - padCy) < 1.5f * padB ? B_SNEAK : -1;
            for (int i = 0; i < NB && dragItem < 0; i++) if (i != B_SNEAK && in(buttons[i], x, y, 8)) dragItem = i;
            dragX = x; dragY = y;
        } else if (act == MotionEvent.ACTION_MOVE && dragItem >= 0) {
            float H = getHeight();
            offs[dragItem][0] += (x - dragX) / H; offs[dragItem][1] += (y - dragY) / H;
            dragX = x; dragY = y; layout(); invalidate();
        } else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_POINTER_UP) {
            if (dragItem >= 0) saveLayout();
            dragItem = -1;
        }
        return true;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        int act = e.getActionMasked(), idx = e.getActionIndex();
        layout();
        if (editMode) return editTouch(act, e.getX(idx), e.getY(idx));
        if (!inGame) {   // menus: only our buttons; anything else goes to the game (SDL makes it the mouse)
            if (act == MotionEvent.ACTION_DOWN) {
                float x = e.getX(), y = e.getY();
                if (in(keybB, x, y, 8)) { keyboardOn = !keyboardOn; nativeTextInput(keyboardOn); invalidate(); return true; }
                if (in(gearB, x, y, 8)) { ((McActivity) getContext()).openSettings(); return true; }
                if (screenFromGame && in(moveB, x, y, 8)) { releaseAll(); editMode = true; invalidate(); return true; }
                if (screenFromGame && in(closeB, x, y, 8)) { tapKey(KeyEvent.KEYCODE_ESCAPE); return true; }
            }
            return false;
        }
        long now = System.currentTimeMillis();
        switch (act) {
            case MotionEvent.ACTION_DOWN: case MotionEvent.ACTION_POINTER_DOWN: {
                float x = e.getX(idx), y = e.getY(idx);
                Finger f = null;
                for (Finger g : fingers) if (g.role == R_NONE) { f = g; break; }
                if (f == null) return true;
                f.id = e.getPointerId(idx); f.x = f.sx = x; f.y = f.sy = y; f.t = now; f.moved = f.breaking = false; f.role = R_NONE;
                for (int i = 0; i < NB; i++) {
                    Button b = buttons[i];
                    if (i == B_SNEAK || !in(b, x, y, 8)) continue;
                    f.role = R_BUTTON; f.button = i; b.pressed = true;
                    if (b.action == ACT_MOUSE) mouse(b.key, true); else key(b.key, true);
                    if (i == B_CHAT) postDelayed(() -> { keyboardOn = true; nativeTextInput(true); }, 150);
                    invalidate();
                    return true;
                }
                if (in(buttons[B_SNEAK], x, y, 0)) {   // the D-pad's middle: sneak on/off
                    f.role = R_BUTTON; f.button = B_SNEAK;
                    buttons[B_SNEAK].pressed = !buttons[B_SNEAK].pressed;
                    key(KeyEvent.KEYCODE_SHIFT_LEFT, buttons[B_SNEAK].pressed);
                    invalidate();
                    return true;
                }
                if (onPad(x, y)) { f.role = R_PAD; padUpdate(x, y); return true; }
                if (x >= hbX && x < hbX + 9 * hbSlot && y >= hbY) {   // hotbar slot
                    tapKey(KeyEvent.KEYCODE_1 + (int) ((x - hbX) / hbSlot));
                    f.role = R_PASS;
                    return true;
                }
                f.role = R_LOOK;
                postDelayed(this::checkBreaking, 310);
                return true;
            }
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < e.getPointerCount(); i++) {
                    Finger f = finger(e.getPointerId(i));
                    if (f == null) continue;
                    float x = e.getX(i), y = e.getY(i);
                    if (f.role == R_PAD) padUpdate(x, y);
                    else if (f.role == R_LOOK) {
                        float s = 2.2f * 720f / getHeight();   // as 1.12.2: 2.2 mouse pixels per finger pixel at 720 lines
                        if (Math.abs(x - f.sx) + Math.abs(y - f.sy) > 12 * getHeight() / 720f) f.moved = true;
                        look((x - f.x) * s, (y - f.y) * s);
                    }
                    f.x = x; f.y = y;
                }
                return true;
            case MotionEvent.ACTION_UP: case MotionEvent.ACTION_POINTER_UP: case MotionEvent.ACTION_CANCEL: {
                Finger f = finger(e.getPointerId(idx));
                if (f == null) return true;
                if (f.role == R_PAD) padUpdate(padCx, padCy);
                else if (f.role == R_BUTTON) {
                    Button b = buttons[f.button];
                    if (b.action == ACT_MOUSE) { b.pressed = false; mouse(b.key, false); }
                    else if (b.action == ACT_KEY) { b.pressed = false; key(b.key, false); }
                    invalidate();
                } else if (f.role == R_LOOK) {
                    if (f.breaking) mouse(1, false);
                    else if (!f.moved && now - f.t < 300) { mouse(1, true); mouse(1, false); }   // tap: attack
                }
                f.role = R_NONE;
                return true;
            }
        }
        return true;
    }
    // a look finger held still for 0.3 s breaks (left button held until it lifts)
    void checkBreaking() {
        long now = System.currentTimeMillis();
        for (Finger f : fingers)
            if (f.role == R_LOOK && !f.breaking && !f.moved && now - f.t >= 300) { f.breaking = true; mouse(1, true); }
    }

    // --- drawing ---
    final Paint border = new Paint(), face = new Paint(), glyphPaint = new Paint();
    final java.util.HashMap<String, Bitmap> bitmaps = new java.util.HashMap<>();
    Bitmap glyph(String g) {
        Bitmap b = bitmaps.get(g);
        if (b != null) return b;
        int n = g.length() == 256 ? 16 : 8;
        b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) if (g.charAt(y * n + x) == '#') b.setPixel(x, y, 0xffffffff);
        bitmaps.put(g, b);
        return b;
    }
    void draw(Canvas c, Button b, boolean pressed) { draw(c, b.r, b.glyph, pressed); }
    void draw(Canvas c, RectF r, String g, boolean pressed) {
        float e = 2 * getHeight() / 720f;
        border.setColor(pressed ? 0x8cffffff : 0x47ffffff);
        face.setColor(pressed ? 0x40000000 : 0x66000000);
        c.drawRect(r, border);
        c.drawRect(r.left + e, r.top + e, r.right - e, r.bottom - e, face);
        float gs = Math.min(r.width(), r.height()) * 0.5f, gx = r.centerX() - gs / 2, gy = r.centerY() - gs / 2;
        glyphPaint.setAlpha(pressed ? 255 : 204);
        glyphPaint.setFilterBitmap(false);
        Bitmap bm = glyph(g);
        c.drawBitmap(bm, new Rect(0, 0, bm.getWidth(), bm.getHeight()), new RectF(gx, gy, gx + gs, gy + gs), glyphPaint);
    }
    void drawPad(Canvas c, boolean pressedState) {
        float b = padB;
        draw(c, new RectF(padCx - b / 2, padCy - 1.5f * b, padCx + b / 2, padCy - 0.5f * b), G_UP, pressedState && (padDirs & 1) != 0);
        draw(c, new RectF(padCx - b / 2, padCy + 0.5f * b, padCx + b / 2, padCy + 1.5f * b), G_DOWN, pressedState && (padDirs & 2) != 0);
        draw(c, new RectF(padCx - 1.5f * b, padCy - b / 2, padCx - 0.5f * b, padCy + b / 2), G_LEFT, pressedState && (padDirs & 4) != 0);
        draw(c, new RectF(padCx + 0.5f * b, padCy - b / 2, padCx + 1.5f * b, padCy + b / 2), G_RIGHT, pressedState && (padDirs & 8) != 0);
    }
    @Override protected void onDraw(Canvas c) {
        layout();
        if (editMode) {
            drawPad(c, false);
            for (Button b : buttons) draw(c, b, true);
            draw(c, doneB, false); draw(c, resetB, false);
            return;
        }
        if (!inGame) {
            draw(c, keybB, keyboardOn);
            draw(c, gearB, false);
            if (screenFromGame) { draw(c, moveB, false); draw(c, closeB, false); }
            return;
        }
        drawPad(c, true);
        for (Button b : buttons) draw(c, b, b.pressed);
    }
}
