package com.ku.autophoto;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import java.io.File;

public class PhotoActivity extends AppCompatActivity {

    private Context context;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo);

        context = this;
        initLayout();
    }

    private void initLayout() {
        final ImageView cameraImage = findViewById(R.id.camera_image);
        cameraImage.setImageURI(Uri.fromFile(new File(getIntent().getStringExtra("photoPath"))));

        RelativeLayout layoutBackButton = findViewById(R.id.layout_back);
        layoutBackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
    }

}
