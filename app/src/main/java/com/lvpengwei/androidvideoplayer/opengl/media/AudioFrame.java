package com.lvpengwei.androidvideoplayer.opengl.media;

public class AudioFrame extends MovieFrame {
    private byte[] buffer;
    private boolean dataUseUp = true;

    public void setBuffer(byte[] buffer) {
        this.buffer = buffer;
    }

    public byte[] getBuffer() {
        return buffer;
    }

    public void fillFullData() {
        dataUseUp = false;
    }

    public void useUpData() {
        dataUseUp = true;
    }

    public boolean isDataUseUp() {
        return dataUseUp;
    }

    @Override
    public MovieFrameType getType() {
        return MovieFrameType.MovieFrameTypeAudio;
    }
}
