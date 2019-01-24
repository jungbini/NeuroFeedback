package bluetoothspp.akexorcist.app.NeuroAnalyzer;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.commons.lang3.ArrayUtils;
import org.jtransforms.fft.DoubleFFT_1D;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReentrantLock;

import app.akexorcist.bluetotohspp.library.BluetoothSPP;
import app.akexorcist.bluetotohspp.library.BluetoothSPP.BluetoothConnectionListener;
import app.akexorcist.bluetotohspp.library.BluetoothSPP.OnDataReceivedListener;
import app.akexorcist.bluetotohspp.library.BluetoothState;
import app.akexorcist.bluetotohspp.library.DeviceList;
import bluetoothspp.akexorcist.app.DataProcessing.DataIOThread;
import bluetoothspp.akexorcist.app.RegressionAnalysis.LinearRegressionModel;
import bluetoothspp.akexorcist.app.RegressionAnalysis.RegressionModel;

public class NeuroFeedbackActivity extends Activity implements OnClickListener {

    private final int ANALYSIS_SECOND = 60;                         // 센서 데이터 수집 시간(초)
    private final double PS_HZ = 0.016667;                          // Power spectrum HZ
    private final ReentrantLock lock = new ReentrantLock();         // 쓰레드 lock을 위한 Util 클래스

    private double elapsedSecond = 0;                               // 경과된 초
    private double gradient = 0;                                     // 회귀식 기울기
    private ArrayList<Double> xAlphaThetaArray;                      // 매초를 저장할 배열
    private ArrayList<Double> yAlphaThetaArray;                      // Alpha / Theta 비율 값을 저장할 배열
    private double totalBrainPSValue = 0.0;                          // 총 뇌파 값 저장 변수
    private double deltaPSValue = 0.0;                               // 총 Delta 값 저장 변수

    private final String [] MODEL_LIST = {"회귀모델(S1,2,3,4)", "회귀모델(S2,3,4)"};
    private final String [] SOUND_LIST = {"물방울 소리1", "물방울 소리2", "물방울 소리3"};
    //private enum SimulationMode {None, Sleep, Wake}                  // 시뮬레이션 모드

    private static ArrayList<Double> sensorData;                     // 센서 데이터를 임시로 보관하는 List
    private static ArrayList<Integer> sensorDataCnt;         // 1초에 읽어온 센서 데이터의 갯수를 저장
    private double fftLChInputs [];                       // FFT 계산을 위한 Channel raw data 배열 변수
    private int inputCount = 0;                             // 읽어 온 센서 데이터 카운터
    private int secCount = -1;                              // 30초 초기화 과정을 위한 카운터
    private int typeOfFB = 0;                               // 피드백 종류 (0: 소리 줄이기, 1: 간격 늘리기)
    private long soundDelay = 5000L;                       // 물방울 반복 딜레이
    private float volumeLevel = 1.0f;                      // 초기 볼륨 값
    private int feedbackChangeTime = 0;                   // 피드백을 반영하기 까지 걸릴 시간
    private int feedbackElapsedTime = 1;                 // 피드백을 주기전 경과 시간

    // UI 변수
    private TextView txtBTStatus, txtMonitoring, fbChnageTerm;
    private Button btnBTConnect;
    private Spinner modelSpinner, soundSpinner;
    private RadioGroup rgSoundSpeed;
    private SeekBar pBarFBTime;
    private ImageView imgView;
    private Drawable sleepImg;
    private Drawable wakeImg;
    private Switch switchFileRecording;

    private BluetoothSPP bt;                                  // BluetoothSPP 변수

    private DataIOThread readThread;                   // 센서 리딩 쓰레드
    private SoundRunnable soundRunnable;                   // 소리 재생 Runnable
    private Message msg;                                     // 센서 리딩 쓰레드에 보낼 메시지

