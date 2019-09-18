package com.lvpengwei.androidvideoplayer.player;

import android.opengl.EGL14;
import android.opengl.EGLSurface;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.view.Surface;

import com.lvpengwei.androidvideoplayer.opengl.media.render.EglCore;
import com.lvpengwei.androidvideoplayer.opengl.media.render.VideoGLSurfaceRender;
import com.lvpengwei.androidvideoplayer.player.common.FrameTexture;

public class VideoOutput {

    private static String TAG = "VideoOutput";

    private static final int VIDEO_OUTPUT_MESSAGE_CREATE_EGL_CONTEXT = 1;
    private static final int VIDEO_OUTPUT_MESSAGE_DESTROY_EGL_CONTEXT = 2;
    private static final int VIDEO_OUTPUT_MESSAGE_RENDER_FRAME = 3;
    private static final int VIDEO_OUTPUT_MESSAGE_DESTROY_WINDOW_SURFACE = 4;
    private static final int VIDEO_OUTPUT_MESSAGE_CREATE_WINDOW_SURFACE = 5;
    private boolean eglHasDestroyed;

    public interface VideoOutputCallback {
        FrameTexture getTexture(Object ctx, boolean forceGetFrame);
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

    public void init(Surface surface, int screenWidth, int screenHeight, VideoOutputCallback callback, Object ctx) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.videoOutputCallback = callback;
        this.ctx = ctx;

        setupHandler();
        sendMessage(VIDEO_OUTPUT_MESSAGE_CREATE_EGL_CONTEXT, surface);
    }

    public void signalFrameAvailable() {
        if (!surfaceExists) return;
        sendMessage(VIDEO_OUTPUT_MESSAGE_RENDER_FRAME);
    }

    public void onSurfaceCreated(Surface surface) {
        Log.i(TAG, "enter VideoOutput::onSurfaceCreated");
        sendMessage(VIDEO_OUTPUT_MESSAGE_CREATE_WINDOW_SURFACE, surface);
        sendMessage(VIDEO_OUTPUT_MESSAGE_RENDER_FRAME);
    }

    public void onSurfaceDestroyed() {
        Log.i(TAG, "enter VideoOutput::onSurfaceDestroyed");
        sendMessage(VIDEO_OUTPUT_MESSAGE_DESTROY_WINDOW_SURFACE);
    }

    public void resetRenderSize(int left, int top, int width, int height) {
        if (width > 0 && height > 0){
            screenWidth = width;
            screenHeight = height;
            if (null != render) {
                render.resetRenderSize(left, top, width, height);
            }
        }
    }

    public int getScreenWidth() {
        return screenWidth;
    }

    public int getScreenHeight() {
        return screenHeight;
    }

    private void sendMessage(int msgId) {
        sendMessage(msgId, null);
    }

    private void sendMessage(int msgId, Object obj) {
        if (mHandler == null) return;
        mHandler.obtainMessage(msgId, obj).sendToTarget();
    }

    private boolean createEGLContext(Surface surface) {
        Log.i(TAG, "enter VideoOutput::createEGLContext");
        eglCore = new EglCore();
        Log.i(TAG, "enter VideoOutput use sharecontext");
        if (!eglCore.initWithShareContext()) {
            Log.i(TAG, "create EGL Context failed...");
            return false;
        }
        createWindowSurface(surface);
        eglCore.doneCurrent();
        return true;
    }

    private void createWindowSurface(Surface surface) {
        Log.i(TAG, "enter VideoOutput::createWindowSurface");
        this.surface = surface;
        renderTexSurface = eglCore.createWindowSurface(surface);
        if (renderTexSurface != null) {
            eglCore.makeCurrent(renderTexSurface);
            // must after makeCurrent
            render = new VideoGLSurfaceRender();
            boolean isGLViewInitialized = render.init(screenWidth, screenHeight);// there must be right：1080, 810 for 4:3
            if (!isGLViewInitialized) {
                Log.i(TAG, "GL View failed on initialized...");
            } else {
                surfaceExists = true;
                forceGetFrame = true;
            }
        }
        Log.i(TAG, "Leave VideoOutput::createWindowSurface");
    }

    private void renderVideo() {
        FrameTexture buffer = videoOutputCallback.getTexture(ctx, forceGetFrame);
        if (buffer == null) return;
        eglCore.makeCurrent(renderTexSurface);
        render.renderToViewWithAspectFit(buffer.texId, 1920, 1080);
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

    public void stopOutput() {
        if (mHandler == null) return;
        sendMessage(VIDEO_OUTPUT_MESSAGE_DESTROY_EGL_CONTEXT);
        mThread.quitSafely();
    }

    private void destroyEGLContext() {
        if (EGL14.EGL_NO_SURFACE != renderTexSurface) {
            eglCore.makeCurrent(renderTexSurface);
        }
        destroyWindowSurface();
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
            private boolean initPlayerResourceFlag = false;
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case VIDEO_OUTPUT_MESSAGE_CREATE_EGL_CONTEXT:
                        if (eglHasDestroyed) break;
                        initPlayerResourceFlag = createEGLContext((Surface) msg.obj);
                        break;
                    case VIDEO_OUTPUT_MESSAGE_RENDER_FRAME:
                        if (eglHasDestroyed) break;
                        if (!initPlayerResourceFlag) break;
                        renderVideo();
                        break;
                    case VIDEO_OUTPUT_MESSAGE_DESTROY_EGL_CONTEXT:
                        destroyEGLContext();
                        break;
                    case VIDEO_OUTPUT_MESSAGE_DESTROY_WINDOW_SURFACE:
                        if (!initPlayerResourceFlag) break;
                        destroyWindowSurface();
                        break;
                    case VIDEO_OUTPUT_MESSAGE_CREATE_WINDOW_SURFACE:
                        if (eglHasDestroyed) break;
                        if (!initPlayerResourceFlag) break;
                        createWindowSurface((Surface) msg.obj);
                    default:
                        break;
                }
            }
        };
    }
}
