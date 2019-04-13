package com.ku.autophoto.utility_camera;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Size;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Display;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.affectiva.android.affdex.sdk.Frame;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.List;

/**
 * The CameraCore class controls the Camera by responding to the lifecycle events of SurfaceHolder. It is
 * important not to miss the SurfaceCreated event fired by SurfaceHolder; thus, CameraCore should be instantiated
 * at the start of its host Activity (which should happen as long as the developer instantiates CameraDetector at
 * the start of its host Activity) (e.g. in onCreate()).
 */
public class CameraCore extends OrientationEventListener implements SurfaceHolder.Callback, Camera.PreviewCallback {

    public enum CameraType {
        CAMERA_BACK, CAMERA_FRONT
    }

    public interface OnCameraCoreEventListener {
        void onFrameAvailable(byte[] frame, int width, int height, Frame.ROTATE rotation);
        void onFrameSizeSelected(int width, int height, Frame.ROTATE rotation);
        void onCameraStarted(boolean success, Throwable error);
    }

    enum CameraState {
        STOPPED, //Camera has not been created
        CREATING, //Camera is being created in the background thread
        STARTED //Camera is ready to use
    }

    public OnCameraCoreEventListener listener = null;

    public final static float TARGET_FRAME_RATE = 30; // Specified at 30 fps on 3/18/2014
    public final static int PREVIEW_IMAGE_FORMAT = ImageFormat.NV21; // NV21 is the default, but this line here in case want to change.
    public final static String LOG_TAG = "CameraCore";

    //Surface and Preview members
    public SurfaceHolder holder;
    public boolean isPreviewing = false;
    public boolean isSurfaceCreated = false;

    //Rotation-related members
    public Display defaultDisplay;
    public Frame.ROTATE frameRotation;
    public int displayRotation;

    //Camera-related members
    public CameraState cameraState = CameraState.STOPPED;
    public final CameraWrapper cameraWrapper;
    public CameraFacade cameraFacade;
    public CameraCreationThread cameraThread;
    public Context context;
    public String photoPath = "";

    MainThreadHandler mHandler;

    CameraCore(Context context, SurfaceView providedSurfaceView, Display defaultDisplay) {
        super(context);

        if (context == null) {
            throw new NullPointerException("context must not be null");
        }

        if (providedSurfaceView == null) {
            throw new NullPointerException("providedSurfaceView must not be null");
        }

        if (defaultDisplay == null) {
            throw new NullPointerException("defaultDisplay must not be null");
        }

        this.context = context;

        this.defaultDisplay = defaultDisplay;
        displayRotation = defaultDisplay.getRotation();
        this.frameRotation = Frame.ROTATE.NO_ROTATION;

        this.holder = providedSurfaceView.getHolder();
        holder.addCallback(this);

        cameraWrapper = new CameraWrapper();
        cameraFacade = new CameraFacade();
        mHandler = new MainThreadHandler(this);
    }

    public void setOnCameraCoreEventListener(OnCameraCoreEventListener listener) {
        this.listener = listener;
    }

    /**
     * Starts a background thread to open the Camera and set its parameters to those that will work
     * best with the Affdex SDK.
     */
    public void startCamera(CameraType cameraType) {
        if (cameraState == CameraState.STOPPED) {
            cameraState = CameraState.CREATING;
            cameraWrapper.cameraType = cameraType;
            cameraThread = new CameraCreationThread();
            cameraThread.start();
        }
    }

    /*
        "Called" by background thread when Camera creation has finished. (This method runs on the main thread)
     */
    public void cameraStarted() {
        Log.e(LOG_TAG,"cameraStarted");
        if (cameraState == CameraState.CREATING) {
            synchronized (cameraWrapper) {
                if (cameraWrapper.error == null) {
                    Log.e(LOG_TAG,"camera creation successful");
                    cameraState = CameraState.STARTED;
                    if (listener != null) {
                        listener.onCameraStarted(true, null);
                    }
                    setCameraDisplayOrientation();
                    if (isSurfaceCreated) {
                        startPreviewing(holder);
                    }
                } else {
                    Log.e(LOG_TAG,"camera creation error");
                    closeCameraSafe(); //sets state to STOPPED
                    if (listener != null) {
                        listener.onCameraStarted(false, cameraWrapper.error);
                    }
                }
            }
        } else {
            Log.e(LOG_TAG,"Camera creation background thread reports it returned, but we were not expecting a camera to be created. Releasing resources.");
            closeCameraSafe();
        }
    }

