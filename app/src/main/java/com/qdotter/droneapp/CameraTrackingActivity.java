package com.qdotter.droneapp;

import androidx.annotation.RequiresApi;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.DragEvent;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraActivity;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Rect2d;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.tracking.Tracker;
import org.opencv.tracking.TrackerMedianFlow;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.util.Collections;
import java.util.List;

public class CameraTrackingActivity extends CameraActivity implements CameraBridgeViewBase.CvCameraViewListener2, View.OnTouchListener, View.OnClickListener {

    private static final String TAG = "ObjectTracker";
    TextView m_textStatus;
    Button m_button;
    Button m_buttonSettingsPage;
    Button m_buttonConnectionPage;
    Mat m_mat;

    // tracking stuff
    Tracker m_tracker = null;
    Point m_startPoint = null;
    Point m_endPoint = null;
    Rect m_boundingBox = new Rect();
    Rect2d m_boundingBox2dMat = new Rect2d();
    boolean m_initTracking = false;
    boolean m_setTrackingMiddle = false;
    long m_lastTrackTime_ms = 0;
    long m_trackInterval_ms = 100;
    Point m_diffVectorMat = new Point();

    final String kNotTrackingText = "Not tracking";
    final String kTrackingFailedText = "Tracking failed...";
    final String kTrackingText = "Tracking...";

