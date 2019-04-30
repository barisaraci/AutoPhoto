package com.ku.autophoto.activities;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.ku.autophoto.utility.BlurBuilder;
import com.ku.autophoto.R;
import com.ku.autophoto.utility.PhotoProvider;

import java.io.File;

public class PhotoConfirmActivity extends AppCompatActivity {

    private Context context;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_confirm);

        context = this;
        initLayout();
    }

    private void initLayout() {
        final ImageView cameraImage = findViewById(R.id.camera_image);
        cameraImage.setImageURI(Uri.fromFile(new File(getIntent().getStringExtra("photoPath"))));

        BitmapDrawable drawable = (BitmapDrawable) cameraImage.getDrawable();
        Bitmap originalBitmap = drawable.getBitmap();

        Bitmap blurredBitmap = BlurBuilder.blur(context, originalBitmap, 0.1f, 25f);
        final ImageView backgroundImage = findViewById(R.id.background_image);
        backgroundImage.setImageBitmap(blurredBitmap);

        RelativeLayout layoutBackButton = findViewById(R.id.layout_back);
        layoutBackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
                overridePendingTransition(R.anim.anim_open_right, R.anim.anim_close_right);
            }
        });

        Button buttonSave = findViewById(R.id.button_save);
        buttonSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                context.getContentResolver().notifyChange(PhotoProvider.getPhotoUri(new File(getIntent().getStringExtra("photoPath"))), null);
                setResult(2);
                finish();
                overridePendingTransition(R.anim.anim_open_right, R.anim.anim_close_right);
            }
        });

        Button buttonDelete = findViewById(R.id.button_delete);
        buttonDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new File(getIntent().getStringExtra("photoPath")).delete();
                setResult(2);
                finish();
                overridePendingTransition(R.anim.anim_open_right, R.anim.anim_close_right);
            }
        });
    }

}