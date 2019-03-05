package com.ku.autophoto;

import android.content.Context;
import android.graphics.Rect;
import android.hardware.Camera;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {

    private SurfaceHolder holder;
    private MainActivity activity;
    private Camera camera;
    final GestureDetector gestureDetector;

    private float dist;

    private static final int FOCUS_AREA_SIZE = 300;

    public CameraPreview(Context context, Camera camera, MainActivity activity) {
        super(context);
        gestureDetector = new GestureDetector(context, new DoubleTapGestureDetector());
        this.activity = activity;
        this.camera = camera;

        holder = getHolder();
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        this.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                gestureDetector.onTouchEvent(event);
                return true;
            }
        });
    }

    public void surfaceCreated(SurfaceHolder holder) {
        try {
            camera.setPreviewDisplay(this.holder);
            camera.startPreview();
        } catch (IOException e) {
            Log.d(getResources().getString(R.string.app_name), "Error setting camera preview: " + e.getMessage());
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {

    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        if (this.holder.getSurface() == null)
            return;

        try {
            camera.stopPreview();
        } catch (Exception e){

        }

        try {
            camera.setPreviewDisplay(this.holder);
            camera.startPreview();
        } catch (Exception e){
            Log.e("Rivvy: ", "Error starting camera preview: " + e.getMessage());
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        Camera.Parameters params = camera.getParameters();
        int action = event.getAction();

        if (event.getPointerCount() > 1) {
            if (action == MotionEvent.ACTION_POINTER_DOWN) {
                dist = getFingerSpacing(event);
            } else if (action == MotionEvent.ACTION_MOVE && params.isZoomSupported()) {
                camera.cancelAutoFocus();
                handleZoom(event, params);
            }
        } else {
            if (action == MotionEvent.ACTION_UP) {
                focusOnTouch(event);
            }
        }

        return true;
    }

    private void handleZoom(MotionEvent event, Camera.Parameters params) {
        int maxZoom = params.getMaxZoom();
        int zoom = params.getZoom();
        float newDist = getFingerSpacing(event);
        if (newDist > dist) {
            if (zoom < maxZoom)
                zoom++;
        } else if (newDist < dist) {
            if (zoom > 0)
                zoom--;
        }
        dist = newDist;
        params.setZoom(zoom);
        camera.setParameters(params);
    }

    private void focusOnTouch(MotionEvent event) {
        if (camera != null ) {

            Camera.Parameters parameters = camera.getParameters();
            if (parameters.getMaxNumMeteringAreas() > 0){
                Rect rect = calculateFocusArea(event.getX(), event.getY());

                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                List<Camera.Area> meteringAreas = new ArrayList<Camera.Area>();
                meteringAreas.add(new Camera.Area(rect, 800));
                parameters.setFocusAreas(meteringAreas);

                camera.setParameters(parameters);
                camera.autoFocus(mAutoFocusTakePictureCallback);
            }else {
                camera.autoFocus(mAutoFocusTakePictureCallback);
            }
        }
    }

    private Rect calculateFocusArea(float x, float y) {
        int left = clamp(Float.valueOf((x / getWidth()) * 2000 - 1000).intValue(), FOCUS_AREA_SIZE);
        int top = clamp(Float.valueOf((y / getHeight()) * 2000 - 1000).intValue(), FOCUS_AREA_SIZE);

        return new Rect(left, top, left + FOCUS_AREA_SIZE, top + FOCUS_AREA_SIZE);
    }

    private int clamp(int touchCoordinateInCameraReper, int focusAreaSize) {
        int result;
        if (Math.abs(touchCoordinateInCameraReper)+focusAreaSize/2>1000){
            if (touchCoordinateInCameraReper>0){
                result = 1000 - focusAreaSize/2;
            } else {
                result = -1000 + focusAreaSize/2;
            }
        } else{
            result = touchCoordinateInCameraReper - focusAreaSize/2;
        }
        return result;
    }

    private Camera.AutoFocusCallback mAutoFocusTakePictureCallback = new Camera.AutoFocusCallback() {
        @Override
        public void onAutoFocus(boolean success, Camera camera) {
            if (success) {
                // do something...
            } else {
                // do something...
            }
        }
    };

    private float getFingerSpacing(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    class DoubleTapGestureDetector extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            activity.stopCamera();

            if (activity.getCurrentCameraId() == Camera.CameraInfo.CAMERA_FACING_BACK)
                activity.setCurrentCameraId(Camera.CameraInfo.CAMERA_FACING_FRONT);
            else
                activity.setCurrentCameraId(Camera.CameraInfo.CAMERA_FACING_BACK);

            activity.startCamera(activity.getCurrentCameraId());
            return true;
        }

    }

}