    private CameraBridgeViewBase mOpenCvCameraView;

    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null) {
            mOpenCvCameraView.disableView();
        }
    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                    break;
                }
                default:
                {
                    super.onManagerConnected(status);
                    break;
                }
            }
        }
    };

    double matrixSum(Mat matrix)
    {
        double sum = 0.0;
        for (int row = 0; row < matrix.rows(); ++row)
        {
            for (int col = 0; col < matrix.cols(); ++col)
            {
                sum += matrix.get(row, col)[0];
            }
        }
        return sum;
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //m_mat = new Mat(10, 20, CvType.CV_8S);
        //m_mat.setTo(new Scalar(3));
        m_textStatus = (TextView)findViewById(R.id.textStatus);
        m_button = (Button)findViewById(R.id.buttonTrack);
        m_buttonSettingsPage = (Button)findViewById(R.id.buttonSettings);
        m_buttonConnectionPage = (Button)findViewById(R.id.buttonConnection);

        Toast.makeText(this,"toasting", Toast.LENGTH_LONG);
        m_textStatus.setText(kNotTrackingText);

        m_button.setOnClickListener(this);
        m_buttonSettingsPage.setOnClickListener(this);
        m_buttonConnectionPage.setOnClickListener(this);

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.cameraView);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);

        mOpenCvCameraView.setOnTouchListener(this);
        mOpenCvCameraView.enableFpsMeter();
    }

    @Override
    protected List<? extends CameraBridgeViewBase> getCameraViewList() {
        return Collections.singletonList(mOpenCvCameraView);
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null) {
            mOpenCvCameraView.disableView();
        }
    }

    public void onCameraViewStarted(int width, int height) {
    }

    public void onCameraViewStopped() {
    }

    void makeTracker()
    {
        m_tracker = (Tracker)TrackerMedianFlow.create();
    }

    Point translateCoords(int matWidth, int matHeight, int viewWidth, int viewHeight, Point onScreenPos)
    {
        final double aspectImg = (double)matWidth / matHeight;
        final double aspectView = (double)viewWidth / viewHeight;

        Point inMatrixPoint = new Point();
        if (aspectImg > aspectView)
        {
            // image is wider than the view
            // so image width on screen is viewWidth
            inMatrixPoint.x = (double)matWidth * (onScreenPos.x / viewWidth);

            double imgHeightOnView = (double)viewWidth / aspectImg;
            double offset = (viewHeight - imgHeightOnView) / 2.0;
            inMatrixPoint.y = ((onScreenPos.y - offset) / imgHeightOnView) * matHeight;
            Log.i("asd","asd");
        }
        else
        {
            // image is taller than view
            // so image height on screen is viewHeight
            inMatrixPoint.y = (double) matHeight * (onScreenPos.y / viewHeight);

            double imgWidthOnView = (double)viewHeight * aspectImg;
            double offset = (viewWidth - imgWidthOnView) / 2.0;
            inMatrixPoint.x = ((onScreenPos.x - offset) / imgWidthOnView) * matWidth;
        }

        return inMatrixPoint;
    }

    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Mat mat = inputFrame.rgba();
        Core.rotate(mat, mat, 0);

        // do track
        long now = System.currentTimeMillis();
        boolean doTrack =  (m_lastTrackTime_ms == 0 || now - m_lastTrackTime_ms > m_trackInterval_ms);

        // track
        if (doTrack)
        {
            m_trackInterval_ms = now;
            if (m_initTracking)
            {
                if (m_setTrackingMiddle)
                {
                    m_boundingBox2dMat.x = mat.cols() / 3;
                    m_boundingBox2dMat.width = mat.cols() / 3;
                    m_boundingBox2dMat.y = mat.rows() / 3;
                    m_boundingBox2dMat.height = mat.rows() / 3;
                }
                else {
                    Point topLeftOnView = new Point(m_boundingBox.x, m_boundingBox.y);
                    Point bottomRightOnView = new Point(m_boundingBox.x + m_boundingBox.width, m_boundingBox.y + m_boundingBox.height);
                    Point topLeft = translateCoords(mat.cols(), mat.rows(), mOpenCvCameraView.getWidth(), mOpenCvCameraView.getHeight(), topLeftOnView);
                    Point bottomRight = translateCoords(mat.cols(), mat.rows(), mOpenCvCameraView.getWidth(), mOpenCvCameraView.getHeight(), bottomRightOnView);
                    m_boundingBox2dMat.x = topLeft.x;
                    m_boundingBox2dMat.y = topLeft.y;
                    m_boundingBox2dMat.width = bottomRight.x - topLeft.x;
                    m_boundingBox2dMat.height = bottomRight.y - topLeft.y;
                }

                makeTracker();
                final boolean initResult = m_tracker.init(mat, m_boundingBox2dMat);
                Log.i("ObjectTracker", initResult ? "Tracking init success" : "Tracking init failed");

                if (!initResult)
                {
                    m_initTracking = true;
                }
                else
                {
                    m_initTracking = false;
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        m_textStatus.setText(initResult ? kTrackingText : kTrackingFailedText);
                    }
                });
            }
            else if (m_tracker != null) {
                final boolean updateResult = m_tracker.update(mat, m_boundingBox2dMat);

                if(updateResult)
                {
                    Point boxMid = new Point(m_boundingBox2dMat.x + m_boundingBox2dMat.width / 2, m_boundingBox2dMat.y + m_boundingBox2dMat.height/ 2);
                    Point matMid = new Point((double)mat.cols() / 2, (double)mat.rows() / 2);
                    m_diffVectorMat.x = boxMid.x - matMid.x;
                    m_diffVectorMat.y = boxMid.y - matMid.y;
                }
                else
                {
                    m_diffVectorMat.x = 0;
                    m_diffVectorMat.y = 0;
                }

                Log.i("ObjectTracker", updateResult ? "Tracking update success" : "Tracking update failed");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        m_textStatus.setText(updateResult ? kTrackingText : kTrackingFailedText);
                    }
                });
            }
        }
        else
        {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    m_textStatus.setText(kNotTrackingText);
                }
            });
        }

        // annotate for viewing
        // starting sketch point
        if (m_startPoint != null)
        {
            Point matPoint = translateCoords(mat.cols(), mat.rows(), mOpenCvCameraView.getWidth(), mOpenCvCameraView.getHeight(), m_startPoint);
            Imgproc.circle(mat, matPoint, 5, new Scalar(1.0, 0, 0), Imgproc.FILLED);
        }
        // bounding box
        if (m_boundingBox2dMat != null && m_boundingBox != null)
        {
            m_boundingBox.x = (int)m_boundingBox2dMat.x;
            m_boundingBox.y = (int)m_boundingBox2dMat.y;
            m_boundingBox.width = (int)m_boundingBox2dMat.width;
            m_boundingBox.height = (int)m_boundingBox2dMat.height;
            Imgproc.rectangle(mat, m_boundingBox, new Scalar(1.0,0,0), 2);
        }
        // movement vector
        if (m_diffVectorMat != null)
        {
            Point start = new Point((double)mat.cols() / 2, (double)mat.rows() / 2);
            Point end = new Point(m_diffVectorMat.x + start.x,  m_diffVectorMat.y + start.y);  // diff goes from the box centre TO the middle
            Imgproc.arrowedLine(mat, start, end, new Scalar(255,0,0));
        }

        return mat;
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public boolean onTouch(View v, MotionEvent event) {

        Log.i("ObjectTracker", String.format("Touch event %d", event.getAction()));
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            {
                if (m_startPoint == null)
                {
                    m_boundingBox = null;
                    m_startPoint = new Point(event.getX() - v.getLeft(), event.getY() -  v.getTop());
                }
                else
                {
                    m_endPoint = new Point(event.getX()- v.getLeft(), event.getY() - v.getTop());
                    m_boundingBox = new Rect();
                    m_boundingBox.x = (int)Math.min(m_startPoint.x, m_endPoint.x);
                    m_boundingBox.y = (int)Math.min(m_startPoint.y, m_endPoint.y);
                    m_boundingBox.width = (int)Math.max(m_startPoint.x, m_endPoint.x) - m_boundingBox.x;
                    m_boundingBox.height = (int)Math.max(m_startPoint.y, m_endPoint.y) - m_boundingBox.y;
                    m_endPoint = null;
                    m_startPoint = null;
                    m_initTracking = true;
                    m_setTrackingMiddle = false;
                }

                return true;
            }
        }

        return false;
    }

    @Override
    public void onClick(View view) {
        if (view == (View)m_button)
        {
            m_initTracking = true;
            m_setTrackingMiddle = true;
            m_startPoint = null;
        }
    }
}