package bluetoothspp.akexorcist.app.DataProcessing;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.telephony.TelephonyManager;
import android.widget.ProgressBar;

import org.apache.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;

import bluetoothspp.akexorcist.app.NeuroAnalyzer.NeuroFeedbackActivity;
import de.mindpipe.android.logging.log4j.LogConfigurator;

import static android.content.Context.MODE_PRIVATE;

/**
 * Created by jungbini on 2018-03-28.
 */

public class DataIOThread extends Thread {

    private Handler mMainHandler;
    public Handler mBackHandler;
    private Logger mLogger = Logger.getLogger(DataIOThread.class);

    private SharedPreferences pref;

    private long now = 0;
    private String str;
    private byte[] data;
    private boolean sync_after = false;
    private byte packet_tx_index;             // 패킷 수신 인덱스
    private byte data_prev = 0;                // 직전 값

    private byte PUD0 = 0;
    private byte CRD_PUD2_PCDT = 0;
    private byte PUD1 = 0;
    private byte packetCount = 0;
    private byte packetCyclicData = 0;
    private byte psd_idx = 0;

    private byte[] packetStreamData = new byte[4];

    private Message retmsg;

    private String ext = Environment.getExternalStorageState();     // 외부 저장소 상태
    private int hourCount;
    private File rawDataPath;
    private File rawDataFile;                                       // 파일 입출력 변수
    private File fbEventFile;                                       // 피드백 이벤트 기록 파일
    private String mSdPath;
    private FileOutputStream datafos, eventfos;
    private int writingCount;                                       // 한 파일당 저장되는 라인수 카운터
    private DecimalFormat df = new DecimalFormat("#.###");

    private SimpleDateFormat fullDateFormat = new SimpleDateFormat("MM-dd-yyyy HH:mm:ss");
    private SimpleDateFormat simpleDateFormat = new SimpleDateFormat("MMddyyyy");

    private String UUID;

    public DataIOThread(Handler handler, Context context) {

        UUID = GetDevicesUUID(context);

        mMainHandler = handler;
        writingCount = 0;
        hourCount = 0;

        context.getApplicationContext();

        if(ext.equals(Environment.MEDIA_MOUNTED))
            mSdPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        else
            mSdPath = Environment.MEDIA_UNMOUNTED;

        rawDataPath = new File(mSdPath + "/neuro");
        if(!rawDataPath.exists()) {
            rawDataPath.mkdir();
        }

        LogConfigurator logConfigurator = new LogConfigurator();            // 로그 관련 설정
        logConfigurator.setFileName(mSdPath + "/neuro/logs/logFile.log");
        logConfigurator.configure();

    }

