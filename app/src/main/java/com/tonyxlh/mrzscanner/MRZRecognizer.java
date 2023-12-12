package com.tonyxlh.mrzscanner;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.util.Log;

import com.dynamsoft.core.CoreException;
import com.dynamsoft.core.LicenseManager;
import com.dynamsoft.core.LicenseVerificationListener;
import com.dynamsoft.dlr.DLRResult;
import com.dynamsoft.dlr.LabelRecognizer;
import com.dynamsoft.dlr.LabelRecognizerException;

import java.io.InputStream;

public class MRZRecognizer {
    private Context context;
    private LabelRecognizer labelRecognizer;
    public MRZRecognizer(Context context){
        this.context = context;
        init();
    }

    private void init() {
        LicenseManager.initLicense("DLS2eyJoYW5kc2hha2VDb2RlIjoiMjAwMDAxLTE2NDk4Mjk3OTI2MzUiLCJvcmdhbml6YXRpb25JRCI6IjIwMDAwMSIsInNlc3Npb25QYXNzd29yZCI6IndTcGR6Vm05WDJrcEQ5YUoifQ==", context, new LicenseVerificationListener() {
            @Override
            public void licenseVerificationCallback(boolean isSuccess, CoreException error) {
                if(!isSuccess){
                    error.printStackTrace();
                }
            }
        });
        try {
            labelRecognizer = new LabelRecognizer();
            updateRuntimeSettingsForMRZ();
        } catch (LabelRecognizerException e) {
            throw new RuntimeException(e);
        }
    }

    private void updateRuntimeSettingsForMRZ() throws LabelRecognizerException {
        labelRecognizer.initRuntimeSettings("{\"CharacterModelArray\":[{\"DirectoryPath\":\"\",\"Name\":\"MRZ\"}],\"LabelRecognizerParameterArray\":[{\"Name\":\"default\",\"ReferenceRegionNameArray\":[\"defaultReferenceRegion\"],\"CharacterModelName\":\"MRZ\",\"LetterHeightRange\":[5,1000,1],\"LineStringLengthRange\":[30,44],\"LineStringRegExPattern\":\"([ACI][A-Z<][A-Z<]{3}[A-Z0-9<]{9}[0-9][A-Z0-9<]{15}){(30)}|([0-9]{2}[(01-12)][(01-31)][0-9][MF<][0-9]{2}[(01-12)][(01-31)][0-9][A-Z<]{3}[A-Z0-9<]{11}[0-9]){(30)}|([A-Z<]{0,26}[A-Z]{1,3}[(<<)][A-Z]{1,3}[A-Z<]{0,26}<{0,26}){(30)}|([ACIV][A-Z<][A-Z<]{3}([A-Z<]{0,27}[A-Z]{1,3}[(<<)][A-Z]{1,3}[A-Z<]{0,27}){(31)}){(36)}|([A-Z0-9<]{9}[0-9][A-Z<]{3}[0-9]{2}[(01-12)][(01-31)][0-9][MF<][0-9]{2}[(01-12)][(01-31)][0-9][A-Z0-9<]{8}){(36)}|([PV][A-Z<][A-Z<]{3}([A-Z<]{0,35}[A-Z]{1,3}[(<<)][A-Z]{1,3}[A-Z<]{0,35}<{0,35}){(39)}){(44)}|([A-Z0-9<]{9}[0-9][A-Z<]{3}[0-9]{2}[(01-12)][(01-31)][0-9][MF<][0-9]{2}[(01-12)][(01-31)][0-9][A-Z0-9<]{14}[A-Z0-9<]{2}){(44)}\",\"MaxLineCharacterSpacing\":130,\"TextureDetectionModes\":[{\"Mode\":\"TDM_GENERAL_WIDTH_CONCENTRATION\",\"Sensitivity\":8}],\"Timeout\":9999}],\"LineSpecificationArray\":[{\"BinarizationModes\":[{\"BlockSizeX\":30,\"BlockSizeY\":30,\"Mode\":\"BM_LOCAL_BLOCK\",\"MorphOperation\":\"Close\"}],\"LineNumber\":\"\",\"Name\":\"defaultTextArea->L0\"}],\"ReferenceRegionArray\":[{\"Localization\":{\"FirstPoint\":[0,0],\"SecondPoint\":[100,0],\"ThirdPoint\":[100,100],\"FourthPoint\":[0,100],\"MeasuredByPercentage\":1,\"SourceType\":\"LST_MANUAL_SPECIFICATION\"},\"Name\":\"defaultReferenceRegion\",\"TextAreaNameArray\":[\"defaultTextArea\"]}],\"TextAreaArray\":[{\"Name\":\"defaultTextArea\",\"LineSpecificationNameArray\":[\"defaultTextArea->L0\"]}]}");
        loadModel();
    }

    private void loadModel(){
        String modelFolder = "MRZ";
        String modelFileName = "MRZ";
        try {
            AssetManager manager = context.getAssets();
            InputStream isPrototxt = manager.open(modelFolder+"/"+modelFileName+".prototxt");
            byte[] prototxt = new byte[isPrototxt.available()];
            isPrototxt.read(prototxt);
            isPrototxt.close();
            InputStream isCharacterModel = manager.open(modelFolder+"/"+modelFileName+".caffemodel");
            byte[] characterModel = new byte[isCharacterModel.available()];
            isCharacterModel.read(characterModel);
            isCharacterModel.close();
            InputStream isTxt = manager.open(modelFolder+"/"+modelFileName+".txt");
            byte[] txt = new byte[isTxt.available()];
            isTxt.read(txt);
            isTxt.close();
            labelRecognizer.appendCharacterModelBuffer(modelFileName, prototxt, txt, characterModel);
        } catch (Exception e) {
            Log.d("DLR","Failed to load model");
            e.printStackTrace();
        }
    }

    public DLRResult[] recognizeBitmap(Bitmap bitmap) throws LabelRecognizerException {
        return labelRecognizer.recognizeImage(bitmap);
    }
}
