/*
 * Copyright 2016-present Tzutalin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tzutalin.dlibtest;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Trace;
import android.os.Vibrator;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.widget.Toast;

import com.tzutalin.dlib.Constants;
import com.tzutalin.dlib.FaceDet;
import com.tzutalin.dlib.VisionDetRet;

import junit.framework.Assert;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import lipdetection.lipDetection;

/**
 * Class that takes in preview frames and converts the image to Bitmaps to process with dlib lib.
 */
@RequiresApi(api = Build.VERSION_CODES.KITKAT)
public class OnGetImageListener implements OnImageAvailableListener {
    private static final boolean SAVE_PREVIEW_BITMAP = false;

    private static final int INPUT_SIZE = 224;
    private static final String TAG = "OnGetImageListener";

    //파일 쓰기
    final static String fileForLearn = "learn.txt";

    private int mScreenRotation = 90;

    private int mPreviewWdith = 0;
    private int mPreviewHeight = 0;
    private byte[][] mYUVBytes;
    private int[] mRGBBytes = null;
    private Bitmap mRGBframeBitmap = null;
    private Bitmap mCroppedBitmap = null;

    private boolean mIsComputing = false;
    private Handler mInferenceHandler;

    private Context mContext;
    private FaceDet mFaceDet;
    private TrasparentTitleView mTransparentTitleView;
    private FloatingCameraWindow mWindow;
    private Paint mFaceLandmarkPaint, mFaceLandmarkPaint_edge;


    //새로 넣은 부분
    private int mode;   //모드 1~4까지 받는다
    private Vibrator mVibrator;

    int measureCount = 0;   //측정되는 횟구를 세어본다.

    //측정되기 이전의 면적값, 선값 등을 선언하고, 바뀔 때마다 이를 반영한다.
    double previousArea = 0.0;
    double previousRow = 0.0;
    double previousCol = 0.0;
    double previousRatio = 0.0;

    double areaChangeSum = 0.0;
    double rowChangeSum = 0.0;
    double colChangeSum = 0.0;
    double ratioChangeSum = 0.0;

    int X, Y;

    public void initialize(final Context context, final AssetManager assetManager, final TrasparentTitleView scoreView, final Handler handler, final int mode) {

        this.mContext = context;
        this.mTransparentTitleView = scoreView;
        this.mInferenceHandler = handler;
        this.mode = mode;
        mFaceDet = new FaceDet(Constants.getFaceShapeModelPath());
        mWindow = new FloatingCameraWindow(mContext);

        mFaceLandmarkPaint = new Paint();
        mFaceLandmarkPaint.setColor(Color.GRAY);
        mFaceLandmarkPaint.setStrokeWidth(2);
        mFaceLandmarkPaint.setStyle(Paint.Style.STROKE);

        mFaceLandmarkPaint_edge = new Paint();
        mFaceLandmarkPaint_edge.setColor(Color.YELLOW);
        mFaceLandmarkPaint_edge.setStrokeWidth(2);
        mFaceLandmarkPaint_edge.setStyle(Paint.Style.STROKE);

        mVibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
    }

    public void deInitialize() {

        synchronized (OnGetImageListener.this) {
            if (mFaceDet != null) {
                mFaceDet.release();
            }

            if (mWindow != null) {
                mWindow.release();
            }
        }
    }

