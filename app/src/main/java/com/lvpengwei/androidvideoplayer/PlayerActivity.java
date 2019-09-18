package com.lvpengwei.androidvideoplayer;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.TextView;

import com.lvpengwei.androidvideoplayer.player.PlayerController;

import java.io.IOException;

/**
 * Author: pengweilv
 * Date: 2019-09-18
 * Description:
 */
public class PlayerActivity extends Activity implements SurfaceHolder.Callback {
    private PlayerController player = new PlayerController();

    private SurfaceView surfaceView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        final TextView textView = findViewById(R.id.text_view);
        textView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (player.isPlaying()) {
                    textView.setText("Play");
                    player.pause();
                } else {
                    textView.setText("Pause");
                    player.play();
                }
            }
        });
        surfaceView = findViewById(R.id.surface_view);
        surfaceView.getHolder().addCallback(this);
        startPlayer();
    }

    private void startPlayer() {
        AssetFileDescriptor fd;
        try {
            fd = getAssets().openFd("demo_video_01.mp4");
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        player.init(fd);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        player.destroy();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        player.onSurfaceCreated(holder.getSurface(), surfaceView.getWidth(), surfaceView.getHeight());
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        player.onSurfaceDestroyed();
    }
}
