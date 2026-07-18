package com.winlator.cmod.delta;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;

import com.winlator.cmod.R;
import com.winlator.cmod.inputcontrols.ExternalController;
import com.winlator.cmod.winhandler.WinHandler;

/**
 * Delta chassis: full-width composite UI from design-exported skin
 * (design/delta-chassis — Figma-style components, black integrated face).
 * Multi-touch hit zones match the 1080×980 artboard layout.
 */
public class DeltaChassisView extends View {
    private static final int C_NONE = 0;
    private static final int C_STICK = 1;
    private static final int C_PAD = 2;
    private static final int C_A = 3;
    private static final int C_B = 4;
    private static final int C_X = 5;
    private static final int C_Y = 6;
    private static final int C_L1 = 7;
    private static final int C_L2 = 8;
    private static final int C_R1 = 9;
    private static final int C_R2 = 10;
    private static final int C_SELECT = 11;
    private static final int C_VIEW = 12;
    private static final int C_GUIDE = 13;
    private static final int C_START = 14;
    private static final int C_DPAD = 15;

    /**
     * Hit regions in normalized skin coords (0..1 of drawn skin dst).
     * Calibrated to concept-matched photoreal skin (dock shell layout):
     * purple stick left, trackpad right, ABXY upper-right, guide top-center,
     * L1/L2/R1/R2 along bottom, system column upper-left.
     */
    // Stick well (circle)
    private static final float STICK_CX = 0.22f, STICK_CY = 0.55f, STICK_R = 0.13f;
    // Trackpad rect
    private static final float PAD_L = 0.42f, PAD_T = 0.40f, PAD_R = 0.90f, PAD_B = 0.78f;
    // Face diamond
    private static final float FACE_CX = 0.78f, FACE_CY = 0.28f, FACE_SP = 0.075f, FACE_R = 0.050f;
    // Guide Xbox
    private static final float GUIDE_CX = 0.50f, GUIDE_CY = 0.12f, GUIDE_R = 0.055f;
    // System left column + mid pills
    private static final float VIEW_CX = 0.38f, VIEW_CY = 0.26f, VIEW_RX = 0.04f, VIEW_RY = 0.035f;
    private static final float MENU_CX = 0.50f, MENU_CY = 0.26f;
    private static final float SELECT_CX = 0.14f, SELECT_CY = 0.18f, SELECT_R = 0.045f;
    private static final float SHARE_CX = 0.14f, SHARE_CY = 0.32f, SHARE_R = 0.045f;
    // Shoulders bottom row: L1 L2 | R1 R2
    private static final float SH_CY = 0.88f, SH_RX = 0.075f, SH_RY = 0.055f;
    private static final float L1_CX = 0.22f, L2_CX = 0.36f, R1_CX = 0.64f, R2_CX = 0.78f;
    // Reserved (no d-pad on shell skin)
    private static final float DPAD_CX = 0.14f, DPAD_CY = 0.45f, DPAD_R = 0.01f;

    private Bitmap skin;
    private Bitmap stickKnob;
    private final Rect src = new Rect();
    private final RectF dst = new RectF();
    private final Rect knobSrc = new Rect();
    private final RectF knobDst = new RectF();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint pressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final SparseArray<Integer> pointerControl = new SparseArray<>();
    private float lastPadX, lastPadY;
    private long lastPadT;
    private float padVx, padVy;
    private float knobNx = STICK_CX, knobNy = STICK_CY;

    private final Runnable padDecay = new Runnable() {
        @Override
        public void run() {
            boolean padHeld = false;
            for (int i = 0; i < pointerControl.size(); i++) {
                if (pointerControl.valueAt(i) == C_PAD) {
                    padHeld = true;
                    break;
                }
            }
            if (!padHeld) {
                padVx *= 0.90f;
                padVy *= 0.90f;
                if (Math.hypot(padVx, padVy) < 0.02f) {
                    padVx = 0f;
                    padVy = 0f;
                }
                publishSticks();
            }
            postDelayed(this, 16);
        }
    };