    private void drawResizedBitmap(final Bitmap src, final Bitmap dst) {

        Display getOrient = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        int orientation = Configuration.ORIENTATION_UNDEFINED;
        Point point = new Point();
        getOrient.getSize(point);
        int screen_width = point.x;
        int screen_height = point.y;
        Log.d(TAG, String.format("screen size (%d,%d)", screen_width, screen_height));
        if (screen_width < screen_height) {
            orientation = Configuration.ORIENTATION_PORTRAIT;
            mScreenRotation = 90;
        } else {
            orientation = Configuration.ORIENTATION_LANDSCAPE;
            mScreenRotation = 0;
        }

        Assert.assertEquals(dst.getWidth(), dst.getHeight());
        final float minDim = Math.min(src.getWidth(), src.getHeight());

        final Matrix matrix = new Matrix();

        // We only want the center square out of the original rectangle.
        final float translateX = -Math.max(0, (src.getWidth() - minDim) / 2);
        final float translateY = -Math.max(0, (src.getHeight() - minDim) / 2);
        matrix.preTranslate(translateX, translateY);

        final float scaleFactor = dst.getHeight() / minDim;
        matrix.postScale(scaleFactor, scaleFactor);

        // Rotate around the center if necessary.
        if (mScreenRotation != 0) {
            matrix.postTranslate(-dst.getWidth() / 2.0f, -dst.getHeight() / 2.0f);
            matrix.postRotate(mScreenRotation);
            matrix.postTranslate(dst.getWidth() / 2.0f, dst.getHeight() / 2.0f);
        }

        final Canvas canvas = new Canvas(dst);
        canvas.drawBitmap(src, matrix, null);
    }   //end of 'drawResizedBitmap()'


    @Override
    public void onImageAvailable(final ImageReader reader) {
        Image image = null;
        try {
            image = reader.acquireLatestImage();

            if (image == null) {
                return;
            }

            // No mutex needed as this method is not reentrant.
            if (mIsComputing) {
                image.close();
                return;
            }
            mIsComputing = true;

            Trace.beginSection("imageAvailable");

            final Plane[] planes = image.getPlanes();

            // Initialize the storage bitmaps once when the resolution is known.
            if (mPreviewWdith != image.getWidth() || mPreviewHeight != image.getHeight()) {
                mPreviewWdith = image.getWidth();
                mPreviewHeight = image.getHeight();

                Log.d(TAG, String.format("Initializing at size %dx%d", mPreviewWdith, mPreviewHeight));
                mRGBBytes = new int[mPreviewWdith * mPreviewHeight];
                mRGBframeBitmap = Bitmap.createBitmap(mPreviewWdith, mPreviewHeight, Config.ARGB_8888);
                mCroppedBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Config.ARGB_8888);

                mYUVBytes = new byte[planes.length][];
                for (int i = 0; i < planes.length; ++i) {
                    mYUVBytes[i] = new byte[planes[i].getBuffer().capacity()];
                }
            }

            for (int i = 0; i < planes.length; ++i) {
                planes[i].getBuffer().get(mYUVBytes[i]);
            }

            final int yRowStride = planes[0].getRowStride();
            final int uvRowStride = planes[1].getRowStride();
            final int uvPixelStride = planes[1].getPixelStride();
            ImageUtils.convertYUV420ToARGB8888(
                    mYUVBytes[0],
                    mYUVBytes[1],
                    mYUVBytes[2],
                    mRGBBytes,
                    mPreviewWdith,
                    mPreviewHeight,
                    yRowStride,
                    uvRowStride,
                    uvPixelStride,
                    false);

            image.close();
        } catch (final Exception e) {
            if (image != null) {
                image.close();
            }
            Log.e(TAG, "Exception!", e);
            Trace.endSection();
            return;
        }

        mRGBframeBitmap.setPixels(mRGBBytes, 0, mPreviewWdith, 0, 0, mPreviewWdith, mPreviewHeight);
        drawResizedBitmap(mRGBframeBitmap, mCroppedBitmap);

        if (SAVE_PREVIEW_BITMAP) {
            ImageUtils.saveBitmap(mCroppedBitmap);
        }



