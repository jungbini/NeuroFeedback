package bluetoothspp.akexorcist.app.NeuroAnalyzer;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import app.akexorcist.bluetotohspp.library.BluetoothSPP;
import app.akexorcist.bluetotohspp.library.BluetoothState;
import app.akexorcist.bluetotohspp.library.DeviceList;
import app.akexorcist.bluetotohspp.library.BluetoothSPP.BluetoothConnectionListener;
import app.akexorcist.bluetotohspp.library.BluetoothSPP.OnDataReceivedListener;
import bluetoothspp.akexorcist.app.DataProcessing.DataReadingThread;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;

import org.jtransforms.fft.DoubleFFT_1D;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class TestActivity extends Activity implements OnClickListener {

    protected String [] labels  = new String[] {"델타파", "세타파", "알파파", "베타파", "감마파"};

    TextView textStatus;              // UI 변수
    Button btnConnect;
    LineChart ch1LineChart, ch2LineChart;
    BarChart ch1BarChart, ch2BarChart;
    LineDataSet ch1DataSet, ch2DataSet;
    XAxis xLineChartAxis, xBarChartAxis;
    YAxis ch1YLAxis, ch1YRAxis, yBarChartAxis;
    RadioGroup rBtnGroup;

    BluetoothSPP bt;                                // BluetoothSPP 변수

    DataReadingThread readThread;                  // 센서값 읽기 쓰레드 변수
    Message msg;

    int msgType;                                   // 데이터 측정을 수행할 메시지 타입 (0=raw 데이터, 1=FFT 수행 후 파워 스팩트럼)
    int XAxisValueCount;                          // 센서 그래프 X 좌표 카운터
    static int ch1RawData, ch2RawData;             // Raw 데이터 그래프를 그리기 위한 임시 변수

    static double fftLChInputs [], fftRChInputs [];         // FFT 계산을 위한 Channel raw data 배열 변수
    DoubleFFT_1D fft_LCh1D, fft_RCh1D;
    int inputCount;                                         // 현재 쌓인 raw data의 갯수

    TimerTask runFFTTask, drawRawGraphTask;
    Timer fftTimer, rawTimer;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_test);
        rBtnGroup = (RadioGroup) findViewById(R.id.radioGroup);
        textStatus = (TextView)findViewById(R.id.textStatus);
        btnConnect = (Button) findViewById(R.id.btnBTConnect);
        ch1LineChart = (LineChart) findViewById(R.id.chart1);
        ch2LineChart = (LineChart) findViewById(R.id.chart2);
        ch1BarChart = (BarChart) findViewById(R.id.barchart1);
        ch2BarChart = (BarChart) findViewById(R.id.barchart2);
        btnConnect.setOnClickListener(this);

        XAxisValueCount = 0;
        inputCount = 0;
        msgType = 0;

        fftLChInputs = new double[500];                                        // 2초 동안의 데이터만 모을 것이므로, 500을 넘지 않음
        fftRChInputs = new double[500];

        rawTimer = new Timer();                                             // 0.1초마다 raw 데이터를 그리는 타이머
        drawRawGraphTask = drawRawGraphTaskMaker();
        rawTimer.schedule(drawRawGraphTask, 100, 100);

        fftTimer = new Timer();                                             // 2초마다 FFT 구하는 타이머
        runFFTTask = timerTaskMaker();
        fftTimer.schedule(runFFTTask, 2000L, 2000L);

        bt = new BluetoothSPP(this);                                // Bluetooth 초기화
        initChart(2);                                                   // Line, Bar 차트 초기화

        readThread = new DataReadingThread(mHandler, getApplicationContext());                      // Data reading 쓰레드 시작
        readThread.setDaemon(true);
        readThread.start();

        bt.setOnDataReceivedListener(new OnDataReceivedListener() {          // 블루투스 데이터 수신 리스너
            public void onDataReceived(byte[] data, String message) {
                msg = new Message();
                msg.what = msgType;
                msg.obj = data;
                readThread.mBackHandler.sendMessage(msg);
            }
        });

        rBtnGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {

                switch (checkedId) {
                    case R.id.rBtnRawData:
                        msgType = 0;

                        initChart(1);                               // Bar 차트 초기화

                        ch1BarChart.setVisibility(View.INVISIBLE);
                        ch2BarChart.setVisibility(View.INVISIBLE);
                        ch1LineChart.setVisibility(View.VISIBLE);
                        ch2LineChart.setVisibility(View.VISIBLE);
                        break;

                    case R.id.rBtnPSGraph:
                        msgType = 1;

                        XAxisValueCount = 0;                // Raw 그래프 차트 x좌표 초기화

                        initChart(0);                               // Line 차트 초기화

                        ch1LineChart.setVisibility(View.INVISIBLE);
                        ch2LineChart.setVisibility(View.INVISIBLE);
                        ch1BarChart.setVisibility(View.VISIBLE);
                        ch2BarChart.setVisibility(View.VISIBLE);
                        break;

                    case R.id.rBtnWriteFile:
                        msgType = 2;

                        XAxisValueCount = 0;                // Raw 그래프 차트 x좌표 초기화
                        initChart(2);                    // Line, Bar 차트 초기화

                        ch1LineChart.setVisibility(View.INVISIBLE);
                        ch2LineChart.setVisibility(View.INVISIBLE);
                        ch1BarChart.setVisibility(View.INVISIBLE);
                        ch2BarChart.setVisibility(View.INVISIBLE);

                        break;
                }
            }
        });

        bt.setBluetoothConnectionListener(new BluetoothConnectionListener() {
            public void onDeviceDisconnected() {
                textStatus.setText("블루투스 장치 연결이 끊겼습니다.");
            }
            public void onDeviceConnectionFailed() {
                textStatus.setText("블루투스 장치에 연결 실패! Orz...");
            }
            public void onDeviceConnected(String name, String address) {
                textStatus.setText(name + "에 연결되었습니다.");
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
            if(resultCode == Activity.RESULT_OK)
                bt.connect(data);
                btnConnect.setText("연결 해제");               // 연결이 성공하면, 버튼 text 바꾸기
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

                Button btn = (Button) findViewById(R.id.btnBTConnect);

                if (btn.getText().equals("연결")) {

                    // 블루투스 수신 시작
                    bt.setDeviceTarget(BluetoothState.DEVICE_OTHER);
                    intent = new Intent(getApplicationContext(), DeviceList.class);
                    startActivityForResult(intent, BluetoothState.REQUEST_CONNECT_DEVICE);

                } else {                                      // 블루투스가 동작 중이고, '블루투스 연결 해제' 버튼을 누르면, 서비스를 종료
                    bt.stopService();
                    btn.setText("연결");

                    readThread.interrupt();

                    XAxisValueCount = 0;      // x 좌표 시작값 초기화
                    initChart(2);           // Line, Bar 차트 초기화
                }

                break;
        }
    }

    private void initChart(int flag) {

        if (flag == 0) {
            setLineCharts(ch1LineChart, "Left");                      // 라인 차트 초기화
            setLineCharts(ch2LineChart, "Right");                     // 라인 차트 초기화
        } else if (flag == 1) {
            setBarChart(ch1BarChart);                                            // 바 차트 초기화
            setBarChart(ch2BarChart);                                            // 바 차트 초기화
        } else if (flag == 2) {
            setLineCharts(ch1LineChart, "Left");
            setLineCharts(ch2LineChart, "Right");
            setBarChart(ch1BarChart);
            setBarChart(ch2BarChart);
        }
    }

    private void setBarChart(BarChart barChart) {
        barChart.setDrawBarShadow(false);
        barChart.setDrawValueAboveBar(true);
        barChart.getDescription().setEnabled(false);
        barChart.setPinchZoom(false);
        barChart.setDrawGridBackground(false);

        xBarChartAxis = barChart.getXAxis();
        xBarChartAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xBarChartAxis.setCenterAxisLabels(false);
        xBarChartAxis.setValueFormatter(new MyXAxisValueFormatter(labels));
        xBarChartAxis.setGranularity(1f);

        yBarChartAxis = barChart.getAxisRight();
        yBarChartAxis.setDrawLabels(false);
        yBarChartAxis.setDrawAxisLine(false);
        yBarChartAxis.setDrawGridLines(false);
    }

    private void setLineCharts(LineChart lChart, String chName) {

        List<Entry> entries = new ArrayList<>();
        entries.add(new Entry(0, 0));

        if (chName.equals("Left")) {
            ch1DataSet = new LineDataSet(entries, "Channel(" + chName + ")");
            ch1DataSet.setCircleRadius(4);
            ch1DataSet.setCircleColor(Color.parseColor("#FFA1B4DC"));
            ch1DataSet.setCircleColorHole(Color.BLUE);
            ch1DataSet.setColor(Color.parseColor("#FFA1B4DC"));
            ch1DataSet.setDrawHorizontalHighlightIndicator(false);
            ch1DataSet.setDrawHighlightIndicators(false);
            ch1DataSet.setDrawValues(false);

            LineData lineData = new LineData(ch1DataSet);
            lChart.setData(lineData);
        }
        else {
            ch2DataSet = new LineDataSet(entries, "Channel(" + chName + ")");
            ch2DataSet.setCircleRadius(4);
            ch2DataSet.setCircleColor(Color.parseColor("#FFA1B4DC"));
            ch2DataSet.setCircleColorHole(Color.BLUE);
            ch2DataSet.setColor(Color.parseColor("#FFA1B4DC"));
            ch2DataSet.setDrawHorizontalHighlightIndicator(false);
            ch2DataSet.setDrawHighlightIndicators(false);
            ch2DataSet.setDrawValues(false);

            LineData lineData = new LineData(ch2DataSet);
            lChart.setData(lineData);
        }

        xLineChartAxis = lChart.getXAxis();
        xLineChartAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xLineChartAxis.enableGridDashedLine(8, 24, 0);

        ch1YLAxis = lChart.getAxisLeft();
        ch1YLAxis.setAxisMinimum(10000f);
        ch1YLAxis.setAxisMaximum(23000f);                   // max = 32767

        ch1YRAxis = lChart.getAxisRight();
        ch1YRAxis.setDrawLabels(false);
        ch1YRAxis.setDrawAxisLine(false);
        ch1YRAxis.setDrawGridLines(false);

        lChart.setDoubleTapToZoomEnabled(false);
        lChart.setDrawGridBackground(false);
        lChart.notifyDataSetChanged();
        lChart.invalidate();
    }

    Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0:                                                 // 0번: 데이터를 받아와 차트를 그려줌

                    ch1RawData = msg.arg1;
                    ch2RawData = msg.arg2;

                    break;

                case 1:

                    if (inputCount < 500) {
                        fftLChInputs[inputCount] = msg.arg1;                // Left channel raw data 누적
                        fftRChInputs[inputCount] = msg.arg2;                // Right channel raw data 누적

                        inputCount++;                                               // 누적 카운트 +1
                    }

                    break;

                case 2:

                    XAxisValueCount++;

                    if (XAxisValueCount > 60) {                     // 60개 이상의 데이터를 받으면, 첫 데이터를 하나씩 지우기
                        ch1DataSet.removeFirst();
                        ch2DataSet.removeFirst();
                    }

                    ch1DataSet.addEntry(new Entry(XAxisValueCount, msg.arg1));
                    ch1LineChart.setData(new LineData(ch1DataSet));
                    ch1LineChart.notifyDataSetChanged();
                    ch1LineChart.invalidate();

                    ch2DataSet.addEntry(new Entry(XAxisValueCount, msg.arg2));
                    ch2LineChart.setData(new LineData(ch2DataSet));
                    ch2LineChart.notifyDataSetChanged();
                    ch2LineChart.invalidate();

                case 3:

                    float [] result = (float[]) msg.obj;

                    if (result != null) {
                        ArrayList<BarEntry> entries = new ArrayList<>();
                        for (int i = 0; i < 5; i++)
                            entries.add(new BarEntry(i, result[i]));

                        BarDataSet bardataset = new BarDataSet(entries, "PS value");
                        bardataset.setColors(ColorTemplate.COLORFUL_COLORS);
                        BarData data = new BarData(bardataset);

                        ch1BarChart.setData(data);
                        ch1BarChart.notifyDataSetChanged();
                        ch1BarChart.invalidate();

                        entries = new ArrayList<>();
                        for (int i = 5; i < 10; i++)
                            entries.add(new BarEntry(i - 5, result[i]));

                        bardataset = new BarDataSet(entries, "PS value");
                        bardataset.setColors(ColorTemplate.COLORFUL_COLORS);
                        BarData data2 = new BarData(bardataset);

                        ch2BarChart.setData(data2);
                        ch2BarChart.notifyDataSetChanged();
                        ch2BarChart.invalidate();
                    }

                    break;
            }
        }
    };

    public TimerTask timerTaskMaker() {

        TimerTask runFFT = new TimerTask() {

            public void run() {

                if (bt.getServiceState() == BluetoothState.STATE_CONNECTED) {

                    if (inputCount > 0) {

                        fft_LCh1D = new DoubleFFT_1D(inputCount);
                        fft_RCh1D = new DoubleFFT_1D(inputCount);

                        double[] fft_LCh = new double[inputCount * 2];
                        double[] fft_RCh = new double[inputCount * 2];

                        System.arraycopy(fftLChInputs, 0, fft_LCh, 0, inputCount);
                        System.arraycopy(fftRChInputs, 0, fft_RCh, 0, inputCount);

                        fft_LCh1D.realForwardFull(fft_LCh);
                        fft_RCh1D.realForwardFull(fft_RCh);

                        double realValue_l, imagValue_l, psValue_l;
                        double realValue_r, imagValue_r, psValue_r;
                        float delta_l = 0, theta_l = 0, alpha_l = 0, beta_l = 0, gamma_l = 0;
                        float delta_r = 0, theta_r = 0, alpha_r = 0, beta_r = 0, gamma_r = 0;

                        for (int i = 2; i < fft_LCh.length - 1; i += 2) {
                            realValue_l = fft_LCh[i];
                            imagValue_l = fft_LCh[i + 1];
                            realValue_r = fft_RCh[i];
                            imagValue_r = fft_RCh[i + 1];

                            psValue_l = Math.sqrt(Math.pow(realValue_l, 2) + Math.pow(imagValue_l, 2));
                            psValue_r = Math.sqrt(Math.pow(realValue_r, 2) + Math.pow(imagValue_r, 2));

                            if (i >= 2 && i < 10) {
                                delta_l += psValue_l;
                                delta_r += psValue_r;
                            } else if (i >= 10 && i < 18) {
                                theta_l += psValue_l;
                                theta_r += psValue_r;
                            } else if (i >= 18 && i < 28) {
                                alpha_l += psValue_l;
                                alpha_r += psValue_r;
                            } else if (i >= 28 && i < 62) {
                                beta_l += psValue_l;
                                beta_r += psValue_r;
                            } else if (i >= 62 && i < 102) {
                                gamma_l += psValue_l;
                                gamma_r += psValue_r;
                            }
                        }

                        float[] result = new float[]{delta_l, theta_l, alpha_l, beta_l, gamma_l,
                                delta_r, theta_r, alpha_r, beta_r, gamma_r};

                        Message msg = Message.obtain();
                        msg.what = 3;
                        msg.obj = result;

                        mHandler.sendMessage(msg);

                        inputCount = 0;
                    }
                }
            }
        };

        return runFFT;
    }

    public TimerTask drawRawGraphTaskMaker() {

        TimerTask drawDataTask = new TimerTask() {

            public void run() {

                if (bt.getServiceState() == BluetoothState.STATE_CONNECTED) {
                    Message msg = Message.obtain();
                    msg.what = 2;
                    msg.arg1 = ch1RawData;
                    msg.arg2 = ch2RawData;

                    mHandler.sendMessage(msg);
                }
            }
        };

        return drawDataTask;

    }

    public void onDestroy() {
        super.onDestroy();
        bt.stopService();
        readThread.mBackHandler.getLooper().quitSafely();
        readThread.interrupt();
    }

}
