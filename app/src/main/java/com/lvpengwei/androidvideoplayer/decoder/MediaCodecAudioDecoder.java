package com.lvpengwei.androidvideoplayer.decoder;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;

import java.nio.ByteBuffer;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class MediaCodecAudioDecoder {
    private static final String TAG = "MediaCodecVideoDecoder";
    private static final boolean m_verbose = false;

    public static final int ERROR_OK = 0;
    public static final int ERROR_EOF = 1;
    public static final int ERROR_FAIL = 2;
    ByteBuffer[] m_decoderInputBuffers = null;
    private MediaExtractor m_extractor = null;
    private int m_videoTrackIndex = -1;
    private MediaFormat m_format = null;
    private long m_duration = 0;
    private boolean m_extractorInOriginalState = true;
    private MediaCodec.BufferInfo m_bufferInfo = null;
    private MediaCodec m_decoder = null;
    private boolean m_decoderStarted = false;
    private byte[] audioBytes = null;

    private long m_timestampOfLastDecodedFrame = Long.MIN_VALUE;
    private long m_timestampOfCurTexFrame = Long.MIN_VALUE;
    private boolean m_firstPlaybackTexFrameUnconsumed = false;
    private boolean m_inputBufferQueued = false;
    private int m_pendingInputFrameCount = 0;
    private boolean m_sawInputEOS = false;
    private boolean m_sawOutputEOS = false;

    public MediaCodecAudioDecoder() {
        m_bufferInfo = new MediaCodec.BufferInfo();
    }

    public boolean OpenFile(String audioFilePath) {
        if (IsValid()) {
            Log.e(TAG, "You can't call OpenFile() twice!");
            return false;
        }

        // Create media extractor and set data source
        try {
            m_extractor = new MediaExtractor();
            m_extractor.setDataSource(audioFilePath);
            m_extractorInOriginalState = true;
        } catch (Exception e) {
            Log.e(TAG, "" + e.getMessage());
            e.printStackTrace();
            CloseFile();
            return false;
        }

        // Find and select video track
        // Select the first video track we find, ignore the rest
        int numTracks = m_extractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
            MediaFormat format = m_extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("audio/")) {
                if (m_verbose)
                    Log.d(TAG, "Extractor selected track " + i + " (" + mime + "): " + format);
                m_videoTrackIndex = i;
                break;
            }
        }

        if (m_videoTrackIndex < 0) {
            Log.e(TAG, "Failed to find a video track from " + audioFilePath);
            CloseFile();
            return false;
        }

        m_extractor.selectTrack(m_videoTrackIndex);
        m_format = m_extractor.getTrackFormat(m_videoTrackIndex);
        if (Build.VERSION.SDK_INT == 16) {
            // NOTE: some android 4.1 devices (such as samsung GT-I8552) will
            // crash in MediaCodec.configure
            // if we don't set MediaFormat.KEY_MAX_INPUT_SIZE.
            // Please refer to
            // http://stackoverflow.com/questions/22457623/surfacetextures-onframeavailable-method-always-called-too-late
            m_format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
        }
        m_duration = m_format.getLong(MediaFormat.KEY_DURATION);
        final String mime = m_format.getString(MediaFormat.KEY_MIME);
        if (m_verbose)
            Log.d(TAG, "Selected video track " + m_videoTrackIndex + ", (" + mime + "): " + m_format
                    + ", duration(us): " + m_duration);

        if (!SetupDecoder(mime)) {
            CloseFile();
            return false;
        }

        // m_extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);

        return true;
    }

    public void CloseFile() {
        CleanupDecoder();

        if (m_extractor != null) {
            m_extractor.release();
            m_extractor = null;
            m_videoTrackIndex = -1;
            m_format = null;
            m_duration = 0;
            m_extractorInOriginalState = true;
        }
    }

    // On success, the texture is updated to the seeked video frame
    public int SeekVideoFrame(long timestamp, long tolerance) {
        if (m_verbose)
            Log.d(TAG, "SeekVideoFrame() called, timestamp=" + timestamp + ", tolerance=" + tolerance);

        if (!IsValid())
            return ERROR_EOF;

        timestamp = Math.max(timestamp, 0);
        if (timestamp >= m_duration)
            return ERROR_EOF;

        if (m_timestampOfCurTexFrame != Long.MIN_VALUE && Math.abs(timestamp - m_timestampOfCurTexFrame) <= tolerance)
            return ERROR_OK; // Texture frame cache hit

        return SeekInternal(timestamp, tolerance);
    }

    public int StartPlayback(long timestamp, long tolerance) {
        if (m_verbose)
            Log.d(TAG, "StartPlayback() called, timestamp=" + timestamp + ", tolerance=" + tolerance);

        if (!IsValid())
            return ERROR_EOF;

        timestamp = Math.max(timestamp, 0);
        if (timestamp >= m_duration)
            return ERROR_EOF;

        if (timestamp == m_timestampOfCurTexFrame && m_timestampOfCurTexFrame == m_timestampOfLastDecodedFrame) {
            m_firstPlaybackTexFrameUnconsumed = true;
            return ERROR_OK;
        }

        final int ret = SeekInternal(timestamp, tolerance);
        if (ret != ERROR_OK)
            return ret;

        m_firstPlaybackTexFrameUnconsumed = true;
        return ERROR_OK;
    }

    public int GetNextVideoFrameForPlayback() {
        if (!IsValid())
            return ERROR_EOF;

        if (!m_firstPlaybackTexFrameUnconsumed) {
            final int ret = DecodeToFrame(Long.MIN_VALUE, 0);
            if (ret != ERROR_OK)
                return ret;
        } else {
            m_firstPlaybackTexFrameUnconsumed = false;
        }

        return ERROR_OK;
    }

    public long GetTimestampOfCurrentTextureFrame() {
        return m_timestampOfCurTexFrame;
    }

    public byte[] getAudioBytes() {
        return audioBytes;
    }

    private boolean IsValid() {
        return m_decoder != null;
    }

    private boolean SetupDecoder(String mime) {
        // Create a MediaCodec decoder, and configure it with the MediaFormat
        // from the
        // extractor. It's very important to use the format from the extractor
        // because
        // it contains a copy of the CSD-0/CSD-1 codec-specific data chunks.
        try {
            m_decoder = MediaCodec.createDecoderByType(mime);
            m_decoder.configure(m_format, null, null, 0);
            m_decoder.start();
            m_decoderStarted = true;

            // Retrieve the set of input buffers
            m_decoderInputBuffers = m_decoder.getInputBuffers();
            if (m_verbose) {
                final int inputBufferCount = m_decoderInputBuffers.length;
                Log.d(TAG, "Input buffer count is " + inputBufferCount);
            }
        } catch (Exception e) {
            Log.e(TAG, "" + e.getMessage());
            e.printStackTrace();
            CleanupDecoder();
            return false;
        }

        return true;
    }

    public void CleanupDecoder() {
        if (m_decoder != null) {
            if (m_decoderStarted) {
                try {
                    if (m_inputBufferQueued) {
                        m_decoder.flush();
                        m_inputBufferQueued = false;
                    }

                    m_decoder.stop();
                } catch (Exception e) {
                    Log.e(TAG, "" + e.getMessage());
                    e.printStackTrace();
                }
                m_decoderStarted = false;
                m_decoderInputBuffers = null;
            }
            m_decoder.release();
            m_decoder = null;
        }

        m_timestampOfLastDecodedFrame = Long.MIN_VALUE;
        m_timestampOfCurTexFrame = Long.MIN_VALUE;
        m_firstPlaybackTexFrameUnconsumed = false;
        m_pendingInputFrameCount = 0;
        m_sawInputEOS = false;
        m_sawOutputEOS = false;

        if (m_verbose)
            Log.d(TAG, "CleanupDecoder called");
    }

    private int SeekInternal(long timestamp, long tolerance) {
        boolean skipSeekFile = false;
        if (m_timestampOfLastDecodedFrame != Long.MIN_VALUE && timestamp > m_timestampOfLastDecodedFrame
                && timestamp < m_timestampOfLastDecodedFrame + 1500000) {
            // In this case, we don't issue seek command to MediaExtractor,
            // since decode from here to find the expected frame may be faster
            skipSeekFile = true;
        } else if (m_extractorInOriginalState && timestamp < 1500000) {
            skipSeekFile = true;
        }

        if (!skipSeekFile) {
            try {
                // Seek media extractor the closest Intra frame whose timestamp
                // is litte than 'timestamp'
                m_extractor.seekTo(timestamp, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                if (m_verbose)
                    Log.d(TAG, "Seek to " + timestamp);

                if (m_sawInputEOS || m_sawOutputEOS) {
                    // The video decoder has seen the input EOS or the output
                    // EOS,
                    // It is not possible for the decoder to decode any more
                    // video frame after the seek point.
                    // Now we have to re-create the video decoder
                    CleanupDecoder();
                    final String mime = m_format.getString(MediaFormat.KEY_MIME);
                    if (!SetupDecoder(mime))
                        return ERROR_FAIL;

                    if (m_verbose)
                        Log.d(TAG, "Decoder has been recreated.");
                } else {
                    if (m_inputBufferQueued) {
                        // NOTE: it seems that MediaCodec in some android
                        // devices (such as Xiaomi 2014011)
                        // will run into trouble if we call MediaCodec.flush()
                        // without queued any buffer before
                        m_decoder.flush();
                        m_inputBufferQueued = false;
                        m_pendingInputFrameCount = 0;
                        if (m_verbose)
                            Log.d(TAG, "Video decoder has been flushed.");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "" + e.getMessage());
                e.printStackTrace();
                return ERROR_FAIL;
            }
        }

        return DecodeToFrame(timestamp, tolerance);
    }

    private int DecodeToFrame(long timestamp, long tolerance) {
        try {
            return DoDecodeToFrame(timestamp, tolerance);
        } catch (Exception e) {
            Log.e(TAG, "" + e.getMessage());
            e.printStackTrace();
            // It is better to cleanup the decoder to prevent further error
            CleanupDecoder();
            return ERROR_FAIL;
        }
    }

    // If timestamp is Long.MIN_VALUE, this function will simply decode the next
    // frame,
    // Otherwise it will decode to the closest frame whose timestamp is equal to
    // or greater than 'timestamp - tolerance'
    private int DoDecodeToFrame(long timestamp, long tolerance) {
        final int inputBufferCount = m_decoderInputBuffers.length;
        final int pendingInputBufferThreshold = Math.max(inputBufferCount / 3, 2);
        final int TIMEOUT_USEC = 4000;
        int deadDecoderCounter = 0;

        while (!m_sawOutputEOS) {
            if (!m_sawInputEOS) {
                // Feed more data to the decoder
                final int inputBufIndex = m_decoder.dequeueInputBuffer(TIMEOUT_USEC);
                if (inputBufIndex >= 0) {
                    ByteBuffer inputBuf = m_decoderInputBuffers[inputBufIndex];
                    // Read the sample data into the ByteBuffer. This neither
                    // respects nor
                    // updates inputBuf's position, limit, etc.
                    final int chunkSize = m_extractor.readSampleData(inputBuf, 0);

                    if (m_verbose)
                        Log.d(TAG, "input packet length: " + chunkSize + " time stamp: " + m_extractor.getSampleTime());

                    if (chunkSize < 0) {
                        // End of stream -- send empty frame with EOS flag set.
                        m_decoder.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        m_sawInputEOS = true;
                        if (m_verbose)
                            Log.d(TAG, "Sent input EOS");
                    } else {
                        if (m_extractor.getSampleTrackIndex() != m_videoTrackIndex) {
                            Log.w(TAG, "WEIRD: got sample from track " + m_extractor.getSampleTrackIndex()
                                    + ", expected " + m_videoTrackIndex);
                        }

                        long presentationTimeUs = m_extractor.getSampleTime();

                        m_decoder.queueInputBuffer(inputBufIndex, 0, chunkSize, presentationTimeUs, 0);
                        if (m_verbose)
                            Log.d(TAG,
                                    "Submitted frame to decoder input buffer " + inputBufIndex + ", size=" + chunkSize);

                        m_inputBufferQueued = true;
                        ++m_pendingInputFrameCount;
                        if (m_verbose)
                            Log.d(TAG, "Pending input frame count increased: " + m_pendingInputFrameCount);

                        m_extractor.advance();
                        m_extractorInOriginalState = false;
                    }
                } else {
                    if (m_verbose)
                        Log.d(TAG, "Input buffer not available");
                }
            }

            // Determine the expiration time when dequeue output buffer
            int dequeueTimeoutUs;
            if (m_pendingInputFrameCount > pendingInputBufferThreshold || m_sawInputEOS) {
                dequeueTimeoutUs = TIMEOUT_USEC;
            } else {
                // NOTE: Too few input frames has been queued and the decoder
                // has not yet seen input EOS
                // wait dequeue for too long in this case is simply wasting
                // time.
                dequeueTimeoutUs = 0;
            }

            // Dequeue output buffer
            final int decoderStatus = m_decoder.dequeueOutputBuffer(m_bufferInfo, dequeueTimeoutUs);

            if (m_verbose)
                Log.d(TAG, "decoderStatus is " + decoderStatus);

            ++deadDecoderCounter;
            if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // No output available yet
                if (m_verbose)
                    Log.d(TAG, "No output from decoder available");
            } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                if (m_verbose)
                    Log.d(TAG, "Decoder output buffers changed");
            } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat newFormat = m_decoder.getOutputFormat();
                if (m_verbose)
                    Log.d(TAG, "Decoder output format changed: " + newFormat);
            } else if (decoderStatus < 0) {
                Log.e(TAG, "Unexpected result from decoder.dequeueOutputBuffer: " + decoderStatus);
                return ERROR_FAIL;
            } else { // decoderStatus >= 0
                if (m_verbose) {
                    Log.d(TAG, "Surface decoder given buffer " + decoderStatus + " (size=" + m_bufferInfo.size + ") "
                            + " (pts=" + m_bufferInfo.presentationTimeUs + ") ");
                }

                if ((m_bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    if (m_verbose)
                        Log.d(TAG, "Output EOS");
                    m_sawOutputEOS = true;
                }

                // Render to texture?
                boolean doRender = false;

                // NOTE: We don't use m_bufferInfo.size != 0 to determine
                // whether we can render the decoded frame,
                // since some stupid android devices such as XIAOMI 2S, Xiaomi
                // 2014011 ... will always report zero-sized buffer
                // if we have configured the video decoder with a surface.
                // Now we will render the frame if the m_bufferInfo didn't carry
                // MediaCodec.BUFFER_FLAG_END_OF_STREAM flag.
                // NOTE: this method is a hack and we may lose the last video
                // frame
                // if the last video frame carry the
                // MediaCodec.BUFFER_FLAG_END_OF_STREAM flag.
                if (!m_sawOutputEOS) {
                    deadDecoderCounter = 0;

                    // Update timestamp of last decoded video frame
                    m_timestampOfLastDecodedFrame = m_bufferInfo.presentationTimeUs;
                    --m_pendingInputFrameCount;
                    if (m_verbose)
                        Log.d(TAG, "Pending input frame count decreased: " + m_pendingInputFrameCount);

                    if (timestamp != Long.MIN_VALUE)
                        doRender = (m_timestampOfLastDecodedFrame >= (timestamp - tolerance));
                    else
                        doRender = true;
                }

                if (doRender) {
                    if (m_verbose)
                        Log.d(TAG, "Rendering decoded frame to surface texture.");

                    m_timestampOfCurTexFrame = m_bufferInfo.presentationTimeUs;
                    if (m_verbose)
                        Log.d(TAG, "Surface texture updated, pts=" + m_timestampOfCurTexFrame);
                    ByteBuffer outputBuffer = getOutputBuffer(m_decoder, decoderStatus);
                    if (outputBuffer != null) {
                        byte[] buffer = new byte[outputBuffer.limit()];
                        outputBuffer.position(0);
                        outputBuffer.get(buffer, 0, outputBuffer.limit());
                        outputBuffer.clear();
                        audioBytes = buffer;
                    } else {
                        audioBytes = null;
                    }

                    m_decoder.releaseOutputBuffer(decoderStatus, true);
                    return ERROR_OK;
                }
                m_decoder.releaseOutputBuffer(decoderStatus, false);
            }

            if (deadDecoderCounter > 100) {
                Log.e(TAG, "We have tried two many times and can't decode a frame!");
                return ERROR_EOF;
            }
        } // while (!m_sawOutputEOS)

        return ERROR_EOF;
    }

    public void beforeSeek() {
        if (m_sawInputEOS || m_sawOutputEOS) {
            // The video decoder has seen the input EOS or the output EOS,
            // It is not possible for the decoder to decode any more video frame
            // after the seek point.
            // Now we have to re-create the video decoder
            CleanupDecoder();
            final String mime = m_format.getString(MediaFormat.KEY_MIME);
            if (!SetupDecoder(mime))
                return;

            if (m_verbose)
                Log.d(TAG, "Decoder has been recreated.");
        } else {
            if (m_inputBufferQueued) {
                // NOTE: it seems that MediaCodec in some android devices (such
                // as Xiaomi 2014011)
                // will run into trouble if we call MediaCodec.flush() without
                // queued any buffer before
                m_decoder.flush();
                m_inputBufferQueued = false;
                m_pendingInputFrameCount = 0;
                if (m_verbose)
                    Log.d(TAG, "Video decoder has been flushed.");
            }
        }
    }

    public static ByteBuffer getOutputBuffer(MediaCodec codec, int index) throws IllegalStateException {
        if (Build.VERSION.SDK_INT < 21) {
            return codec.getOutputBuffers()[index];
        }
        return codec.getOutputBuffer(index);
    }

}