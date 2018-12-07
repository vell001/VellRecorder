package com.bibi.vell.vellrecorder;

import android.media.MediaRecorder;
import android.util.Log;
import android.view.Surface;

public class MediaRecordThread extends Thread {

    private static final String TAG = "MediaRecordService";
    private int mWidth;
    private int mHeight;
    private String mDstPath;
    private MediaRecorder mMediaRecorder;
    private static final int FRAME_RATE = 24; // 24 fps

    private int mAudioResource;
    private int mOrientation;


    public MediaRecordThread(int width, int height, int orientation, String dstPath, int audioResource) {
        mWidth = width;
        mHeight = height;
        mDstPath = dstPath;
        mAudioResource = audioResource;
        mOrientation = orientation;
        initMediaRecorder();
    }

    @Override
    public void run() {
        try {
            mMediaRecorder.start();
            Log.i(TAG, "mediarecorder start");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Surface getSurface() {
        return mMediaRecorder.getSurface();
    }

    /**
     * 初始化MediaRecorder
     *
     * @return
     */
    public void initMediaRecorder() {
        mMediaRecorder = new MediaRecorder();
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        if (mAudioResource >= 0) {
            mMediaRecorder.setAudioSource(mAudioResource);
        }
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setVideoSize(mWidth, mHeight);
        mMediaRecorder.setVideoFrameRate(FRAME_RATE);
        mMediaRecorder.setVideoEncodingBitRate(6000000);
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        if (mAudioResource >= 0) {
            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        }

        mMediaRecorder.setOutputFile(mDstPath);

        mMediaRecorder.setOrientationHint(mOrientation);

        try {
            mMediaRecorder.prepare();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void release() {
        if (mMediaRecorder != null) {
            mMediaRecorder.setOnErrorListener(null);
            mMediaRecorder.reset();
            mMediaRecorder.release();
        }
        Log.i(TAG, "release");
    }
}