    /*
        Starts camera preview.
        Method should only be called when state is CameraState.STARTED
     */
    public void startPreviewing(SurfaceHolder holder) {
        Log.e(LOG_TAG, "startPreviewing");
        try {
            cameraWrapper.camera.setPreviewDisplay(holder);
        } catch (IOException e) {
            Log.i(LOG_TAG, "Unable to start camera preview" + e.getMessage());
        }

        // enable orientation listening
        enable();

        // setPreviewCallbackWithBuffer only seems to work if you establish it after the first onPreviewFrame callback
        // (otherwise it never gets called back at all). So, use a one-shot callback for the first one, then
        // swap in the callback that uses the buffers.
        cameraWrapper.camera.setOneShotPreviewCallback(new Camera.PreviewCallback() {
            @Override
            public void onPreviewFrame(byte[] data, Camera camera) {
                if (listener != null) {
                    listener.onFrameAvailable(data, cameraWrapper.previewWidth, cameraWrapper.previewHeight, frameRotation);
                }
                setupPreviewWithCallbackBuffers();
            }
        });

        isPreviewing = true;

        cameraWrapper.camera.startPreview();
    }

    /*
        Stops camera preview.
        Method should only be called when state is CameraState.STARTED
     */
    public void stopPreviewing() {
        Log.e(LOG_TAG, "stopPreviewing");
        if (isPreviewing) {
            cameraWrapper.camera.stopPreview();
            cameraWrapper.camera.setPreviewCallback(null);
            disable(); // disable orientation listening
        }
        isPreviewing = false;
    }

    /**
     * Stops the camera.
     * If a camera was in the process of being created, this method will attempt to block until the camera has been released.
     */
    public void stopCamera() {
        Log.e(LOG_TAG, "CameraCore.stopCamera()");
        if (cameraState == CameraState.STARTED) {
            if (isPreviewing) {
                stopPreviewing();
            }
            closeCameraSafe(); //sets state to STOPPED
            cameraWrapper.camera = null; //facilitate GC
        } else if (cameraState == CameraState.CREATING) {
            //if camera was in the process of being created, we should wait until we can close the camera.
            //The reason for this is other apps will be unable to use the camera unless we release it
            try {
                //attempt to block until cameraThread has finished, then close camera
                cameraThread.join();
            } catch (InterruptedException e) {
                Log.e(LOG_TAG,"Attempt to stop background thread was interrupted. Will try to release all resources.");
            } finally {
                mHandler.removeMessages(MainThreadHandler.CAMERA_CREATED);
                closeCameraSafe(); //sets state to STOPPED
            }
        }
    }


    protected void takePhoto(Camera.PictureCallback picture, boolean flashOn){
        if(isFlashAvailable()) {
            Camera.Parameters params = cameraWrapper.camera.getParameters();
            params.setFlashMode(flashOn ? Camera.Parameters.FLASH_MODE_ON : Camera.Parameters.FLASH_MODE_OFF);
            cameraWrapper.camera.setParameters(params);
        }
        cameraWrapper.camera.takePicture(null, null, picture);
    }

    public boolean isFlashAvailable(){
        List<String> flashModes = cameraWrapper.camera.getParameters().getSupportedFlashModes();
        if(flashModes == null) return false;

        for(String flashMode : flashModes) {
            if(Camera.Parameters.FLASH_MODE_ON.equals(flashMode)) {
                return true;
            }
        }
        return false;
    }

    public int getCameraRotation(){
        return this.displayRotation;
    }


    /*
     * Implement SurfaceHolder.Callback interface
     *
     * In order to capture camera frames, you must have a preview displayed (Android requirement).
     *
     * When a surface is created, start previewing; when it is destroyed, stop previewing
     *
     * Note: on rotation, surfaceCreated will get called, followed by surfaceChanged. Oddly, surfaceDestroyed does not
     * get called in this case. So, in a rotation scenario, we just call startPreviewing again, but that's OK, because
     * it updates the preview display to use the new SurfaceView held by the holder.
     */

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.e(LOG_TAG, "surfaceChanged");
        if (cameraState == CameraState.STARTED) {
            stopPreviewing();
            startPreviewing(holder);
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.e(LOG_TAG, "surfaceCreated");
        isSurfaceCreated = true;
        if (cameraState == CameraState.STARTED) {
            startPreviewing(holder);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.e(LOG_TAG, "surfaceDestroyed");
        isSurfaceCreated = false;
        if (cameraState == CameraState.STARTED) {
            stopPreviewing();
        }
    }

    // Implement Camera.PreviewCallback interface
    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        if (listener!= null) {
            listener.onFrameAvailable(data, cameraWrapper.previewWidth, cameraWrapper.previewHeight, frameRotation);
        }
        // put the buffer back in the queue, so that it can be used again
        camera.addCallbackBuffer(data);
    }

