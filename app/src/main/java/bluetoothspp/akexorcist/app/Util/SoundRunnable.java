package bluetoothspp.akexorcist.app.Util;

import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.View;

import java.io.IOException;

import bluetoothspp.akexorcist.app.NeuroAnalyzer.R;

/**
 * Created by jungbini on 2018-06-04.
 */

public final class SoundRunnable implements Runnable {

    private final MediaPlayer mediaPlayer;
    private final View view;
    private long interval;
    private AssetFileDescriptor afd;

    public SoundRunnable(@NonNull View view, float volume, long interval, int soundNum) {
        this.view = view;

        int resourceID = 0;
        if (soundNum == 0)
            resourceID = R.raw.waterdrop2;
        else if (soundNum == 1)
            resourceID = R.raw.waterdrop3;
        else if (soundNum == 2)
            resourceID = R.raw.rain;

        mediaPlayer = MediaPlayer.create(this.view.getContext(), resourceID);
        mediaPlayer.setVolume(volume, volume);
        this.interval = interval;
    }

    public void run() {
        mediaPlayer.start();
        view.postDelayed(this, interval);
    }

    public void setVolume(float volume) {
        mediaPlayer.setVolume(volume, volume);
    }

    public void setInterval(long interval) {
        this.interval = interval;
    }

    public void destroy() {
        if(mediaPlayer.isPlaying())
            mediaPlayer.stop();

        mediaPlayer.release();
        view.removeCallbacks(this);
    }

}
