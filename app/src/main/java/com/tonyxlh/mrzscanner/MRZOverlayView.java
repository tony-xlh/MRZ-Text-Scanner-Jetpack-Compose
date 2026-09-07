package com.tonyxlh.mrzscanner;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws an MRZ guide frame and the polygons of the detected MRZ text lines
 * on top of the camera preview. Line coordinates are given in the analyzed
 * image space and mapped to the view with the FILL_CENTER scale mode of the
 * PreviewView.
 */
public class MRZOverlayView extends View {
    private final Paint guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaintVerified = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaintRaw = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private List<float[]> quads = new ArrayList<>(); // 8 floats: x1,y1 ... x4,y4 in image space
    private List<String> texts = new ArrayList<>();
    private List<Boolean> verified = new ArrayList<>();
    private int imageWidth;
    private int imageHeight;
    // true when the target view displays the image with FIT_CENTER (file result view);
    // false for the camera preview (FILL_CENTER).
    private boolean fitCenter;

    public MRZOverlayView(Context context) {
        super(context);
        guidePaint.setStyle(Paint.Style.STROKE);
        guidePaint.setStrokeWidth(4f);
        guidePaint.setColor(Color.argb(220, 255, 214, 64));
        guidePaint.setPathEffect(new DashPathEffect(new float[]{20f, 14f}, 0f));

        linePaintVerified.setStyle(Paint.Style.STROKE);
        linePaintVerified.setStrokeWidth(6f);
        linePaintVerified.setColor(Color.argb(230, 80, 255, 140));

        linePaintRaw.setStyle(Paint.Style.STROKE);
        linePaintRaw.setStrokeWidth(6f);
        linePaintRaw.setColor(Color.argb(230, 255, 170, 40));

        labelPaint.setColor(Color.WHITE);
        labelPaint.setTextSize(34f);
        labelPaint.setShadowLayer(6f, 0f, 2f, Color.BLACK);
    }

    public void setFitCenter(boolean fitCenter) {
        this.fitCenter = fitCenter;
    }

    public void clearTargets() {
        quads = new ArrayList<>();
        texts = new ArrayList<>();
        verified = new ArrayList<>();
        postInvalidate();
    }

    public void setTargets(List<float[]> quads, List<String> texts, List<Boolean> verified, int imageWidth, int imageHeight) {
        this.quads = quads;
        this.texts = texts;
        this.verified = verified;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int vw = getWidth();
        int vh = getHeight();
        if (vw == 0 || vh == 0) {
            return;
        }

        // Guide frame: only for the live camera view, not the file result view.
        if (!fitCenter) {
            float guideWidth = vw * 0.88f;
            float guideHeight = vh * 0.22f;
            float guideLeft = (vw - guideWidth) / 2f;
            float guideTop = vh * 0.52f;
            canvas.drawRect(guideLeft, guideTop, guideLeft + guideWidth, guideTop + guideHeight, guidePaint);
        }

        if (imageWidth <= 0 || imageHeight <= 0 || quads.isEmpty()) {
            return;
        }

        // Map image-space coordinates to view coordinates.
        float scale;
        if (fitCenter) {
            scale = Math.min((float) vw / imageWidth, (float) vh / imageHeight);
        } else {
            scale = Math.max((float) vw / imageWidth, (float) vh / imageHeight);
        }
        float dx = (vw - imageWidth * scale) / 2f;
        float dy = (vh - imageHeight * scale) / 2f;

        for (int i = 0; i < quads.size(); i++) {
            float[] q = quads.get(i);
            if (q == null || q.length < 8) {
                continue;
            }
            Path path = new Path();
            for (int j = 0; j < 4; j++) {
                float x = q[j * 2] * scale + dx;
                float y = q[j * 2 + 1] * scale + dy;
                if (j == 0) {
                    path.moveTo(x, y);
                } else {
                    path.lineTo(x, y);
                }
            }
            path.close();
            boolean isVerified = verified.size() > i && verified.get(i);
            canvas.drawPath(path, isVerified ? linePaintVerified : linePaintRaw);
            if (texts.size() > i) {
                float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
                for (int j = 0; j < 4; j++) {
                    minX = Math.min(minX, q[j * 2] * scale + dx);
                    minY = Math.min(minY, q[j * 2 + 1] * scale + dy);
                }
                canvas.drawText(texts.get(i), minX, minY - 12f, labelPaint);
            }
        }
    }
}
