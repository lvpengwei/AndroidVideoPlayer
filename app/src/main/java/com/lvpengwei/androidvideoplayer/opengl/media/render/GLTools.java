package com.lvpengwei.androidvideoplayer.opengl.media.render;

import android.opengl.GLES20;
import android.util.Log;

public class GLTools {
    private static String TAG = "GLTools";

    public static boolean checkEglError(String msg) {
        int error;
        String errorContent = "";
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            errorContent += (msg + ": EGL error: 0x" + Integer.toHexString(error));
            Log.e(TAG, errorContent);
            return true;
        }
        return false;
    }

    public static int loadProgram(String pVertexSource, String pFragmentSource) {
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendEquationSeparate(GLES20.GL_FUNC_ADD, GLES20.GL_FUNC_ADD);
        GLES20.glBlendFuncSeparate(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA, GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, pVertexSource);
        if (vertexShader == 0) {
            return 0;
        }
        int pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, pFragmentSource);
        if (pixelShader == 0) {
            return 0;
        }

        int program = GLES20.glCreateProgram();
        checkEglError("glCreateProgram");
        if (program == 0) {
            Log.e(TAG, "Could not create program");
            return 0;
        }
        GLES20.glAttachShader(program, vertexShader);
        checkEglError("glAttachShader");
        GLES20.glAttachShader(program, pixelShader);
        checkEglError("glAttachShader");
        GLES20.glLinkProgram(program);
        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] != GLES20.GL_TRUE) {
            Log.e(TAG, "Could not link program: ");
            Log.e(TAG, GLES20.glGetProgramInfoLog(program));
            GLES20.glDeleteProgram(program);
            program = 0;
        }

        return program;
    }

    public static int loadShader(int shaderType, String source) {
        int shader = GLES20.glCreateShader(shaderType);
        checkEglError("glCreateShader type=" + shaderType);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            GLES20.glDeleteShader(shader);
            shader = 0;
        }
        return shader;
    }

}
