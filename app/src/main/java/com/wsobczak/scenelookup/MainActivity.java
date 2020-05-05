package com.wsobczak.scenelookup;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.location.Location;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.util.SparseArray;
import android.view.Display;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    private static final SparseArray ORIENTATIONS = new SparseArray();
    private static final String IMAGES_FOLDER_NAME = "Scene Lookup";
    private byte jpegQuality = 100;

    static  {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 180);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 0);
    }

    private static final int STATE_PREVIEW = 0;
    private static final int STATE_WAIT_LOCK = 1;
    private int mState;
    private String mCameraID;
    private Size mPreviewSize;
    private TextureView mTextureView;
    private ImageView mOverlayImageView;
    private ImageView mBlueArrow, mCompassFace;
    private TextView debugFPStv;
    private TextView debugGPStv;
    private SeekBar mOverlaySeekBar;
    private View mTrueLevelView;
    private View mPhotoLevelView;
    private CameraDevice mCameraDevice;
    private CaptureRequest mPreviewCaptureRequest;
    private CaptureRequest.Builder mPreviewCaptureRequestBuilder;
    private CameraCaptureSession mCameraCaptureSession;
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private static File mImageFile;
    private ImageReader mImageReader;
    private File workingDirectory;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;
    public Location mLocation;
    private SensorManager mSensorManager;
    private Sensor mOrientationSensor;
    private long displayRefreshInterval;
    private int displayWidth, displayHeight;
    private float[] mOrientation = new float[3];
    private float[] imageDirection = new float[3];

    private OrientationEventListener listener;
    private int rotation = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        mTextureView = findViewById(R.id.textureView);
        mOverlayImageView = findViewById(R.id.overlayImageView);
        mOverlayImageView.setAlpha(0.5f);
        mBlueArrow = findViewById(R.id.blueArrow);
        mBlueArrow.setVisibility(View.GONE);
        mCompassFace = findViewById(R.id.compassFaceView);
        debugFPStv = findViewById(R.id.debugFPStv);
        debugGPStv = findViewById(R.id.debugGPStv);
        mOverlaySeekBar = findViewById(R.id.overlayTransparencySeekBar);
        mOverlaySeekBar.setProgress(50);
        mTrueLevelView = findViewById(R.id.trueLevelView);
        mPhotoLevelView = findViewById(R.id.photoLevelView);
        mPhotoLevelView.setVisibility(View.GONE);
        locationRequest = new LocationRequest();
        locationRequest.setInterval(displayRefreshInterval);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mOrientationSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        mLocation = location;
                        debugGPStv.setText("latitude: " + location.getLatitude() +
                                "\nLongitude: " + location.getLongitude() +
                                "\nAltitude: " + location.getAltitude());
                    }
                }
            }
        };

        listener = new OrientationEventListener(this, SensorManager.SENSOR_DELAY_NORMAL) {
            @Override
            public void onOrientationChanged(int orientation) {
                rotation = ((orientation + 45) / 90) % 4;
                //mLevelView.setRotation(-orientation);
            }
        };
        if (listener.canDetectOrientation()) listener.enable();
        else listener = null;

        workingDirectory = new File(getIntent().getStringExtra("workingDirectory"));

        ArrayList<Cell> imageFiles = listAllFiles(workingDirectory.getAbsolutePath());

        if (imageFiles.size() > 0) {
            Cell oldestFile = Collections.min(imageFiles, new Cell.CellComparator());
            Bitmap image = ImageHelper.bitmapFromPath(oldestFile.getPath(), 700, 700);
            image = ImageHelper.rotateImage(image,90);
            mOverlayImageView.setImageBitmap(image);

            imageDirection = ImageHelper.getYawPitchRoll(oldestFile.getPath());

            if (imageDirection[0] != 720) mBlueArrow.setVisibility(View.VISIBLE);
            mPhotoLevelView.setRotation(-imageDirection[2] + 90);
            if (imageDirection[0] != 720) mPhotoLevelView.setVisibility(View.VISIBLE);

        }

        mOverlaySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mOverlayImageView.setAlpha(((float)progress/100));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        mBlueArrow.setImageBitmap(getBitmapFromAssets("BlueArrow.png"));
        mCompassFace.setImageBitmap(getBitmapFromAssets("CompassFace.png"));
    }

    @Override
    public void onResume() {
        super.onResume();

        hideSystemUI();
        openBackgroundThread();

        Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        displayRefreshInterval = (long) (1 / display.getRefreshRate() * 1000);
        Point size = new Point();
        display.getSize(size);
        displayWidth = size.x;
        displayHeight = size.y;
        display = null;

        if (mTextureView.isAvailable()) {
            setupCamera(mTextureView.getWidth(), mTextureView.getHeight());
            openCamera();
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }

        startLocationUpdates();
        mSensorManager.registerListener((SensorEventListener) this, mOrientationSensor, SensorManager.SENSOR_DELAY_FASTEST);
    }

    @Override
    public void onPause() {
        super.onPause();
        closeCamera();
        closeBackgroundThread();
        stopLocationUpdates();
        mSensorManager.unregisterListener((SensorEventListener) this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float[] rotMatrix = new float[9];
        float[] correctedRotMatrix = new float[9];

        SensorManager.getRotationMatrixFromVector(rotMatrix, event.values);
        SensorManager.remapCoordinateSystem(rotMatrix, SensorManager.AXIS_Z, SensorManager.AXIS_MINUS_X, correctedRotMatrix);
        SensorManager.getOrientation(correctedRotMatrix, mOrientation);
        mOrientation[0] = mOrientation[0]*180/(float)Math.PI;
        mOrientation[1] = mOrientation[1]*-180/(float)Math.PI;
        mOrientation[2] = mOrientation[2]*180/(float)Math.PI;

        debugFPStv.setText("Yaw: " + mOrientation[0] +
                "\nPitch: " + mOrientation[1] +
                "\nRoll: " + mOrientation[2]);


        int rotationQuarts = (int) -(360+mOrientation[2]+45)/90*90+90;
        mCompassFace.setRotation(rotationQuarts);
        mBlueArrow.setRotation(ImageSaver.yawToDirection(mOrientation[0])-imageDirection[0]+rotationQuarts);
        mTrueLevelView.setRotation(-mOrientation[2]+90);

        if (Math.abs(mOrientation[2] - imageDirection[2]) > 5) {
            mTrueLevelView.setBackgroundColor(Color.WHITE);
        } else {
            mTrueLevelView.setBackgroundColor(Color.GREEN);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    private CameraCaptureSession.CaptureCallback mSessionCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        private void process(CaptureResult result) {
            switch (mState) {
                case STATE_PREVIEW:
                    //Do nothing
                    break;
                case STATE_WAIT_LOCK:
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    switch (afState) {
                        case CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED:
                            captureStillImage();
                            mState = STATE_PREVIEW;
                            break;
                        case CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED:
                            Toast.makeText(getApplicationContext(), "Focus failed, keep device still", Toast.LENGTH_SHORT).show();
                            unlockFocus();
                            mState = STATE_PREVIEW;
                            break;
                    }
                    break;
            }
        }

        @Override
        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            process(result);
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
        }
    };

    private CameraDevice.StateCallback mCameraDeviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCameraDevice = camera;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            camera.close();
            mCameraDevice = null;
        }
    };

    private TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            setupCamera(width, height);
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            /*
            float frameTime = (float) (System.nanoTime() - lastFrameTime)/1000000;
            lastFrameTime = System.nanoTime();

            debugFPStv.setText(String.valueOf(frameTime) +
                    " ms\n" + 1/frameTime*1000 + " FPS");
            */
        }
    };

    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            mBackgroundHandler.post(new ImageSaver(mImageFile, reader.acquireNextImage(), mLocation, mOrientation));
        }
    };

    private void setupCamera(int width, int height) {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraID : cameraManager.getCameraIdList()) {
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraID);
                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }
                StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                Size largestImageSize = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new Comparator<Size>() {
                    @Override
                    public int compare(Size o1, Size o2) {
                        return Long.signum(o1.getWidth() * o1.getHeight() - o2.getWidth() * o2.getHeight());
                    }
                });
                mImageReader = ImageReader.newInstance(largestImageSize.getWidth(), largestImageSize.getHeight(), ImageFormat.JPEG, 1);
                mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);

                float cameraSensorAspectRatio = (float) largestImageSize.getWidth() / largestImageSize.getHeight();

                int tvHeight = (int) (cameraSensorAspectRatio * width);

                mTextureView.setLayoutParams(new ConstraintLayout.LayoutParams(width ,tvHeight));

                mPreviewSize = getPreferredPreviewSize(map.getOutputSizes(SurfaceTexture.class), width, tvHeight, cameraSensorAspectRatio);
                mCameraID = cameraID;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
            ;
        }
    }

    private Size getPreferredPreviewSize(Size[] mapSizes, int width, int height, float aspect) {
        List<Size> collectorSizes = new ArrayList<>();
        for (Size option : mapSizes) {
            if (option.getWidth() > width && option.getHeight() == (int) (option.getWidth()*aspect)) {
                collectorSizes.add(option);
            } else if (option.getHeight() > width && option.getWidth() == (int) (option.getHeight()*aspect)) {
                collectorSizes.add(option);
            }
        }

        if (collectorSizes.size() > 0) {
            return Collections.min(collectorSizes, new Comparator<Size>() {
                @Override
                public int compare(Size o1, Size o2) {
                    return Long.signum(o1.getWidth() * o1.getHeight() - o2.getWidth() * o2.getHeight());
                }
            });
        }
        return mapSizes[0];
    }

    private void openCamera() {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                cameraManager.openCamera(mCameraID, mCameraDeviceStateCallback, mBackgroundHandler);
        }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        if (mCameraCaptureSession != null) {
            mCameraCaptureSession.close();
            mCameraCaptureSession = null;
        }
        if (mCameraDevice !=null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }
    }

    private void createCameraPreviewSession() {
        try {
            SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
            surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            Surface previewSurface = new Surface(surfaceTexture);
            mPreviewCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewCaptureRequestBuilder.addTarget(previewSurface);
            mPreviewCaptureRequestBuilder.set(mPreviewCaptureRequest.CONTROL_AE_ANTIBANDING_MODE, CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO);

            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface, mImageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    if(mCameraDevice ==null) {
                        return;
                    }
                    try {
                        mPreviewCaptureRequest = mPreviewCaptureRequestBuilder.build();
                        mCameraCaptureSession = session;
                        mCameraCaptureSession.setRepeatingRequest(mPreviewCaptureRequest, mSessionCaptureCallback, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Toast.makeText(getApplicationContext(), "Creating camera session failed", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    private void showSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    private void openBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera2 background thread");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void closeBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void takePhoto(View view) {
         try {
             mImageFile = createImageFile();
         } catch (IOException e) {
             e.printStackTrace();
         }
        lockFocus();
    }

    public void openProjects(View view) {
        Intent myIntent = new Intent(this, ProjectActivity.class);
        //myIntent.putExtra("key", value); //Optional parameters
        startActivity(myIntent);
    }

    private void lockFocus() {
        try {
            mState = STATE_WAIT_LOCK;
            mPreviewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
            mCameraCaptureSession.capture(mPreviewCaptureRequestBuilder.build(), mSessionCaptureCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void unlockFocus() {
        mState = STATE_PREVIEW;
        try {
            mPreviewCaptureRequestBuilder.set(mPreviewCaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
            mCameraCaptureSession.capture(mPreviewCaptureRequestBuilder.build(), mSessionCaptureCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void captureStillImage() {
        Handler uiHandler = new Handler(getMainLooper());

        try {
            CaptureRequest.Builder captureStillImageBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureStillImageBuilder.addTarget(mImageReader.getSurface());
            captureStillImageBuilder.set(CaptureRequest.JPEG_QUALITY, jpegQuality);
            captureStillImageBuilder.set(CaptureRequest.JPEG_ORIENTATION, (Integer) ORIENTATIONS.get(rotation));

            CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);

                    Toast.makeText(getApplicationContext(), "Image Captured!", Toast.LENGTH_SHORT).show();
                    unlockFocus();
                }
            };
            mCameraCaptureSession.capture(captureStillImageBuilder.build(), captureCallback, uiHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void createImageFolder() {
        workingDirectory = new File(getIntent().getStringExtra("workingDirectory"));
        if(!workingDirectory.exists()) {
            workingDirectory.mkdirs();
            Toast.makeText(this, "Project directory not found! Creating new!", Toast.LENGTH_LONG).show();
        }
    }

    private File createImageFile() throws IOException {
        createImageFolder();
        String timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss.SSS").format(new Date());
        String imageFileName = timeStamp;

           // File image = File.createTempFile(imageFileName, ".jpg", mGalleryFolder);
            File image = new File(workingDirectory, imageFileName + ".jpg");

        //mImageFileLocation = image.getAbsolutePath();

        //Uri directoryURI = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        return image;
    }

    private void saveImage(Bitmap bitmap, @NonNull String name) throws IOException {
        boolean saved;
        OutputStream fos;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = getApplicationContext().getContentResolver();
            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/" + IMAGES_FOLDER_NAME);
            Uri imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
            fos = resolver.openOutputStream(imageUri);
        } else {
            String imagesDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DCIM).toString() + File.separator + IMAGES_FOLDER_NAME;

            File file = new File(imagesDir);

            if (!file.exists()) {
                file.mkdir();
            }

            File image = new File(imagesDir, name + ".jpg");
            fos = new FileOutputStream(image);

        }

        saved = bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
        fos.flush();
        fos.close();
    }

    private void startLocationUpdates() {
        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, null);
    }

    private void stopLocationUpdates() {
        fusedLocationProviderClient.removeLocationUpdates(locationCallback);
    }

    private ArrayList<Cell> listAllFiles(String pathName) {
        String extension;
        ArrayList<Cell> allFiles = new ArrayList<>();
        File file = new File(pathName);
        File[] files = file.listFiles();
        if (files != null) {
            for (File f : files) {
                if (!f.isDirectory()) {
                    extension = f.getAbsolutePath().substring(f.getAbsolutePath().lastIndexOf(".") + 1);
                    byte[] bytes = extension.getBytes();
                    if (extension.equals("jpg")) {
                        Cell cell = new Cell();
                        cell.setTitle(f.getName());
                        cell.setPath(f.getAbsolutePath());
                        allFiles.add(cell);
                    }
                }
            }
        }
        return allFiles;
    }

    public Bitmap getBitmapFromAssets(String fileName) {

        InputStream istr = null;
        try {
            istr = getAssets().open(fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Bitmap bitmap = BitmapFactory.decodeStream(istr);

        return bitmap;
    }
}
