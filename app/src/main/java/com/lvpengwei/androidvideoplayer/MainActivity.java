package com.lvpengwei.androidvideoplayer;

import android.content.res.AssetFileDescriptor;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.lvpengwei.androidvideoplayer.decoder.SampleBuffer;
import com.lvpengwei.androidvideoplayer.decoder.VideoReader;
import com.lvpengwei.androidvideoplayer.player.VideoOutput;

import java.io.IOException;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    private static String TAG = "MainActivity";

    private static final Object renderStartLock = new Object();
    private static SampleBuffer sampleBuffer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        SurfaceView surfaceView = findViewById(R.id.surface_view);
        surfaceView.getHolder().addCallback(this);
        startReading();
    }

    private VideoReader reader;

    private void startReading() {
        final AssetFileDescriptor fd;
        try {
            fd = getAssets().openFd("demo_video_01.mp4");
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        reader = new VideoReader();
        if (!reader.startReading(fd)) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (renderStartLock) {
                    try {
                        renderStartLock.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                while (!reader.isVideoEOF()) {
                    if (sampleBuffer != null) {
                        sampleBuffer.dealloc();
                    }
                    sampleBuffer = reader.copyNextSample();
                    Log.i(TAG, "" + sampleBuffer);
                    if (sampleBuffer.texID > 0) {
                        synchronized (videoOutputLock) {
                            videoOutput.signalFrameAvailable();
                        }
                    }
                    try {
                        Thread.sleep(32);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                reader.stopReading();
            }
        }).start();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

    }

    private final Object videoOutputLock = new Object();
    private VideoOutput videoOutput;

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        synchronized (videoOutputLock) {
            if (videoOutput != null) {
                videoOutput.stopOutput();
            }
            videoOutput = new VideoOutput(holder.getSurface(), width, height, new VideoOutput.VideoOutputCallback() {
                @Override
                public SampleBuffer getTexture(Object ctx, boolean forceGetFrame) {
                    return sampleBuffer;
                }
            }, this);
        }
        synchronized (renderStartLock) {
            renderStartLock.notify();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        videoOutput.onSurfaceDestroyed();
    }

}
