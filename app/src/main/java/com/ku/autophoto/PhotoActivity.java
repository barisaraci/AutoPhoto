package com.ku.autophoto;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatSeekBar;
import androidx.core.content.ContextCompat;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Typeface;

import android.media.ThumbnailUtils;
import android.media.effect.Effect;
import android.media.effect.EffectContext;
import android.media.effect.EffectFactory;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.ittianyu.bottomnavigationviewex.BottomNavigationViewEx;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.IntBuffer;
import java.util.ArrayList;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class PhotoActivity extends AppCompatActivity {

    private Context context;
    private ViewGroup layoutMain;
    private ViewGroup layoutCrop, layoutEffect, layoutBrightness, layoutAdjust;
    private BottomNavigationViewEx bottomNavigationView;
    private TextView tvBrightness, tvContrast, tvFilllight, tvSharpen, tvSaturate, tvTemp, tvRotate;
    private AppCompatSeekBar barBrightness, barContrast, barFilllight, barSharpen, barSaturate, barTemp, barRotate;
    private ImageButton buttonFlipV, buttonFlipH;
    private ProgressBar progressBar;
    private ArrayList<TextView> tvEffects = new ArrayList<>();

    private GLSurfaceView effectView;
    private int[] textures = new int[3];
    private EffectContext effectContext;
    private Effect effects[] = new Effect[8]; // brightness, contrast, filllight, sharpen, saturate, temp, rotation, flip
    private Effect effectBrightness, effectContrast, effectFillight, effectSharpen, effectSaturate, effectTemp, effectRotate, effectFlip;
    private TextureRenderer textureRenderer = new TextureRenderer();
    private int imageWidth, imageHeight;
    private boolean isVertical, isHorizontal, isEffectApplied, isDone;
    private String effectNames[] = new String[] {"original", "summer", "vivid", "cold", "dark", "lucid", "noir", "americano"};

    private static final int TARGET_WIDTH = 768;
    private static final int TARGET_HEIGHT = 1024;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo);

        context = this;

        initLayout();
        initEffectSystem();
    }

    private void initLayout() {
        layoutMain = (ViewGroup) getWindow().getDecorView().getRootView();
        layoutCrop = findViewById(R.id.layout_crop);
        layoutEffect = findViewById(R.id.layout_effect);
        layoutBrightness = findViewById(R.id.layout_brightness);
        layoutAdjust = findViewById(R.id.layout_adjust);

        RelativeLayout layoutBackButton = findViewById(R.id.layout_back);
        layoutBackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
                overridePendingTransition(R.anim.anim_open_right, R.anim.anim_close_right);
            }
        });

        RelativeLayout layoutForwardButton = findViewById(R.id.layout_forward);
        layoutForwardButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!isDone) {
                    progressBar.setVisibility(View.VISIBLE);
                    isDone = true;
                    effectView.requestRender();
                }
            }
        });

        tvBrightness = findViewById(R.id.text_brightness);
        tvContrast = findViewById(R.id.text_contrast);
        tvFilllight = findViewById(R.id.text_filllight);
        tvSharpen = findViewById(R.id.text_sharpen);
        tvTemp = findViewById(R.id.text_temp);
        tvSaturate = findViewById(R.id.text_saturate);
        tvRotate = findViewById(R.id.text_rotate);

        bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setTextVisibility(false);
        bottomNavigationView.enableShiftingMode(false);
        bottomNavigationView.setOnNavigationItemSelectedListener(
                new BottomNavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(MenuItem item) {
                        if (item.getItemId() == R.id.action_crop) {
                            layoutEffect.setVisibility(View.GONE);
                            layoutBrightness.setVisibility(View.GONE);
                            layoutAdjust.setVisibility(View.GONE);
                            layoutCrop.setVisibility(View.VISIBLE);
                        } else if (item.getItemId() == R.id.action_effect) {
                            layoutCrop.setVisibility(View.GONE);
                            layoutBrightness.setVisibility(View.GONE);
                            layoutAdjust.setVisibility(View.GONE);
                            layoutEffect.setVisibility(View.VISIBLE);
                        } else if (item.getItemId() == R.id.action_brightness) {
                            layoutCrop.setVisibility(View.GONE);
                            layoutEffect.setVisibility(View.GONE);
                            layoutAdjust.setVisibility(View.GONE);
                            layoutBrightness.setVisibility(View.VISIBLE);
                        } else if (item.getItemId() == R.id.action_adjust) {
                            layoutCrop.setVisibility(View.GONE);
                            layoutEffect.setVisibility(View.GONE);
                            layoutBrightness.setVisibility(View.GONE);
                            layoutAdjust.setVisibility(View.VISIBLE);
                        }
                        return true;
                    }
                });
        bottomNavigationView.setCurrentItem(1);

        progressBar = findViewById(R.id.progress_bar);

        initEffectKeys();
    }

    private void initEffectKeys() {
        barBrightness = findViewById(R.id.seekbar_brightness);
        barBrightness.setProgress(50);
        barBrightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                effectBrightness.setParameter("brightness", 0.5f + ((float) i / 100));
                effectView.requestRender();
                tvBrightness.setText(Integer.toString(i * 2 - 100));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isEffectApplied = true;
                resetTvEffects();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        barContrast = findViewById(R.id.seekbar_contrast);
        barContrast.setProgress(50);
        barContrast.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                effectContrast.setParameter("contrast", 0.5f + ((float) i / 100));
                effectView.requestRender();
                tvContrast.setText(Integer.toString(i * 2 - 100));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isEffectApplied = true;
                resetTvEffects();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        barFilllight = findViewById(R.id.seekbar_filllight);
        barFilllight.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                effectFillight.setParameter("strength", ((float) i / 100));
                effectView.requestRender();
                tvFilllight.setText(Integer.toString(i));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isEffectApplied = true;
                resetTvEffects();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        barSharpen = findViewById(R.id.seekbar_sharpen);
        barSharpen.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                effectSharpen.setParameter("scale", ((float) i / 100));
                effectView.requestRender();
                tvSharpen.setText(Integer.toString(i));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isEffectApplied = true;
                resetTvEffects();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        barTemp = findViewById(R.id.seekbar_temp);
        barTemp.setProgress(50);
        barTemp.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                effectTemp.setParameter("scale", ((float) i / 100));
                effectView.requestRender();
                tvTemp.setText(Integer.toString(i * 2 - 100));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isEffectApplied = true;
                resetTvEffects();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        barSaturate = findViewById(R.id.seekbar_saturate);
        barSaturate.setProgress(50);
        barSaturate.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                effectSaturate.setParameter("scale", ((float) i / 50) - 1f);
                effectView.requestRender();
                tvSaturate.setText(Integer.toString(i * 2 - 100));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isEffectApplied = true;
                resetTvEffects();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        barRotate = findViewById(R.id.seekbar_rotate);
        barRotate.setProgress(50);
        barRotate.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                effectRotate.setParameter("angle", -1 * (((float) i * 9 / 20) - 22.5f));
                effectView.requestRender();
                int val = (i * (seekBar.getWidth() - 2 * seekBar.getThumbOffset())) / seekBar.getMax();
                tvRotate.setText(Integer.toString(i * 2 - 100));
                tvRotate.setX(seekBar.getX() + val + seekBar.getThumbOffset() / 2);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isEffectApplied = true;
                tvRotate.setVisibility(View.VISIBLE);
                resetTvEffects();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                tvRotate.setVisibility(View.GONE);
            }
        });

        buttonFlipV = findViewById(R.id.button_flipv);
        buttonFlipV.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                isEffectApplied = true;
                resetTvEffects();
                isVertical = !isVertical;
                effectFlip.setParameter("vertical", isVertical);
                effectView.requestRender();
            }
        });

        buttonFlipH = findViewById(R.id.button_fliph);
        buttonFlipH.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                isEffectApplied = true;
                resetTvEffects();
                isHorizontal = !isHorizontal;
                effectFlip.setParameter("horizontal", isHorizontal);
                effectView.requestRender();
            }
        });
    }

    private void initEffectSystem() {
        effectView = findViewById(R.id.effectsview);
        effectView.setEGLContextClientVersion(2);
        effectView.setRenderer(new GLSurfaceView.Renderer() {
            @Override
            public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
                effectContext = EffectContext.createWithCurrentGlContext();
                initEffect();
                textureRenderer.init();
                loadTextures();
            }

            @Override
            public void onSurfaceChanged(GL10 gl10, int width, int height) {
                if (textureRenderer != null) {
                    textureRenderer.updateViewSize(width, height);
                }
            }

            @Override
            public void onDrawFrame(GL10 gl10) {
                if (isEffectApplied)
                    applyEffect();

                renderTexture();
                if (isDone) saveBitmap(takeScreenshot(gl10));
            }
        });
        effectView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        final LinearLayout layoutEffectButtons = findViewById(R.id.layout_effect_buttons);
        for (int i = 0; i < effectNames.length; i++) {
            final int n = i;
            final LinearLayout layoutEffectButton = new LinearLayout(context);
            layoutEffectButton.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT));
            layoutEffectButton.setGravity(Gravity.CENTER_HORIZONTAL);
            layoutEffectButton.setOrientation(LinearLayout.VERTICAL);

            final TextView textEffect = new TextView(context);
            textEffect.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            textEffect.setText(effectNames[n]);
            textEffect.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            if (n == 0) {
                textEffect.setTextColor(getResources().getColor(R.color.colorPrimaryDark));
                textEffect.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            } else {
                textEffect.setTextColor(getResources().getColor(R.color.colorDark));
                textEffect.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            }
            textEffect.setGravity(Gravity.CENTER);
            tvEffects.add(textEffect);

            final ImageView effectButton = new ImageView(context);
            effectButton.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT));
            effectButton.setAdjustViewBounds(true);
            effectButton.setBackground(ContextCompat.getDrawable(context, android.R.drawable.dialog_holo_light_frame));
            effectButton.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.apply_effect));
            //effectButton.setImageBitmap(ThumbnailUtils.extractThumbnail(BitmapFactory.decodeFile(getIntent().getStringExtra("photoPath")), 64, 64);
            effectButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    isEffectApplied = true;

                    if (n == 0) {
                        if (isVertical) buttonFlipV.performClick();
                        if (isHorizontal) buttonFlipH.performClick();
                        barRotate.setProgress(50);
                        applyFullEffect(0, 0, 0, 0, 0, 0);
                    }
                    else if (n == 1) applyFullEffect(20, 32, 20, 40, 16, 14); // summer
                    else if (n == 2) applyFullEffect(30, 40, 15, 16, 8, -10); // vivid
                    else if (n == 3) applyFullEffect(56, 36, 12, 36, -64, -22); // cold
                    else if (n == 4) applyFullEffect(-44, 6, 0, 0, -28, -32); // dark
                    else if (n == 5) applyFullEffect(-32, 100, 44, 100, 72, -56); // lucid
                    else if (n == 6) applyFullEffect(-14, 80, 0, 34, 56, -100); // noir
                    else if (n == 7) applyFullEffect(54, -16, 17, 11, -26, 6); // americano

                    resetTvEffects();
                    textEffect.setTextColor(getResources().getColor(R.color.colorPrimaryDark));
                    textEffect.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                }
            });

            layoutEffectButton.addView(textEffect);
            layoutEffectButton.addView(effectButton);
            layoutEffectButtons.addView(layoutEffectButton);
        }
    }

    private void resetTvEffects() {
        for (TextView tvEffect : tvEffects) {
            tvEffect.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            tvEffect.setTextColor(getResources().getColor(R.color.colorDark));
        }
    }

    private void applyFullEffect(int brightness, int contrast, int filllight, int sharpen, int temp, int saturate) {
        barBrightness.setProgress(50 + brightness / 2);
        barContrast.setProgress(50 + contrast / 2);
        barFilllight.setProgress(filllight);
        barSharpen.setProgress(sharpen);
        barTemp.setProgress(50 + temp / 2);
        barSaturate.setProgress(50 + saturate / 2);
    }

    private void loadTextures() {
        GLES20.glGenTextures(3, textures, 0);
        Bitmap bitmap = BitmapFactory.decodeFile(getIntent().getStringExtra("photoPath"));
        imageWidth = bitmap.getWidth();
        imageHeight = bitmap.getHeight();
        textureRenderer.updateTextureSize(imageWidth, imageHeight);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
        GLToolbox.initTexParams();
    }

    private void initEffect() {
        EffectFactory effectFactory = effectContext.getFactory();

        effectBrightness = effectFactory.createEffect(EffectFactory.EFFECT_BRIGHTNESS);
        effectBrightness.setParameter("brightness", 1f); // 0 _ 2
        effects[0] = effectBrightness;

        effectContrast = effectFactory.createEffect(EffectFactory.EFFECT_CONTRAST);
        effectContrast.setParameter("contrast", 1f); // 0.5 _ 1.5
        effects[1] = effectContrast;

        effectFillight = effectFactory.createEffect(EffectFactory.EFFECT_FILLLIGHT);
        effectFillight.setParameter("strength", 0f); // 0 _ 1
        effects[2] = effectFillight;

        effectSharpen = effectFactory.createEffect(EffectFactory.EFFECT_SHARPEN);
        effectSharpen.setParameter("scale", 0f); // 0 _ 1
        effects[3] = effectSharpen;

        effectTemp = effectFactory.createEffect(EffectFactory.EFFECT_TEMPERATURE);
        effectTemp.setParameter("scale", 0.5f); // 0 _ 1
        effects[4] = effectTemp;

        effectSaturate = effectFactory.createEffect(EffectFactory.EFFECT_SATURATE);
        effectSaturate.setParameter("scale", 0f); // -1 _ 1
        effects[5] = effectSaturate;

        effectRotate = effectFactory.createEffect(EffectFactory.EFFECT_STRAIGHTEN);
        effectRotate.setParameter("angle", 0f); // -22.5 _ 22.5
        effects[6] = effectRotate;

        effectFlip = effectFactory.createEffect(EffectFactory.EFFECT_FLIP);
        effectFlip.setParameter("vertical", false);
        effectFlip.setParameter("horizontal", false);
        effects[7] = effectFlip;
    }

    private void applyEffect() {
        effects[0].apply(textures[0], imageWidth, imageHeight, textures[1]);
        for (int i = 1; i < 8; i++) {
            int sourceTexture = textures[1];
            int destinationTexture = textures[2];
            effects[i].apply(sourceTexture, imageWidth, imageHeight, destinationTexture);
            textures[1] = destinationTexture;
            textures[2] = sourceTexture;
        }
    }

    private void renderTexture() {
        if (isEffectApplied)
            textureRenderer.renderTexture(textures[1]);
        else
            textureRenderer.renderTexture(textures[0]);
    }

    private void saveBitmap(Bitmap bitmap) {
        try {
            FileOutputStream fos = new FileOutputStream(getIntent().getStringExtra("photoPath"));
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.flush();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        bitmap.recycle();
        isDone = false;

        Intent intent = new Intent(context, PhotoConfirmActivity.class);
        intent.putExtra("photoPath", getIntent().getStringExtra("photoPath"));
        startActivity(intent);
        overridePendingTransition(R.anim.anim_open_left, R.anim.anim_close_left);
    }

    public Bitmap takeScreenshot(GL10 mGL) {
        final int width = effectView.getWidth();
        final int height = effectView.getHeight();
        IntBuffer ib = IntBuffer.allocate(width * height);
        IntBuffer ibt = IntBuffer.allocate(width * height);
        mGL.glReadPixels(0, 0, width, height, GL10.GL_RGBA, GL10.GL_UNSIGNED_BYTE, ib);

        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                ibt.put((height - i - 1) * width + j, ib.get(i * width + j));
            }
        }

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(ibt);

        float scaleWidth = ((float) TARGET_WIDTH) / width;
        float scaleHeight = ((float) TARGET_HEIGHT) / height;
        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scaleHeight);
        Bitmap resizedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, false);
        bitmap.recycle();
        return resizedBitmap;
    }

    @Override
    public void onPause() {
        super.onPause();

        isDone = false;
        progressBar.setVisibility(View.GONE);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

}
