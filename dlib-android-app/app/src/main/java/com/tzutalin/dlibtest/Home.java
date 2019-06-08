package com.tzutalin.dlibtest;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.CardView;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import hugo.weaving.DebugLog;

public class Home extends AppCompatActivity {
    GridLayout mainGrid;
    ImageButton connectionCheckButton;

    private static final int REQUEST_CODE_PERMISSION = 2;
    private long pressedTime;

    //  TCP연결 관련
    private Socket clientSocket;
    private BufferedReader socketIn;
    private PrintWriter socketOut;
    private int port = 7070;
    private String ip = "0.0.0.0";
    private MyHandler myHandler;
    private MyThread myThread;

    boolean isMenuOpen = false;
    LetterList letterList;
    Intent intentToPop1;

    // Storage Permissions
    private static String[] PERMISSIONS_REQ = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        isExternalStorageWritable();
        isExternalStorageReadable();

        // For API 23+ you need to request the read/write permissions even if they are already in your manifest.
        int currentapiVersion = android.os.Build.VERSION.SDK_INT;

        if (currentapiVersion >= Build.VERSION_CODES.M) {
            verifyPermissions(this);
        }

        mainGrid = (GridLayout) findViewById(R.id.mainGrid);
        //Set Event
        setSingleEvent(mainGrid);
        //setToggleEvent(mainGrid);

