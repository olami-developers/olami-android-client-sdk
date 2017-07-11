package ai.olami.android.sample;

import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.google.gson.Gson;

import ai.olami.cloudService.APIConfiguration;
import ai.olami.cloudService.APIResponse;
import ai.olami.cloudService.TextRecognizer;
import ai.olami.util.GsonFactory;

/**
 * Created by ROGER on 2017/6/25.
 */

public class TextInputNLIAnalysisActivity extends AppCompatActivity {
    public final static String TAG = "TextInputNLIAnalysisActivity";

    private Button textInputSubmitButton;
    private EditText textInputEdit;
    private TextView textInputResponse;

    private Gson mJsonDump;
    private TextRecognizer mRecoginzer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text_input_nli_analysis);

        textInputSubmitButton = (Button) findViewById(R.id.textSubmitButton);
        textInputSubmitButton.setOnClickListener(new textInputSubmitButtonListener());
        textInputEdit = (EditText) findViewById(R.id.textInputEditText);
        textInputResponse = (TextView) findViewById(R.id.textInputNLIAPIResponse);
        textInputResponse.setMovementMethod(ScrollingMovementMethod.getInstance());

        mJsonDump = GsonFactory.getDebugGson(false);

        // * Step 1: Configure your key and localize option.
        APIConfiguration config = new APIConfiguration(
                RecognizerConfiguration.getAppKey(), RecognizerConfiguration.getAppSecret(), RecognizerConfiguration.getLocalizeOption());

        // * Step 2: Create the text recognizer.
        mRecoginzer = new TextRecognizer(config);

        // * Optional steps: Setup some other configurations.
        mRecoginzer.setEndUserIdentifier("Someone");
        mRecoginzer.setTimeout(10000);

    }

    private void submitButtonChangeHandler(final boolean isEnabled, final String buttonString) {
        new Handler(this.getMainLooper()).post(new Runnable(){
            public void run(){
                textInputSubmitButton.setEnabled(isEnabled);
                textInputSubmitButton.setText(buttonString);
            }
        });
    }

    protected class textInputSubmitButtonListener implements View.OnClickListener {
        @Override
        public void onClick (View v) {
            submitButtonChangeHandler(false, getString(R.string.RecognizeState_PROCESSING) +"...");
            new Thread(new Runnable() {
                @Override
                public void run() {
                try {
                    // * Send text
                    APIResponse response = mRecoginzer.requestNLI(textInputEdit.getText().toString());
                    if (response.ok() && response.hasData()) {
                        textInputAPIResponseChangeHandler(mJsonDump.toJson(response));
                        submitButtonChangeHandler(true, getString(R.string.Submit));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                }
            }).start();
        }
    }

    private void textInputAPIResponseChangeHandler(final String APIResponseDump) {
        new Handler(this.getMainLooper()).post(new Runnable(){
            public void run(){
                textInputResponse.setText(getString(R.string.Response) +" : \n"+ APIResponseDump);
            }
        });
    }
}