    public void closeFileStream() {
        try {
            if (datafos != null) {
                datafos.flush();
                datafos.close();
            }

            if (eventfos != null) {
                eventfos.flush();
                eventfos.close();
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    public void run() {

        Looper.prepare();

        mBackHandler = new Handler() {

            public void handleMessage(Message msg) {

                if (msg.what == 1) {

                    data = (byte[]) msg.obj;
                    sync_after = false;
                    packet_tx_index = 0;       // 패킷 수신 인덱스
                    data_prev = 0;             // 직전 값

                    PUD0 = 0;
                    CRD_PUD2_PCDT = 0;
                    PUD1 = 0;
                    packetCount = 0;
                    packetCyclicData = 0;
                    psd_idx = 0;

                    packetStreamData = new byte[4];

                    // byte 값을 읽을 때, unsigned 값을 받아와야 하므로 &0xff 를 넣어 0~255 범위로 바꿔줘야 한다
                    // Java에서는 unsigned byte 변수는 없다
                    for (int i = 0; i < data.length; i++) {
                        byte data_crnt = data[i];

                        if ((data_prev & 0xff) == 255 && (data_crnt & 0xff) == 254) {     // 싱크 지점을 찾았다
                            sync_after = true;
                            packet_tx_index = 0;                        // 패킷 tx 인덱스를 0으로 초기화
                        }

                        data_prev = data_crnt;                          // 현재 값을 직전 값으로 저장

                        if (sync_after) {                                // 싱크가 발견된 이후에만 실행

                            // tx 인덱스를 1 증가, 254가 발견된 지점이 1
                            // 시리얼로 1바이트 수신될 때 마다 1씩 증가
                            packet_tx_index++;

                            // tx 인덱스가 2 이상부터 데이터 바이트이므로 2 이상부터만 수행
                            if ((packet_tx_index & 0xff) > 1) {                        // packet_tx_index 가 0부터 시작인데 0xff가 필요할까?? 점검...

                                if (packet_tx_index == 2)        // PUD0 확보
                                    PUD0 = data_crnt;
                                else if (packet_tx_index == 3)   // CRD, PUD2, PCD Type 확보
                                    CRD_PUD2_PCDT = data_crnt;
                                else if (packet_tx_index == 4)   // PC 확보
                                    packetCount = data_crnt;
                                else if (packet_tx_index == 5)   // PUD1 확보
                                    PUD1 = data_crnt;
                                else if (packet_tx_index == 6)   // PCD(패킷순환데이터) 확보
                                    packetCyclicData = data_crnt;
                                else if (packet_tx_index > 6) {   // index가 7이상이면 스트림데이터(파형데이터)가 1바이트씩
                                    psd_idx = (byte) (packet_tx_index - 7);       // 스트림 데이터의 배열 인덱스 만들기
                                    packetStreamData[psd_idx] = data_crnt;      // 채널1(상하), 채널2(상하) 4바이트 순차적 저장

                                    if (packet_tx_index == 10) {                // 10 = 채널수(2) x 2(바이트) x 샘플링수(1) + 헤더 바이트 수(6)
                                        sync_after = false;

                                        // High bit([0][2])는 7bit이므로 0x7f(01111111)을 AND 연산한다.
                                        int channel1_value = ((packetStreamData[0] & 0x7f) * 256) + (packetStreamData[1] & 0xff);
                                        int channel2_value = ((packetStreamData[2] & 0x7f) * 256) + (packetStreamData[3] & 0xff);

                                        switch (msg.what) {

                                            // 차트 출력 모드
                                            case 0:
                                                retmsg = mMainHandler.obtainMessage();
                                                retmsg.what = 0;
                                                retmsg.arg1 = channel1_value;
                                                retmsg.arg2 = channel2_value;
                                                try {
                                                    mMainHandler.sendMessage(retmsg);
                                                } catch (IllegalStateException ise) {
                                                    mLogger.error(Arrays.toString(ise.getStackTrace()));
                                                }
                                                break;

                                            // FFT 변환 모드
                                            case 1:
                                                retmsg = mMainHandler.obtainMessage();
                                                retmsg.what = 1;
                                                retmsg.arg1 = channel1_value;
                                                retmsg.arg2 = channel2_value;
                                                try {
                                                    mMainHandler.sendMessage(retmsg);
                                                } catch (IllegalStateException ise) {
                                                    mLogger.error(Arrays.toString(ise.getStackTrace()));
                                                }
                                                break;

                                            case 2:
                                                now = System.currentTimeMillis();                          // 현재시간을 msec 으로 구한다.
                                                rawDataFile = new File(mSdPath + "/neuro/RAWdata_" + simpleDateFormat.format(now) + '_' + hourCount + ".txt");

                                                try {
                                                    datafos = new FileOutputStream(rawDataFile, true);
                                                    Thread.sleep(300);
                                                } catch (FileNotFoundException fnfe) {
                                                    fnfe.printStackTrace();
                                                } catch (InterruptedException ie) {
                                                    ie.printStackTrace();
                                                }

                                                //                                            str = "" + now + '|' + channel1_value + "|" + channel2_value + '\n';
                                                str = "" + channel1_value + '\n';

                                                // 파일로 기록하기
                                                try {
                                                    if (writingCount > 450000) {                      // 45만줄이면 약 30분 가량의 데이터 {
                                                        datafos.close();

                                                        hourCount++;                                    // 1시간이 지남

                                                        rawDataFile = new File(mSdPath + "/neuro/RAWdata_"  + simpleDateFormat.format(now) + '_' + hourCount + ".txt");
                                                        try {
                                                            datafos = new FileOutputStream(rawDataFile, true);
                                                            Thread.sleep(300);
                                                        } catch (FileNotFoundException fnfe) {
                                                            fnfe.printStackTrace();
                                                        } catch (InterruptedException ie) {
                                                            ie.printStackTrace();
                                                        } finally {
                                                            writingCount = 0;
                                                        }
                                                    } else {
                                                        writingCount++;
                                                        datafos.write(str.getBytes());
                                                    }
                                                } catch (IOException ioe) {
                                                    ioe.printStackTrace();
                                                }

                                                break;
                                        }

                                    }
                                }
                            }
                        }
                    }

                } else if (msg.what == 3){

                    now = System.currentTimeMillis();                          // 현재시간을 msec 으로 구한다.
                    Date resultdate = new Date(now);

                    rawDataFile = new File(mSdPath + "/neuro/NeuroFeedback_" + UUID.substring(UUID.lastIndexOf('-')+1) + '_' + fullDateFormat.format(resultdate) + ".txt");

                    try {
                        if (datafos == null)
                            datafos = new FileOutputStream(rawDataFile, true);

                        String finalResult = "";
                        if (msg.obj != null) {
                            // 보낸 로그 메시지: 예측모델 종류, 피드백 타입, 피드백 반영 시간, 볼륨 크기, 물방울 소리 간격, 회기식 기울기, Delta파 비율, Sleep/Wake 확률, delta PS 값, theta PS 값, alpha PS 값, beta PS 값
                            double[] receivedLogMsg = (double[]) msg.obj;

                            // 기록할 로그: UUID, 날짜, [보낸 로그 메시지]
                            finalResult = UUID.substring(UUID.lastIndexOf('-')+1) + ',' + fullDateFormat.format(resultdate) + ',' +
                                    df.format(receivedLogMsg[0]) + ',' + df.format(receivedLogMsg[1]) + ',' + df.format(receivedLogMsg[2]) + ',' +
                                    df.format(receivedLogMsg[3]) + ',' + df.format(receivedLogMsg[4]) + ',' + receivedLogMsg[5] + ',' +
                                    df.format(receivedLogMsg[6]) + ',' + (((receivedLogMsg[7] * 1000000) > 50.0) ? "Wake" : "Sleep") + ',' +
                                    df.format(receivedLogMsg[8]) + ',' + df.format(receivedLogMsg[9]) + ',' +
                                    df.format(receivedLogMsg[10]) + df.format(receivedLogMsg[11]) + "\n";
                        } else {
                            finalResult = fullDateFormat.format(resultdate) + ",0,0,0,0,0,0,0,unknown,0,0,0,0\n";
                        }

                        datafos.write(finalResult.getBytes());
                        datafos.flush();

                    } catch (FileNotFoundException fnfe) {
                        fnfe.printStackTrace();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                } else if (msg.what == 4) {                                 // 피드백 이벤트 로그 기록하기

                    now = System.currentTimeMillis();                          // 현재시간을 msec 으로 구한다.
                    Date resultdate = new Date(now);

                    try {
                        fbEventFile = new File(mSdPath + "/neuro/FeedbackEvent_" + simpleDateFormat.format(now) + '_' + hourCount + ".txt");
                        eventfos = new FileOutputStream(fbEventFile, true);
                        Thread.sleep(300);

                        if (writingCount > 450000) {                      // 45만줄이면 약 30분 가량의 데이터 {
                            if (eventfos != null)
                                eventfos.close();
                            hourCount++;                                    // 30분이 지남

                            fbEventFile = new File(mSdPath + "/neuro/FeedbackEvent_" + simpleDateFormat.format(now) + '_' + hourCount + ".txt");
                            eventfos = new FileOutputStream(fbEventFile, true);
                            Thread.sleep(300);

                        } else {

                            String finalResult = "";
                            if (msg.obj != null) {
                                double[] tmpResults = (double[]) msg.obj;
                                finalResult = fullDateFormat.format(resultdate) + ',' + df.format(tmpResults[0]) + ',' + df.format(tmpResults[1]) + ',' + df.format(tmpResults[2]) + ',' +
                                        df.format(tmpResults[3]) + ',' + df.format(tmpResults[4]) + ',' + df.format(tmpResults[5]) + "\n";
                            } else {
                                finalResult = fullDateFormat.format(resultdate) + ",0,0,0,0,0,0\n";
                            }

                            if (eventfos != null) {
                                eventfos.write(finalResult.getBytes());
                            }
                        }

                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    } catch (InterruptedException ie) {
                        ie.printStackTrace();
                    }

                }

            }
        };

        Looper.loop();
    }

    private String GetDevicesUUID(Context mContext){
        final TelephonyManager tm = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        final String tmDevice, tmSerial, androidId;
        tmDevice = "" + tm.getDeviceId();
        tmSerial = "" + tm.getSimSerialNumber();
        androidId = "" + android.provider.Settings.Secure.getString(mContext.getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
        java.util.UUID deviceUuid = new UUID(androidId.hashCode(), ((long)tmDevice.hashCode() << 32) | tmSerial.hashCode());
        String deviceId = deviceUuid.toString();
        return deviceId;
    }
}
