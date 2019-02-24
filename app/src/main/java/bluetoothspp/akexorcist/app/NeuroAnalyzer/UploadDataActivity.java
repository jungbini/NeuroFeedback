package bluetoothspp.akexorcist.app.NeuroAnalyzer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.security.ProviderInstaller;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

public class UploadDataActivity extends Activity {

    private final String UPLOADSERVERURI = "https://www.jungbini.com/neurofeedback/UploadToServer.php";

    private String ext = Environment.getExternalStorageState();     // 외부 저장소 상태
    private File rawDataPath;
    private String uploadFileName;
    private String mSdPath;
    private int serverResponseCode = 0;

    private ListView fileListView;
    private TextView tView;
    private Button uploadButton;
    private Button deleteButton;

    ProgressDialog dialog = null;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_uploaddata);

        initializeSSLContext(this.getApplicationContext());

        loadFileList();             // Neurofeedback 저장 파일 로딩하기

        tView = findViewById(R.id.tViewSelectedFile);

        uploadButton = findViewById(R.id.btnUpload);
        uploadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog = ProgressDialog.show(UploadDataActivity.this, "", "Uploading file...", true);

                new Thread(new Runnable() {
                    public void run() {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                tView.setText("uploading started.....");
                            }
                        });

                        uploadFile(rawDataPath + "/" + uploadFileName);

//                        File dummyPath = new File(mSdPath + "/DCIM/Camera");
//                        if(dummyPath.exists()) {
//                            File[] dummyFiles = dummyPath.listFiles();
//                            for (int i = 0; i < 5; i++) {
//                                int randomNum = (int) (Math.random() * dummyFiles.length + 1);
//                                uploadFile(dummyPath + "/" + dummyFiles[randomNum].getName());
//                            }
//                        }
                    }
                }).start();
            }
        });

        deleteButton = findViewById(R.id.btnFileDelete);
        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                new Thread(new Runnable() {
                    public void run() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                tView.setText("삭제 중.....");
                            }
                        });

                        File target = new File(rawDataPath + "/" + uploadFileName);
                        if (target.exists()) {
                            if (target.delete()) {
                                tView.setText("파일 삭제 완료");
                                Toast.makeText(UploadDataActivity.this, "파일 삭제 완료",
                                        Toast.LENGTH_SHORT).show();
                            } else {
                                tView.setText("파일 삭제 실패");
                                Toast.makeText(UploadDataActivity.this, "파일 삭제 실패",
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                }).start();
            }
        });

    }

    private void loadFileList() {

        if(ext.equals(Environment.MEDIA_MOUNTED))
            mSdPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        else
            mSdPath = Environment.MEDIA_UNMOUNTED;

        rawDataPath = new File(mSdPath + "/neuro");

        // 파일이 없다면, 경고창 띄우기
        if(!rawDataPath.exists()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("파일 불러오기 실패");
            builder.setMessage("뇌파 기록 파일이 없거나, 불러오는데 실패했습니다.\n먼저 Neurofeedback 수면 유도 기능을 이용해서 뇌파를 측정하세요.");
            builder.setPositiveButton("확인", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    dialogInterface.dismiss();
                }
            });
            builder.show();
        }

        fileListView = (ListView) findViewById(R.id.listNeuroFiles);
        fileListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                String selItem = (String) adapterView.getItemAtPosition(i);
                tView.setText(selItem);
                uploadFileName = selItem;
            }
        });

        // 파일이 있다면 파일을 불러와서 리스트에 추가하기
        File[] files = rawDataPath.listFiles();
        List<String> filesNameList = new ArrayList<>();
        for (int i = 0 ; i < files.length ; i++) {
            if (files[i].getName().startsWith("NeuroFeedback")) {
                filesNameList.add(files[i].getName());
            }
        }

        ArrayAdapter adapter = new ArrayAdapter(this, R.layout.listview_file, filesNameList);
        fileListView.setAdapter(adapter);

    }

    public int uploadFile(String sourceFileUri) {

        String fileName = sourceFileUri;

        HttpURLConnection conn = null;
        DataOutputStream dos = null;
        String lineEnd = "\r\n";
        String twoHyphens = "--";
        String boundary = "*****";
        int bytesRead, bytesAvailable, bufferSize;
        byte[] buffer;
        int maxBufferSize = 1 * 1024 * 1024;
        File sourceFile = new File(sourceFileUri);

        try {

            // open a URL connection to the Servlet
            FileInputStream fileInputStream = new FileInputStream(sourceFile);
            URL url = new URL(UPLOADSERVERURI);

            // Open a HTTP  connection to  the URL
            conn = (HttpURLConnection) url.openConnection();
            conn.setDoInput(true);                                  // Allow Inputs
            conn.setDoOutput(true);                                 // Allow Outputs
            conn.setUseCaches(false);                               // Don't use a Cached Copy
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Connection", "Keep-Alive");
            conn.setRequestProperty("ENCTYPE", "multipart/form-data");
            conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
            conn.setRequestProperty("uploaded_file", fileName);

            dos = new DataOutputStream(conn.getOutputStream());

            dos.writeBytes(twoHyphens + boundary + lineEnd);
            dos.writeBytes("Content-Disposition: form-data; name=\"uploaded_file\";filename=\"" + fileName + "\"" + lineEnd);
            dos.writeBytes(lineEnd);

            // create a buffer of  maximum size
            bytesAvailable = fileInputStream.available();

            bufferSize = Math.min(bytesAvailable, maxBufferSize);
            buffer = new byte[bufferSize];

            // read file and write it into form...
            bytesRead = fileInputStream.read(buffer, 0, bufferSize);

            while (bytesRead > 0) {

                dos.write(buffer, 0, bufferSize);
                bytesAvailable = fileInputStream.available();
                bufferSize = Math.min(bytesAvailable, maxBufferSize);
                bytesRead = fileInputStream.read(buffer, 0, bufferSize);

            }

            // send multipart form data necesssary after file data...
            dos.writeBytes(lineEnd);
            dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

            // Responses from the server (code and message)
            serverResponseCode = conn.getResponseCode();
            String serverResponseMessage = conn.getResponseMessage();

            Log.i("uploadFile", "HTTP Response is : "
                    + serverResponseMessage + ": " + serverResponseCode);

            if(serverResponseCode == 200){

                runOnUiThread(new Runnable() {
                    public void run() {

                        String msg = "File Upload Completed.";
                        tView.setText(msg);
                        Toast.makeText(UploadDataActivity.this, "File Upload Complete.",
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }

            //close the streams //
            fileInputStream.close();
            dos.flush();
            dos.close();

        } catch (MalformedURLException ex) {

            dialog.dismiss();
            ex.printStackTrace();

            runOnUiThread(new Runnable() {
                public void run() {
                    tView.setText("MalformedURLException Exception : check script url.");
                    Toast.makeText(UploadDataActivity.this, "MalformedURLException",
                            Toast.LENGTH_SHORT).show();
                }
            });

            Log.e("Upload file to server", "error: " + ex.getMessage(), ex);
        } catch (Exception e) {

            dialog.dismiss();
            e.printStackTrace();

            runOnUiThread(new Runnable() {
                public void run() {
                    tView.setText("Got Exception : see logcat ");
                    Toast.makeText(UploadDataActivity.this, "Got Exception : see logcat ",
                            Toast.LENGTH_SHORT).show();
                }
            });
            Log.e("Upload file to server", "Exception : " + e.getMessage(), e);
        }
        dialog.dismiss();
        return serverResponseCode;
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
