package com.ku.autophoto.activities;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
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

import com.affectiva.android.affdex.sdk.Frame;
import com.affectiva.android.affdex.sdk.detector.Face;
import com.google.android.material.snackbar.Snackbar;
import com.ku.autophoto.utility_camera.AsyncFrameDetector;
import com.ku.autophoto.utility_camera.CameraCore;
import com.ku.autophoto.utility_camera.CameraView;
import com.ku.autophoto.utility.PhotoProvider;
import com.ku.autophoto.R;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private Context context;
    private ViewGroup layoutMain;
    private TextView tvNumPeople, tvCountdown;

    private AsyncFrameDetector asyncDetector;

    private boolean isCameraFront = true;
    private float lastTimestamp = -1f;
    private final float epsilon = .3f;
    private long firstFrameTime = -1;
    private float lastReceivedTimestamp = -1f;

    private CheckBox checkboxFlash;
    private CameraView cameraView;
    private ImageButton buttonSnap, buttonTrain;

    private static final int MY_PERMISSIONS_REQUEST_SNAP = 100;

    private String photoTrainingPath[];
    private int trainingCount = 0;
    private int trainingNumber = 6;
    private boolean isTrainingActive = false;

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

        buttonTrain = findViewById(R.id.button_train);
        buttonTrain.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isTrainingActive = true;
                tvCountdown.setVisibility(View.VISIBLE);
            }
        });

        tvNumPeople = findViewById(R.id.text_num_people);
        tvCountdown = findViewById(R.id.text_countdown);
        photoTrainingPath = new String[trainingNumber];

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

                int state = 0;

                if (faces.size() == 0) {
                    state = 0;
                    tvNumPeople.setText("NO FACE");
                } else if (faces.size() == 1) {
                    state = 1;
                } else {
                    state = 2;
                }

                int numSmiles = 0;
                boolean desiredState = true;
                float joy;
                for (int i = 0 ; i < faces.size() ; i++) {
                    Face face = faces.get(i);
                    joy = face.emotions.getJoy();

                    if (state == 1)
                        tvNumPeople.setText((int) joy + " %");

                    if (joy < 90)
                        desiredState = false;
                    else
                        numSmiles++;
                }

                if (state == 2)
                    tvNumPeople.setText(numSmiles + " / " + faces.size());

                if (state != 0 && desiredState) {
                    if (isTrainingActive) {
                        cameraView.takePhoto(picture, checkboxFlash.isChecked());
                    } else {
                        File photoFile = getOutputMediaFile();
                        Bitmap bmp = getBitmapFromFrame(image);
                        try {
                            FileOutputStream fos = new FileOutputStream(photoFile);
                            bmp.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                            fos.flush();
                            fos.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        adjustAndSaveBitmap(photoFile.getPath(), true);
                        Intent intent = new Intent(context, FilterActivity.class);
                        intent.putExtra("photoPath", photoFile.getAbsolutePath());
                        startActivity(intent);
                        overridePendingTransition(R.anim.anim_open_left, R.anim.anim_close_left);
                        asyncDetector.stop();
                    }
                }
            }

            @Override
            public void onDetectorStarted() {

            }
        });
    }

    public static Bitmap getBitmapFromFrame(@NonNull final Frame frame) {
        Bitmap bitmap;

        if (frame instanceof Frame.BitmapFrame) {
            bitmap = ((Frame.BitmapFrame) frame).getBitmap();
        } else { //frame is ByteArrayFrame
            switch (frame.getColorFormat()) {
                case RGBA:
                    bitmap = getBitmapFromRGBFrame(frame);
                    break;
                case YUV_NV21:
                    bitmap = getBitmapFromYuvFrame(frame);
                    break;
                case UNKNOWN_TYPE:
                default:
                    Log.e("AutoPhoto", "Unable to get bitmap from unknown frame type");
                    return null;
            }
        }

        if (bitmap == null || frame.getTargetRotation().toDouble() == 0.0) {
            return bitmap;
        } else {
            return rotateBitmap(bitmap, (float) frame.getTargetRotation().toDouble());
        }
    }

    public static Bitmap getBitmapFromRGBFrame(@NonNull final Frame frame) {
        byte[] pixels = ((Frame.ByteArrayFrame) frame).getByteArray();
        Bitmap bitmap = Bitmap.createBitmap(frame.getWidth(), frame.getHeight(), Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(pixels));
        return bitmap;
    }

    public static Bitmap getBitmapFromYuvFrame(@NonNull final Frame frame) {
        byte[] pixels = ((Frame.ByteArrayFrame) frame).getByteArray();
        YuvImage yuvImage = new YuvImage(pixels, ImageFormat.NV21, frame.getWidth(), frame.getHeight(), null);
        return convertYuvImageToBitmap(yuvImage);
    }

    public static Bitmap convertYuvImageToBitmap(@NonNull final YuvImage yuvImage) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 100, out);
        byte[] imageBytes = out.toByteArray();
        try {
            out.close();
        } catch (IOException e) {
            Log.e("AutoPhoto", "Exception while closing output stream", e);
        }
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    public static Bitmap rotateBitmap(@NonNull final Bitmap source, final float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    private Camera.PictureCallback picture = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            if (isTrainingActive) {
                File photoFile = getOutputMediaFile();
                photoTrainingPath[trainingCount] = photoFile.getAbsolutePath();

                try {
                    FileOutputStream fos = new FileOutputStream(photoFile);
                    fos.write(data);
                    fos.close();
                } catch (FileNotFoundException e) {
                    Log.e(context.getResources().getString(R.string.app_name), "File not found: " + e.getMessage());
                } catch (IOException e) {
                    Log.e(context.getResources().getString(R.string.app_name), "Error accessing file: " + e.getMessage());
                }

                adjustAndSaveBitmap(photoFile.getPath(), false);

                if (trainingCount < trainingNumber - 1) {
                    trainingCount++;
                    tvCountdown.setText("Keep smiling (" + (trainingNumber - trainingCount) + ")");
                    cameraView.takePhoto(picture, checkboxFlash.isChecked());
                } else {
                    isTrainingActive = false;
                    tvCountdown.setVisibility(View.GONE);
                    tvCountdown.setText("Keep smiling");
                    trainingCount = 0;
                    Intent intent = new Intent(context, BatchShotActivity.class);
                    intent.putExtra("photoPaths", photoTrainingPath);
                    startActivity(intent);
                    overridePendingTransition(R.anim.anim_open_left, R.anim.anim_close_left);
                }
            } else {
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

                adjustAndSaveBitmap(photoFile.getPath(), false);

                Intent intent = new Intent(context, FilterActivity.class);
                intent.putExtra("photoPath", photoFile.getAbsolutePath());
                startActivity(intent);
                overridePendingTransition(R.anim.anim_open_left, R.anim.anim_close_left);
            }
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
        String path = mediaStorageDir.getPath() + File.separator + "IMG_"+ timeStamp + ".jpg";
        File mediaFile = new File(path);
        return mediaFile;
    }

    private void adjustAndSaveBitmap(String photoPath, boolean isScreenshot) {
        Bitmap bm = BitmapFactory.decodeFile(photoPath);
        int rotationAngle = getCameraPhotoOrientation(this, PhotoProvider.getPhotoUri(new File(photoPath)), photoPath);

        Matrix matrix = new Matrix();
        if (isCameraFront && !isScreenshot) {
            matrix.preScale(1, -1);
        } else if (isCameraFront && isScreenshot) {
            matrix.preScale(-1, 1);
        }
        if (!isScreenshot) {
            if (isCameraFront) {
                matrix.postRotate(rotationAngle + 270, (float) bm.getWidth() / 2, (float) bm.getHeight() / 2);
            } else {
                matrix.postRotate(rotationAngle + 90, (float) bm.getWidth() / 2, (float) bm.getHeight() / 2);
            }
        }
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
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, MY_PERMISSIONS_REQUEST_SNAP);
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

        if (asyncDetector != null && !asyncDetector.isRunning()) {
            asyncDetector.start();
        }

        buttonSnap.setEnabled(true);
    }

    @Override
    public void onPause() {
        super.onPause();

        if (asyncDetector != null && asyncDetector.isRunning()) {
            asyncDetector.stop();
        }

        cameraView.stopCamera();
    }

    private Frame createFrameFromData(byte[] frameData, int width, int height, Frame.ROTATE rotation) {
        Frame.ByteArrayFrame frame = new Frame.ByteArrayFrame(frameData, width, height, Frame.COLOR_FORMAT.YUV_NV21);
        frame.setTargetRotation(rotation);
        return frame;
    }
}
