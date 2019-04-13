package com.ku.autophoto.activities;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.ku.autophoto.R;
import com.ku.autophoto.utility.PhotoModal;

import java.util.ArrayList;

public class TrainingActivity extends AppCompatActivity {

    private int swipeStatus, width, screenCenter, x_cord, y_cord, x, y;
    private float alphaValue = 0;

    private Context context;
    private ArrayList<PhotoModal> photos = new ArrayList<>();

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_training);

        context = this;

        width = getWindowManager().getDefaultDisplay().getWidth();

        screenCenter = width / 2;

        final RelativeLayout parentView = findViewById(R.id.layout_swipe);

        initPhotos();

        for (int i = 0; i < photos.size(); i++) {

            LayoutInflater inflate = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            final View containerView = inflate.inflate(R.layout.item_photo, null);

            ImageView userIMG = containerView.findViewById(R.id.userIMG);
            RelativeLayout relativeLayoutContainer = containerView.findViewById(R.id.container);

            RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
            containerView.setLayoutParams(layoutParams);

            containerView.setTag(i);
            //userIMG.setBackgroundResource();

            RelativeLayout.LayoutParams layoutTvParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);

            final TextView tvLike = new TextView(context);
            tvLike.setLayoutParams(layoutTvParams);
            tvLike.setPadding(10, 10, 10, 10);
            tvLike.setBackgroundResource(R.drawable.selector_swipe);
            tvLike.setText("YES");
            tvLike.setGravity(Gravity.CENTER);
            tvLike.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
            tvLike.setTextSize(25);
            tvLike.setTextColor(ContextCompat.getColor(context, R.color.colorPink));
            tvLike.setX(width / 10);
            tvLike.setY(100);
            tvLike.setRotation(-50);
            tvLike.setAlpha(alphaValue);
            relativeLayoutContainer.addView(tvLike);

            final TextView tvUnLike = new TextView(context);
            tvUnLike.setLayoutParams(layoutTvParams);
            tvUnLike.setPadding(10, 10, 10, 10);
            tvUnLike.setBackgroundResource(R.drawable.selector_swipe);
            tvUnLike.setText("NO");
            tvUnLike.setGravity(Gravity.CENTER);
            tvUnLike.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
            tvUnLike.setTextSize(25);
            tvUnLike.setTextColor(ContextCompat.getColor(context, R.color.colorPink));
            tvUnLike.setX(width * 7 / 10);
            tvUnLike.setY(150);
            tvUnLike.setRotation(50);
            tvUnLike.setAlpha(alphaValue);
            relativeLayoutContainer.addView(tvUnLike);

            TextView tvName = containerView.findViewById(R.id.tvId);

            tvName.setText(Integer.toString(1 + (int) containerView.getTag()));

            relativeLayoutContainer.setOnTouchListener(new View.OnTouchListener() {

                public boolean onTouch(View v, MotionEvent event) {

                    x_cord = (int) event.getRawX();
                    y_cord = (int) event.getRawY();

                    containerView.setX(0);
                    containerView.setY(0);

                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:

                            x = (int) event.getX();
                            y = (int) event.getY();

                            break;
                        case MotionEvent.ACTION_MOVE:

                            x_cord = (int) event.getRawX();
                            y_cord = (int) event.getRawY();

                            containerView.setX(x_cord - x);
                            containerView.setY(y_cord - y);

                            if (x_cord >= screenCenter) {
                                containerView.setRotation((float) ((x_cord - screenCenter) * (Math.PI / 32)));
                                if (x_cord > (screenCenter + (screenCenter / 2))) {
                                    tvLike.setAlpha(1);
                                    if (x_cord > (width - (screenCenter / 4))) {
                                        swipeStatus = 2;
                                    } else {
                                        swipeStatus = 0;
                                    }
                                } else {
                                    swipeStatus = 0;
                                    tvLike.setAlpha(0);
                                }
                                tvUnLike.setAlpha(0);
                            } else {
                                containerView.setRotation((float) ((x_cord - screenCenter) * (Math.PI / 32)));
                                if (x_cord < (screenCenter / 2)) {
                                    tvUnLike.setAlpha(1);
                                    if (x_cord < screenCenter / 4) {
                                        swipeStatus = 1;
                                    } else {
                                        swipeStatus = 0;
                                    }
                                } else {
                                    swipeStatus = 0;
                                    tvUnLike.setAlpha(0);
                                }
                                tvLike.setAlpha(0);
                            }
                            break;
                        case MotionEvent.ACTION_UP:

                            x_cord = (int) event.getRawX();
                            y_cord = (int) event.getRawY();

                            Log.e("X Point", "" + x_cord + " , Y " + y_cord);
                            tvUnLike.setAlpha(0);
                            tvLike.setAlpha(0);

                            if (swipeStatus == 0) {
                                Log.e("Event_Status :-> ", "Nothing");
                                containerView.setX(0);
                                containerView.setY(0);
                                containerView.setRotation(0);
                            } else if (swipeStatus == 1) {
                                Log.e("Event_Status :-> ", "UNLIKE");
                                parentView.removeView(containerView);
                            } else if (swipeStatus == 2) {
                                Log.e("Event_Status :-> ", "Liked");
                                parentView.removeView(containerView);
                            }
                            break;
                        default:
                            break;
                    }
                    return true;
                }
            });

            parentView.addView(containerView);
        }

    }

    private void initPhotos() {
        PhotoModal model = new PhotoModal();
        photos.add(model);

        PhotoModal model2 = new PhotoModal();
        photos.add(model2);

        PhotoModal model3 = new PhotoModal();
        photos.add(model3);

        PhotoModal model4 = new PhotoModal();
        photos.add(model4);

        PhotoModal model5 = new PhotoModal();
        photos.add(model5);

        PhotoModal model6 = new PhotoModal();
        photos.add(model6);

        PhotoModal model7 = new PhotoModal();
        photos.add(model7);

        PhotoModal model8 = new PhotoModal();
        photos.add(model8);

        PhotoModal model9 = new PhotoModal();
        photos.add(model9);
    }

}
