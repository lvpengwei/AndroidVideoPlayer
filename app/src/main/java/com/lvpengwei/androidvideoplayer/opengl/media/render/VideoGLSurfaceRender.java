package com.lvpengwei.androidvideoplayer.opengl.media.render;

import android.opengl.GLES20;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class VideoGLSurfaceRender {

    public static String TAG = "VideoGLSurfaceRender";
    private static String OUTPUT_VIEW_VERTEX_SHADER = "" +
            "attribute vec4 position;    \n" +
            "attribute vec2 texcoord;   \n" +
            "varying vec2 v_texcoord;     \n" +
            "void main(void)               \n" +
            "{                            \n" +
            "   gl_Position = position;  \n" +
            "   v_texcoord = texcoord;  \n" +
            "}                            \n";
    private static String OUTPUT_VIEW_FRAG_SHADER = "" +
            "varying highp vec2 v_texcoord;\n" +
            "uniform sampler2D yuvTexSampler;\n" +
            "void main() {\n" +
            "  gl_FragColor = texture2D(yuvTexSampler, v_texcoord);\n" +
            "}\n";
    private int _backingLeft;
    private int _backingTop;
    private int _backingWidth;
    private int _backingHeight;

    private boolean mIsInitialized = false;
    private int mGLProgId;
    private int mGLVertexCoords;
    private int mGLTextureCoords;
    private int mGLUniformTexture;

    public VideoGLSurfaceRender(int width, int height) {
        _backingLeft = 0;
        _backingTop = 0;
        _backingWidth = 0;
        _backingHeight = 0;
        mGLProgId = GLTools.loadProgram(OUTPUT_VIEW_VERTEX_SHADER, OUTPUT_VIEW_FRAG_SHADER);
        if (mGLProgId == 0) {
            Log.d(TAG, "Could not create program.");
            return;
        }
        mGLVertexCoords = GLES20.glGetAttribLocation(mGLProgId, "position");
        checkEglError("glGetAttribLocation vPosition");
        mGLTextureCoords = GLES20.glGetAttribLocation(mGLProgId, "texcoord");
        checkEglError("glGetAttribLocation vTexCords");
        mGLUniformTexture = GLES20.glGetAttribLocation(mGLProgId, "yuvTexSampler");
        checkEglError("glGetAttribLocation yuvTexSampler");
        mIsInitialized = true;
    }

    public static boolean checkEglError(String msg) {
        return GLTools.checkEglError(msg);
    }

    public void dealloc() {
        mIsInitialized = false;
        GLES20.glDeleteProgram(mGLProgId);
    }

    public void renderToView(int texID, int width, int height) {
        if (!mIsInitialized) return;
        GLES20.glViewport(0, 0, width, height);
        GLES20.glUseProgram(mGLProgId);
        float[] vertices = {-1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f};
        FloatBuffer vertexBuffer = ByteBuffer.allocateDirect(vertices.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(vertices);
        vertexBuffer.position(0);
        GLES20.glVertexAttribPointer(mGLVertexCoords, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);
        GLES20.glEnableVertexAttribArray(mGLVertexCoords);

        float[] texCoords = {0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f};
        FloatBuffer texCoordsBuffer = ByteBuffer.allocateDirect(texCoords.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(texCoords);
        texCoordsBuffer.position(0);
        GLES20.glVertexAttribPointer(mGLTextureCoords, 2, GLES20.GL_FLOAT, false, 0, texCoordsBuffer);
        GLES20.glEnableVertexAttribArray(mGLTextureCoords);

        /* Binding the input texture */
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texID);
        GLES20.glUniform1i(mGLUniformTexture, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glDisableVertexAttribArray(mGLVertexCoords);
        GLES20.glDisableVertexAttribArray(mGLTextureCoords);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    public void renderToTexture(int inputTexId, int outputTexId) {
        GLES20.glViewport(_backingLeft, _backingTop, _backingWidth, _backingHeight);

        if (!mIsInitialized) {
            Log.e(TAG, "ViewRenderEffect::renderEffect effect not initialized!");
            return;
        }

        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, outputTexId, 0);
        GLTools.checkEglError("PassThroughRender::renderEffect glFramebufferTexture2D");
        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            Log.i(TAG, "failed to make complete framebuffer object " + status);
        }

        GLES20.glUseProgram(mGLProgId);
        float[] _vertices = {-1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f, -1.0f};
        FloatBuffer verticesBuffer = ByteBuffer.allocateDirect(_vertices.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(_vertices);
        GLES20.glVertexAttribPointer(mGLVertexCoords, 2, GLES20.GL_FLOAT, false, 0, verticesBuffer);
        GLES20.glEnableVertexAttribArray(mGLVertexCoords);
        float[] texCoords = {0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f};
        FloatBuffer texCorrdsBuffer = ByteBuffer.allocateDirect(texCoords.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(texCoords);
        GLES20.glVertexAttribPointer(mGLTextureCoords, 2, GLES20.GL_FLOAT, false, 0, texCorrdsBuffer);
        GLES20.glEnableVertexAttribArray(mGLTextureCoords);

        /* Binding the input texture */
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, inputTexId);
        GLES20.glUniform1i(mGLUniformTexture, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glDisableVertexAttribArray(mGLVertexCoords);
        GLES20.glDisableVertexAttribArray(mGLTextureCoords);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, 0, 0);
    }

}