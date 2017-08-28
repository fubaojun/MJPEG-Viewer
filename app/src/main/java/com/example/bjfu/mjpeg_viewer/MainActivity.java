
package com.example.bjfu.mjpeg_viewer;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;

import com.rtrk.mjpeg.MjpegUtils;
import com.rtrk.mjpeg.MjpegView;

public class MainActivity extends Activity {
    private static final String SAMPLE_URL = "http://webcam.st-malo.com/axis-cgi/mjpg/video.cgi?resolution=352x288";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mMjpegView = (MjpegView) findViewById(R.id.mjpegView);

        // Set the default sample url
        mUrlText = (EditText) findViewById(R.id.url);
        mUrlText.setText(SAMPLE_URL);

        Button btnPlay = (Button) findViewById(R.id.button1);
        btnPlay.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onPlay();
            }
        });
    }

    private void onPlay() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                mMjpegView.play(MjpegUtils
                        .getHTTPInputStream(mUrlText.getText().toString()));
            }
        }).start();
    }

    private MjpegView mMjpegView = null;
    private EditText mUrlText = null;
}
