package com.ku.autophoto;

import android.Manifest;
import android.content.Context;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

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
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import com.affectiva.android.affdex.sdk.Frame;
import com.affectiva.android.affdex.sdk.detector.CameraDetector;
import com.affectiva.android.affdex.sdk.detector.Detector;
import com.affectiva.android.affdex.sdk.detector.Face;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private Context context;
    private ViewGroup layoutMain;
    private Camera camera;
    private CameraPreview preview;
    private FrameLayout cameraView;
    private CameraDetector detector;
    private Detector.ImageListener detectorListener;
    private TextView tvJoy, tvAnger, tvDisgust;

    private String photoPath = "";
    private int currentCameraId;
    private boolean isSnapping;

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
        cameraView = findViewById(R.id.layout_camera);

        final CheckBox checkboxFlash = findViewById(R.id.checkbox_flash);

        ImageButton saveButton = findViewById(R.id.button_snap);
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!isSnapping) {
                    if (currentCameraId == Camera.CameraInfo.CAMERA_FACING_BACK) {
                        Camera.Parameters params = camera.getParameters();
                        params.setFlashMode((checkboxFlash.isChecked()) ? Camera.Parameters.FLASH_MODE_ON : Camera.Parameters.FLASH_MODE_OFF);
                        camera.setParameters(params);
                    }

                    camera.takePicture(null, null, picture);
                    isSnapping = true;
                }
            }
        });

        ImageButton flipButton = findViewById(R.id.button_flip);
        flipButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopCamera();

                if (currentCameraId == Camera.CameraInfo.CAMERA_FACING_BACK)
                    currentCameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;
                else
                    currentCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;

                startCamera(currentCameraId);
            }
        });

        tvJoy = findViewById(R.id.tv_Joy);
        tvAnger = findViewById(R.id.tv_Anger);
        tvDisgust = findViewById(R.id.tv_Disgust);

        detectorListener = new Detector.ImageListener() {
            @Override
            public void onImageResults(List<Face> faces, Frame frame, float v) {
                if (faces == null)
                    return;

                if (faces.size() == 0)
                    return;

                for (int i = 0 ; i < faces.size() ; i++) {
                    Face face = faces.get(i);

                    float joy = face.emotions.getJoy();
                    tvJoy.setText("Joy: " + String.valueOf((int) joy) + " %");
                    float anger = face.emotions.getAnger();
                    tvAnger.setText("Anger: " + String.valueOf((int) anger) + " %");
                    float disgust = face.emotions.getDisgust();
                    tvDisgust.setText("Disgust: " + String.valueOf((int) disgust) + " %");
                }
            }};

        startCamera(Camera.CameraInfo.CAMERA_FACING_FRONT);
    }

    public void startCamera(int cameraId) {
        currentCameraId = cameraId;
        camera = Camera.open(currentCameraId);
        setCameraProperties(currentCameraId, camera);

        preview = new CameraPreview(context, camera, this);
        detector = new CameraDetector(this, CameraDetector.CameraType.CAMERA_FRONT,
                preview, 1, Detector.FaceDetectorMode.LARGE_FACES);
        detector.setDetectAllEmotions(true);
        detector.setImageListener(detectorListener);
        detector.start();
        cameraView.addView(preview);
    }

    public void stopCamera() {
        isSnapping = false;
        preview.getHolder().removeCallback(preview);
        camera.stopPreview();
        camera.release();
        camera = null;
        cameraView.removeAllViews();
        detector.stop();
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
        matrix.postRotate(rotationAngle, (float) bm.getWidth() / 2, (float) bm.getHeight() / 2);
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

    private int getCameraPhotoOrientation(Context context, Uri imageUri, String imagePath){
        int rotate = 0;
        try {
            context.getContentResolver().notifyChange(imageUri, null);
            File imageFile = new File(imagePath);
            ExifInterface exif = new ExifInterface(imageFile.getAbsolutePath());
            int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED);
            switch (orientation) {
                case ExifInterface.ORIENTATION_NORMAL:
                    rotate = 0;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotate = 270;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotate = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotate = 90;
                    break;
            }
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
    public void onPause() {
        super.onPause();

        if (camera != null)
            stopCamera();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (camera == null) {
            if (Build.VERSION.SDK_INT >= 23) {
                requestCameraPermission();
            } else {
                startCamera(Camera.CameraInfo.CAMERA_FACING_FRONT);
            }
        }
    }

    public int getCurrentCameraId() {
        return currentCameraId;
    }

    public void setCurrentCameraId(int currentCameraId) {
        this.currentCameraId = currentCameraId;
    }

}
