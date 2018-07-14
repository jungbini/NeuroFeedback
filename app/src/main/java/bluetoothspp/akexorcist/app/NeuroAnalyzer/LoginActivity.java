package bluetoothspp.akexorcist.app.NeuroAnalyzer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

import app.akexorcist.bluetotohspp.library.BluetoothSPP;


public class LoginActivity extends Activity implements OnClickListener {

    BluetoothSPP bt;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        Button btnLogin = (Button) findViewById(R.id.btnLogin);
        btnLogin.setOnClickListener(this);

        Button btnNeuroFeedback = (Button) findViewById(R.id.btnNeuroFeedback);
        btnNeuroFeedback.requestFocus();
        btnNeuroFeedback.setOnClickListener(this);

        bt = new BluetoothSPP(this);
        if(!bt.isBluetoothAvailable()) {
            Toast.makeText(getApplicationContext()
                    , "블루투스를 지원하지 않는 기기입니다."
                    , Toast.LENGTH_LONG).show();
            finish();
        }
    }

    public void onClick(View v) {
        int id = v.getId();
        Intent intent;

        switch(id) {

            case R.id.btnLogin:

                intent = new Intent(getApplicationContext(), TestActivity.class);
                startActivity(intent);
                break;

            case R.id.btnNeuroFeedback:

                intent = new Intent(getApplicationContext(), NeuroFeedbackActivity.class);
                startActivity(intent);
                break;
        }
    }
}