        letterList = new LetterList();
        intentToPop1 = new Intent(this, ResultPop1Activity.class);
    }

    public void mOnPopupClick(View v){
        //데이터 담아서 팝업(액티비티) 호출
        Intent intent = new Intent(this, ConnectionPopupActivity.class);
        startActivityForResult(intent, 1);
    }


    private void setToggleEvent(GridLayout mainGrid) {
        //Loop all child item of Main Grid
        for (int i = 0; i < mainGrid.getChildCount(); i++) {
            //You can see , all child item is CardView , so we just cast object to CardView
            final CardView cardView = (CardView) mainGrid.getChildAt(i);
            cardView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (cardView.getCardBackgroundColor().getDefaultColor() == -1) {
                        //Change background color
                        cardView.setCardBackgroundColor(Color.parseColor("#FF6F00"));
                        Toast.makeText(Home.this, "State : True", Toast.LENGTH_SHORT).show();

                    } else {
                        //Change background color
                        cardView.setCardBackgroundColor(Color.parseColor("#FFFFFF"));
                        Toast.makeText(Home.this, "State : False", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }

    private void setSingleEvent(GridLayout mainGrid) {
        //Loop all child item of Main Grid
        for (int i = 0; i < mainGrid.getChildCount(); i++) {
            //You can see , all child item is CardView , so we just cast object to CardView
            CardView cardView = (CardView) mainGrid.getChildAt(i);
            final int finalI = i;
            cardView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if(isMenuOpen){
                        if(finalI==0) //training
                        {
                            Intent intent = new Intent(Home.this,CameraActivity.class);
                            storeFile(100, "mode.txt");
                            startActivityForResult(intent, 100);
                        }
                        else if(finalI==1){ //use
                            Intent intent = new Intent(Home.this,CameraActivity.class);
                            storeFile(200, "mode.txt");
                            startActivityForResult(intent,200);
                        }
                    }else{
                        Toast.makeText(Home.this, "우선 네트워크 연결부터 먼저 확인해주세요!", Toast.LENGTH_SHORT).show();
                    }
                    
                }
            });
        }
    }



    /**
     * Checks if the app has permission to write to device storage or open camera
     * If the app does not has permission then the user will be prompted to grant permissions
     *
     * @param activity
     */
    @DebugLog
    private static boolean verifyPermissions(Activity activity) {
        // Check if we have write permission
        int write_permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int read_persmission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int camera_permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.CAMERA);

        if (write_permission != PackageManager.PERMISSION_GRANTED ||
                read_persmission != PackageManager.PERMISSION_GRANTED ||
                camera_permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_REQ,
                    REQUEST_CODE_PERMISSION
            );
            return false;
        } else {
            return true;
        }
    }

    /* Checks if external storage is available for read and write */
    @DebugLog
    private boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    /* Checks if external storage is available to at least read */
    @DebugLog
    private boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            return true;
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        if (pressedTime == 0){
            Toast.makeText(this, "종료하려면 한번 더 누르세요", Toast.LENGTH_SHORT).show();
            pressedTime = System.currentTimeMillis();
        } else{
            int seconds = (int) (System.currentTimeMillis() - pressedTime);

            //2000: 2000밀리초 (= 2초)
            if(seconds > 2000){
                pressedTime = 0;
            }else{
                finish();   //app 을 종료한다.
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode==1){
            //데이터 받기, ip(string) 수정
            ip = data.getStringExtra("ipAddress");

            if(resultCode == Activity.RESULT_OK){
                Toast.makeText(this, ip + "로 연결중...", Toast.LENGTH_SHORT).show();

                sendMessageToServer("connection");
            }
            else if(resultCode == Activity.RESULT_CANCELED){
                Toast.makeText(this, "다시 연결하세요", Toast.LENGTH_SHORT).show();
            }
        }
        else if(requestCode == 100){
            if(resultCode == Activity.RESULT_CANCELED){
                Toast.makeText(this, "Learn 진행중...", Toast.LENGTH_SHORT).show();

                //X, Y값을 읽는다.
                int X = readFile("xdata.txt");
                int Y = readFile("ydata.txt");

                sendMessageToServer("learn_" + X + "," + Y + ".");
            }
        }
        else if(requestCode == 200){
            if(resultCode == Activity.RESULT_CANCELED){
                Toast.makeText(this, "Predict 진행중...", Toast.LENGTH_SHORT).show();

                //X, Y값을 읽는다.
                int X = readFile("xdata.txt");
                int Y = readFile("ydata.txt");

                sendMessageToServer("predict_" + X + "," + Y + ".");
            }
        }
    }

    //서버에 메세지를 보낸다.
    public void sendMessageToServer(String message){
        //1. (쓰레드 실행 전에) 소켓부터 먼저 열기
        try {
            clientSocket = new Socket(ip, port);
            socketIn = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            socketOut = new PrintWriter(clientSocket.getOutputStream(), true);
        } catch (Exception e) {
            e.printStackTrace();
        }

        //2. 소켓을 열었으면, 실행할 쓰레드 & 이의 수신값을 다룰 handler를 선언한다.
        myHandler = new MyHandler();
        myThread = new MyThread();
        myThread.start();


        //3. 쓰레드 실행했으면, query를 보내보기
        socketOut.println(message);
    }

    //4. 실시간 쓰레드에서 받은 데이터를 출력한다.
    private class MyThread extends Thread{
        @Override
        public void run() {
            while (true) {
                try {
                    // 4-1. InputStream의 값을 읽어와서 data에 저장
                    String data = socketIn.readLine();
                    // Message 객체를 생성, 핸들러에 정보를 보낼 땐 이 메세지 객체를 이용
                    Message msg = myHandler.obtainMessage();
                    msg.obj = data;
                    myHandler.sendMessage(msg);
                }
                catch (Exception e) {
                    e.printStackTrace();
                }finally {
                    //4-2. handler에 값을 보냈으면 이를 종료한다.
                    myThread.interrupt();
                }
            }

        }
    }

    //5. 마지막으로 받은 값을 handler에서 실행한다.
    private class MyHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            String resultMessage = msg.obj.toString();
            //연결 여부 확인용
            if(resultMessage.equalsIgnoreCase("connected")){
                Toast.makeText(Home.this, "정상적으로 연결되었습니다.\n이제 시작하세요~!", Toast.LENGTH_SHORT).show();
                isMenuOpen = true;    //이제 메뉴에 진입할 수 있다.
            }

            //학습 여부 확인용
            else if(resultMessage.equalsIgnoreCase("complete")){
                Toast.makeText(Home.this, "학습을 완료했습니다!", Toast.LENGTH_SHORT).show();
            }

            //메세지 유츄 후 보여주기용
            else{
                Toast.makeText(Home.this, "유추 완료했습니다 (" + resultMessage + ")", Toast.LENGTH_SHORT).show();
                int mode = readFile("mode.txt");
                if(mode == 200){
                    intentToPop1.putExtra("predictedMsg", letterList.getStoredMessage(resultMessage));
                    startActivity(intentToPop1);
                }
            }
        }


    }

    public int readFile(String fileName){

        //파일 경로 선언하기
        String dirPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/ReadYourLip";
        File file = new File(dirPath);
        //해당 경로가 없으면 경로를 만든다.
        if( !file.exists() ) {
            file.mkdir();
        }

        String stored = null;
        try{
            BufferedReader bufRead = new BufferedReader(new FileReader(dirPath + "/" + fileName));
            stored = bufRead.readLine();
            bufRead.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        int XY = Integer.parseInt(stored);
        return XY;
    }

    public void storeFile(int inputInt, String fileName){
        //파일 경로 선언하기
        String dirPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/ReadYourLip";
        File file = new File(dirPath);
        //해당 경로가 없으면 경로를 만든다.
        if( !file.exists() ) {
            file.mkdir();
        }

        String input = String.valueOf(inputInt);
        try{
            BufferedWriter buf = new BufferedWriter(new FileWriter(dirPath + "/" + fileName, false));
            buf.write(input);
            buf.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}