    public void setupPreviewWithCallbackBuffers() {
        // calculate the size for the callback buffers
        Camera.Parameters params = cameraWrapper.camera.getParameters();
        int previewFormat = params.getPreviewFormat();
        int bitsPerPixel = ImageFormat.getBitsPerPixel(previewFormat);
        Size size = params.getPreviewSize();

        int bufSize = size.width * size.height * bitsPerPixel / 8;

        // add two buffers to the queue, so the camera can be working with one, while the callback is working with the
        // other. The callback will put each buffer it receives back into the buffer queue when it's done, so the
        // camera can use it again.
        cameraWrapper.camera.addCallbackBuffer(new byte[bufSize]);
        cameraWrapper.camera.addCallbackBuffer(new byte[bufSize]);

        cameraWrapper.camera.setPreviewCallbackWithBuffer(this);
    }

    void closeCameraSafe() {
        synchronized (cameraWrapper) {
            cameraWrapper.camera.release();
        }
        cameraState = CameraState.STOPPED;
    }

    // If you quickly rotate 180 degrees, Activity does not restart, so you need this orientation Listener.
    @Override
    public void onOrientationChanged(int orientation) {
        // this method gets called for every tiny 1 degree change in orientation, so it's called really often
        // if the device is handheld. We don't need to reset the camera display orientation unless there
        // is a change to the display rotation (i.e. a 90/180/270 degree switch).
        if (defaultDisplay.getRotation() != displayRotation) {
            displayRotation = defaultDisplay.getRotation();
            setCameraDisplayOrientation();
        }
    }

    // Make the camera image show in the same orientation as the display.
    // This code is partially based on sample code at http://developer.android.com/reference/android/hardware/Camera.html
    public void setCameraDisplayOrientation() {
        CameraInfo info = new CameraInfo();
        cameraFacade.getCameraInfo(cameraWrapper.cameraId, info);

        int degrees = 0;
        switch (displayRotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        int rotation;

        if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
            //determine amount to rotate image and call computeFrameRotation()
            //to have the Frame.ROTATE object ready for CameraDetector to use
            rotation = (info.orientation + degrees) % 360;

            computeFrameRotation(rotation);

            //Android mirrors the image that will be displayed on screen, but not the image
            //that will be sent as bytes[] in onPreviewFrame(), so we compensate for mirroring after
            //calling computeFrameRotation()
            rotation = (360 - rotation) % 360; // compensate the mirror
        } else { // back-facing
            //determine amount to rotate image and call computeFrameRotation()
            //to have the Frame.ROTATE object ready for CameraDetector to use
            rotation = (info.orientation - degrees + 360) % 360;

            computeFrameRotation(rotation);
        }
        cameraWrapper.camera.setDisplayOrientation(rotation);

        //Now that rotation has been determined (or updated) inform listener of new frame size.
        if (listener!= null) {
            listener.onFrameSizeSelected(cameraWrapper.previewWidth, cameraWrapper.previewHeight, frameRotation);
        }
    }

    public void computeFrameRotation(int rotation) {
        switch(rotation) {
            case 0:
                frameRotation = Frame.ROTATE.NO_ROTATION;
                break;
            case 90:
                frameRotation = Frame.ROTATE.BY_90_CW;
                break;
            case 180:
                frameRotation = Frame.ROTATE.BY_180;
                break;
            case 270:
                frameRotation = Frame.ROTATE.BY_90_CCW;
                break;
            default:
                frameRotation = Frame.ROTATE.NO_ROTATION;
        }
    }

    /**
     * A Facade  class to wrap around the static methods of Android's Camera API
     */
    public class CameraFacade {

        void acquireCamera(CameraWrapper cameraWrapper) throws IllegalStateException {
            if (cameraWrapper.cameraType == null) {
                throw new IllegalStateException("camera type must be set before calling acquireCamera");
            }

            int cameraToOpen = CameraType.CAMERA_FRONT == cameraWrapper.cameraType ? Camera.CameraInfo.CAMERA_FACING_FRONT
                    : Camera.CameraInfo.CAMERA_FACING_BACK;

            int cnum = getNumberOfCameras();
            int cameraID = -1;
            CameraInfo caminfo = new CameraInfo();

            for (int i = 0; i < cnum; i++) {
                getCameraInfo(i, caminfo);
                if (caminfo.facing == cameraToOpen) {
                    cameraID = i;
                    break;
                }
            }
            if (cameraID == -1) {
                throw new IllegalStateException("This device does not have a camera of the requested type");
            }
            Camera result;
            try {
                result = open(cameraID); // attempt to get a Camera instance.
            } catch (RuntimeException e) {
                // Camera is not available (in use or does not exist). Translate to a more appropriate exception type.
                String msg = "Camera is unavailable. Please close the app that is using the camera and then try again.\n"
                        + "Error:  " + e.getMessage();
                throw new IllegalStateException(msg, e);
            }

            cameraWrapper.camera = result;
            cameraWrapper.cameraId = cameraID;
        }

