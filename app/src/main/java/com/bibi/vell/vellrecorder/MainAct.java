package com.bibi.vell.vellrecorder;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.View;
import android.widget.Button;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainAct extends Activity {
    private static final String TAG = "MainAct";
    public static final int RECORD_REQUEST_CODE = 10001;
    private MediaProjectionManager mProjectionManager;
    public static final int PERMISSION_REQUEST_CODE = 10002;
    private Button startBtn;
    private MediaRecordThread screenRecorder = null;
    private MediaRecordThread cameraRecorder = null;
    private CameraDevice mCameraDevice;
    private Size videoSize;
    private CameraCaptureSession mCaptureSession;
    private CaptureRequest mCaptureRequest;
    private VirtualDisplay mVirtualDisplay;

    String[] mPermissions = {
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
    };

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mBackgroundThread;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mBackgroundHandler;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_act);
        mProjectionManager =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        while (!requestPermission(mPermissions)) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // 摄像头
        openCamera();

        startBackgroundThread();

        startBtn = findViewById(R.id.start_btn);

        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (cameraRecorder == null) {
                    startRecordCamera();
                    startBtn.setText(R.string.end);
                } else {
                    stopRecordCamera();
                    startBtn.setText(R.string.start);
                }
            }
        });

        final Button startRecordScreenBtn = findViewById(R.id.start_record_screen_btn);
        startRecordScreenBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (screenRecorder == null) {
                    Intent captureIntent = mProjectionManager.createScreenCaptureIntent();
                    startActivityForResult(captureIntent, RECORD_REQUEST_CODE);
                    startRecordScreenBtn.setText(R.string.end);
                } else {
                    stopRecordScreen();
                    startRecordScreenBtn.setText(R.string.start);
                }
            }
        });

        final Button startRecordAllBtn = findViewById(R.id.start_record_all_btn);
        startRecordAllBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (screenRecorder == null) {
                    mBackgroundHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Intent captureIntent = mProjectionManager.createScreenCaptureIntent();
                            startActivityForResult(captureIntent, RECORD_REQUEST_CODE);
                        }
                    });

                    mBackgroundHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (screenRecorder == null) {
                                mBackgroundHandler.postDelayed(this, 100);
                            } else {
                                startRecordCamera();
                            }
                        }
                    }, 100);
                    startRecordAllBtn.setText(R.string.end);
                } else {
                    stopRecordScreen();
                    stopRecordCamera();
                    startRecordAllBtn.setText(R.string.start);
                }
            }
        });
    }

    private void openCamera() {
        CameraManager manager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {
                String cameraId = manager.getCameraIdList()[1];
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                StreamConfigurationMap map = characteristics
                        .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    throw new RuntimeException("Cannot get available preview/video sizes");
                }
                Size[] sizes = map.getOutputSizes(MediaRecorder.class);
                videoSize = sizes[0];
                manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(@NonNull CameraDevice cameraDevice) {
                        mCameraDevice = cameraDevice;
                    }

                    @Override
                    public void onDisconnected(@NonNull CameraDevice cameraDevice) {
                        cameraDevice.close();
                    }

                    @Override
                    public void onError(@NonNull CameraDevice cameraDevice, int i) {
                        cameraDevice.close();
                    }
                }, null);
            } else {
                Log.e(TAG, "摄像头权限获取失败");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }


    private void startRecordCamera() {
        File file = new File(Environment.getExternalStorageDirectory(), "002_test.mp4");
        cameraRecorder = new MediaRecordThread(videoSize.getWidth(), videoSize.getHeight(), 180, file.getAbsolutePath(),
                MediaRecorder.AudioSource.CAMCORDER);
        final Surface recorderSurface = cameraRecorder.getSurface();
        List<Surface> surfaceList = new ArrayList<>();
        surfaceList.add(recorderSurface);
        try {
            final CaptureRequest.Builder captureRequest = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            captureRequest.addTarget(recorderSurface);
            mCaptureRequest = captureRequest.build();
            mCameraDevice.createCaptureSession(surfaceList, new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    mCaptureSession = cameraCaptureSession;

                    mBackgroundHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            cameraRecorder.start();
                            try {
                                mCaptureSession.setRepeatingRequest(mCaptureRequest,
                                        new CameraCaptureSession.CaptureCallback() {
                                            @Override
                                            public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
                                                super.onCaptureStarted(session, request, timestamp, frameNumber);
                                            }

                                            @Override
                                            public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                                                super.onCaptureCompleted(session, request, result);
                                            }
                                        }, null);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                            Log.i(TAG, "开始录摄像头");
                        }
                    });

                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    mCaptureSession = cameraCaptureSession;
                    Log.e(TAG, "onConfigureFailed");
                }
            }, mBackgroundHandler);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void stopRecordCamera() {
        if (cameraRecorder == null) {
            return;
        }
        cameraRecorder.release();
        cameraRecorder = null;
        try {
            mCaptureSession.stopRepeating();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private boolean requestPermission(String[] permissions) {
        String[] p = getDeniedPermissions(this, permissions);
        if (p == null) {
            Log.i(TAG, "权限全部获取成功");
            return true;
        }
        ActivityCompat.requestPermissions(this, p, PERMISSION_REQUEST_CODE);
        return false;
    }

    public static String[] getDeniedPermissions(Context context, String[] permissions) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ArrayList<String> deniedPermissionList = new ArrayList<>();
            for (String permission : permissions) {
                if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                    deniedPermissionList.add(permission);
                }
            }
            int size = deniedPermissionList.size();
            if (size > 0) {
                return deniedPermissionList.toArray(new String[deniedPermissionList.size()]);
            }
        }
        return null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        requestPermission(mPermissions);
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RECORD_REQUEST_CODE && resultCode == RESULT_OK) {
            MediaProjection mediaProjection = mProjectionManager.getMediaProjection(resultCode, data);
            if (mediaProjection == null) {
                Log.e(TAG, "media projection is null");
                return;
            }
            File file = new File(Environment.getExternalStorageDirectory(), "001_test.mp4");
            startRecordScreen(mediaProjection, file);
        }
        Log.i(TAG, "onActivityResult");
    }

    private void startRecordScreen(MediaProjection mediaProjection, File saveFile) {
        if (screenRecorder != null) {
            Log.e(TAG, "screenRecorder is exist, maybe recording now");
            return;
        }
        //录屏生成文件
        Log.i(TAG, "start record");

        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int height = displayMetrics.heightPixels;
        int width = displayMetrics.widthPixels;


        screenRecorder = new MediaRecordThread(width, height, 0,
                saveFile.getAbsolutePath(), -1);
        mVirtualDisplay = mediaProjection.createVirtualDisplay(TAG + "-display", width, height, 1,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, screenRecorder.getSurface(), null, null);
        screenRecorder.start();
        Log.i(TAG, "开始录屏幕");
    }

    private void stopRecordScreen() {
        if (screenRecorder == null) {
            Log.e(TAG, "screenRecorder == null, maybe already stopped");
            return;
        }
        screenRecorder.release();
        screenRecorder = null;
        if (mVirtualDisplay == null) {
            return;
        }
        mVirtualDisplay.release();
        mVirtualDisplay = null;
    }
}
