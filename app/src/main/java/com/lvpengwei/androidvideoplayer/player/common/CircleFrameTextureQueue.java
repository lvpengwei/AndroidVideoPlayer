package com.lvpengwei.androidvideoplayer.player.common;

import android.opengl.GLES20;

import com.lvpengwei.androidvideoplayer.opengl.media.render.GLTools;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.lvpengwei.androidvideoplayer.player.common.FrameTexture.INVALID_FRAME_POSITION;

public class CircleFrameTextureQueue {
    private String queueNameParam;
    private FrameTextureNode head;
    private FrameTextureNode tail;
    private FrameTextureNode pullCursor;
    private FrameTextureNode pushCursor;
    private FrameTexture firstFrame;
    private int queueSize;
    private boolean isAvailable;
    private boolean mAbortRequest;
    private boolean isFirstFrame;
    private Lock mLock;
    private Condition mCondition;

    public CircleFrameTextureQueue(String queueNameParam) {
        this.queueNameParam = queueNameParam;
        mLock = new ReentrantLock();
        mCondition = mLock.newCondition();
        mAbortRequest = false;
        isAvailable = false;
        firstFrame = null;
    }

    public void init(int width, int height, int queueSize) {
        this.queueSize = queueSize;
        mLock.lock();
        tail = new FrameTextureNode();
        tail.texture = buildFrameTexture(width, height, INVALID_FRAME_POSITION);
        int i = queueSize - 1;
        FrameTextureNode nextCursor = tail;
        FrameTextureNode curCursor = null;
        while (i > 0) {
            curCursor = new FrameTextureNode();
            curCursor.texture = buildFrameTexture(width, height, INVALID_FRAME_POSITION);
            curCursor.next = nextCursor;
            nextCursor = curCursor;
            i--;
        }
        head = curCursor;
        tail.next = head;
        pullCursor = head;
        pushCursor = head;
        mLock.unlock();

        // allocate first frame
        firstFrame = new FrameTexture();
        buildGPUFrame(firstFrame, width, height);
        firstFrame.position = 0.0f;
        firstFrame.width = width;
        firstFrame.height = height;

        isFirstFrame = false;
    }

    public boolean isFirstFrame() {
        return isFirstFrame;
    }

    public void setFirstFrame(boolean firstFrame) {
        isFirstFrame = firstFrame;
    }

    public FrameTexture getFirstFrameFrameTexture() {
        return firstFrame;
    }

    public FrameTexture lockPushCursorFrameTexture() {
        if (mAbortRequest) {
            return null;
        }
        mLock.lock();
        return pushCursor.texture;
    }

    public void unLockPushCursorFrameTexture() {
        if (mAbortRequest) {
            return;
        }
        if (pushCursor == pullCursor) {
            if (!isAvailable) {
                isAvailable = true;
                isFirstFrame = false;
                mCondition.signal();
            } else {
                pullCursor = pullCursor.next;
            }
        }
        pushCursor = pushCursor.next;
        mLock.unlock();
    }

    /* return < 0 if aborted, 0 if no packet and > 0 if packet.  */
    public FrameTexture front() {
        if (mAbortRequest) {
            return null;
        }
        mLock.lock();
        if (!isAvailable) {
            //如果isAvailable 说明还没有推送进来 直到等到signal信号才进入下面
            mCondition.awaitUninterruptibly();
        }
        FrameTextureNode frontCursor = pullCursor;
        if (frontCursor.next != pushCursor) {
            //如果没有追上pushCursor 则将pullCursor向后推移 否则直接进行复制视频帧操作
            frontCursor = frontCursor.next;
        }
        FrameTexture frameTexture = frontCursor.texture;
        mLock.unlock();
        return frameTexture;
    }

    public int pop() {
        if (mAbortRequest) {
            return -1;
        }
        int ret = 1;
        mLock.lock();
        if (!isAvailable) {
            //如果isAvailable 说明还没有推送进来 直到等到signal信号才进入下面
            mCondition.awaitUninterruptibly();
        }
        if (pullCursor.next != pushCursor) {
            //如果没有追上pushCursor 则将pullCursor向后推移 否则直接进行复制视频帧操作
            pullCursor = pullCursor.next;
        }
        mLock.unlock();
        return ret;
    }

    public void clear() {
        mLock.lock();
        isAvailable = false;
        pullCursor = head;
        pushCursor = head;
        mLock.unlock();
    }

    public int getValidSize() {
        if (mAbortRequest || !isAvailable) {
            return 0;
        }
        int size = 0;

        mLock.lock();
        FrameTextureNode beginCursor = pullCursor;
        FrameTextureNode endCursor = pushCursor;

        if (beginCursor.next == endCursor) {
            size = 1;
        } else {
            while (beginCursor.next != endCursor) {
                size++;
                beginCursor = beginCursor.next;
            }
        }
        mLock.unlock();
        return size;
    }

    public void abort() {
        mLock.lock();
        mAbortRequest = true;
        mCondition.signal();
        mLock.unlock();
    }

    public void flush() {
        mLock.lock();
        FrameTextureNode node = head;
        FrameTextureNode tempNode;
        while (node != tail) {
            tempNode = node.next;
            node.next = null;
            node = tempNode;
        }
        tail.next = null;

        head = null;
        tail = null;
        pullCursor = null;
        pushCursor = null;

        // delete the cached first frame
        firstFrame = null;

        isFirstFrame = false;

        mLock.unlock();
    }

    private FrameTexture buildFrameTexture(int width, int height, float position) {
        FrameTexture frameTexture = new FrameTexture();
        //生成纹理与FBO并且关联两者
        buildGPUFrame(frameTexture, width, height);
        frameTexture.position = position;
        frameTexture.width = width;
        frameTexture.height = height;
        return frameTexture;
    }

    private void buildGPUFrame(FrameTexture frameTexture, int width, int height) {
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);

        int texId = textures[0];
        GLTools.checkEglError("glGenTextures texId");
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId);
        GLTools.checkEglError("glBindTexture texId");
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        int internalFormat = GLES20.GL_RGBA;
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, internalFormat, width, height, 0, internalFormat, GLES20.GL_UNSIGNED_BYTE, null);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        frameTexture.texId = texId;
    }
}
