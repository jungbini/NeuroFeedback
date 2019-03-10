package bluetoothspp.akexorcist.app.NeuroAnalyzer;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;

import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.security.ProviderInstaller;

import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLContext;

public class CustomApplication extends Application {

    public void onCreate() {
        super.onCreate();
    }

    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    public void onLowMemory() {
        super.onLowMemory();
    }

    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
    }

    public static void initializeSSLContext(Context mContext){
        try {
            SSLContext.getInstance("TLSv1.2");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        try {
            ProviderInstaller.installIfNeeded(mContext.getApplicationContext());
        } catch (GooglePlayServicesRepairableException e) {
            e.printStackTrace();
        } catch (GooglePlayServicesNotAvailableException e) {
            e.printStackTrace();
        }
    }
}
