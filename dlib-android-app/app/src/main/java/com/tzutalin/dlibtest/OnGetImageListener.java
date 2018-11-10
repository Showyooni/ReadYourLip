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

import java.io.File;
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
    private float totalTime = 0.0f; //총 측정시간. 초기값은 0.0초이다.
    private Vibrator mVibrator;
    private long[] pattern = {1000, 500}; // vibrate for 1 sec, 0.5 sec for pause

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


                        //여기서부터는 시간 측정을 한다.
                        long startTime = System.currentTimeMillis();    //측정 시작 시간(mills)

                        List<VisionDetRet> results; //result = 얼굴 검출 데이터
                        synchronized (OnGetImageListener.this) {
                            results = mFaceDet.detect(mCroppedBitmap);
                        }

                        long endTime = System.currentTimeMillis();  //측정 끝 시간(mills)

                        //총 측정된 시간을 float 형으로 저장
                        float timeCost = (endTime - startTime) / 1000f;
                        totalTime += timeCost;



                        // Draw on bitmap if data set 'results' is detected
                        if (results != null) {
                            for (final VisionDetRet ret : results) {
                                //입술 끝지점(노란색)의 좌표를 저장한다.
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


                                        if(position == 48 || position == 51 || position == 54 || position == 57){
                                            canvas.drawCircle(pointX, pointY, 2, mFaceLandmarkPaint_edge);
                                            lipEdges[(position-48)/3][0] = pointX;
                                            lipEdges[(position-48)/3][1] = pointY;
                                        }
                                        else{
                                            canvas.drawCircle(pointX, pointY, 2, mFaceLandmarkPaint);
                                        }

                                    }   //end of if
                                }//end of for(points)

                                //입술 끝지점을 연결한 다각형(마름모꼴)의 면적을 구한다.
                                double areaOfEdges = getArea(lipEdges);

                                //마지막으로 mTransparentTitleView에다가 time cost와 다각형(마름모꼴) 면적 정보를 저장한다.
                                //이는 점이 찍힐 때에만 나오며, 입술이 인식 안 될 때에는 .setText가 되지 않는다.
                                mTransparentTitleView.setText("Time: " + totalTime + " sec / Area: " + areaOfEdges);

                            }//end of for(results)
                        }//end of 'if(results != null)'

                        mWindow.setRGBBitmap(mCroppedBitmap);
                        mIsComputing = false;

                        if(totalTime >= 5.0f){
                            totalTime = 0.0f;
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

        return result / 2.0;
    }
}
