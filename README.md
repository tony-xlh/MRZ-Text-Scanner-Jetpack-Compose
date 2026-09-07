# MRZ-Text-Scanner-Jetpack-Compose

An MRZ Text Scanner Project in Jetpack Compose.

It uses CameraX for camera access and [Dynamsoft MRZ Scanner](https://www.dynamsoft.com/mrz-scanner/docs/mobile/programming/android/index.html) to recognize and parse MRZ (machine-readable zone) text on passports, ID cards and visas.

Camera frames and picked images are fed into the video pipeline of `CaptureVisionRouter`, and the verified MRZ lines arrive through a result receiver:

```java
// Load the MRZ templates bundled in the SDK (required once).
router.initSettingsFromFile("mrzscanner-mobile-templates.json");
router.setInput(frameSource);
router.startCapturing("ReadPassportAndId");

// Push frames (camera or a picked image) into the buffer.
frameSource.addImageToBuffer(ImageData.fromBitmap(bitmap));
```

The app draws the detected MRZ lines as an overlay on the preview and can also read the MRZ from an image file picked with the system photo picker.

https://github.com/tony-xlh/MRZ-Text-Scanner-Jetpack-Compose/assets/112376616/1ea069d8-c27f-43d3-b9f6-8204d09037ff

## Blog

https://www.dynamsoft.com/codepool/mrz-text-scanner-in-jetpack-compose.html
