package com.lvpengwei.androidvideoplayer.player;

import android.opengl.EGL14;
import android.opengl.EGLSurface;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.view.Surface;

import com.lvpengwei.androidvideoplayer.opengl.media.render.EglCore;
import com.lvpengwei.androidvideoplayer.opengl.media.render.VideoGLSurfaceRender;

public class VideoOutput {

    private static final int VIDEO_OUTPUT_MESSAGE_CREATE_EGL_CONTEXT = 1;
    private static final int VIDEO_OUTPUT_MESSAGE_DESTROY_EGL_CONTEXT = 2;
    private static final int VIDEO_OUTPUT_MESSAGE_RENDER_FRAME = 3;
    private boolean eglHasDestroyed;

    public interface VideoOutputCallback {
        int getTexture();
    }

    private Surface surface;
    private int screenWidth;
    private int screenHeight;
    private VideoOutputCallback videoOutputCallback;
    private Object ctx;
    private HandlerThread mThread;
    private Handler mHandler;

    private boolean surfaceExists = false;
    private boolean forceGetFrame = false;
    private EglCore eglCore;
    private EGLSurface renderTexSurface;
    private VideoGLSurfaceRender render;

    public VideoOutput(Surface surface, int screenWidth, int screenHeight, VideoOutputCallback callback, Object ctx) {
        this.surface = surface;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.videoOutputCallback = callback;
        this.ctx = ctx;

        setupHandler();
        sendMessage(VIDEO_OUTPUT_MESSAGE_CREATE_EGL_CONTEXT);
    }

    public void signalFrameAvailable() {
        if (!surfaceExists) return;
        sendMessage(VIDEO_OUTPUT_MESSAGE_RENDER_FRAME);
    }

    private void sendMessage(int msgId) {
        mHandler.obtainMessage(msgId).sendToTarget();
    }

    private void createEGLContext() {
        eglCore = new EglCore();
        renderTexSurface = eglCore.createWindowSurface(surface);
        if (renderTexSurface == null) return;
        eglCore.makeCurrent(renderTexSurface);
        render = new VideoGLSurfaceRender();
        render.init(screenWidth, screenHeight);
        eglCore.doneCurrent();
        surfaceExists = true;
        forceGetFrame = true;
    }

    private void renderVideo() {
        int texID = videoOutputCallback.getTexture();
        eglCore.makeCurrent(renderTexSurface);
        render.renderToView(texID, screenWidth, screenHeight);
        eglCore.swapBuffers(renderTexSurface);
        if (forceGetFrame) forceGetFrame = false;
    }

    private void destroyWindowSurface() {
        if (EGL14.EGL_NO_SURFACE == renderTexSurface) return;
        if (render != null) {
            render.dealloc();
            render = null;
        }

        if (eglCore != null) {
            eglCore.releaseSurface(renderTexSurface);
        }

        renderTexSurface = EGL14.EGL_NO_SURFACE;
        surfaceExists = false;
    }

    private void stopOutput() {
        if (mHandler == null) return;
        sendMessage(VIDEO_OUTPUT_MESSAGE_DESTROY_EGL_CONTEXT);
    }

    private void destroyEGLContext() {
        if (EGL14.EGL_NO_SURFACE != renderTexSurface) {
            eglCore.makeCurrent(renderTexSurface);
        }
        if (eglCore != null) {
            eglCore.release();
            eglCore = null;
        }

        eglHasDestroyed = true;
    }

    private void setupHandler() {
        mThread = new HandlerThread("VideoOutput");
        mThread.start();
        mHandler = new Handler(mThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case VIDEO_OUTPUT_MESSAGE_CREATE_EGL_CONTEXT:
                        if (eglHasDestroyed) break;
                        createEGLContext();
                        break;
                    case VIDEO_OUTPUT_MESSAGE_RENDER_FRAME:
                        if (eglHasDestroyed) break;
                        renderVideo();
                        break;
                    case VIDEO_OUTPUT_MESSAGE_DESTROY_EGL_CONTEXT:
                        destroyEGLContext();
                        break;
                    default:
                        break;
                }
            }
        };
    }
}
