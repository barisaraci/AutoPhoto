package com.ku.autophoto;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.affectiva.android.affdex.sdk.Frame;
import com.affectiva.android.affdex.sdk.detector.Face;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity implements
        CameraView.OnCameraViewEventListener, AsyncFrameDetector.OnDetectorEventListener{

    private Context context;
    private ViewGroup layoutMain;
    private TextView tvJoy, tvAnger, tvDisgust;

    private String photoPath = "";

    private AsyncFrameDetector asyncDetector;
    private boolean isCameraFront = true;
    private boolean isCameraStarted  = false;
    private boolean isSDKRunning = false;

    private long numberCameraFramesReceived = 0;
    private long lastCameraFPSResetTime = -1L;
    private long numberSDKFramesReceived = 0;
    private long lastSDKFPSResetTime = -1L;

    private int startTime = 0;
    private float lastTimestamp = -1f;
    private final float epsilon = .3f;
    private long firstFrameTime = -1;
    private float lastReceivedTimestamp = -1f;

    private CheckBox checkboxFlash;
    private CameraView cameraView;
    private ImageButton saveButton;

    private static final int TARGET_WIDTH = 768;
    private static final int TARGET_HEIGHT = 1024;

    private static final int MY_PERMISSIONS_REQUEST_SNAP = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        context = this;
        layoutMain = (ViewGroup) getWindow().getDecorView().getRootView();

        if (Build.VERSION.SDK_INT >= 23) {
            requestCameraPermission();
        } else {
            init();
        }
    }

    private void init() {
        initEmotionSDK();

        cameraView = findViewById(R.id.camera_view);
        cameraView.setOnCameraViewEventListener(this);

        checkboxFlash = findViewById(R.id.checkbox_flash);

        saveButton = findViewById(R.id.button_snap);
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                cameraView.takePhoto(picture, checkboxFlash.isChecked());
            }
        });

        ImageButton flipButton = findViewById(R.id.button_flip);
        flipButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                isCameraFront = !isCameraFront;
                startCamera();
            }
        });

        tvJoy = findViewById(R.id.tv_Joy);
        tvAnger = findViewById(R.id.tv_Anger);
        tvDisgust = findViewById(R.id.tv_Disgust);

        startCamera();
    }

    private void initEmotionSDK(){
        asyncDetector = new AsyncFrameDetector(this);
        asyncDetector.setOnDetectorEventListener(this);

        final CheckBox checkboxEmotion = findViewById(R.id.checkbox_emotion);
        checkboxEmotion.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isSDKRunning) {
                    isSDKRunning = false;
                    asyncDetector.stop();
                    tvAnger.setText("");tvDisgust.setText("");tvJoy.setText("");
                } else {
                    isSDKRunning = true;
                    asyncDetector.start();
                }
                resetFPS();
            }
        });
    }

    public void startCamera() {
        if (isCameraStarted) {
            cameraView.stopCamera();
        }
        cameraView.startCamera(isCameraFront ? CameraCore.CameraType.CAMERA_FRONT : CameraCore.CameraType.CAMERA_BACK);
        isCameraStarted = true;
        asyncDetector.reset();
    }

    public void stopCamera() {
        if (!isCameraStarted)
            return;

        cameraView.stopCamera();
        isCameraStarted = false;
    }

    private Camera.PictureCallback picture = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            File photoFile = getOutputMediaFile();

            try {
                FileOutputStream fos = new FileOutputStream(photoFile);
                fos.write(data);
                fos.close();
            } catch (FileNotFoundException e) {
                Log.e(context.getResources().getString(R.string.app_name), "File not found: " + e.getMessage());
            } catch (IOException e) {
                Log.e(context.getResources().getString(R.string.app_name), "Error accessing file: " + e.getMessage());
            }

            adjustAndSaveBitmap(photoFile.getPath());

            Intent intent = new Intent(context, PhotoActivity.class);
            intent.putExtra("photoPath", photoPath);
            startActivity(intent);
        }
    };

    private void setCameraProperties(int cameraId, Camera camera) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        Camera.Parameters params = camera.getParameters();

        // orientation parameter
        int degrees = getWindowManager().getDefaultDisplay().getRotation();
        int rotation, displayRotation;

        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            rotation = (360 + info.orientation + degrees) % 360;
        } else {
            rotation = (360 + info.orientation - degrees) % 360;
        }
        params.setRotation(rotation);
        params.set("orientation", "portrait");

        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            displayRotation = (info.orientation + degrees) % 360;
            displayRotation = (360 - displayRotation) % 360;
        } else {
            displayRotation = (360 + info.orientation - degrees) % 360;
        }
        camera.setDisplayOrientation(displayRotation);

        // aspect ratio parameter
        List<Camera.Size> sizes = params.getSupportedPictureSizes();
        Camera.Size optimalSize = getOptimalPictureSize(sizes, TARGET_WIDTH, TARGET_HEIGHT);
        params.setPictureSize(optimalSize.width, optimalSize.height);
        params.setPreviewSize(optimalSize.width, optimalSize.height);

        // auto focus parameter
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK)
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);

        camera.setParameters(params);
    }

    private Camera.Size getOptimalPictureSize(List<Camera.Size> sizes, int w, int h) {
        if (sizes == null)
            return null;

        if (sizes.get(0).width > sizes.get(0).height) {
            int temp = w;
            w = h;
            h = temp;
        }

        final double ASPECT_TOLERANCE = 0.05;
        double targetRatio = (double) w / h;
        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;
        int targetHeight = h;

        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }

        return optimalSize;
    }

    private File getOutputMediaFile() {
        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), getResources().getString(R.string.app_name));

        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return null;
            }
        }

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        photoPath = mediaStorageDir.getPath() + File.separator + "IMG_"+ timeStamp + ".jpg";
        File mediaFile = new File(mediaStorageDir.getPath() + File.separator + "IMG_"+ timeStamp + ".jpg");
        return mediaFile;
    }

    private void adjustAndSaveBitmap(String photoPath) {
        Bitmap bm = BitmapFactory.decodeFile(photoPath);
        int rotationAngle = getCameraPhotoOrientation(this, Uri.fromFile(new File(photoPath)), photoPath);

        Matrix matrix = new Matrix();
        matrix.postRotate(getCameraRotation(), (float) bm.getWidth() / 2, (float) bm.getHeight() / 2);
        Bitmap finalBitmap = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), matrix, true);
        try {
            FileOutputStream fos = new FileOutputStream(photoPath);
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.flush();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        bm.recycle();
    }

    private int getCameraRotation(){
       return cameraView.getCameraRotation();
    }

    private int getCameraPhotoOrientation(Context context, Uri imageUri, String imagePath){
        int rotate = 0;
        try {
            context.getContentResolver().notifyChange(imageUri, null);
            File imageFile = new File(imagePath);
            ExifInterface exif = new ExifInterface(imageFile.getAbsolutePath());
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_270:
                    return 270;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    return 180;
                case ExifInterface.ORIENTATION_ROTATE_90:
                    return 90;
            }
            Log.d("try", "getCameraPhotoOrientation: " + orientation);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return rotate;
    }

    private void requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA) &&
                    ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE) &&
                    ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, MY_PERMISSIONS_REQUEST_SNAP);
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, MY_PERMISSIONS_REQUEST_SNAP);
            }
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            init();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_SNAP: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    init();
                } else {
                    Snackbar snackbar = Snackbar.make(layoutMain, getResources().getString(R.string.error_camera_permission), Snackbar.LENGTH_LONG).setAction("Action", null);
                    snackbar.getView().setBackgroundColor(ContextCompat.getColor(context, R.color.colorPrimaryDark));
                    snackbar.show();
                }
                return;
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (Build.VERSION.SDK_INT >= 23) {
            requestCameraPermission();
        } else {
            startCamera();
        }

        if (isSDKRunning && !asyncDetector.isRunning()) {
            asyncDetector.start();
        }

        resetFPS();
    }

    @Override
    public void onPause() {
        super.onPause();

        if (asyncDetector != null && asyncDetector.isRunning()) {
            asyncDetector.stop();
            tvAnger.setText("");tvDisgust.setText("");tvJoy.setText("");
        }

        stopCamera();
    }

    void resetFPS() {
        lastCameraFPSResetTime = lastSDKFPSResetTime = SystemClock.elapsedRealtime();
        numberCameraFramesReceived = numberSDKFramesReceived = 0;
    }

    @Override
    public void onImageResults(List<Face> faces, Frame image, float timeStamp) {
        //statusTextView.setText(String.format("Most recent time stamp: %.4f",timeStamp));
        if (timeStamp < lastReceivedTimestamp)
            throw new RuntimeException("Got a timestamp out of order!");
        lastReceivedTimestamp = timeStamp;
        Log.e("MainActivity", String.valueOf(timeStamp));

        if (faces == null)
            return;
        if (faces.size() == 0)
            return;

        boolean desiredState = true;
        float joy, anger, disgust;
        for (int i = 0 ; i < faces.size() ; i++) {
            Face face = faces.get(i);
            joy = face.emotions.getJoy(); if (joy < 90) desiredState = false;
            tvJoy.setText(String.valueOf((int) joy));
            anger = face.emotions.getAnger();
            tvAnger.setText(String.valueOf((int) anger));
            disgust = face.emotions.getDisgust();
            tvDisgust.setText(String.valueOf((int) disgust));
        }

        numberSDKFramesReceived += 1;
        if(numberSDKFramesReceived % 100 == 0) {
            Toast.makeText(this, "Frames received: " + numberSDKFramesReceived,
                    Toast.LENGTH_SHORT).show();
        }

        if(desiredState){
            asyncDetector.stop();
            cameraView.takePhoto(picture, checkboxFlash.isChecked());
            saveButton.setPressed(true);
            saveButton.invalidate();
            saveButton.setPressed(false);
            saveButton.invalidate();
        }
    }

    @Override
    public void onDetectorStarted() {
        /*Toast.makeText(this, "Detector started",
                Toast.LENGTH_SHORT).show();*/
    }

    @Override
    public void onCameraFrameAvailable(byte[] frame, int width, int height, Frame.ROTATE rotation) {
        numberCameraFramesReceived += 1;
        //cameraFPS.setText(String.format("CAM: %.3f", 1000f * (float) numberCameraFramesReceived / (SystemClock.elapsedRealtime() - lastCameraFPSResetTime)));
        Log.d("oncameraframeavailable", "abc");
        float timestamp = 0;
        long currentTime = SystemClock.elapsedRealtime();
        if (firstFrameTime == -1) {
            firstFrameTime = currentTime;
        } else {
            timestamp = (currentTime - firstFrameTime) / 1000f;
        }

        if (timestamp > (lastTimestamp + epsilon)) {
            lastTimestamp = timestamp;
            asyncDetector.process(createFrameFromData(frame,width,height,rotation),timestamp);
        }
    }

    @Override
    public void onCameraStarted(boolean success, Throwable error) {
        /*Toast.makeText(this, "Camera started",
                Toast.LENGTH_SHORT).show();*/
    }

    @Override
    public void onSurfaceViewSizeChanged() {
        asyncDetector.reset();
    }

    static Frame createFrameFromData(byte[] frameData, int width, int height, Frame.ROTATE rotation) {
        Frame.ByteArrayFrame frame = new Frame.ByteArrayFrame(frameData, width, height, Frame.COLOR_FORMAT.YUV_NV21);
        frame.setTargetRotation(rotation);
        return frame;
    }
}
