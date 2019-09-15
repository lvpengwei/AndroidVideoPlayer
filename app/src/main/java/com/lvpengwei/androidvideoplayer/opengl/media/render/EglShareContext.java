package com.lvpengwei.androidvideoplayer.opengl.media.render;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.util.Log;

public class EglShareContext {
    private static String TAG = "EglShareContext";

    private static final int EGL_RECORDABLE_ANDROID = 0x3142;
    private static final int EGL_OPENGL_ES2_BIT = 4;
    private EGLDisplay sharedDisplay;
    private EGLContext sharedContext;

    public static final EglShareContext getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        private static final EglShareContext INSTANCE = new EglShareContext();
    }

    public EGLContext getSharedContext() {
        return sharedContext;
    }

    private EglShareContext() {
        sharedContext = EGL14.EGL_NO_CONTEXT;
        sharedDisplay = EGL14.EGL_NO_DISPLAY;
        if ((sharedDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)) == EGL14.EGL_NO_DISPLAY) {
            Log.e(TAG, "eglGetDisplay() returned error " + EGL14.eglGetError());
            return;
        }
        int[] version = new int[2];
        if (!EGL14.eglInitialize(sharedDisplay, version, 0, version, 1)) {
            Log.e(TAG, "eglInitialize() returned error " + EGL14.eglGetError());
            return;
        }
        int[] attributeList = {
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                EGL_RECORDABLE_ANDROID, 1,
                EGL14.EGL_NONE
        };

        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!EGL14.eglChooseConfig(sharedDisplay, attributeList, 0, configs, 0, configs.length,
                numConfigs, 0)) {
            Log.e(TAG, "eglChooseConfig() returned error " + EGL14.eglGetError());
            return;
        }
        int[] attribute_list = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
        };
        sharedContext = EGL14.eglCreateContext(sharedDisplay, configs[0], null == sharedContext ? EGL14.EGL_NO_CONTEXT : sharedContext, attribute_list, 0);
        if (sharedContext == null) {
            Log.e(TAG, "eglCreateContext() returned error " + EGL14.eglGetError());
            return;
        }
    }
}