    public DeltaChassisView(Context context) {
        super(context);
        init(context);
    }

    public DeltaChassisView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        setClickable(true);
        setFocusable(false);
        setBackgroundColor(Color.BLACK);

        // Design-exported full chassis skin (black integrated UI, no product photo)
        skin = BitmapFactory.decodeResource(context.getResources(), R.drawable.delta_chassis_skin);
        if (skin == null) {
            skin = BitmapFactory.decodeResource(context.getResources(), R.drawable.delta_chassis_skin_portrait);
        }
        if (skin != null) {
            src.set(0, 0, skin.getWidth(), skin.getHeight());
        }

        stickKnob = BitmapFactory.decodeResource(context.getResources(), R.drawable.delta_el_stick_knob);
        if (stickKnob != null) {
            knobSrc.set(0, 0, stickKnob.getWidth(), stickKnob.getHeight());
        }

        pressPaint.setStyle(Paint.Style.FILL);
        pressPaint.setColor(0x44FFFFFF);
        post(padDecay);
    }

    public void bindWinHandler(WinHandler wh) {
        ChassisInputBridge.get().bind(wh);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(padDecay);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        computeDst(w, h);
    }

    /**
     * FILL the chassis pane edge-to-edge (no floating letterboxed product photo).
     * Slight vertical stretch is fine for a control surface.
     */
    private void computeDst(int w, int h) {
        if (w <= 0 || h <= 0) {
            dst.set(0, 0, 0, 0);
            return;
        }
        // Exact fill — integrated full-width controller face
        dst.set(0, 0, w, h);
    }

    private float sx(float nx) {
        return dst.left + nx * dst.width();
    }

    private float sy(float ny) {
        return dst.top + ny * dst.height();
    }

    private float srx(float nr) {
        return nr * dst.width();
    }

    private float sry(float nr) {
        return nr * dst.height();
    }

    private float sr(float nr) {
        return nr * Math.min(dst.width(), dst.height());
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(0xFF050508);
        if (skin != null && !dst.isEmpty()) {
            c.drawBitmap(skin, src, dst, paint);
        }

        // Press feedback rings
        drawPress(c, C_A, FACE_CX, FACE_CY + FACE_SP, FACE_R);
        drawPress(c, C_B, FACE_CX + FACE_SP, FACE_CY, FACE_R);
        drawPress(c, C_X, FACE_CX - FACE_SP, FACE_CY, FACE_R);
        drawPress(c, C_Y, FACE_CX, FACE_CY - FACE_SP, FACE_R);
        drawPress(c, C_GUIDE, GUIDE_CX, GUIDE_CY, GUIDE_R);
        drawPress(c, C_L1, L1_CX, SH_CY, SH_RX * 0.7f);
        drawPress(c, C_L2, L2_CX, SH_CY, SH_RX * 0.7f);
        drawPress(c, C_R1, R1_CX, SH_CY, SH_RX * 0.7f);
        drawPress(c, C_R2, R2_CX, SH_CY, SH_RX * 0.7f);

        // Live stick knob (design element export)
        if (stickOwned()) {
            float cx = sx(knobNx);
            float cy = sy(knobNy);
            float r = sr(STICK_R * 0.55f);
            if (stickKnob != null) {
                knobDst.set(cx - r, cy - r, cx + r, cy + r);
                c.drawBitmap(stickKnob, knobSrc, knobDst, paint);
            } else {
                paint.setColor(0xAAB24BFF);
                c.drawCircle(cx, cy, r, paint);
                paint.setColor(0xEEF0D0FF);
                c.drawCircle(cx - r * 0.2f, cy - r * 0.25f, r * 0.35f, paint);
            }
        }
    }

    private void drawPress(Canvas c, int control, float nx, float ny, float nr) {
        if (!owned(control)) return;
        float cx = sx(nx), cy = sy(ny), r = sr(nr);
        c.drawCircle(cx, cy, r * 1.15f, pressPaint);
    }

    private boolean stickOwned() {
        for (int i = 0; i < pointerControl.size(); i++) {
            if (pointerControl.valueAt(i) == C_STICK) return true;
        }
        return false;
    }

    private void publishSticks() {
        float maxR = Math.max(1f, sr(STICK_R * 0.55f));
        float dx = (sx(knobNx) - sx(STICK_CX)) / maxR;
        float dy = (sy(knobNy) - sy(STICK_CY)) / maxR;
        ChassisInputBridge.get().setStick(cl(dx), cl(dy), cl(padVx), cl(padVy));
    }

    private static float cl(float v) {
        return Math.max(-1f, Math.min(1f, v));
    }

    private int hitTest(float x, float y) {
        if (dst.width() <= 0 || dst.height() <= 0) return C_NONE;
        float nx = (x - dst.left) / dst.width();
        float ny = (y - dst.top) / dst.height();
        if (nx < -0.02f || nx > 1.02f || ny < -0.02f || ny > 1.02f) return C_NONE;

        // Face (priority)
        if (hitCN(nx, ny, FACE_CX, FACE_CY + FACE_SP, FACE_R * 1.2f)) return C_A;
        if (hitCN(nx, ny, FACE_CX + FACE_SP, FACE_CY, FACE_R * 1.2f)) return C_B;
        if (hitCN(nx, ny, FACE_CX - FACE_SP, FACE_CY, FACE_R * 1.2f)) return C_X;
        if (hitCN(nx, ny, FACE_CX, FACE_CY - FACE_SP, FACE_R * 1.2f)) return C_Y;
        if (hitCN(nx, ny, GUIDE_CX, GUIDE_CY, GUIDE_R * 1.25f)) return C_GUIDE;
        if (hitCN(nx, ny, SELECT_CX, SELECT_CY, SELECT_R * 1.2f)) return C_SELECT;
        if (hitCN(nx, ny, SHARE_CX, SHARE_CY, SHARE_R * 1.2f)) return C_VIEW;
        if (hitOvalN(nx, ny, VIEW_CX, VIEW_CY, VIEW_RX, VIEW_RY)) return C_VIEW;
        if (hitOvalN(nx, ny, MENU_CX, MENU_CY, VIEW_RX, VIEW_RY)) return C_START;
        if (hitOvalN(nx, ny, L1_CX, SH_CY, SH_RX, SH_RY)) return C_L1;
        if (hitOvalN(nx, ny, L2_CX, SH_CY, SH_RX, SH_RY)) return C_L2;
        if (hitOvalN(nx, ny, R1_CX, SH_CY, SH_RX, SH_RY)) return C_R1;
        if (hitOvalN(nx, ny, R2_CX, SH_CY, SH_RX, SH_RY)) return C_R2;
        if (hitCN(nx, ny, STICK_CX, STICK_CY, STICK_R * 1.08f)) return C_STICK;
        if (nx >= PAD_L && nx <= PAD_R && ny >= PAD_T && ny <= PAD_B) return C_PAD;
        return C_NONE;
    }

    private static boolean hitCN(float nx, float ny, float cx, float cy, float r) {
        float dx = (nx - cx);
        float dy = (ny - cy) * 1.05f;
        return dx * dx + dy * dy <= r * r;
    }

    private static boolean hitOvalN(float nx, float ny, float cx, float cy, float rx, float ry) {
        float dx = (nx - cx) / rx;
        float dy = (ny - cy) / ry;
        return dx * dx + dy * dy <= 1f;
    }

    private boolean owned(int control) {
        for (int i = 0; i < pointerControl.size(); i++) {
            if (pointerControl.valueAt(i) == control) return true;
        }
        return false;
    }

    private void pressControl(int control, boolean isDown) {
        ChassisInputBridge b = ChassisInputBridge.get();
        switch (control) {
            case C_A: b.setButton(ExternalController.IDX_BUTTON_A, isDown); break;
            case C_B: b.setButton(ExternalController.IDX_BUTTON_B, isDown); break;
            case C_X: b.setButton(ExternalController.IDX_BUTTON_X, isDown); break;
            case C_Y: b.setButton(ExternalController.IDX_BUTTON_Y, isDown); break;
            case C_L1: b.setButton(ExternalController.IDX_BUTTON_L1, isDown); break;
            case C_R1: b.setButton(ExternalController.IDX_BUTTON_R1, isDown); break;
            case C_L2: b.setTrigger(isDown, null); break;
            case C_R2: b.setTrigger(null, isDown); break;
            case C_SELECT:
            case C_VIEW: b.setButton(ExternalController.IDX_BUTTON_SELECT, isDown); break;
            case C_START:
            case C_GUIDE: b.setButton(ExternalController.IDX_BUTTON_START, isDown); break;
            case C_DPAD:
                // Map whole pad press to nothing directional for now; keep reserved
                break;
            case C_STICK:
                if (!isDown) {
                    knobNx = STICK_CX;
                    knobNy = STICK_CY;
                    publishSticks();
                    invalidate();
                }
                break;
            case C_PAD:
                if (!isDown) publishSticks();
                break;
            default: break;
        }
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        int index = e.getActionIndex();
        int id = e.getPointerId(index);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                float x = e.getX(index);
                float y = e.getY(index);
                int control = hitTest(x, y);
                if (control == C_NONE) return true;
                if (control != C_STICK && control != C_PAD && owned(control)) return true;
                if (control == C_STICK && owned(C_STICK)) return true;
                if (control == C_PAD && owned(C_PAD)) return true;
                pointerControl.put(id, control);
                pressControl(control, true);
                if (control == C_STICK) moveStick(x, y);
                if (control == C_PAD) {
                    lastPadX = x;
                    lastPadY = y;
                    lastPadT = System.nanoTime();
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                for (int i = 0; i < e.getPointerCount(); i++) {
                    int pid = e.getPointerId(i);
                    Integer ctl = pointerControl.get(pid);
                    if (ctl == null) continue;
                    float px = e.getX(i), py = e.getY(i);
                    if (ctl == C_STICK) {
                        moveStick(px, py);
                    } else if (ctl == C_PAD) {
                        long now = System.nanoTime();
                        float dt = Math.max(1e-3f, (now - lastPadT) / 1e9f);
                        float dx = px - lastPadX, dy = py - lastPadY;
                        lastPadX = px;
                        lastPadY = py;
                        lastPadT = now;
                        float inv = 1f / dt;
                        padVx = cl((dx * inv) / 900f * 1.35f);
                        padVy = cl((dy * inv) / 900f * 1.35f * 0.85f);
                        publishSticks();
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                Integer ctl = pointerControl.get(id);
                pointerControl.remove(id);
                if (ctl != null) pressControl(ctl, false);
                return true;
            }
            case MotionEvent.ACTION_CANCEL: {
                for (int i = 0; i < pointerControl.size(); i++) {
                    pressControl(pointerControl.valueAt(i), false);
                }
                pointerControl.clear();
                return true;
            }
        }
        return super.onTouchEvent(e);
    }

    private void moveStick(float x, float y) {
        float cx = sx(STICK_CX);
        float cy = sy(STICK_CY);
        float maxR = Math.max(1f, sr(STICK_R * 0.55f));
        float dx = x - cx, dy = y - cy;
        float mag = (float) Math.hypot(dx, dy);
        if (mag > maxR && mag > 0f) {
            dx = dx / mag * maxR;
            dy = dy / mag * maxR;
        }
        knobNx = STICK_CX + dx / dst.width();
        knobNy = STICK_CY + dy / dst.height();
        publishSticks();
        invalidate();
    }
}
