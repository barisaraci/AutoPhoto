package com.ku.autophoto;

import android.content.Context;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;

import com.camerakit.CameraKit;
import com.camerakit.CameraKitView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    private Context context;
    private ViewGroup layoutMain;
    private String photoPath = "";
    private CameraKitView camera;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        context = this;
        layoutMain = (ViewGroup) getWindow().getDecorView().getRootView();
        initLayout();

        /*if (Build.VERSION.SDK_INT >= 23) {
            requestCameraPermission();
        } else {
            initLayout();
        }*/
    }

    /*private void requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA) &&
                    ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE) &&
                    ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, MY_PERMISSIONS_REQUEST_SNAP);
            }
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            initLayout();
        }
    }*/

    private void initLayout() {
        camera = findViewById(R.id.camera);

        final CheckBox checkBoxFlash = findViewById(R.id.checkbox_flash);

        ImageButton buttonSnap = findViewById(R.id.button_snap);
        buttonSnap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                camera.setFlash(checkBoxFlash.isChecked() ? CameraKit.FLASH_ON : CameraKit.FLASH_OFF);
                camera.captureImage(new CameraKitView.ImageCallback() {
                    @Override
                    public void onImage(CameraKitView cameraKitView, final byte[] capturedImage) {
                        File photoFile = getOutputMediaFile();

                        try {
                            FileOutputStream fos = new FileOutputStream(photoFile);
                            fos.write(capturedImage);
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
                });
            }
        });

        ImageButton buttonFlip = findViewById(R.id.button_flip);
        buttonFlip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                camera.toggleFacing();
            }
        });
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

    @Override
    protected void onStart() {
        super.onStart();
        camera.onStart();
    }
    @Override
    protected void onResume() {
        super.onResume();
        camera.onResume();
    }
    @Override
    protected void onPause() {
        camera.onPause();
        super.onPause();
    }
    @Override
    protected void onStop() {
        camera.onStop();
        super.onStop();
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        camera.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }


}
