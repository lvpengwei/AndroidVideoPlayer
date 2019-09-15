package com.lvpengwei.androidvideoplayer.opengl.media.texture.copier;

import android.opengl.GLES20;
import android.util.Log;

import com.lvpengwei.androidvideoplayer.opengl.media.render.GLTools;
import com.lvpengwei.androidvideoplayer.opengl.media.texture.TextureFrame;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class GPUTextureFrameCopier extends TextureFrameCopier {
    private static String TAG = "GPUTextureFrameCopier";
    private static String GPU_FRAME_VERTEX_SHADER = "" +
            "attribute vec4 vPosition;\n" +
            "attribute vec4 vTexCords;\n" +
            "varying vec2 yuvTexCoords;\n" +
            "uniform highp mat4 trans; \n" +
            "void main() {\n" +
            "  yuvTexCoords = vTexCords.xy;\n" +
            "  gl_Position = trans * vPosition;\n" +
            "}\n";
    private static String GPU_FRAME_FRAGMENT_SHADER = "" +
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "uniform samplerExternalOES yuvTexSampler;\n" +
            "varying vec2 yuvTexCoords;\n" +
            "void main() {\n" +
            "  gl_FragColor = texture2D(yuvTexSampler, yuvTexCoords);\n" +
            "}\n";

    private int mGLUniformTexture;

    public GPUTextureFrameCopier() {
        mVertexShader = NO_FILTER_VERTEX_SHADER;
        mFragmentShader = GPU_FRAME_FRAGMENT_SHADER;
    }

    @Override
    public boolean init() {
        mGLProgId = GLTools.loadProgram(mVertexShader, mFragmentShader);
        if (mGLProgId <= 0) {
            Log.e(TAG, "Could not create program.");
            return false;
        }

        mGLVertexCoords = GLES20.glGetAttribLocation(mGLProgId, "vPosition");
        GLTools.checkEglError("glGetAttribLocation vPosition");
        mGLTextureCoords = GLES20.glGetAttribLocation(mGLProgId, "vTexCords");
        GLTools.checkEglError("glGetAttribLocation vTexCords");
        mGLUniformTexture = GLES20.glGetUniformLocation(mGLProgId, "yuvTexSampler");
        GLTools.checkEglError("glGetAttribLocation yuvTexSampler");

        mUniformTexMatrix = GLES20.glGetUniformLocation(mGLProgId, "texMatrix");
        GLTools.checkEglError("glGetUniformLocation mUniformTexMatrix");

        mUniformTransforms = GLES20.glGetUniformLocation(mGLProgId, "trans");
        GLTools.checkEglError("glGetUniformLocation mUniformTransforms");

        mIsInitialized = true;
        return true;
    }

    @Override
    public void renderWithCoords(TextureFrame textureFrame, int texId, FloatBuffer vertexCoords, FloatBuffer textureCoords) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId);
        GLTools.checkEglError("glBindTexture");

        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, texId, 0);
        GLTools.checkEglError("glFramebufferTexture2D");

        GLES20.glUseProgram(mGLProgId);
        if (!mIsInitialized) {
            return;
        }

        GLES20.glVertexAttribPointer(mGLVertexCoords, 2, GLES20.GL_FLOAT, false, 0, vertexCoords);
        GLES20.glEnableVertexAttribArray(mGLVertexCoords);
        GLES20.glVertexAttribPointer(mGLTextureCoords, 2, GLES20.GL_FLOAT, false, 0, textureCoords);
        GLES20.glEnableVertexAttribArray(mGLTextureCoords);
        /* Binding the input texture */
        textureFrame.bindTexture(mGLUniformTexture);

        float[] matrix = new float[4 * 4];
        matrix[0] = matrix[5] = matrix[10] = matrix[15] = 1.0f;
        FloatBuffer texTransMatrix = ByteBuffer.allocateDirect(matrix.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(matrix);
        texTransMatrix.position(0);
        GLES20.glUniformMatrix4fv(mUniformTexMatrix, 1, false, texTransMatrix);

        matrix = new float[4 * 4];
        matrix[0] = matrix[5] = matrix[10] = matrix[15] = 1.0f;
        FloatBuffer rotateMatrix = ByteBuffer.allocateDirect(matrix.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(matrix);
        rotateMatrix.position(0);
        GLES20.glUniformMatrix4fv(mUniformTransforms, 1, false, rotateMatrix);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glDisableVertexAttribArray(mGLVertexCoords);
        GLES20.glDisableVertexAttribArray(mGLTextureCoords);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, 0, 0);
    }

}
