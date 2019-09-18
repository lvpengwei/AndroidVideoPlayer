package com.lvpengwei.androidvideoplayer;

import android.content.res.AssetFileDescriptor;
import android.graphics.SurfaceTexture;
import android.opengl.EGLSurface;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.TextureView;

import com.lvpengwei.androidvideoplayer.decoder.SampleBuffer;
import com.lvpengwei.androidvideoplayer.decoder.VideoReader;
import com.lvpengwei.androidvideoplayer.opengl.media.render.EglCore;
import com.lvpengwei.androidvideoplayer.opengl.media.render.VideoGLSurfaceRender;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private static String TAG = "MainActivity";

    private static final Object renderStartLock = new Object();
    private static final Object renderLock = new Object();
    private static SampleBuffer sampleBuffer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        TextureView surfaceView = findViewById(R.id.surface_view);
        Renderer renderer = new Renderer();
        surfaceView.setSurfaceTextureListener(renderer);
        startReading();
        renderer.start();
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
                    synchronized (renderLock) {
                        if (sampleBuffer != null) {
                            sampleBuffer.dealloc();
                        }
                        sampleBuffer = reader.copyNextSample();
                        Log.i(TAG, "" + sampleBuffer);
                        if (sampleBuffer.texID > 0) {
                            renderLock.notify();
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

    private static class Renderer extends Thread implements TextureView.SurfaceTextureListener {
        private final Object mLock = new Object();        // guards mSurfaceTexture, mDone
        private int width;
        private int height;
        private SurfaceTexture mSurfaceTexture;
        private EglCore mEglCore;
        private EGLSurface eglSurface;
        private VideoGLSurfaceRender render;
        private boolean mDone;

        public Renderer() {
            super("TextureViewGL Renderer");
        }

        @Override
        public void run() {
            while (true) {
                SurfaceTexture surfaceTexture = null;

                // Latch the SurfaceTexture when it becomes available.  We have to wait for
                // the TextureView to create it.
                synchronized (mLock) {
                    while (!mDone && (surfaceTexture = mSurfaceTexture) == null) {
                        try {
                            mLock.wait();
                        } catch (InterruptedException ie) {
                            throw new RuntimeException(ie);     // not expected
                        }
                    }
                    if (mDone) {
                        break;
                    }
                }
                Log.d(TAG, "Got surfaceTexture=" + surfaceTexture);

                mEglCore = new EglCore();
                eglSurface = mEglCore.createWindowSurface(surfaceTexture);
                mEglCore.makeCurrent(eglSurface);
                render = new VideoGLSurfaceRender();
                render.init(width, height);

                synchronized (renderStartLock) {
                    renderStartLock.notify();
                }

                doAnimation();

                mEglCore.releaseSurface(eglSurface);
                mEglCore.release();
            }

            Log.d(TAG, "Renderer thread exiting");
        }

        private void doAnimation() {
            while (true) {
                // Check to see if the TextureView's SurfaceTexture is still valid.
                synchronized (mLock) {
                    SurfaceTexture surfaceTexture = mSurfaceTexture;
                    if (surfaceTexture == null) {
                        Log.d(TAG, "doAnimation exiting");
                        return;
                    }
                }
                synchronized (renderLock) {
                    try {
                        renderLock.wait();
                        mEglCore.makeCurrent(eglSurface);
                        render.renderToViewWithAspectFit(sampleBuffer.texID, 1920, 1080);
                        mEglCore.swapBuffers(eglSurface);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        /**
         * Tells the thread to stop running.
         */
        public void halt() {
            synchronized (mLock) {
                mDone = true;
                mLock.notify();
            }
        }

        @Override   // will be called on UI thread
        public void onSurfaceTextureAvailable(SurfaceTexture st, int width, int height) {
            Log.d(TAG, "onSurfaceTextureAvailable(" + width + "x" + height + ")");
            synchronized (mLock) {
                this.width = width;
                this.height = height;
                mSurfaceTexture = st;
                mLock.notify();
            }
        }

        @Override   // will be called on UI thread
        public void onSurfaceTextureSizeChanged(SurfaceTexture st, int width, int height) {
            Log.d(TAG, "onSurfaceTextureSizeChanged(" + width + "x" + height + ")");
        }

        @Override   // will be called on UI thread
        public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
            Log.d(TAG, "onSurfaceTextureDestroyed");

            synchronized (mLock) {
                mSurfaceTexture = null;
            }
            return false;
        }

        @Override   // will be called on UI thread
        public void onSurfaceTextureUpdated(SurfaceTexture st) {
            //Log.d(TAG, "onSurfaceTextureUpdated");
        }
    }
}
