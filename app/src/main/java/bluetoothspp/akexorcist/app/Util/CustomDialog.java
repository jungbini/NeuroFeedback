package bluetoothspp.akexorcist.app.Util;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;

import bluetoothspp.akexorcist.app.NeuroAnalyzer.R;

public class CustomDialog extends Dialog implements View.OnClickListener {
    private float volumeChangeLevel = 0.1f;
    private int deltaRatio = 33;

    private Button positiveButton;
    private Button negativeButton;
    private RadioGroup rGroupVolume, rGroupDeltaRatio;
    private Context context;

    private CustomDialogListener customDialogListener;

    public CustomDialog(Context context) {
        super(context);
        this.context = context;
    }

    public interface CustomDialogListener {
        void onPositiveClicked(float volumeUnit, int deltaRatio);
        void onNegativeClicked();
    }

    public void setDialogListener(CustomDialogListener customDialogListener) {
        this.customDialogListener = customDialogListener;
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.custom_dialog);

        positiveButton = findViewById(R.id.btnOK);
        negativeButton = findViewById(R.id.btnCancel);

        rGroupVolume = findViewById(R.id.rGroupVolume);
        rGroupVolume.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                switch (checkedId) {
                    case R.id.rBtnVolume10:     volumeChangeLevel = 0.1f;     break;
                    case R.id.rBtnVolume20:     volumeChangeLevel = 0.05f;     break;
                }
            }
        });

        rGroupDeltaRatio = findViewById(R.id.rGroupDeltaRatio);
        rGroupDeltaRatio.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                switch (checkedId) {
                    case R.id.rBtnDelta33:      deltaRatio = 30;    break;
                    case R.id.rBtnDelta36:      deltaRatio = 35;    break;
                    case R.id.rBtnDelta40:      deltaRatio = 40;    break;
                }
            }
        });

        positiveButton.setOnClickListener(this);
        negativeButton.setOnClickListener(this);
    }

    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnOK:
                customDialogListener.onPositiveClicked(volumeChangeLevel, deltaRatio);
                dismiss();
                break;
            case R.id.btnCancel:
                cancel();
                break;
        }
    }
}