        /**
         * 밑의 줄은 중요한 부분!
         * 점을 찍을 때마다 계속 무한으로 돌린다.
         * 언제 멈출지는 추후에 결정한다.
         * Handler를 post하는 것을 다룬다.
         */
        mInferenceHandler.post(
                new Runnable() {
                    @Override
                    public void run() {
                        if (!new File(Constants.getFaceShapeModelPath()).exists()) {
                            mTransparentTitleView.setText("Copying landmark model to " + Constants.getFaceShapeModelPath());
                            FileUtils.copyFileFromRawToOthers(mContext, R.raw.shape_predictor_68_face_landmarks, Constants.getFaceShapeModelPath());
                        }


                        List<VisionDetRet> results; //result = 얼굴 검출 데이터
                        synchronized (OnGetImageListener.this) {
                            results = mFaceDet.detect(mCroppedBitmap);
                        }

                        //이미지가 감지되면...
                        if (results != null) {

                            // 얼굴값이 검출되면 해야 할 일을 정의한다!
                            for (final VisionDetRet ret : results) {
                                if (measureCount == 0){
                                    mVibrator.vibrate(150);
                                }

                                //입술 끝지점(노란색)의 좌표를 저장한다. (60, 62, 64, 66 총 4개)
                                int[][] lipEdges = new int[4][2];

                                float resizeRatio = 1.0f;
                                Rect bounds = new Rect();
                                bounds.left = (int) (ret.getLeft() * resizeRatio);
                                bounds.top = (int) (ret.getTop() * resizeRatio);
                                bounds.right = (int) (ret.getRight() * resizeRatio);
                                bounds.bottom = (int) (ret.getBottom() * resizeRatio);
                                Canvas canvas = new Canvas(mCroppedBitmap);
                                //canvas.drawRect(bounds, mFaceLandmardkPaint);

                                // Draw landmark
                                ArrayList<Point> landmarks = ret.getFaceLandmarks();
                                if(lipDetection.checkSpeak(landmarks)){
                                    mFaceLandmarkPaint.setColor(Color.GRAY);
                                }
                                else {
                                    mFaceLandmarkPaint.setColor(Color.RED);
                                }
                                for (Point point : landmarks) {
                                    //First, get the position of landmarks (우선 점의 좌표 순서값을 부른다)
                                    int position = landmarks.indexOf(point);
                                    /*
                                     * The validate position of landmarks for lip detection is 48(49th) ~ 67(68th)
                                     * */
                                    if(position >= 48 && position <= 67){
                                        int pointX = (int) (point.x * resizeRatio);
                                        int pointY = (int) (point.y * resizeRatio);


                                        if(position == 60 || position == 62 || position == 64 || position == 66){
                                            canvas.drawCircle(pointX, pointY, 2, mFaceLandmarkPaint_edge);
                                            lipEdges[(position-60)/2][0] = pointX;
                                            lipEdges[(position-60)/2][1] = pointY;
                                        }
                                        else{
                                            canvas.drawCircle(pointX, pointY, 2, mFaceLandmarkPaint);
                                        }

                                    }   //end of if
                                }//end of for(points)

                                //입술 끝지점을 연결한 다각형(마름모꼴)의 면적을 구한다.
                                double areaOfEdges = getArea(lipEdges);
                                double edgeRow = getLength(lipEdges[0][0], lipEdges[0][1], lipEdges[2][0], lipEdges[2][1]);
                                double edgeCol = getLength(lipEdges[1][0], lipEdges[1][1], lipEdges[3][0], lipEdges[3][1]);
                                double ratio = (edgeCol / edgeRow) * 100;

                                double areaChange = areaOfEdges - previousArea;
                                double rowChange = edgeRow - previousRow;
                                double colChange = edgeCol - previousCol;
                                double ratioChange = ratio - previousRatio;

                                //mTransparentTitleView에다가 time cost와 다각형(마름모꼴) 면적 정보를 출력한다.
                                //이는 점이 찍힐 때에만 나오며, 입술이 인식 안 될 때에는 .setText가 되지 않는다.
                                mTransparentTitleView.setText(measureCount + "th change: " + Double.parseDouble(String.format("%.3f", areaChange)) + ", " + Double.parseDouble(String.format("%.3f", rowChange)) + "/" + Double.parseDouble(String.format("%.3f", colChange)) + " ratio: " + Double.parseDouble(String.format("%.3f", ratio)));
                                measureCount += 1;

                                areaChangeSum += (areaChange >= 0 ? areaChange : -areaChange);
                                rowChangeSum += (rowChange >= 0 ? rowChange : -rowChange);
                                colChangeSum += (colChange >= 0 ? colChange : -colChange);
                                ratioChangeSum += (ratioChange >=0 ? ratioChange: -ratioChange);

                                //마지막으로 최근에 측정된 면적, 선길이 등을 업데이트한다.
                                previousArea = areaOfEdges;
                                previousRow = edgeRow;
                                previousCol = edgeCol;
                                previousRatio = ratio;

                            }//end of for(results)
                        }//end of 'if(results != null)'

                        mWindow.setRGBBitmap(mCroppedBitmap);
                        mIsComputing = false;

                        if(measureCount >= 51){
                            mVibrator.vibrate(500);
                            Toast.makeText(mContext, "result: " + areaChangeSum + " / " + ratioChangeSum, Toast.LENGTH_SHORT).show();

                            //X, Y 정의
                            X = (int) areaChangeSum;
                            Y = (int) (ratioChangeSum/10.0);

                            //xdata.txt에 기존 데이터를 지우고 새로운 데이터를 넣기
                            boolean isXstored = writeXY(X, "xdata.txt");
                            //ydata.txt에 기존 데이터를 지우고 새로운 데이터를 넣기
                            boolean isYstored = writeXY(Y, "ydata.txt");
                            
                            if(isXstored && isYstored){
                                Toast.makeText(mContext, X + "와 " + Y + "가 성공적으로 저장되었습니다!", Toast.LENGTH_SHORT).show();
                            }else{
                                Toast.makeText(mContext, "저장 실패...", Toast.LENGTH_SHORT).show();
                            }
                            
                            resetParameters();
                        }

                    }   //end of 'run()'
                }); //end of 'mInferenceHandler.post(new Runnable())'

