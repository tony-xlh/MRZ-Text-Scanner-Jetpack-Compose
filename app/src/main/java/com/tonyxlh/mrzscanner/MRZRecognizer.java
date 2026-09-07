package com.tonyxlh.mrzscanner;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.dynamsoft.core.basic_structures.CompletionListener;
import com.dynamsoft.core.basic_structures.EnumBufferOverflowProtectionMode;
import com.dynamsoft.core.basic_structures.ImageData;
import com.dynamsoft.core.basic_structures.ImageSourceAdapter;
import com.dynamsoft.core.intermediate_results.IntermediateResultExtraInfo;
import com.dynamsoft.cvr.CaptureVisionRouter;
import com.dynamsoft.cvr.CaptureVisionRouterException;
import com.dynamsoft.cvr.CapturedResult;
import com.dynamsoft.cvr.CapturedResultReceiver;
import com.dynamsoft.cvr.EnumPresetTemplate;
import com.dynamsoft.cvr.intermediate_results.IntermediateResultReceiver;
import com.dynamsoft.dcp.ParsedResult;
import com.dynamsoft.ddn.intermediate_results.DeskewedImageUnit;
import com.dynamsoft.dlr.RecognizedTextLinesResult;
import com.dynamsoft.dlr.TextLineResultItem;
import com.dynamsoft.dlr.intermediate_results.RawTextLinesUnit;
import com.dynamsoft.license.LicenseManager;
import com.dynamsoft.license.LicenseVerificationListener;

public class MRZRecognizer {
    private static final String TAG = "MRZRecognizer";
    private final CaptureVisionRouter router;
    private final FrameSource frameSource;
    private LinesListener listener;
    // The latest unverified MRZ lines from the intermediate results.
    private volatile String latestRawLines = "";
    // Dimensions of the latest fed image, used to map result coordinates.
    private volatile int imageWidth;
    private volatile int imageHeight;
    // While a file scan is active, camera frames are ignored so the overlay
    // only reflects the static image.
    private volatile boolean fileScanActive = false;
    // Whether a raw result arrived since the last final result. Used to decide
    // whether an empty final result should clear the overlay.
    private volatile boolean rawSinceLastFinal = false;

    public interface LinesListener {
        void onLines(String lines, boolean verified);

        default void onParsed(String codeType, HashMap<String, String> fields) {
        }

        /**
         * The polygons of the detected MRZ lines in the analyzed image space.
         *
         * @param quads  each entry holds 8 floats: x1,y1,x2,y2,x3,y3,x4,y4.
         * @param texts  the text of each line.
         * @param verified whether the line comes from the verified result.
         */
        default void onLineLocations(List<float[]> quads, List<String> texts, List<Boolean> verified,
                                     int imageWidth, int imageHeight) {
        }
    }

    private static class FrameSource extends ImageSourceAdapter {
        @Override
        public boolean hasNextImageToFetch() {
            return true;
        }
    }

    public MRZRecognizer(Context context) {
        initLicense(context);
        router = new CaptureVisionRouter(context);
        try {
            // Load the MRZ templates bundled in the SDK, like the IdScanner sample does.
            router.initSettingsFromFile("mrzscanner-mobile-templates.json");
        } catch (CaptureVisionRouterException e) {
            Log.e(TAG, "Failed to load MRZ templates: " + e.getMessage());
        }
        frameSource = new FrameSource();
        frameSource.setBufferOverflowProtectionMode(EnumBufferOverflowProtectionMode.BOPM_UPDATE);
        frameSource.setMaximumImageCount(3);
        try {
            router.setInput(frameSource);
        } catch (CaptureVisionRouterException e) {
            Log.e(TAG, "Failed to set input: " + e.getMessage());
        }
        router.addResultReceiver(new CapturedResultReceiver() {
            @Override
            public void onRecognizedTextLinesReceived(RecognizedTextLinesResult result) {
                if (result != null && result.getItems() != null && result.getItems().length > 0 && listener != null) {
                    StringBuilder sb = new StringBuilder();
                    List<float[]> quads = new ArrayList<>();
                    List<String> texts = new ArrayList<>();
                    List<Boolean> verifiedFlags = new ArrayList<>();
                    for (TextLineResultItem item : result.getItems()) {
                        sb.append(item.getText()).append("\n");
                        texts.add(item.getText());
                        quads.add(quadToFloats(item.getLocation()));
                        verifiedFlags.add(true);
                    }
                    listener.onLines(sb.toString().trim(), true);
                    listener.onLineLocations(quads, texts, verifiedFlags, imageWidth, imageHeight);
                } else if (listener != null && !rawSinceLastFinal) {
                    // Nothing was recognized in this frame: clear the overlay.
                    listener.onLineLocations(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                            imageWidth, imageHeight);
                }
                rawSinceLastFinal = false;
            }

            @Override
            public void onParsedResultsReceived(ParsedResult result) {
                if (result != null && result.getItems() != null && result.getItems().length > 0 && listener != null) {
                    listener.onParsed(result.getItems()[0].getCodeType(), result.getItems()[0].getParsedFields());
                }
            }
        });
        router.getIntermediateResultManager().addResultReceiver(new IntermediateResultReceiver() {
            @Override
            public void onRawTextLinesUnitReceived(RawTextLinesUnit u, IntermediateResultExtraInfo i) {
                if (u.getRawTextLines() != null && u.getRawTextLines().length > 0) {
                    StringBuilder sb = new StringBuilder();
                    for (int j = 0; j < u.getRawTextLines().length; j++) {
                        sb.append(u.getRawTextLines()[j].getText()).append("\n");
                    }
                    String newLines = sb.toString().trim();
                    if (newLines.isEmpty()) {
                        return;
                    }
                    rawSinceLastFinal = true;
                    String current = latestRawLines;
                    // Keep the most complete read: merge when a later unit carries fewer lines.
                    if (newLines.split("\n").length >= current.split("\n").length) {
                        latestRawLines = newLines;
                    } else {
                        java.util.LinkedHashSet<String> merged = new java.util.LinkedHashSet<>();
                        for (String line : current.split("\n")) merged.add(line);
                        for (String line : newLines.split("\n")) merged.add(line);
                        latestRawLines = String.join("\n", merged);
                    }
                    if (listener != null) {
                        listener.onLines(latestRawLines, false);
                    }
                    List<float[]> quads = new ArrayList<>();
                    List<String> texts = new ArrayList<>();
                    List<Boolean> verifiedFlags = new ArrayList<>();
                    for (int j = 0; j < u.getRawTextLines().length; j++) {
                        texts.add(u.getRawTextLines()[j].getText());
                        quads.add(quadToFloats(u.getRawTextLines()[j].getLocation()));
                        verifiedFlags.add(false);
                    }
                    // In file mode only the verified polygons are drawn: raw and
                    // verified results use different coordinate spaces, which would
                    // make the overlay jump between the two.
                    if (listener != null && !fileScanActive) {
                        listener.onLineLocations(quads, texts, verifiedFlags, imageWidth, imageHeight);
                    }
                }
            }
        });
    }

