/*
	Copyright 2017, VIA Technologies, Inc. & OLAMI Team.

	http://olami.ai

	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at

	http://www.apache.org/licenses/LICENSE-2.0

	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.
*/

package ai.olami.android.example;

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

public class TextInputWordSegmentationActivity extends AppCompatActivity {
    public final static String TAG = "TextInputWordSegmentationActivity";

    private Button textInputSubmitButton;
    private EditText textInputEdit;
    private TextView textInputResponse;

    private Gson mJsonDump;
    private TextRecognizer mRecognizer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text_input_word_segmentation);

        textInputSubmitButton = (Button) findViewById(R.id.textSubmitButton);
        textInputSubmitButton.setOnClickListener(new textInputSubmitButtonListener());
        textInputEdit = (EditText) findViewById(R.id.textInputEditText);
        textInputResponse = (TextView) findViewById(R.id.textInputSegAPIResponse);
        textInputResponse.setMovementMethod(ScrollingMovementMethod.getInstance());

        mJsonDump = GsonFactory.getDebugGson(false);

        // * Step 1: Configure your key and localize option.
        APIConfiguration config = new APIConfiguration(
                Config.getAppKey(), Config.getAppSecret(), Config.getLocalizeOption());

        // * Step 2: Create the text recognizer.
        mRecognizer = new TextRecognizer(config);
        mRecognizer.setSdkType("android");

        // * Optional steps: Setup some other configurations.
        mRecognizer.setEndUserIdentifier("Someone");
        mRecognizer.setTimeout(10000);

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
                    APIResponse response = mRecognizer.requestWordSegmentation(textInputEdit.getText().toString());
                    // Check request status.
                    if (response.ok() && response.hasData()) {

                        String responseWordSeg = getString(R.string.Result) +" :\n";

                        // * Get and sjpw the word segmentation results.
                        String[] wordSegmentation = response.getData().getWordSegmentation();

                        for (int i = 0; i < wordSegmentation.length; i++) {
                            responseWordSeg += wordSegmentation[i];
                            if (i != wordSegmentation.length - 1) {
                                responseWordSeg += ", ";
                            } else {
                                responseWordSeg += "\n\n";
                            }
                        }

                        responseWordSeg += getString(R.string.Response) +" :\n";
                        responseWordSeg += mJsonDump.toJson(response);
                        textInputAPIResponseChangeHandler(responseWordSeg);

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
                textInputResponse.setText(APIResponseDump);
            }
        });
    }
}