    private Timer fftTimer;
    private TimerTask runFFTTask;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_neurofeedback);

        sensorData = new ArrayList<>();                                     // 센서 데이터를 임시로 보관하는 List
        sensorDataCnt = new ArrayList<>();                                  // 30초 동안 읽어온 데이터의 갯수를 저장

        xAlphaThetaArray = new ArrayList<>();                          // Alpha / Theta 회귀식을 구하기 위한 x 값 배열
        yAlphaThetaArray = new ArrayList<>();                          // Alpha / Theta 회귀식을 구하기 위한 y 값 배열

        bt = new BluetoothSPP(this);                                // Bluetooth 초기화
        bt.setOnDataReceivedListener(new OnDataReceivedListener() {          // 블루투스 데이터 수신 리스너
            public void onDataReceived(byte[] data, String message) {
                msg = Message.obtain(readThread.mBackHandler, 1);
                msg.what = 1;
                msg.obj = data;
                readThread.mBackHandler.sendMessage(msg);
            }
        });

        sleepImg = getResources().getDrawable(R.drawable.sleep);
        wakeImg = getResources().getDrawable(R.drawable.wake);

        TabHost tabHost = findViewById(R.id.TabHost);
        tabHost.setup();

        TabHost.TabSpec ts1 = tabHost.newTabSpec("Main");
        ts1.setContent(R.id.tabMain);
        ts1.setIndicator("뉴로피드백");
        tabHost.addTab(ts1);

        TabHost.TabSpec ts2 = tabHost.newTabSpec("Setting");
        ts2.setContent(R.id.tabSetting);
        ts2.setIndicator("설정");
        tabHost.addTab(ts2);

        RadioGroup rgFeedbackMethod = findViewById(R.id.rgroupFeedback);
        rgFeedbackMethod.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.rbtnSetVolume) {
                    typeOfFB = 0;                           // 볼륨 줄이기 피드백

                    for (int i = 0 ; i < rgSoundSpeed.getChildCount() ; i++)
                        (rgSoundSpeed.getChildAt(i)).setEnabled(true);                  // 수동으로 소리간격 조절하는 옵션 활성화하기
                    rgSoundSpeed.check(R.id.rbtnSet50);

                } else {
                    typeOfFB = 1;                           // 간격 조절 피드백
                    for (int i = 0 ; i < rgSoundSpeed.getChildCount() ; i++)
                        (rgSoundSpeed.getChildAt(i)).setEnabled(false);     // 수동으로 소리간격 조절하는 옵션 비활성화하기
                }

                rgSoundSpeed.check(R.id.rbtnSet50);       // 피드백 타입이 바뀌면 값을 초기화 하기
                pBarFBTime.setProgress(1);
                if (soundRunnable != null)
                    soundRunnable.setVolume(1.0f);
            }
        });
        rgFeedbackMethod.check(R.id.rbtnSetVolume);

        rgSoundSpeed = findViewById(R.id.rgSoundSpeed);                            // 물방울 소리 간격 조절
        rgSoundSpeed.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                switch (checkedId) {
                    case R.id.rbtnSet25:        soundDelay = 2500L;     break;
                    case R.id.rbtnSet50:        soundDelay = 5000L;     break;
                    case R.id.rbtnSet75:        soundDelay = 7500L;     break;
                    case R.id.rbtnSet100:       soundDelay = 10000L;    break;
                }

                if (soundRunnable != null)
                    soundRunnable.setInterval(soundDelay);                                          // 바로 소리 재생 딜레이로 반영
            }
        });

        RadioGroup rgSimulation = findViewById(R.id.rgSimulation);                             // 시뮬레이션 모드 설정
        rgSimulation.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                switch (checkedId) {
                    case R.id.rbtnNoSimulation:
                        rgSoundSpeed.check(R.id.rbtnSet50);                     // 일반 모드로 돌아오면 모든 값 초기화하기
                        pBarFBTime.setProgress(1);
                        if (soundRunnable != null)
                            soundRunnable.setVolume(1.0f);
                        break;
//                    case R.id.rbtnSleepMode:                                    // Sleep 시뮬레이션 모드이면 무조건 Sleep 상태
//                        typeOfSleepStage = 1;
//                        break;
//                    case R.id.rbtnWakeMode:                                     // Wake 시뮬레이션 모드: 무조건 Wake 상태
//                        typeOfSleepStage = 0;
//                        break;
                }
            }
        });

         // 수면 예측 모델 선택 스피너 아이템 초기화
        modelSpinner = findViewById(R.id.spnModelSelection);   // 스피너에 리스트 추가
        ArrayAdapter<String> spinnerListAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, MODEL_LIST);
        modelSpinner.setAdapter(spinnerListAdapter);
        modelSpinner.setSelection(1);

        // 소리 선택 스피터 아이템 초기화
        soundSpinner = findViewById(R.id.spnSoundSelection);    // 사운드 선택 리스트 추가
        ArrayAdapter<String> soundSpinnerListAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, SOUND_LIST);
        soundSpinner.setAdapter(soundSpinnerListAdapter);
        soundSpinner.setSelection(1);

        fbChnageTerm = findViewById(R.id.tboxFBChangeTerm);
        feedbackChangeTime = Integer.parseInt(fbChnageTerm.getText().toString()) * 60;       // 변화를 주는 시간 텀 (분 * 60 = 초 단위로 변경)

        pBarFBTime = findViewById(R.id.pBarFeedbackChangeTime);
        pBarFBTime.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                fbChnageTerm.setText(String.valueOf(progress));
                feedbackChangeTime = progress * 60;
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        switchFileRecording = findViewById(R.id.switchFileRecording);

        txtBTStatus = findViewById(R.id.textBTStatus);
        txtMonitoring = findViewById(R.id.tboxStatus);
        txtMonitoring.setMovementMethod(new ScrollingMovementMethod());

        imgView = findViewById(R.id.imageView);
        imgView.setImageDrawable(wakeImg);

        // 블루투스 연결 버튼 클릭 리스너 등록
        btnBTConnect = findViewById(R.id.btnBTConnect);
        btnBTConnect.setOnClickListener(this);

        bt.setBluetoothConnectionListener(new BluetoothConnectionListener() {
            public void onDeviceDisconnected() {
                ((Button) findViewById(R.id.btnBTConnect)).setText("연결");
                txtBTStatus.setText("블루투스 장치 연결이 끊겼습니다.");
                txtMonitoring.append("프로그램이 종료되었습니다.\n\n");

                if (readThread != null) {
                    readThread.closeFileStream();                                   // 기록 중인 파일 스트림 중지
                    readThread.interrupt();                                         // 센서 데이터 리딩 정지
                    readThread = null;
                }

                if (fftTimer != null) {
                    fftTimer.cancel();
                    fftTimer = null;
                }

                if (runFFTTask != null) {
                    runFFTTask.cancel();
                    runFFTTask = null;
                }

                if (soundRunnable != null) {
                    txtMonitoring.removeCallbacks(soundRunnable);
                    soundRunnable.destroy();
                    soundRunnable = null;
                }

                secCount = -1;                      // 경과 시간 초기화
                inputCount = 0;                     // 초당 센싱 데이터 초기화

                sensorData.clear();                 // 센싱 데이터 배열 초기화
                sensorDataCnt.clear();              // 센싱 데이터 갯수 배열 초기화

            }

            public void onDeviceConnectionFailed() {
                txtBTStatus.setText("블루투스 장치에 연결 실패! Orz...");
            }
            public void onDeviceConnected(String name, String address)
            {
                readThread = new DataIOThread(mHandler, getApplicationContext());          // Data reading 쓰레드 시작
                readThread.setDaemon(true);
                readThread.start();                                                               // 센서 데이터 읽기 시작

                runFFTTask = timerTaskMaker();
                fftTimer = new Timer();                                                           // 1초마다 FFT 구하는 테스크를 실행하는 타이머
                fftTimer.schedule(runFFTTask, 0, 1000L);                         // FFT 구하는 타이머 시작

                ((Button) findViewById(R.id.btnBTConnect)).setText("연결 해제");
                txtBTStatus.setText(name + "에 연결되었습니다.");

                soundRunnable = new SoundRunnable(txtBTStatus, volumeLevel, soundDelay);    // 소리 피드백 시작
                txtBTStatus.post(soundRunnable);
            }
        });
    }

    public void onStart() {
        super.onStart();
        if (!bt.isBluetoothEnabled()) {         // 블루투스가 켜져 있지 않으면 켜도록 권유
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, BluetoothState.REQUEST_ENABLE_BT);
        } else {
            if(!bt.isServiceAvailable()) {
                bt.setupService();
                bt.startService(BluetoothState.DEVICE_ANDROID);
            }
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == BluetoothState.REQUEST_CONNECT_DEVICE) {
            if(resultCode == Activity.RESULT_OK) {              // 블루투스 디바이스 연결이 성공하면
                bt.connect(data);
            }

        } else if(requestCode == BluetoothState.REQUEST_ENABLE_BT) {
            if(resultCode == Activity.RESULT_OK) {
                bt.setupService();
                bt.startService(BluetoothState.DEVICE_ANDROID);
            } else {
                Toast.makeText(getApplicationContext(),
                        "블루투스 기기가 올바르게 동작하지 않습니다.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    public void onClick(View v) {
        int id = v.getId();
        Intent intent;

        switch(id) {

            case R.id.btnBTConnect:

                if (btnBTConnect.getText().equals("연결")) {               // 블루투스 수신 시작

                    bt.setDeviceTarget(BluetoothState.DEVICE_OTHER);
                    intent = new Intent(getApplicationContext(), DeviceList.class);
                    startActivityForResult(intent, BluetoothState.REQUEST_CONNECT_DEVICE);

                } else if(btnBTConnect.getText().equals("연결 해제")){   // 블루투스가 동작 중이고, '블루투스 연결 해제' 버튼을 누르면, 서비스를 종료
                    bt.stopService();
                }

                break;
        }
    }

    Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {

            double expValue =0.0;
            double sleepStageClass = 0.0;
            switch (msg.what) {

                case 1:                                                             // 센서 ReadingThread로부터 데이터를 읽어오는 핸들러
                    inputCount++;

                    lock.lock();
                    sensorData.add((double) ((msg.arg1 + msg.arg2) / 2));           // Left/Right 채널 평균으로 계산
                    lock.unlock();

                    break;

                case 2:                                                             // 예측 모델 회귀식이 들어갈 자리

                    double [] result = (double[]) msg.obj;

                    ++elapsedSecond;                                               // xAlphaThetaArray 리스트에 넣을 초 증가
                    xAlphaThetaArray.add(elapsedSecond);
                    yAlphaThetaArray.add(result[8]);

                    totalBrainPSValue += result[0] + result[2] + result[4] + result[6];
                    deltaPSValue += result[0];

                    if (elapsedSecond > 2) {                                       // ellapsedSecond 가 2초 이상이여야만 회귀분석 가능
                        double[] xArray = toDoublePrimitive(xAlphaThetaArray);
                        double[] yArray = toDoublePrimitive(yAlphaThetaArray);

                        RegressionModel regressionModel = new LinearRegressionModel(xArray, yArray);
                        regressionModel.compute();
                        gradient = regressionModel.getCoefficients()[1];     // 기울기 가져오기

                        ((TextView)findViewById(R.id.tboxRegressionResult)).setText(String.format("%.5f", gradient));
                    }

                    if (modelSpinner.getSelectedItemPosition() == 0) {
                        sleepStageClass = (-0.0022 * result[0]) + (-0.0016 * result[1]) +
                                        (0.0049 * result[2]) + (-0.0031 * result[3]) +
                                        (-0.124 * result[4]) + (0.3691 * result[5]) +
                                        (0.2545 * result[6]) + (-0.4685 * result[7]) +
                                        (0.4833 * result[8]) + (-1.0742 * result[9]) +
                                        (-0.6102 * result[10]) + (5.2727 * result[11]) - 6.5939;
                    } else if (modelSpinner.getSelectedItemPosition() == 1) {
                        sleepStageClass = (0 * result[0]) + (0 * result[1]) +
                                        (0 * result[2]) + (0 * result[3]) +
                                        (-0.0001 * result[4]) + (0.0004 * result[5]) +
                                        (0.0003 * result[6]) + (-0.0005 * result[7]) +
                                        (0.8042 * result[8]) + (0.1474 * result[9]) +
                                        (-0.7677 * result[10]) + (3.9715 * result[11]) - 7.2659;
                    }

                    // 기대값 구하기
                    expValue = ( Math.exp(sleepStageClass) / (1+Math.exp(sleepStageClass)) ) * 100;

                    // 최종 수면 상태 값
                    ((TextView)findViewById(R.id.tboxModelValue)).setText(String.format("%.2f", expValue));

                    // 수면 상태 판별
                    if (gradient != 0)
                        ((TextView)findViewById(R.id.tboxSleepStage)).setText(expValue > 50 ? "Wake" : "Sleep");
                    else
                        ((TextView)findViewById(R.id.tboxSleepStage)).setText("회귀식 계산 중...");

                    // 현재는 시뮬레이션 모드가 없으므로, Wake/Sleep 시뮬레이션 모드를 체크할 필요가 없음
                    // typeOfSleepStage = sleepStageClass > 0.5 ? 0 : 1;
                    int typeOfSleepStage = gradient > 0 ? 0 : 1;                        // 0: Wake, 1: Sleep

                    // 수면 상태에 따라 메인 이미지 바꾸기
                    if (typeOfSleepStage == 0)      imgView.setImageDrawable(wakeImg);
                    else                            imgView.setImageDrawable(sleepImg);

                    if (typeOfFB == 0) {                                                 // 피드백 형식이 소리 줄이기(0)인지, 간격 줄이기(1)인지에 따라 다른 기능 수행
                        volumeFBProcess(typeOfSleepStage, expValue);
                    } else {
                        freqFBProcess(typeOfSleepStage, expValue);
                    }

                case 3:

                    // 모니터링 현황 알림
                    String strFeedbackTime = "피드백 " + (feedbackChangeTime - feedbackElapsedTime) + "초 남음";
                    String infoMsg = msg.arg1 < ANALYSIS_SECOND ? "초기 데이터 수집중...(" + msg.arg1 + "초/" + ANALYSIS_SECOND + "초)" : "뇌파 분석 중...(" + msg.arg1 + "초 경과/" + strFeedbackTime + ")";

                    inputCount = 0;                                                                 // inputCount를 다시 초기화
                    txtMonitoring.append(infoMsg + "\n");

                    break;

                case 4:     // 파일로 기록하기

                    StringBuffer tmpStr = new StringBuffer();

                    Message msg2 = new Message();
                    msg2.what = 3;
                    msg2.arg1 = (int) (expValue * 1000000);
                    msg2.arg2 = sleepStageClass > 0.5 ? 0:1;
                    msg2.obj = msg.obj;
                    readThread.mBackHandler.sendMessage(msg2);

                    break;

                case 5:
                    txtMonitoring.append("현재 소리 간격 = " + (long)msg.obj + "\n");
                    break;

            }
        }
    };

    public void volumeFBProcess(int typeOfSleepStage, double expValue) {

        // 잠이 드는 상태이고, 볼륨이 0 이면 피드백을 중단
        if ( typeOfSleepStage == 1 && volumeLevel == 0 ) {
            txtMonitoring.append("볼륨 피드백 중지\n");
            txtBTStatus.removeCallbacks(soundRunnable);
        } else {                                                        // typeOfSleepStage가 0 (깨고 있는 중)이거나 volumeLevel이 0이 아니면,

            if (!txtBTStatus.getHandler().hasMessages(0)) {
                txtBTStatus.post(soundRunnable);
                txtMonitoring.append("중단됐던 볼륨 피드백 재시작\n");
            }

            if (feedbackElapsedTime >= feedbackChangeTime) {           // 피드백 주기가 될 때까지 기다렸다가 볼륨 조절하기

                double deltaByTotalPS = deltaPSValue / totalBrainPSValue;

                if (typeOfSleepStage == 0) {                           // 잠이 깨는 중이므로 볼륨 키우기
                    volumeLevel += 0.1;
                    if (volumeLevel >= 1.0f)
                        volumeLevel = 1.0f;                         // 볼륨이 100%를 넘을 수 없으므로 1.0으로 고정

                } else {                                            // 잠이 드는 중이므로 볼륨 줄이기

                    // 볼륨이 최소 볼륨인 10%이고, delta 파의 비율이 1/3을 넘지 않으면 볼륨을 10%로 계속 유지
                    if (volumeLevel == 0.1f && deltaByTotalPS < 33)
                        volumeLevel = 0.1f;
                    else if (volumeLevel <= 0.0f)                   // 볼륨이 0%보다 낮을 수 없으므로 0.0으로 고정
                        volumeLevel = 0.0f;
                    else
                        volumeLevel -= 0.1;
                }

                txtMonitoring.append("현재 볼륨 = " + String.format("%.0f", volumeLevel*100) + "\n");
                soundRunnable.setVolume(volumeLevel);

                if (switchFileRecording.isChecked()) {
                    msg = Message.obtain(readThread.mBackHandler, 3);                        // 파일로 피드백 이벤트 기록
                    msg.what = 3;

                    // 기록할 로그 메시지: 예측모델 종류, 피드백 타입, 피드백 반영 시간, 볼륨 크기, 물방울 소리 간격, Delta파 비율, Wake/Sleep 확률값
                    msg.obj = new double[]{modelSpinner.getSelectedItemPosition(), typeOfFB, feedbackChangeTime, volumeLevel, soundDelay, deltaByTotalPS, expValue};
                    readThread.mBackHandler.sendMessage(msg);
                }

                txtMonitoring.append("Delta파의 비율 = " + String.format("%.2f", deltaByTotalPS*100) + "%\n");

                feedbackElapsedTime = 0;                               // 경과 시간을 다시 0으로 설정
                totalBrainPSValue = 0.0;                                // 총 뇌파 값 새로 저장
                deltaPSValue = 0.0;                                     // 총 Theta 값 새로 저장

            } else {
                feedbackElapsedTime++;                                 // 경과시간이 피드백 주기보다 작으면, +1
            }
        }
    }

    public void freqFBProcess(int typeOfSleepStage, double expValue) {

        if ( typeOfSleepStage == 1 && soundDelay >= 60000 ) {          // 잠을 자는 상태이고, 딜레이가 60초가 넘으면
            txtMonitoring.append("소리 간격 피드백이 중단 되었습니다.\n");
            txtBTStatus.removeCallbacks(soundRunnable);
        } else {

            if (!txtBTStatus.getHandler().hasMessages(0)) {
                txtBTStatus.post(soundRunnable);
                txtMonitoring.append("중단됐던 소리 간격 피드백을 다시 시작합니다.\n");
            }

            if (feedbackElapsedTime >= feedbackChangeTime) {         // 피드백 주기가 될 때까지 기다렸다가 딜레이 조절하기
                feedbackElapsedTime = 0;                               // 경과 시간을 다시 0으로 설정

                if (typeOfSleepStage == 0) {                            // Wake 프로세스이므로 딜레이 줄이기(빠르게)
                    soundDelay -= 500;                                   // 0.5초씩 딜레이 감소
                    if (soundDelay <= 5000L) {                            // 5초 이하로 딜레이가 떨어지면
                        soundDelay = 5000L;                               // 5초로 고정
                        rgSoundSpeed.check(R.id.rbtnSet50);            // 5초로 라디오 버튼 세팅
                    }
                } else {                                                  // Sleep 프로세스이므로 딜레이 늘이기(느리게)
                    soundDelay += 500;                                   // 0.5초씩 딜레이 증가
                    if (soundDelay >= 60000)                            // 피드백 주기가 60초 이상이면
                        soundDelay = 60000;                             // 60초로 고정
                }

                txtMonitoring.append("현재 소리 간격은 " + soundDelay + "입니다.\n");
                soundRunnable.setInterval(soundDelay);                                          // 바로 소리 재생 딜레이로 반영

                if (switchFileRecording.isChecked()) {
                    msg = Message.obtain(readThread.mBackHandler, 3);                        // 파일로 피드백 이벤트 기록
                    msg.what = 3;

                    // 기록할 로그 메시지: 예측모델 종류, 피드백 타입, 피드백 반영 시간, 볼륨 크기, 물방울 소리 간격, Wake/Sleep 확률값
                    msg.obj = new double[]{modelSpinner.getSelectedItemPosition(), typeOfFB, feedbackChangeTime, volumeLevel, soundDelay, expValue};
                    readThread.mBackHandler.sendMessage(msg);
                }

            } else {
                feedbackElapsedTime++;                                 // 경과시간이 피드백 주기보다 작으면, +1
            }
        }
    }

    public TimerTask timerTaskMaker() {

        TimerTask runFFT = new TimerTask() {

            public void run() {

                // 61초면 맨 앞의 센서 데이터 갯수를 가져와 해당 센서 데이터 list에서 그 갯수만큼 지우기
                if (sensorDataCnt.size() == ANALYSIS_SECOND) {
                    lock.lock();
                    int removeDatasize = sensorDataCnt.get(0);
                    sensorDataCnt.remove(0);
                    sensorData.subList(0,removeDatasize).clear();
                    lock.unlock();
                }
                sensorDataCnt.add(inputCount);                           // 1초 동안 읽어온 데이터의 갯수 저장

                secCount++;

                if (secCount >= ANALYSIS_SECOND) {

                    if (bt.getServiceState() == BluetoothState.STATE_CONNECTED) {

                        // ArrayList<Double>을 double 형태로 변환하여 저장
                        fftLChInputs = ArrayUtils.toPrimitive(sensorData.toArray(new Double[sensorData.size()]));

                        DoubleFFT_1D fft_LCh1D = new DoubleFFT_1D(fftLChInputs.length);
                        double[] fft_LCh = new double[fftLChInputs.length * 2];

                        System.arraycopy(fftLChInputs, 0, fft_LCh, 0, fftLChInputs.length);
                        fft_LCh1D.realForwardFull(fft_LCh);

                        double realValue_l, imgValue_l, psValue_l;
                        double [] delta = new double[230];      // (int) ((3.99-0.21)/PS_HZ)
                        double [] theta = new double[250];
                        double [] alpha = new double[310];
                        double [] beta = new double[1040];
                        int delta_cnt = 0, theta_cnt = 0, alpha_cnt = 0, beta_cnt = 0;

                        for (int i = 2; i < fft_LCh.length - 1; i += 2) {
                            realValue_l = fft_LCh[i];
                            imgValue_l = fft_LCh[i + 1];

                            psValue_l = Math.sqrt(Math.pow(realValue_l, 2) + Math.pow(imgValue_l, 2));

                            if (i >= (int)(0.21 / PS_HZ  * 2) && i < (int)(3.99 / PS_HZ * 2) ) {		    // Delta 영역: 0.21Hz ~ 3.99Hz
                                delta[delta_cnt++] = psValue_l;
                            } else if (i >= (int)(3.99 / PS_HZ * 2) && i < (int)(7.99 / PS_HZ * 2)) {	    // Theta 영역: 3.99Hz ~ 7.99Hz
                                theta[theta_cnt++] = psValue_l;
                            } else if (i >= (int)(7.99 / PS_HZ * 2) && i < (int)(12.99 / PS_HZ * 2)) {      // Alpha 영역: 7.99Hz ~ 12.99Hz
                                alpha[alpha_cnt++] = psValue_l;
                            } else if (i >= (int)(12.99 / PS_HZ * 2) && i < (int)(30 / PS_HZ * 2)) {		// Beta 영역: 12.99Hz ~ 30Hz
                                beta[beta_cnt++] = psValue_l;
                            }
                        }

                        double[] result = calcMetrics(delta, theta, alpha, beta);
                        Log.i("Power Spectrum result", "delta=" + result[0] + ", theta=" + result[2] + ", alpha=" + result[4] + ", beta=" + result[6]);

                        msg = Message.obtain();
                        msg.what = 2;
                        msg.arg1 = secCount;
                        msg.arg2 = inputCount;
                        msg.obj = result;
                        mHandler.sendMessage(msg);
                    }
                } else {                                                                            // 30초가 안되면 그냥 데이터 수집용 메시지만 보여주기
                    if (msg == null)
                        msg = new Message();
                    else
                        msg = Message.obtain();
                    msg.what = 3;
                    msg.arg1 = secCount;
                    msg.arg2 = inputCount;
                    mHandler.sendMessage(msg);
                }
            }

            public double [] calcMetrics(double [] delta, double [] theta, double [] alpha, double [] beta) {

                double delta_mean = getMean(delta);
                double theta_mean = getMean(theta);
                double alpha_mean = getMean(alpha);
                double beta_mean = getMean(beta);

                double delta_stdev = getStd(delta, delta_mean);
                double theta_stdev = getStd(theta, theta_mean);
                double alpha_stdev = getStd(alpha, alpha_mean);
                double beta_stdev = getStd(beta, beta_mean);

                double alphaBytheta = alpha_mean / theta_mean;
                double alphaBydelta = alpha_mean / delta_mean;
                double thetaBydelta = theta_mean / delta_mean;
                double betaBydelta = beta_mean / delta_mean;

                double [] result = {delta_mean, delta_stdev, theta_mean, theta_stdev,
                                     alpha_mean, alpha_stdev, beta_mean, beta_stdev,
                                     alphaBytheta, alphaBydelta, thetaBydelta, betaBydelta};
                return result;
            }
        };

        return runFFT;
    }

    public static double getMean(double [] array) {
        double totalSum = 0.0;
        int original_length = array.length;

        for (int i = 0 ; i < array.length ; i++) {
            if (array[i] == 0.0) original_length -= 1;
            totalSum += array[i];
        }

        return totalSum/array.length;
    }

    public static double getStd(double [] array, double mean) {
        double sum = 0.0;
        double sd = 0.0;
        double diff;

        int original_length = array.length;

        for (int i = 0 ; i < array.length ; i++) {
            if (array[i] == 0.0) {
                original_length -= 1;
            } else {
                diff = array[i] - mean;
                sum += diff * diff;
            }
        }

        sd = Math.sqrt(sum / array.length);

        return sd;
    }

    public void onDestroy() {
        super.onDestroy();

        if (bt.getServiceState() == BluetoothState.STATE_CONNECTED) {
            bt.stopService();
        }
        bt = null;

        if (readThread != null) {
            readThread.closeFileStream();                               // 기록 중인 파일 스트림 중지
            readThread.interrupt();                                 // 센서 데이터 리딩 정지
            readThread = null;
        }

        if (fftTimer != null) {
            fftTimer.cancel();
            fftTimer = null;
        }

        if (runFFTTask != null) {
            runFFTTask.cancel();
            runFFTTask = null;
        }

        if (soundRunnable != null) {
            txtMonitoring.removeCallbacks(soundRunnable);
            soundRunnable.destroy();
            soundRunnable = null;
        }
    }

    public double[] toDoublePrimitive(ArrayList<Double> doubles) {
        double[] target = new double[doubles.size()];
        for (int i = 0 ; i < target.length ; i++)
            target[i] = doubles.get(i);

        return target;
    }
}