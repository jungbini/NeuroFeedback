package bluetoothspp.akexorcist.app.NeuroAnalyzer;

import android.media.MediaPlayer;
import android.support.annotation.NonNull;
import android.view.View;

/**
 * Created by jungbini on 2018-06-04.
 */

public final class SoundRunnable implements Runnable {

    private final MediaPlayer mediaPlyaer;
    private final View view;
    private long interval;

    public SoundRunnable(@NonNull View view, float volume, long interval) {
        this.view = view;
        mediaPlyaer = MediaPlayer.create(this.view.getContext(), R.raw.waterdrop);
        mediaPlyaer.setVolume(volume, volume);
        this.interval = interval;
    }

    public void run() {
        mediaPlyaer.start();
        view.postDelayed(this, interval);
    }

    public void setVolume(float volume) {
        mediaPlyaer.setVolume(volume, volume);
    }

    public void setInterval(long interval) {
        this.interval = interval;
    }

    public void destroy() {
        if(mediaPlyaer.isPlaying())
            mediaPlyaer.stop();

        mediaPlyaer.release();
        view.removeCallbacks(this);
    }

}
