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
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;

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

public class MainActivity extends AppCompatActivity {

    private Context context;
    private ViewGroup layoutMain;
    private TextView tvJoy, tvAnger, tvDisgust, tvNumPeople;

    private String photoPath = "";

    private AsyncFrameDetector asyncDetector;

    private boolean isCameraFront = true;
    private float lastTimestamp = -1f;
    private final float epsilon = .3f;
    private long firstFrameTime = -1;
    private float lastReceivedTimestamp = -1f;

    private CheckBox checkboxFlash, checkboxEmotion;
    private CameraView cameraView;
    private ImageButton buttonSnap;

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
        cameraView = findViewById(R.id.camera_view);
        cameraView.setOnCameraViewEventListener(new CameraView.OnCameraViewEventListener() {
            @Override
            public void onCameraFrameAvailable(byte[] frame, int width, int height, Frame.ROTATE rotation) {
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

            }

            @Override
            public void onSurfaceViewSizeChanged() {
                asyncDetector.reset();
            }
        });

        checkboxFlash = findViewById(R.id.checkbox_flash);

        buttonSnap = findViewById(R.id.button_snap);
        buttonSnap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                cameraView.takePhoto(picture, checkboxFlash.isChecked());
            }
        });

        ImageButton buttonFlip = findViewById(R.id.button_flip);
        buttonFlip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                isCameraFront = !isCameraFront;
                cameraView.stopCamera();
                cameraView.startCamera(isCameraFront ? CameraCore.CameraType.CAMERA_FRONT : CameraCore.CameraType.CAMERA_BACK);
                asyncDetector.reset();
            }
        });

        checkboxEmotion = findViewById(R.id.checkbox_emotion);
        checkboxEmotion.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkboxEmotion.setEnabled(false);
                if (asyncDetector.isRunning()) {
                    asyncDetector.stop();
                    tvAnger.setText("");tvDisgust.setText("");tvJoy.setText("");tvNumPeople.setText("X");
                } else {
                    asyncDetector.start();
                    tvAnger.setText("0");tvDisgust.setText("0");tvJoy.setText("0");
                }
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        checkboxEmotion.setEnabled(true);
                    }
                },500);
            }
        });

        tvJoy = findViewById(R.id.tv_Joy);
        tvAnger = findViewById(R.id.tv_Anger);
        tvDisgust = findViewById(R.id.tv_Disgust);
        tvNumPeople = findViewById(R.id.text_num_people);

        initEmotionSDK();
        cameraView.startCamera(isCameraFront ? CameraCore.CameraType.CAMERA_FRONT : CameraCore.CameraType.CAMERA_BACK);
    }

    private void initEmotionSDK() {
        asyncDetector = new AsyncFrameDetector(this);
        asyncDetector.setOnDetectorEventListener(new AsyncFrameDetector.OnDetectorEventListener() {
            @Override
            public void onImageResults(List<Face> faces, Frame image, float timeStamp) {
                if (timeStamp < lastReceivedTimestamp)
                    throw new RuntimeException("Got a timestamp out of order!");

                lastReceivedTimestamp = timeStamp;

                if (faces == null)
                    return;

                if (faces.size() == 0)
                    return;
                else
                    tvNumPeople.setText(faces.size());

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

                if (desiredState) {
                    asyncDetector.stop();
                    buttonSnap.setEnabled(false);
                    buttonSnap.setImageResource(R.drawable.shape_snap_selected);
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            buttonSnap.setImageResource(R.drawable.shape_snap);
                            cameraView.takePhoto(picture, checkboxFlash.isChecked());
                        }
                    },150);
                }
            }

            @Override
            public void onDetectorStarted() {

            }
        });
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
            tvAnger.setText("");tvDisgust.setText("");tvJoy.setText("");
            checkboxEmotion.setChecked(false);

            Intent intent = new Intent(context, PhotoActivity.class);
            intent.putExtra("photoPath", photoPath);
            startActivity(intent);
            overridePendingTransition(R.anim.anim_open_left, R.anim.anim_close_left);
        }
    };

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
        int rotationAngle = getCameraPhotoOrientation(this, PhotoProvider.getPhotoUri(new File(photoPath)), photoPath);

        Matrix matrix = new Matrix();
        matrix.preScale(1, -1);
        matrix.postRotate(rotationAngle + 270, (float) bm.getWidth() / 2, (float) bm.getHeight() / 2);
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
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_270:
                    return 270;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    return 180;
                case ExifInterface.ORIENTATION_ROTATE_90:
                    return 90;
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
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (Build.VERSION.SDK_INT >= 23) {
            requestCameraPermission();
        } else {
            cameraView.startCamera(isCameraFront ? CameraCore.CameraType.CAMERA_FRONT : CameraCore.CameraType.CAMERA_BACK);
        }

        buttonSnap.setEnabled(true);
    }

    @Override
    public void onPause() {
        super.onPause();

        if (asyncDetector != null && asyncDetector.isRunning()) {
            asyncDetector.stop();
            tvAnger.setText("");tvDisgust.setText("");tvJoy.setText("");
            checkboxEmotion.setChecked(false);
        }

        cameraView.stopCamera();
    }

    private Frame createFrameFromData(byte[] frameData, int width, int height, Frame.ROTATE rotation) {
        Frame.ByteArrayFrame frame = new Frame.ByteArrayFrame(frameData, width, height, Frame.COLOR_FORMAT.YUV_NV21);
        frame.setTargetRotation(rotation);
        return frame;
    }
}