        Trace.endSection();
    }   //end of 'onImageAvailable()'



    public double getArea(int[][] coordinate){
        int length = coordinate.length;
        int result = 0;
        for(int i=0; i<length-1; i++){
            result += (coordinate[i][0] * coordinate[i+1][1]);
        }
        result += coordinate[length-1][0] * coordinate[0][1];

        for(int i=0; i<length-1; i++){
            result -= (coordinate[i+1][0] * coordinate[i][1]);
        }
        result -= coordinate[0][0] * coordinate[length-1][1];
        if(result < 0){
            result *= -1;
        }

        return result / 200.0;
    }

    public double getLength(int firstX, int firstY, int secondX, int secondY){
        return Math.sqrt(Math.pow(secondX - firstX, 2) + Math.pow(secondY - firstY, 2));
    }

    public void resetParameters(){
        measureCount = 0;
        previousArea = 0.0;
        previousRow = 0.0;
        previousCol = 0.0;
        previousRatio = 0.0;

        areaChangeSum = 0.0;
        rowChangeSum = 0.0;
        colChangeSum = 0.0;
        ratioChangeSum = 0.0;
    }

    public boolean writeXY(int XY, String fileName){
        boolean result = false;

        //파일 경로 선언하기
        String dirPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/ReadYourLip";
        File file = new File(dirPath);
        //해당 경로가 없으면 경로를 만든다.
        if( !file.exists() ) {
            file.mkdir();
        }

        String input = String.valueOf(XY);
        try{
            BufferedWriter buf = new BufferedWriter(new FileWriter(dirPath + "/" + fileName, false));
            buf.write(input);
            buf.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }


        int storedInt;
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

        storedInt = Integer.parseInt(stored);

        if(storedInt == XY) {
            result = true;
        }

        return result;
    }


}
