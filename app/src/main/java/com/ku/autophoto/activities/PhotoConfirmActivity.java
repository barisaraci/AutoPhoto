package com.ku.autophoto.activities;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.ku.autophoto.utility.BlurBuilder;
import com.ku.autophoto.R;

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

        Button buttonShare = findViewById(R.id.button_share);
        buttonShare.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("image/jpeg");
                Uri uri = Uri.fromFile(new File(getIntent().getStringExtra("photoPath")));
                share.putExtra(Intent.EXTRA_STREAM, uri);
                startActivity(Intent.createChooser(share, "Share photo"));
            }
        });
    }
}