/*
 * The application needs to have the permission to write to external storage
 * if the output file is written to the external storage, and also the
 * permission to record audio. These permissions must be set in the
 * application's AndroidManifest.xml file, with something like:
 *
 * <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
 * <uses-permission android:name="android.permission.RECORD_AUDIO" />
 *
 */
package net.gandrade.oscillosomething;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;

import java.util.Vector;


public class MainActivity extends Activity
{
    public static final String LOG_TAG = "WaveCancelling";
    public static final int SAMPLING_RATE = 44100;
    public static final int AUDIO_SOURCE = MediaRecorder.AudioSource.MIC;
    public static final int CHANNEL_IN_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    public static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    public static final int BUFFER_SIZE = intMax(1920 * 2, AudioRecord.getMinBufferSize(SAMPLING_RATE, CHANNEL_IN_CONFIG, AUDIO_FORMAT));
    public static final float WAVE_DRAW_FACTOR = 1.5f;

    private volatile boolean dontRecord = true;
    private volatile View myView;
    private volatile byte[] audioDataHistory;

    public MainActivity() {
        audioDataHistory = new byte[BUFFER_SIZE];
    }

    public class MyView extends View {
        public MyView(Context context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            Paint paint = new Paint();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            canvas.drawPaint(paint);

            int viewWidth = getWidth();
            int viewHeight = getHeight();

            paint.setColor(Color.parseColor("#FF0000"));
            float[] points = new float[audioDataHistory.length];
            int iterationStep = 2 * intMax(1, audioDataHistory.length / viewWidth);
            Vector<Pair<Float, Float>> fillerPointsVec = new Vector<Pair<Float, Float>>();
            int strokeWidth = 3;
            paint.setStrokeWidth(strokeWidth);

            for (int i = 0; i < audioDataHistory.length; i += iterationStep) {
                int sample = Math.round(WAVE_DRAW_FACTOR * (float) LEbytes_to_short(audioDataHistory, i));
                float sampleMagnitude = (float)(sample - Short.MIN_VALUE) / (float)0xFFFF;
                float pointX = (((float) viewWidth * ((float) (i + 1) / (float) audioDataHistory.length)));
                float pointY = ((sampleMagnitude * (float) viewHeight));
                pointY = (pointY < 0.0f ? 0.0f : (pointY > (float) viewHeight ? (float) viewHeight : pointY));
                points[i] = pointX;
                points[i + 1] = pointY;
                if (i > 0) {
                    float prevPointX = points[i - iterationStep];
                    float prevPointY = points[i - iterationStep + 1];
                    float horizontalDistance = pointX - prevPointX;
                    float verticalDistance = pointY - prevPointY;
                    int fillerCount = intMin(5, ((int)Math.floor(Math.abs(verticalDistance))) / strokeWidth);
                    for (int j = 0; j < fillerCount; j++) {
                        float perc = ((float)(j + 1) / (float)(fillerCount + 1));
                        float fillerX = prevPointX + (horizontalDistance * perc);
                        float fillerY = prevPointY + (verticalDistance * perc);
                        fillerPointsVec.add(Pair.create(fillerX, fillerY));
                    }
                }
            }

            float[] fillerPoints = new float[fillerPointsVec.size() * 2];
            int k = 0;
            for (Pair<Float, Float> fillerPointPair : fillerPointsVec) {
                fillerPoints[k++] = fillerPointPair.first;
                fillerPoints[k++] = fillerPointPair.second;
            }
            canvas.drawPoints(points, paint);
            canvas.drawPoints(fillerPoints, paint);
        }
    }

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        myView = new MyView(this);
        setContentView(myView);
        maybeLaunchAudioThread();
    }
    @Override
    public void onResume() {
        super.onResume();
        ((ViewGroup) myView.getParent()).removeView(myView);
        myView = new MyView(this);
        setContentView(myView);
        maybeLaunchAudioThread();
    }

    @Override
    public void onPause() {
        super.onPause();
        dontRecord = true;
    }

    private void maybeLaunchAudioThread() {
        if (! dontRecord) {
            // already running and wasn't stopped for some particular reason
            return;
        }
        dontRecord = false;

        final Runnable r = new Runnable() {
            @Override
            public void run() {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
                Log.v(LOG_TAG, "Starting recording… buffer size: " + BUFFER_SIZE);

                AudioRecord recorder = new AudioRecord(AUDIO_SOURCE,
                        SAMPLING_RATE, CHANNEL_IN_CONFIG,
                        AUDIO_FORMAT, BUFFER_SIZE);
                recorder.startRecording();

                while (!dontRecord) {
                    int status = recorder.read(audioDataHistory, 0, audioDataHistory.length);
                    if (status == AudioRecord.ERROR_INVALID_OPERATION ||
                            status == AudioRecord.ERROR_BAD_VALUE) {
                        Log.e(LOG_TAG, "Error reading audio data!");
                        return;
                    }

                    myView.postInvalidate();
                }


                recorder.stop();
                recorder.release();

                Log.v(LOG_TAG, "Recording done…");
            }

        };

        Thread t = new Thread(r);
        t.start();
    }

    private static final short LEbytes_to_short(byte audioData[], int position) {
        byte low = audioData[position];
        byte high = audioData[position + 1];
        return (short)( ((high & 0xFF) << 8) | (low & 0xFF) );
    }

    private static final int intMax(int a, int b) {
        return (a > b ? a : b);
    }

    private static final int intMin(int a, int b) {
        return (a < b ? a : b);
    }
}