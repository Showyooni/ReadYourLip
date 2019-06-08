package com.tzutalin.dlibtest;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class ResultPop1Activity extends AppCompatActivity {

    TextView textView_result1;
    Button button_sms, button_kakao;
    String messageToSend;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result_pop1);

        final Intent intent = getIntent();
        messageToSend = intent.getStringExtra("predictedMsg");

        textView_result1 = (TextView)findViewById(R.id.textView_result1);
        textView_result1.setText(messageToSend);

        button_sms = (Button) findViewById(R.id.button_sms);
        button_kakao = (Button) findViewById(R.id.button_kakao);

        //SMS 보내기 버튼 누르면...
        button_sms.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent_sms = new Intent(Intent.ACTION_VIEW);
                intent_sms.putExtra("sms_body", messageToSend);
                intent_sms.setType("vnd.android-dir/mms-sms");
                startActivity(intent_sms);
            }
        });

        //Kakao 버튼을 누르면...
        button_kakao.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent_kakao = new Intent(Intent.ACTION_SEND);
                intent_kakao.setType("text/plain");
                intent_kakao.putExtra(Intent.EXTRA_TEXT, messageToSend);
                intent_kakao.setPackage("com.kakao.talk");
                startActivity(intent_kakao);

            }
        });
    }

    @Override
    public void onBackPressed() {
        Toast.makeText(this, "메세지 전송 취소하셨어요.", Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        //바깥레이어 클릭시 안닫히게
        if(event.getAction()==MotionEvent.ACTION_OUTSIDE){
            return false;
        }
        return true;
    }



}
