package com.lvpengwei.androidvideoplayer.opengl.media.texture.copier;

import android.opengl.GLES20;

import com.lvpengwei.androidvideoplayer.opengl.media.texture.TextureFrame;

import java.nio.FloatBuffer;

public abstract class TextureFrameCopier {
    public static String NO_FILTER_VERTEX_SHADER = "" +
            "attribute vec4 vPosition;\n" +
            "attribute vec4 vTexCords;\n" +
            "varying vec2 yuvTexCoords;\n" +
            "uniform highp mat4 texMatrix;\n" +
            "uniform highp mat4 trans; \n" +
            "void main() {\n" +
            "  yuvTexCoords = (texMatrix*vTexCords).xy;\n" +
            "  gl_Position = trans * vPosition;\n" +
            "}\n";
    public static String NO_FILTER_FRAGMENT_SHADER = "" +
            "varying vec2 yuvTexCoords;\n" +
            "uniform sampler2D yuvTexSampler;\n" +
            "void main() {\n" +
            "  gl_FragColor = texture2D(yuvTexSampler, yuvTexCoords);\n" +
            "}\n";
    String mVertexShader;
    String mFragmentShader;
    int mGLProgId = 0;
    int mGLVertexCoords;
    int mGLTextureCoords;
    int mUniformTexMatrix;
    int mUniformTransforms;
    boolean mIsInitialized = false;

    public void destroy() {
        mIsInitialized = false;
        if (mGLProgId != 0) {
            GLES20.glDeleteProgram(mGLProgId);
            mGLProgId = 0;
        }
    }

    public abstract boolean init();
    public abstract void renderWithCoords(TextureFrame textureFrame, int texId, FloatBuffer vertexCoords, FloatBuffer textureCoords);

}
