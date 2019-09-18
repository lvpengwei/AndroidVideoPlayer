package com.lvpengwei.androidvideoplayer.opengl.media.render;

import android.opengl.GLES20;
import android.util.Log;

import com.lvpengwei.androidvideoplayer.player.common.FrameTexture;

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
            "varying vec2 v_texcoord;\n" +
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

    public boolean init(int width, int height) {
        _backingLeft = 0;
        _backingTop = 0;
        _backingWidth = width;
        _backingHeight = height;
        mGLProgId = GLTools.loadProgram(OUTPUT_VIEW_VERTEX_SHADER, OUTPUT_VIEW_FRAG_SHADER);
        if (mGLProgId == 0) {
            Log.d(TAG, "Could not create program.");
            return false;
        }
        mGLVertexCoords = GLES20.glGetAttribLocation(mGLProgId, "position");
        GLTools.checkEglError("glGetAttribLocation vPosition");
        mGLTextureCoords = GLES20.glGetAttribLocation(mGLProgId, "texcoord");
        GLTools.checkEglError("glGetAttribLocation vTexCords");
        mGLUniformTexture = GLES20.glGetUniformLocation(mGLProgId, "yuvTexSampler");
        GLTools.checkEglError("glGetAttribLocation yuvTexSampler");
        mIsInitialized = true;
        return true;
    }

    public void dealloc() {
        mIsInitialized = false;
        GLES20.glDeleteProgram(mGLProgId);
    }

    public void resetRenderSize(int left, int top, int width, int height) {
        this._backingLeft = left;
        this._backingTop = top;
        this._backingWidth = width;
        this._backingHeight = height;
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

    enum ScaleType {
        FILL,
        ASPECT_FIT,
    }

    public void renderToView(int texID, int texWidth, int texHeight, ScaleType scaleType) {
        GLES20.glViewport(0, 0, _backingWidth, _backingHeight);

        if (!mIsInitialized) {
            Log.e(TAG, "ViewRenderEffect::renderEffect effect not initialized!");
            return;
        }

        float textureAspectRatio = (float) texHeight / (float) texWidth;
        float viewAspectRatio = (float) _backingWidth / (float) _backingHeight;
        float xOffset = 0.0f;
        float yOffset = 0.0f;
        if (scaleType == ScaleType.FILL) {
            if (textureAspectRatio > viewAspectRatio) {
                // Update Y Offset
                int expectedHeight = (int) ((float) texHeight * _backingWidth / (float) texWidth + 0.5f);
                yOffset = (float) (expectedHeight - _backingHeight) / (2 * expectedHeight);
            } else if (textureAspectRatio < viewAspectRatio) {
                // Update X Offset
                int expectedWidth = (int) ((float) (texHeight * _backingWidth) / (float) _backingHeight + 0.5);
                xOffset = (float) (texWidth - expectedWidth) / (2 * texWidth);
            }
        } else {
            if (textureAspectRatio > viewAspectRatio) {
                //Update X Offset
                int expectedWidth = (int) ((float) (texHeight * _backingWidth) / (float) _backingHeight + 0.5);
                xOffset = (float) (texWidth - expectedWidth) / (2 * texWidth);
            } else if (textureAspectRatio < viewAspectRatio) {
                //Update Y Offset
                int expectedHeight = (int) ((float) texHeight * _backingWidth / (float) texWidth + 0.5f);
                yOffset = (float) (expectedHeight - _backingHeight) / (2 * expectedHeight);
            }
        }

        GLES20.glUseProgram(mGLProgId);
        float[] vertices = {-1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f};
        FloatBuffer vertexBuffer = ByteBuffer.allocateDirect(vertices.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(vertices);
        vertexBuffer.position(0);
        GLES20.glVertexAttribPointer(mGLVertexCoords, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);
        GLES20.glEnableVertexAttribArray(mGLVertexCoords);
        float[] texCoords = {xOffset, (float) (1.0 - yOffset), (float) (1.0 - xOffset), (float) (1.0 - yOffset), xOffset, yOffset,
                (float) (1.0 - xOffset), yOffset};
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

    public void renderToViewWithAspectFit(int texID, int texWidth, int texHeight) {
        renderToView(texID, texWidth, texHeight, ScaleType.ASPECT_FIT);
    }

    public void renderToTexture(int inputTexId, FrameTexture texture) {
//        GLES20.glViewport(_backingLeft, _backingTop, _backingWidth, _backingHeight);
        GLES20.glViewport(_backingLeft, _backingTop, 1920, 1080);

        if (!mIsInitialized) {
            Log.e(TAG, "ViewRenderEffect::renderEffect effect not initialized!");
            return;
        }

        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, texture.texId, 0);
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
