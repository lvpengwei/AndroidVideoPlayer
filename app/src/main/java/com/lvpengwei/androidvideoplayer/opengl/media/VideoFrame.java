package com.lvpengwei.androidvideoplayer.opengl.media;

public class VideoFrame extends MovieFrame implements Cloneable {
    private int luma;
    private int chromaB;
    private int chromaR;
    private int width;
    private int height;

    @Override
    protected Object clone() {
        VideoFrame frame = new VideoFrame();
        frame.width = width;
        frame.height = height;
        frame.luma = luma;
        frame.chromaB = chromaB;
        frame.chromaR = chromaR;
        return frame;
    }

    @Override
    public MovieFrameType getType() {
        return MovieFrameType.MovieFrameTypeVideo;
    }
}
