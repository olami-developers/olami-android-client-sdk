package ai.olami.android.sample;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import ai.olami.android.IRecorderSpeechRecognizerListener;
import ai.olami.android.RecorderSpeechRecognizer;
import ai.olami.cloudService.APIConfiguration;
import ai.olami.cloudService.APIResponse;
import ai.olami.cloudService.SpeechResult;

public class SpeechInputActivity extends AppCompatActivity {
    public final static String TAG = "SpeechInputActivity";

    private static final int REQUEST_EXTERNAL_PERMISSION = 1;
    private static final int REQUEST_MICROPHONE = 3;

    RecorderSpeechRecognizer mRecongnizer = null;

    private final int VOLUME_BAR_MAX_VALUE = 40;
    private final int VOLUME_BAR_MAX_ITEM = 20;
    private final int VOLUME_BAR_ITEM_VALUE = VOLUME_BAR_MAX_VALUE / VOLUME_BAR_MAX_ITEM;

    private Button recordButton;
    private Button cancelButton;

    private TextView voiceVolumeText;
    private TextView voiceVolumeBar;
    private TextView STTText;
    private TextView APIResponseText;
    private TextView recognizeStatusText;
    private TextView recordStatusText;

    private RecorderSpeechRecognizer.RecordState mRecordState;
    private RecorderSpeechRecognizer.RecognizeState mRecognizeState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_speech_input);

        recordButton = (Button) findViewById(R.id.recordButton);
        cancelButton = (Button) findViewById(R.id.cancelButton);
        voiceVolumeText = (TextView) findViewById(R.id.voiceVolume);
        voiceVolumeBar = (TextView) findViewById(R.id.voiceVolumeBar);
        STTText = (TextView) findViewById(R.id.STTText);
        APIResponseText = (TextView) findViewById(R.id.APIResponse);
        APIResponseText.setMovementMethod(ScrollingMovementMethod.getInstance());
        recognizeStatusText = (TextView) findViewById(R.id.recognizeStatus);
        recordStatusText = (TextView) findViewById(R.id.recordStatus);

        recordButton.setOnClickListener(new recordButtonListener());
        cancelButton.setOnClickListener(new cancelButtonListener());
    }

    @Override
    protected void onResume() {
        super.onResume();

        APIConfiguration config = new APIConfiguration(
                RecognizerConfiguration.getAppKey(),
                RecognizerConfiguration.getAppSecret(),
                RecognizerConfiguration.getLocalizeOption());

        mRecongnizer = RecorderSpeechRecognizer.create(new SpeechRecognizerListener(), config);
        mRecongnizer.setEndUserIdentifier("Someone");
        mRecongnizer.setTimeout(3000);
        mRecongnizer.setAutoStopAtSpeechEnd(3000);
        mRecongnizer.setFrequencyOfGettingResult(300);
        mRecongnizer.setUploadLengthOfSpeech(300);
        // Initialize volume bar
        voiceVolumeChangeHandler(0);
    }

    @Override
    protected void onPause() {
        super.onPause();

        mRecongnizer.release();
    }

    protected class recordButtonListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            // Get RecordState now
            mRecordState = mRecongnizer.getRecordState();
            if (mRecordState == RecorderSpeechRecognizer.RecordState.STOPPED) {
                // 確認是否取得麥克風權限
                boolean hasMicrophonePermission = checkApplicationPermissions(
                        Manifest.permission.RECORD_AUDIO,
                        REQUEST_MICROPHONE);
                // 成功取得麥克風權限
                if (hasMicrophonePermission) {
                    try {
                        mRecongnizer.start();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    recordButton.setEnabled(false);
                }
            } else if (mRecordState == RecorderSpeechRecognizer.RecordState.RECORDING) {
                mRecongnizer.stop();
            }
        }
    }

    private class cancelButtonListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            mRecongnizer.cancel();
        }
    }

    private class SpeechRecognizerListener implements IRecorderSpeechRecognizerListener {
        @Override
        public void onRecordStateChange(RecorderSpeechRecognizer.RecordState state) {
            String StatusStr = getString(R.string.RecordState) +" : ";
            mRecordState = state;

            if (state == RecorderSpeechRecognizer.RecordState.STOPPED) {
                StatusStr += getString(R.string.RecordState_STOPPED);
                Log.i(TAG, StatusStr);
                recordStateHandler(StatusStr);
                recordButtonChangeHandler(true, getString(R.string.recordButton_start));
                cancelButtonChangeHandler(View.INVISIBLE, "");
            } else if (state == RecorderSpeechRecognizer.RecordState.INITIALIZING) {
                StatusStr += getString(R.string.RecordState_INITIALIZING) +"...";
                Log.i(TAG, StatusStr);
                recordStateHandler(StatusStr);
                recordButtonChangeHandler(false, StatusStr);
                cancelButtonChangeHandler(View.INVISIBLE, "");
                APIResponseChangeHandler("");
                STTChangeHandler("");
                APIResponseChangeHandler("");
            } else if (state == RecorderSpeechRecognizer.RecordState.INITIALIZED) {
                StatusStr += getString(R.string.RecordState_INITIALIZED);
                Log.i(TAG, StatusStr);
                recordStateHandler(StatusStr);
                recordButtonChangeHandler(false, StatusStr);
                cancelButtonChangeHandler(View.INVISIBLE, "");
            } else if (state == RecorderSpeechRecognizer.RecordState.RECORDING) {
                StatusStr += getString(R.string.RecordState_RECORDING) +"...";
                Log.i(TAG, StatusStr);
                recordStateHandler(StatusStr);
                recordButtonChangeHandler(true, getString(R.string.recordButton_stop));
                cancelButtonChangeHandler(View.VISIBLE, "X");
            } else if (state == RecorderSpeechRecognizer.RecordState.STOPPING) {
                StatusStr += getString(R.string.RecordState_STOPPING) +"...";
                Log.i(TAG, StatusStr);
                recordStateHandler(StatusStr);
                recordButtonChangeHandler(false, StatusStr);
                cancelButtonChangeHandler(View.VISIBLE, "X");
                voiceVolumeChangeHandler(0);
            } else if (state == RecorderSpeechRecognizer.RecordState.ERROR) {
                StatusStr += getString(R.string.RecordState_ERROR);
                Log.i(TAG, StatusStr);
                recordStateHandler(StatusStr);
                recordButtonChangeHandler(false, StatusStr);
                cancelButtonChangeHandler(View.VISIBLE, "X");
                voiceVolumeChangeHandler(0);
                errorStateHandler(StatusStr);
            }
        }

        public void onRecordVolumeChange(int volumeValue) {
            voiceVolumeChangeHandler(volumeValue);
        }

        @Override
        public void onRecognizeStateChange(RecorderSpeechRecognizer.RecognizeState state) {
            String StatusStr = getString(R.string.RecognizeState) +" : ";
            mRecognizeState = state;

            if (state == RecorderSpeechRecognizer.RecognizeState.STOPPED) {
                StatusStr += getString(R.string.RecognizeState_STOPPED);
                Log.i(TAG, StatusStr);
                recognizeStateHandler(StatusStr);
            } else if (state == RecorderSpeechRecognizer.RecognizeState.PROCESSING) {
                StatusStr += getString(R.string.RecognizeState_PROCESSING) +"...";
                Log.i(TAG, StatusStr);
                recognizeStateHandler(StatusStr);
            } else if (state == RecorderSpeechRecognizer.RecognizeState.COMPLETED) {
                StatusStr += getString(R.string.RecognizeState_COMPLETED);
                Log.i(TAG, StatusStr);
                recognizeStateHandler(StatusStr);
            } else if (state == RecorderSpeechRecognizer.RecognizeState.ERROR) {
                StatusStr += getString(R.string.RecognizeState_ERROR);
                Log.i(TAG, StatusStr);
                recognizeStateHandler(StatusStr);
                errorStateHandler(StatusStr);
            }
        }

        @Override
        public void onRecognizeResultChange(APIResponse response) {
            SpeechResult sttResult = response.getData().getSpeechResult();
            if (sttResult.complete()) {
                STTChangeHandler(sttResult.getResult());
                APIResponseChangeHandler(response.toString());
            } else {
                if (sttResult.getStatus() == SpeechResult.STATUS_RECOGNIZE_OK) {
                    STTChangeHandler(sttResult.getResult());
                    APIResponseChangeHandler(response.toString());
                }
            }
        }


        @Override
        public void onServerError(APIResponse response) {
            Log.e(TAG, "Server error code: "+ response.getErrorCode()
                    +", Error message: " + response.getErrorMessage());
            errorStateHandler("onServerError Code: "+ response.getErrorCode());
        }

        public void onError(RecorderSpeechRecognizer.Error error) {
            Log.e(TAG, "Error code:"+ error.name());
            errorStateHandler("RecorderSpeechRecognizer.Error: "+ error.name());
        }

        @Override
        public void onException(Exception e) {
            e.printStackTrace();
        }
    }

    private void recordButtonChangeHandler(final boolean isEnabled, final String buttonString) {
        new Handler(this.getMainLooper()).post(new Runnable(){
            public void run(){
                recordButton.setEnabled(isEnabled);
                recordButton.setText(buttonString);
            }
        });
    }

    private void cancelButtonChangeHandler(final int isVisibility, final String buttonString) {
        new Handler(this.getMainLooper()).post(new Runnable(){
            public void run(){
                cancelButton.setVisibility(isVisibility);
                cancelButton.setText(buttonString);
            }
        });
    }

    private void voiceVolumeChangeHandler(final int volume) {
        final int volumeBarItemCount = volume / VOLUME_BAR_ITEM_VALUE;

        new Handler(this.getMainLooper()).post(new Runnable(){
            public void run(){
                voiceVolumeText.setText(getString(R.string.Volume) +" : "+ volume);
                // Voice volume bar value change
                String voiceVolumeBarStr = "▌";
                for (int i = 1; i < volumeBarItemCount && i <= VOLUME_BAR_MAX_ITEM;
                     i++) {
                    voiceVolumeBarStr += "▌";
                }
                voiceVolumeBar.setText(voiceVolumeBarStr);

                // Voice volume bar color change
                if (volumeBarItemCount >= 0 && volumeBarItemCount <= 7) {
                    voiceVolumeBar.setTextColor(Color.GREEN);
                } else if (volumeBarItemCount >= 7 && volumeBarItemCount <= 14) {
                    voiceVolumeBar.setTextColor(Color.BLUE);
                } else {
                    voiceVolumeBar.setTextColor(Color.RED);
                }
            }
        });
    }

    private void STTChangeHandler(final String STTStr) {
        new Handler(this.getMainLooper()).post(new Runnable(){
            public void run(){
                STTText.setText(STTStr);
            }
        });
    }

    private void APIResponseChangeHandler(final String APIResponseStr) {
        new Handler(this.getMainLooper()).post(new Runnable(){
            public void run(){
                APIResponseText.setText(getString(R.string.Response) +" :\n"+ APIResponseStr);
            }
        });
    }

    private void recognizeStateHandler(final String recognizeStatusStr) {
        new Handler(this.getMainLooper()).post(new Runnable(){
            public void run(){
                recognizeStatusText.setText(recognizeStatusStr);
            }
        });
    }

    private void recordStateHandler(final String recordStatusStr) {
        new Handler(this.getMainLooper()).post(new Runnable(){
            public void run(){
                recordStatusText.setText(recordStatusStr);
            }
        });
    }

    private void errorStateHandler(final String errorString) {
        new Handler(this.getMainLooper()).post(new Runnable(){
            public void run(){
                Toast.makeText(getApplicationContext(),
                        errorString,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private boolean checkApplicationPermissions(String permissionStr, int requestCode) {
        // 執行的時候取得麥克風權限
        int permission = ActivityCompat.checkSelfPermission(
                SpeechInputActivity.this,
                permissionStr);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            // 無權限，向使用者請求
            ActivityCompat.requestPermissions(
                    SpeechInputActivity.this,
                    new String[] {permissionStr},
                    requestCode
            );
            return false;
        } else {
            return true;
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_MICROPHONE:
                for (int i = 0; i < permissions.length; i++) {
                    String permission = permissions[i];
                    int grantResult = grantResults[i];
                    if (permission.equals(Manifest.permission.RECORD_AUDIO)) {
                        if(grantResult == PackageManager.PERMISSION_GRANTED) {
                            Toast.makeText(
                                    this,
                                    getString(R.string.GetMicrophonePermission),
                                    Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this,
                                    getString(R.string.GetMicrophonePermissionDenied),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                }
                break;
            case REQUEST_EXTERNAL_PERMISSION:
                for (int i = 0; i < permissions.length; i++) {
                    String permission = permissions[i];
                    int grantResult = grantResults[i];
                    if (permission.equals(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                        if(grantResult == PackageManager.PERMISSION_GRANTED) {
                            Toast.makeText(
                                    this,
                                    getString(R.string.GetWriteStoragePermission),
                                    Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this,
                                    getString(R.string.GetWriteStoragePermissionDenied),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                }
                break;
        }
    }

}