        Camera open(int cameraId) {
            return Camera.open(cameraId);
        }

        int getNumberOfCameras() {
            return Camera.getNumberOfCameras();
        }

        void getCameraInfo(int cameraId, CameraInfo cameraInfo) {
            Camera.getCameraInfo(cameraId, cameraInfo);
        }
    }

    /**
     * A wrapper class to encapsulate the camera and its properties. This is meant to contain instance-specific data.
     * This class is also used as a synchronization lock and thus serves to ensure both the background and foreground
     * thread have access to the latest data regarding the camera and its parameters.
     */
    public class CameraWrapper {
        Camera camera = null;
        Throwable error = null;
        CameraType cameraType;
        int cameraId;
        int previewWidth;
        int previewHeight;
    }

    /*
        A Handler to send messages back to the main thread.
        This Handler class holds a WeakReference to the CameraCore to avoid memory leaks.
     */
    static class MainThreadHandler extends Handler {
        WeakReference<CameraCore> cameraHelperRef;

        final static int CAMERA_CREATED = 0;

        MainThreadHandler(CameraCore cameraCore) {
            super(Looper.getMainLooper());
            cameraHelperRef = new WeakReference<>(cameraCore);
        }

        @Override
        public void handleMessage(Message msg) {
            CameraCore cameraCore = cameraHelperRef.get();
            switch (msg.what) {
                case CAMERA_CREATED:
                    cameraCore.cameraStarted();
                    break;
                default:
                    Log.e(LOG_TAG,"Received unhandled message of code " + String.valueOf(msg.what));
                    break;
            }

        }
    }

    class CameraCreationThread extends Thread {
        @Override
        public void run() {
            synchronized (cameraWrapper) {
                cameraFacade.acquireCamera(cameraWrapper);
                initCameraParams(cameraWrapper);
                cameraWrapper.error = null;
                mHandler.obtainMessage(MainThreadHandler.CAMERA_CREATED).sendToTarget();
            }
        }

        public void initCameraParams(CameraWrapper cameraWrapper) {
            Camera.Parameters cameraParams = cameraWrapper.camera.getParameters();
            // NV21 is the default, but this line here in case want to change.
            cameraParams.setPreviewFormat(PREVIEW_IMAGE_FORMAT);
            setOptimalPreviewFrameRate(cameraParams);
            setOptimalPreviewSize(cameraParams, 640, 480); // Youssef requested roughly 640x480 on 3/18/2014

            cameraWrapper.camera.setParameters(cameraParams);

            cameraWrapper.previewWidth = cameraParams.getPreviewSize().width;
            cameraWrapper.previewHeight = cameraParams.getPreviewSize().height;
        }

        //Sets camera frame to be as close to TARGET_FRAME_RATE as possible
        public void setOptimalPreviewFrameRate(Camera.Parameters cameraParams) {
            int targetHiMS = (int) (1000 * TARGET_FRAME_RATE);
            List<int[]> ranges = cameraParams.getSupportedPreviewFpsRange();
            if (1 == ranges.size()) {
                return; // only one option: no need to set anything.
            }
            String rangeStr = "";
            for (int[] r : ranges) {
                rangeStr += "[" + r[0] + "," + r[1] + "]; ";
            }

            int[] optimalRange = null;
            int minDiff = Integer.MAX_VALUE;
            for (int[] range : ranges) {
                int currentDiff = Math.abs(range[1] - targetHiMS);
                if (currentDiff <= minDiff) {
                    optimalRange = range;
                    minDiff = currentDiff;
                }
            }
            cameraParams.setPreviewFpsRange(optimalRange[0], optimalRange[1]); // this will take the biggest lo range.
        }

        // Finds the closest height - simple algo. NOTE: only height is used as a target, width is ignored!
        //TODO: this could benefit from revision - for example, it chooses a square image on the Nexus 7, which looks bad
        public void setOptimalPreviewSize(Camera.Parameters cameraParams, int targetWidth, int targetHeight) {
            List<Size> supportedPreviewSizes = cameraParams.getSupportedPreviewSizes();
            // according to Android bug #6271, the emulator sometimes returns null from getSupportedPreviewSizes,
            // although this shouldn't happen on a real device.
            // See https://code.google.com/p/android/issues/detail?id=6271
            if (null == supportedPreviewSizes) {
                Log.v(LOG_TAG, "Camera returning null for getSupportedPreviewSizes(), will use default");
                return;
            }

            Size optimalSize = null;
            double minDiff = Double.MAX_VALUE;

            for (Size size : supportedPreviewSizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
            cameraParams.setPreviewSize(optimalSize.width, optimalSize.height);
        }
    }
}