    private void initLicense(Context context) {
        LicenseManager.initLicense("DLS2eyJoYW5kc2hha2VDb2RlIjoiMjAwMDAxLTE2NDk4Mjk3OTI2MzUiLCJvcmdhbml6YXRpb25JRCI6IjIwMDAwMSIsInNlc3Npb25QYXNzd29yZCI6IndTcGR6Vm05WDJrcEQ5YUoifQ==", context, new LicenseVerificationListener() {
            @Override
            public void onLicenseVerified(boolean isSuccess, Exception error) {
                if (!isSuccess && error != null) {
                    error.printStackTrace();
                }
            }
        });
    }

    public void setLinesListener(LinesListener listener) {
        this.listener = listener;
    }

    private static float[] quadToFloats(com.dynamsoft.core.basic_structures.Quadrilateral quad) {
        if (quad == null || quad.points == null || quad.points.length < 4) {
            return null;
        }
        float[] out = new float[8];
        for (int i = 0; i < 4; i++) {
            Point p = quad.points[i];
            out[i * 2] = p.x;
            out[i * 2 + 1] = p.y;
        }
        return out;
    }

    /**
     * Start the video-mode scanning pipeline. Camera frames should be fed with
     * {@link #feedBitmap(Bitmap)} afterwards.
     */
    public void start() {
        router.startCapturing("ReadPassportAndId", new CompletionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "startCapturing ok");
            }

            @Override
            public void onFailure(int errorCode, String errorString) {
                Log.e(TAG, "startCapturing failed: " + errorCode + " " + errorString);
            }
        });
    }

    public void stop() {
        router.stopCapturing();
    }

    /**
     * Feed a camera frame into the video-mode pipeline.
     */
    public boolean acceptsCameraFrames() {
        return !fileScanActive;
    }

    public void feedBitmap(Bitmap bitmap) {
        if (fileScanActive) {
            return;
        }
        imageWidth = bitmap.getWidth();
        imageHeight = bitmap.getHeight();
        frameSource.addImageToBuffer(ImageData.fromBitmap(bitmap));
    }

    /**
     * Recognize a static image by feeding it through the video pipeline several times,
     * so the multi-frame verification of the SDK applies. Results are delivered to the
     * registered {@link LinesListener}.
     */
    public void scanBitmapAsFrames(Bitmap bitmap, int frames, long intervalMs) {
        fileScanActive = true;
        frameSource.clearBuffer();
        imageWidth = bitmap.getWidth();
        imageHeight = bitmap.getHeight();
        for (int i = 0; i < frames; i++) {
            frameSource.addImageToBuffer(ImageData.fromBitmap(bitmap));
            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Leave the file scan mode and resume the live camera pipeline.
     */
    public void endFileScan() {
        fileScanActive = false;
    }

    /**
     * Recognize the MRZ lines of a static image (e.g. picked from the gallery).
     * The verified result is preferred; if the single-frame verification does not pass,
     * the raw recognized lines from the intermediate results are returned.
     *
     * @return the recognized MRZ lines, or an empty array if nothing is recognized.
     */
    public String[] recognizeBitmap(Bitmap bitmap) {
        latestRawLines = "";
        String verified = null;
        try {
            CapturedResult capturedResult = router.capture(bitmap, "ReadPassportAndId");
            if (capturedResult != null) {
                RecognizedTextLinesResult textResult = capturedResult.getRecognizedTextLinesResult();
                if (textResult != null && textResult.getItems() != null && textResult.getItems().length > 0) {
                    StringBuilder sb = new StringBuilder();
                    for (TextLineResultItem item : textResult.getItems()) {
                        sb.append(item.getText()).append("\n");
                    }
                    verified = sb.toString().trim();
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "Failed to recognize MRZ from image");
            e.printStackTrace();
        }
        String result = verified != null ? verified : latestRawLines;
        if (result.isEmpty()) {
            return new String[0];
        }
        return result.split("\n");
    }
}
