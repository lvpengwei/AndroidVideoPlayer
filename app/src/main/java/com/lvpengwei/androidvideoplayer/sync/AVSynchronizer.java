package com.lvpengwei.androidvideoplayer.sync;

import android.opengl.EGLContext;
import android.util.Log;

import com.lvpengwei.androidvideoplayer.decoder.DecoderRequestHeader;
import com.lvpengwei.androidvideoplayer.decoder.VideoDecoder;
import com.lvpengwei.androidvideoplayer.opengl.media.AudioFrame;
import com.lvpengwei.androidvideoplayer.opengl.media.MovieFrame;
import com.lvpengwei.androidvideoplayer.opengl.media.render.EglCore;
import com.lvpengwei.androidvideoplayer.opengl.media.render.VideoGLSurfaceRender;
import com.lvpengwei.androidvideoplayer.player.common.CircleFrameTextureQueue;
import com.lvpengwei.androidvideoplayer.player.common.FrameTexture;

import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class AVSynchronizer {
    private static String TAG = "AVSynchronizer";
    private static float LOCAL_MIN_BUFFERED_DURATION = 0.5f;
    private static float LOCAL_MAX_BUFFERED_DURATION = 0.8f;
    private static float LOCAL_AV_SYNC_MAX_TIME_DIFF = 0.08f;
    private static float FIRST_BUFFER_DURATION = 0.5f;
    private static float DEFAULT_AUDIO_BUFFER_DURATION_IN_SECS = 0.03f;
    private static int SEEK_REQUEST_LIST_MAX_SIZE = 2;
    /**
     * 当客户端调用destroy方法之后 只为true
     **/
    public boolean isDestroyed = false;
    public boolean isOnDecoding;
    //当前缓冲区是否有数据
    public boolean buffered;
    public boolean isCompleted;
    UploaderCallbackImpl mUploaderCallback = new UploaderCallbackImpl();
    EGLContext loadTextureContext;
    VideoGLSurfaceRender passThorughRender;
    //init 方法中调用的私有方法
//    VideoDecoder videoDecoder;
    int decodeVideoErrorState;
    /**
     * 是否初始化解码线程
     **/
    boolean isInitializeDecodeThread;
    boolean isLoading;
    float syncMaxTimeDiff;
    float minBufferedDuration;//缓冲区的最短时长
    float maxBufferedDuration;//缓冲区的最长时长
    /**
     * 解码出来的videoFrame与audioFrame的容器，以及同步操作信号量
     **/
    Lock audioFrameQueueMutex;
    List<AudioFrame> audioFrameQueue;
    CircleFrameTextureQueue circleFrameTextureQueue;
    /**
     * 这里是为了将audioFrame中的数据，缓冲到播放音频的buffer中，有可能需要积攒几个frame，所以记录位置以及保存当前frame
     **/
    AudioFrame currentAudioFrame;
    int currentAudioFramePos;
    /**
     * 当前movie的position，用于同步音画
     **/
    double moviePosition;
    /**
     * 根据缓冲区来控制是否需要编解码的变量
     **/
    float bufferedDuration;//当前缓冲区时长
    //由于在解码器中解码的时候有可能会由于网络原因阻塞，甚至超时，所以这个时候需要将解码过程放到新的线程中
    Thread videoDecoderThread;
    /**
     * 由于在解码线程中要用到以下几个值，所以访问控制符是public的
     **/
    boolean isDecodingFrames;
    boolean isDecodingAudio;
    boolean pauseDecodeThreadFlag;
    private Lock videoDecoderLock;
    private Condition videoDecoderCondition;

    private Lock audioDecoderLock;
    private Condition audioDecoderCondition;

    public AVSynchronizer() {
    }

    public void init(DecoderRequestHeader requestHeader, float minBufferedDuration, float maxBufferedDuration) {
        circleFrameTextureQueue = new CircleFrameTextureQueue("DecodeVideoQueue");
        mUploaderCallback.setParent(this);
        isOnDecoding = false;
        isDestroyed = false;
        createDecoderInstance();
        videoDecoder.openFile(requestHeader);
        videoDecoder.startUploader(mUploaderCallback);
    }

    public void OnInitFromUploaderGLContext(EglCore eglCore, int videoFrameWidth, int videoFrameHeight) {
        if (null == passThorughRender) {
            passThorughRender = new VideoGLSurfaceRender();
            passThorughRender.init(videoFrameWidth, videoFrameHeight);
        }
        initCircleQueue(videoFrameWidth, videoFrameHeight);
        eglCore.doneCurrent();
    }

    public void onDestroyFromUploaderGLContext() {
        destroyPassThorughRender();
        //清空并且销毁视频帧队列
        if (null != circleFrameTextureQueue) {
//		LOGI("clear and destroy video frame queue ...");
            clearVideoFrameQueue();
//		LOGI("dealloc circleFrameTextureQueue ...");
            circleFrameTextureQueue.abort();
        }
    }

    public void onSeek(float seek_seconds) {
        clearCircleFrameTextureQueue();
    }

    public void frameAvailable() {

    }

    public void processVideoFrame(int inputTexId, int width, int height, float position) {
        renderToVideoQueue(inputTexId, width, height, position);
    }

    public int processAudioData(short sample, int size, float position, byte buffer) {
        return 0;
    }

    public boolean validAudio() {
        return false;
    }

    public boolean isValid() {
        return false;
    }

    public int getVideoFrameHeight() {
        return 0;
    }

    public int getVideoFrameWidth() {
        return 0;
    }

    public float getVideoFPS() {
        return 30;
    }

    public int getAudioChannels() {
        return 2;
    }

    public int getAudioSampleRate() {
        return 0;
    }

    public void start() {
        isOnDecoding = true;
        circleFrameTextureQueue.setFirstFrame(true);
        initDecoderThread();
    }

    private void signalDecodeThread() {
        if (null == videoDecoder || isDestroyed) {
            Log.i(TAG, "NULL == decoder || isDestroyed == true");
            return;
        }

        //如果没有剩余的帧了或者当前缓存的长度大于我们的最小缓冲区长度的时候，就再一次开始解码
        boolean isBufferedDurationDecreasedToMin = bufferedDuration <= minBufferedDuration ||
                (circleFrameTextureQueue.getValidSize() <= minBufferedDuration * getVideoFPS());

        if (!isDestroyed && (videoDecoder.hasSeekReq()) || ((!isDecodingFrames) && isBufferedDurationDecreasedToMin)) {
            videoDecoderLock.lock();
            videoDecoderCondition.signal();
            videoDecoderLock.unlock();
        }
    }

    private void signalDecodeAudioThread() {
        if (null == videoDecoder || isDestroyed) {
            Log.i(TAG, "NULL == decoder || isDestroyed == true");
            return;
        }

        //如果没有剩余的帧了或者当前缓存的长度大于我们的最小缓冲区长度的时候，就再一次开始解码
        boolean isBufferedDurationDecreasedToMin = bufferedDuration <= minBufferedDuration;

        if (!isDestroyed && (videoDecoder.hasSeekReq()) || ((!isDecodingAudio) && isBufferedDurationDecreasedToMin)) {
            audioDecoderLock.lock();
            audioDecoderCondition.signal();
            audioDecoderLock.unlock();
        }
    }

    public void destroy() {
        Log.i(TAG, "enter AVSynchronizer::destroy ...");
        isDestroyed = true;
        //先停止掉解码线程
        destroyDecoderThread();
        isLoading = true;

        Log.i(TAG, "clear and destroy audio frame queue ...");
        //清空并且销毁音频帧队列
        if (null != audioFrameQueue) {
            clearAudioFrameQueue();
            audioFrameQueueMutex.lock();
            audioFrameQueue = null;
            audioFrameQueueMutex.unlock();
        }

        Log.i(TAG, "call decoder close video source URI ...");
        if (null != videoDecoder) {
            videoDecoder.closeFile();
        }
    }

    public void interruptRequest() {
    }

    public void initCircleQueue(int videoWidth, int videoHeight) {
        circleFrameTextureQueue.init(videoWidth, videoHeight, 60);
    }
//    inline bool canDecode(){
//        return !pauseDecodeThreadFlag && !isDestroyed && videoDecoder && (videoDecoder->validVideo() || videoDecoder->validAudio()) && !videoDecoder->isVideoEOF();
//    }

    public byte[] fillAudioData(int bufferSize) {
        audioFrameQueueMutex.lock();
        AudioFrame frame = audioFrameQueue.remove(0);
        audioFrameQueueMutex.unlock();
        moviePosition = frame.position;
        signalDecodeAudioThread();
        return frame.getBuffer();
    }

    public FrameTexture getCorrectRenderTexture(boolean forceGetFrame) {
        FrameTexture texture = null;
        if (null == circleFrameTextureQueue) {
            Log.e(TAG, "getCorrectRenderTexture::circleFrameTextureQueue is NULL");
            return null;
        }
//        int leftVideoFrames = videoDecoder.validVideo() ? circleFrameTextureQueue.getValidSize() : 0;
        int leftVideoFrames = circleFrameTextureQueue.getValidSize();
        if (leftVideoFrames == 1) {
            return texture;
        }
        while (true) {
            texture = circleFrameTextureQueue.front();
            if (texture != null) {
                if (forceGetFrame) {
                    return texture;
                }
                float delta = (float) ((moviePosition - DEFAULT_AUDIO_BUFFER_DURATION_IN_SECS) - texture.position);
                if (delta < (0 - syncMaxTimeDiff)) {
                    //视频比音频快了好多,我们还是渲染上一帧
//				LOGI("视频比音频快了好多,我们还是渲染上一帧 moviePosition is %.4f texture->position is %.4f", moviePosition, texture->position);
                    texture = null;
                    break;
                }
                circleFrameTextureQueue.pop();
                if (delta > syncMaxTimeDiff) {
                    //视频比音频慢了好多,我们需要继续从queue拿到合适的帧
//				LOGI("视频比音频慢了好多,我们需要继续从queue拿到合适的帧 moviePosition is %.4f texture->position is %.4f", moviePosition, texture->position);
                    continue;
                } else {
                    break;
                }
            } else {
                texture = null;
                break;
            }
        }
        return texture;
    }

    FrameTexture getFirstRenderTexture() {
        if (circleFrameTextureQueue == null) return null;
        return circleFrameTextureQueue.getFirstFrameFrameTexture();
    }

    FrameTexture getSeekRenderTexture() {
        return circleFrameTextureQueue.front();
    }

    public float getDuration() {
        return 0;
    }

    public float getBufferedProgress() {
        return 0;
    }

    public float getPlayProgress() {
        return 0;
    }

    private void decodeFrames() {
        while (true) {
            if (canDecodeVideo()) {
                videoDecoder.decode();
            } else {
                break;
            }
        }
    }

    public void renderToVideoQueue(int inputTexId, int width, int height, float position) {
        if (passThorughRender == null) {
            Log.e(TAG, "renderToVideoQueue::passThorughRender is NULL");
            return;
        }

        if (circleFrameTextureQueue == null) {
            Log.e(TAG, "renderToVideoQueue::circleFrameTextureQueue is NULL");
            return;
        }

        //注意:先做上边一步的原因是 担心videoEffectProcessor处理速度比较慢 这样子就把circleQueue锁住太长时间了
        boolean isFirstFrame = circleFrameTextureQueue.isFirstFrame();
        FrameTexture frameTexture = circleFrameTextureQueue.lockPushCursorFrameTexture();
        if (null != frameTexture) {
            frameTexture.position = position;
//		LOGI("Render To TextureQueue texture Position is %.3f ", position);
            //cpy input texId to target texId
            passThorughRender.renderToTexture(inputTexId, frameTexture.texId);
            circleFrameTextureQueue.unLockPushCursorFrameTexture();

            frameAvailable();

            // backup the first frame
            if (isFirstFrame) {
                FrameTexture firstFrameTexture = circleFrameTextureQueue.getFirstFrameFrameTexture();
                if (firstFrameTexture != null) {
                    //cpy input texId to target texId
                    passThorughRender.renderToTexture(inputTexId, firstFrameTexture.texId);
                }
            }
        }
    }

    public void clearCircleFrameTextureQueue() {
        circleFrameTextureQueue.clear();
    }

    public boolean isPlayCompleted() {
        return isCompleted;
    }

    public void destroyPassThorughRender() {
        if (passThorughRender == null) return;
        passThorughRender.dealloc();
    }

    public void seekToPosition(float position) {
        if (videoDecoder == null) return;
        buffered = true;
        isCompleted = false;
        moviePosition = position;
        videoDecoder.setPosition(position);
    }

    private void decode() {
        videoDecoderLock.lock();
        videoDecoderCondition.awaitUninterruptibly();
        videoDecoderLock.unlock();
        isDecodingFrames = true;
        decodeFrames();
        isDecodingFrames = false;
    }

    private void decodeAudio() {
        audioDecoderLock.lock();
        audioDecoderCondition.awaitUninterruptibly();
        audioDecoderLock.unlock();
        isDecodingAudio = true;
        decodeAudioFrames();
        isDecodingAudio = false;
    }

    private void decodeAudioFrames() {
        while (true) {
            if (canDecodeAudio()) {
                byte[] buffer = videoDecoder.decodeAudio();
                AudioFrame frame = new AudioFrame();
                frame.setBuffer(buffer);
                audioFrameQueueMutex.lock();
                audioFrameQueue.add(frame);
                int size = audioFrameQueue.size();
                audioFrameQueueMutex.unlock();
                if (size > 10) {
                    break;
                }
            } else {
                break;
            }
        }
    }

    //    bool isHWCodecAvaliable();
    private VideoDecoder videoDecoder;
    private void createDecoderInstance() {
        videoDecoder = new VideoDecoder();
    }

    private void initMeta() {

    }
//    static void* startDecoderThread(void* ptr);

    private void viewStreamMetaCallback(int videoWidth, int videoHeight, float duration) {

    }

    private void closeDecoder() {

    }

    //start 中用到的变量以及方法
    //将frame加到对应的容器中 并且根据返回值决定是否需要继续解码
    private boolean addFrames(List<MovieFrame> frames) {
        return false;
    }

    private boolean addFrames(float thresholdDuration, List<MovieFrame> frames) {
        return false;
    }

    private Thread videoThread;
    private Thread audioThread;

    /**
     * 开启解码线程
     **/
    private void initDecoderThread() {
        if (isDestroyed) return;
        isDecodingFrames = false;
        videoDecoderLock = new ReentrantLock();
        videoDecoderCondition = videoDecoderLock.newCondition();
        audioFrameQueueMutex = new ReentrantLock();
        audioDecoderLock = new ReentrantLock();
        audioDecoderCondition = audioDecoderLock.newCondition();
        isInitializeDecodeThread = true;
        videoThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (isOnDecoding) {
                    decode();
                }
            }
        });
        videoThread.start();
        audioThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (isOnDecoding) {
                    decodeAudio();
                }
            }
        });
        audioThread.start();
    }

    /**
     * 销毁解码线程
     **/
    void destroyDecoderThread() {
        isOnDecoding = false;
        if (!isInitializeDecodeThread) {
            return;
        }
        videoDecoderLock.lock();
        videoDecoderCondition.signal();
        videoDecoderLock.unlock();
    }

    // 调用解码器解码指定position的frame
    void decodeFrameByPosition(float pos) {
    }

    private void clearVideoFrameQueue() {
        if (circleFrameTextureQueue == null) return;
        circleFrameTextureQueue.clear();
    }

    void clearAudioFrameQueue() {
        audioFrameQueueMutex.lock();
        audioFrameQueue.clear();

        bufferedDuration = 0;
        audioFrameQueueMutex.unlock();
    }

    private boolean canDecodeVideo() {
        return !pauseDecodeThreadFlag && !isDestroyed && videoDecoder != null && !videoDecoder.isVideoEOF();
    }

    private boolean canDecodeAudio() {
        return !pauseDecodeThreadFlag && !isDestroyed && videoDecoder != null && !videoDecoder.isAudioEOF();
    }
}
