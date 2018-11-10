package lipdetection;

import android.graphics.Point;
import android.util.Log;

import java.util.ArrayList;

public class lipDetection {
    private static lipDetection mObject = new lipDetection();

    public static boolean checkSpeak(ArrayList<Point> face){
        return mObject.checkSpeak_method(face);
    }

    private boolean checkSpeak_method(ArrayList<Point> face){
        ArrayList<Point> lip = new ArrayList<>();

        ////no. 51-68 부터 lip 에 추가
        for(int i=48; i<68; i++){
            Point p = face.get(i);
            lip.add(p);
        }
        Log.d("lip detected...",String.valueOf(lip.size()));

        //입술 패턴이 감지되면 true, 감지되지 않으면 false 를 넘긴다
        if(lip.size() >= 20){
            return true;
        }
        else
            return false;
    }
}
