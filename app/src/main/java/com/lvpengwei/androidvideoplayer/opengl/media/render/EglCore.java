package com.lvpengwei.androidvideoplayer.opengl.media.render;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.util.Log;
import android.view.Surface;

public class EglCore {
    private static final String TAG = "EglCore";

    private static final int EGL_RECORDABLE_ANDROID = 0x3142;
    private static final int EGL_OPENGL_ES2_BIT = 4;

    private EGLDisplay eglDisplay;

    private EGLContext eglContext;
    private EGLConfig[] configs;

    public boolean initWithShareContext() {
        return init(EglShareContext.getInstance().getSharedContext());
    }

    public boolean init(EGLContext context) {
        if ((eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)) == EGL14.EGL_NO_DISPLAY) {
            Log.e(TAG, "eglGetDisplay() returned error " + EGL14.eglGetError());
            return false;
        }
        int[] version = new int[2];
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            Log.e(TAG, "eglInitialize() returned error " + EGL14.eglGetError());
            return false;
        }
        int[] attributeList = {
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                EGL_RECORDABLE_ANDROID, 1,
                EGL14.EGL_NONE
        };

        configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!EGL14.eglChooseConfig(eglDisplay, attributeList, 0, configs, 0, configs.length,
                numConfigs, 0)) {
            Log.e(TAG, "eglChooseConfig() returned error " + EGL14.eglGetError());
            return false;
        }
        int[] attribute_list = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
        };
        eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], null == context ? EGL14.EGL_NO_CONTEXT : context, attribute_list, 0);
        if (eglContext == null) {
            Log.e(TAG, "eglCreateContext() returned error " + EGL14.eglGetError());
            return false;
        }
        return true;
    }

    public void setPresentationTime(EGLSurface eglSurface, long usecs) {
        if (eglSurface != null) {
            EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, usecs * 1000);
        }
    }

    public void release() {
        if (EGL14.EGL_NO_CONTEXT != eglContext && EGL14.EGL_NO_DISPLAY != eglDisplay) {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            Log.i(TAG, "after eglMakeCurrent...");
            EGL14.eglDestroyContext(eglDisplay, eglContext);
            Log.i(TAG, "after eglDestroyContext...");
        }
        eglDisplay = EGL14.EGL_NO_DISPLAY;
        eglContext = EGL14.EGL_NO_CONTEXT;
    }

    public void releaseSurface(EGLSurface eglSurface) {
        EGL14.eglDestroySurface(eglDisplay, eglSurface);
    }

    public EGLContext getEglContext() {
        return eglContext;
    }

    public EGLDisplay getEglDisplay() {
        return eglDisplay;
    }

    public void makeCurrent(EGLSurface eglSurface) {
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            GLTools.checkEglError("eglMakeCurrent failed");
        }
    }

    public void doneCurrent() {
        EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
    }

    public boolean swapBuffers(EGLSurface eglSurface) {
        return EGL14.eglSwapBuffers(eglDisplay, eglSurface);
    }

    public EGLSurface createWindowSurface(Object surface) {
        if (surface == null) return null;
        if (!(surface instanceof Surface) && !(surface instanceof SurfaceTexture)) {
            throw new RuntimeException("invalid surface: " + surface);
        }
        int[] surfaceAttribs = {EGL14.EGL_NONE};
        EGLSurface eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, configs[0], surface, surfaceAttribs, 0);
        if (eglSurface == null) {
            Log.e(TAG, "eglCreateWindowSurface() returned error " + EGL14.eglGetError());
            release();
            return null;
        }
        return eglSurface;
    }

    public EGLSurface createOffscreenSurface(int width, int height) {
        int[] PbufferAttributes = {EGL14.EGL_WIDTH, width, EGL14.EGL_HEIGHT, height, EGL14.EGL_NONE, EGL14.EGL_NONE};
        EGLSurface surface = EGL14.eglCreatePbufferSurface(eglDisplay, configs[0], PbufferAttributes, 0);
        if (surface == null) {
            Log.e(TAG, "eglCreatePbufferSurface() returned error %d" + EGL14.eglGetError());
        }
        return surface;
    }
}
