package com.lvpengwei.androidvideoplayer.opengl.media.texture.uploader;

import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.util.Log;

import com.lvpengwei.androidvideoplayer.opengl.media.render.EglCore;
import com.lvpengwei.androidvideoplayer.opengl.media.texture.TextureFrame;
import com.lvpengwei.androidvideoplayer.opengl.media.texture.copier.TextureFrameCopier;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TextureFrameUploader {
    private static String TAG = "TextureFrameUploader";
    private static int OPENGL_VERTEX_COORDNATE_CNT = 8;
    float[] DECODER_COPIER_GL_VERTEX_COORDS = new float[]{
            -1.0f, -1.0f,    // 0 top left
            1.0f, -1.0f,    // 1 bottom left
            -1.0f, 1.0f,  // 2 bottom right
            1.0f, 1.0f,    // 3 top right
    };
    float[] DECODER_COPIER_GL_TEXTURE_COORDS_NO_ROTATION = new float[]{
            0.0f, 0.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f
    };
    float[] DECODER_COPIER_GL_TEXTURE_COORDS_ROTATED_90 = new float[]{
            0.0f, 1.0f,
            0.0f, 0.0f,
            1.0f, 1.0f,
            1.0f, 0.0f
    };
    float[] DECODER_COPIER_GL_TEXTURE_COORDS_ROTATED_180 = new float[]{
            1.0f, 1.0f,
            0.0f, 1.0f,
            1.0f, 0.0f,
            0.0f, 0.0f,
    };
    float[] DECODER_COPIER_GL_TEXTURE_COORDS_ROTATED_270 = new float[]{
            1.0f, 0.0f,
            1.0f, 1.0f,
            0.0f, 0.0f,
            0.0f, 1.0f
    };
    boolean isInitial;
    EglCore eglCore;
    EGLSurface copyTexSurface;
    int videoWidth;
    int videoHeight;
    float[] vertexCoords;
    float[] textureCoords;
    UploaderCallback mUploaderCallback;

    TextureFrameCopier textureFrameCopier;
    /**
     * videoEffectProcessor相关的
     **/
    private int outputTexId;
    /**
     * 操作纹理的FBO
     **/
    private int mFBO;
    /**
     * 解码线程
     * 拷贝以及处理线程
     * 当解码出一帧之后会signal拷贝线程（自己wait住）,拷贝线程会利用updateTexImageCallback来更新解码线程最新解码出来的Frame
     * 然后拷贝线程会把这个Frame进行拷贝以及通过VideoEffectProcessor进行处理
     * 最终会给解码线程发送signal信号（自己wait住）
     **/
    TextureFrame textureFrame;
    UpdateTexImageCallback updateTexImageCallback;
    SignalDecodeThreadCallback signalDecodeThreadCallback;
    Object updateTexImageContext;
    Lock mLock;
    Condition mCondition;
    RenderThreadMessage _msg;
    Thread mThread;

    public TextureFrameUploader() {
        _msg = RenderThreadMessage.MSG_NONE;
        eglCore = null;
        textureFrame = null;
        outputTexId = -1;
        isInitial = false;
        vertexCoords = new float[OPENGL_VERTEX_COORDNATE_CNT];
        textureCoords = new float[OPENGL_VERTEX_COORDNATE_CNT];
        mLock = new ReentrantLock();
        mCondition = mLock.newCondition();
    }

    public void setUploaderCallback(UploaderCallback mUploaderCallback) {
        this.mUploaderCallback = mUploaderCallback;
    }

    public boolean start(int videoWidth, int videoHeight, int degress) {
        this.videoWidth = videoWidth;
        this.videoHeight = videoHeight;
        vertexCoords = DECODER_COPIER_GL_VERTEX_COORDS;
        switch (degress) {
            case 90:
                textureCoords = DECODER_COPIER_GL_TEXTURE_COORDS_ROTATED_90;
                break;
            case 180:
                textureCoords = DECODER_COPIER_GL_TEXTURE_COORDS_ROTATED_180;
                break;
            case 270:
                textureCoords = DECODER_COPIER_GL_TEXTURE_COORDS_ROTATED_270;
                break;
            default:
                textureCoords = DECODER_COPIER_GL_TEXTURE_COORDS_NO_ROTATION;
                break;
        }
        _msg = RenderThreadMessage.MSG_WINDOW_SET;
        mThread = new Thread(new Runnable() {
            @Override
            public void run() {
                renderLoop();
            }
        });
        mThread.start();
        return true;
    }

    public void signalFrameAvailable() throws InterruptedException {
        while (!isInitial || _msg == RenderThreadMessage.MSG_WINDOW_SET || null == eglCore) {
            Thread.sleep(100 * 1000);
        }
        mLock.lock();
        mCondition.signal();
        mLock.unlock();
    }

    public void stop() {
        mLock.lock();
        _msg = RenderThreadMessage.MSG_RENDER_LOOP_EXIT;
        mCondition.signal();
        mLock.unlock();
    }

    public void registerUpdateTexImageCallback(UpdateTexImageCallback updateTexImageCallback, SignalDecodeThreadCallback signalDecodeThreadCallback, Object context) {
        this.updateTexImageCallback = updateTexImageCallback;
        this.signalDecodeThreadCallback = signalDecodeThreadCallback;
        this.updateTexImageContext = context;
    }

    public void renderLoop() {
        boolean renderingEnabled = true;
        Log.i(TAG, "renderLoop()");
        while (renderingEnabled) {
            mLock.lock();
            switch (_msg) {
                case MSG_WINDOW_SET:
                    Log.i(TAG, "receive msg MSG_WINDOW_SET");
                    isInitial = initialize();
                    break;
                case MSG_RENDER_LOOP_EXIT:
                    Log.i(TAG, "receive msg MSG_RENDER_LOOP_EXIT");
                    renderingEnabled = false;
                    destroy();
                    break;
                default:
                    break;
            }
            _msg = RenderThreadMessage.MSG_NONE;
            if (null != eglCore) {
                signalDecodeThread();
                mCondition.awaitUninterruptibly();
                eglCore.makeCurrent(copyTexSurface);
                drawFrame();
            }
            mLock.unlock();
        }
        Log.i(TAG, "Render loop exits");
    }

    public void destroy() {
        Log.i(TAG, "dealloc eglCore ...");
        if (mUploaderCallback != null)
            mUploaderCallback.destroyFromUploaderGLContext();

        eglCore.makeCurrent(copyTexSurface);
        if (-1 != outputTexId) {
            GLES20.glDeleteTextures(1, new int[]{outputTexId}, 0);
        }
        if (mFBO > 0) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            GLES20.glDeleteFramebuffers(1, new int[]{mFBO}, 0);
        }
        eglCore.releaseSurface(copyTexSurface);
        eglCore.release();
    }

    public boolean initialize() {
        eglCore = new EglCore();

        copyTexSurface = eglCore.createOffscreenSurface(videoWidth, videoHeight);
        eglCore.makeCurrent(copyTexSurface);

        int[] frameBuffers = new int[1];
        GLES20.glGenFramebuffers(1, frameBuffers, 0);
        mFBO = frameBuffers[0];
        //初始化outputTexId
        frameBuffers = new int[1];
        GLES20.glGenTextures(1, frameBuffers, 0);
        outputTexId = frameBuffers[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, outputTexId);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, videoWidth, videoHeight, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        if (mUploaderCallback != null) {
            mUploaderCallback.initFromUploaderGLContext(eglCore);
        }

        eglCore.makeCurrent(copyTexSurface);
        Log.i(TAG, "leave TextureFrameUploader::initialize");
        return true;
    }

    public void drawFrame() {
        float position = this.updateTexImage();
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFBO);
        /** 将YUV数据(软件解码), samplerExternalOES格式的TexId(硬件解码) 拷贝到GL_RGBA格式的纹理ID上 **/
        FloatBuffer vertexBuffer = ByteBuffer.allocateDirect(vertexCoords.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(vertexCoords);
        vertexBuffer.position(0);
        FloatBuffer texCoordsBuffer = ByteBuffer.allocateDirect(textureCoords.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(textureCoords);
        texCoordsBuffer.position(0);
        textureFrameCopier.renderWithCoords(textureFrame, outputTexId, vertexBuffer, texCoordsBuffer);
        if (mUploaderCallback != null) {
            mUploaderCallback.processVideoFrame(outputTexId, videoWidth, videoHeight, position);
        } else {
            Log.e(TAG, "TextureFrameUploader::mUploaderCallback is NULL");
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }

    public float updateTexImage() {
        return updateTexImageCallback.call(textureFrame, updateTexImageContext);
    }

    public void signalDecodeThread() {
        signalDecodeThreadCallback.call(updateTexImageContext);
    }

    enum RenderThreadMessage {
        MSG_NONE,
        MSG_WINDOW_SET,
        MSG_RENDER_LOOP_EXIT
    }

    public interface UploaderCallback {
        void processVideoFrame(int inputTexId, int width, int height, float position);

        int processAudioData(short sample, int size, float position, byte buffer);

        void onSeekCallback(float seek_seconds);

        void initFromUploaderGLContext(EglCore eglCore);

        void destroyFromUploaderGLContext();
    }

    public interface UpdateTexImageCallback {
        float call(TextureFrame textureFrame, Object context);
    }

    public interface SignalDecodeThreadCallback {
        void call(Object context);
    }
}
