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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.LocaleList;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import java.util.Locale;

import ai.olami.cloudService.APIConfiguration;

import static java.util.Locale.CHINA;
import static java.util.Locale.TAIWAN;


public class MainActivity extends AppCompatActivity {

    private final static String TAG = "MainActivity";

    private EditText appKeyEditText;
    private EditText appSecretEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Create example list
        ListView ActivityListView = (ListView) findViewById(R.id.listview);
        String[] ActivityList = {
                getString(R.string.SpeechInput),
                getString(R.string.TextInput) +" - "+ getString(R.string.WordSegmentation),
                getString(R.string.TextInput) +" - "+ getString(R.string.NLIAnalysis),
        };
        ArrayAdapter adapter = new ArrayAdapter(this,
                android.R.layout.simple_list_item_1,
                ActivityList);
        ActivityListView.setAdapter(adapter);
        ActivityListView.setOnItemClickListener(ActivityListCick);

        // load the default App-Key & App-Secret.
        String appKey = Config.getAppKey();
        String appSecret = Config.getAppSecret();
        if ((appKey.isEmpty() || appKey.startsWith("*"))
                || (appSecret.isEmpty() || appSecret.startsWith("*"))) {
            // If the developer doesn't change keys, pop up and the developer to input their keys.
            onCreateConfigurationDialog().show();
        }

        // Set default localization setting by the setting of system language.
        String systemLanguage = getSystemLanguage();
        if (systemLanguage.equals("zh-TW")) {
            Config.setLocalizeOption(APIConfiguration.LOCALIZE_OPTION_TRADITIONAL_CHINESE);
        } else {
            Config.setLocalizeOption(APIConfiguration.LOCALIZE_OPTION_SIMPLIFIED_CHINESE);
        }
    }

    public Dialog onCreateConfigurationDialog() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.Input);
        final LayoutInflater inflater = this.getLayoutInflater();
        View view = inflater.inflate(R.layout.configuration_setting, null);

        final EditText appKeyInput = (EditText)  view.findViewById(R.id.appKeyInput);
        final EditText appSecretInput = (EditText) view.findViewById(R.id.appSecretInput);

        builder.setView(view)
            .setPositiveButton(R.string.Submit, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int id) {
                    String userAppKeyInput = appKeyInput.getText().toString();
                    String userAppSecret = appSecretInput.getText().toString();

                    if (userAppKeyInput.isEmpty() || userAppSecret.isEmpty()) {
                        Toast.makeText(MainActivity.this, R.string.InputKeyIsEmpty, Toast.LENGTH_LONG).show();
                        onCreateConfigurationDialog().show();
                    } else {
                        // The developer has already inputted keys.
                        Config.setAppKey(userAppKeyInput);
                        Config.setAppSecret(userAppSecret);
                    }
                }
            })
            .setNegativeButton(R.string.Register, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    Intent intent= new Intent(Intent.ACTION_VIEW, Uri.parse("https://olami.ai"));
                    startActivity(intent);
                    onCreateConfigurationDialog().show();
                }
            });
        return builder.create();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        // Manually switch the localization setting.
        if (id == R.id.setting_changeCN) {
            Config.setLocalizeOption(APIConfiguration.LOCALIZE_OPTION_SIMPLIFIED_CHINESE);
            switchLanguage(this, "china");
        } else if (id == R.id.setting_changeTW){
            Config.setLocalizeOption(APIConfiguration.LOCALIZE_OPTION_TRADITIONAL_CHINESE);
            switchLanguage(this, "taiwan");
        }

        return super.onOptionsItemSelected(item);
    }

    protected void switchLanguage(Context context, String language) {
        Resources resources = getResources();
        android.content.res.Configuration config = resources.getConfiguration();
        DisplayMetrics dm = resources.getDisplayMetrics();
        if (language.equals("taiwan")) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                config.setLocale(TAIWAN);
            } else {
                config.locale = TAIWAN;
            }
        } else {
            // default language: mainland
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                config.setLocale(CHINA);
            } else {
                config.locale = CHINA;
            }
        }
        context.getResources().updateConfiguration(config, dm);
        Intent refresh = new Intent(context, MainActivity.class);
        startActivity(refresh);
        finish();
    }

    protected Locale getSystemlLocale() {
        Locale locale;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            locale = getResources().getConfiguration().getLocales().get(0);
        } else {
            locale = getResources().getConfiguration().locale;
        }
        return locale;
    }

    protected String getSystemLanguage() {
        Locale locale;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            locale = LocaleList.getDefault().get(0);
        } else {
            locale = Locale.getDefault();
        }

        String language = locale.getLanguage() + "-" + locale.getCountry();
        return language;
    }

    private AdapterView.OnItemClickListener ActivityListCick = new AdapterView.OnItemClickListener(){
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

            String appKey = Config.getAppKey();
            String appSecret = Config.getAppSecret();
            if ((appKey.isEmpty() || appKey.startsWith("*"))
                    || (appSecret.isEmpty() || appSecret.startsWith("*"))) {
                onCreateConfigurationDialog().show();
            } else {
                Intent intent;
                switch (position){
                    case 0:
                        // Start SpeechInput Activity
                        intent = new Intent(MainActivity.this, SpeechInputActivity.class);
                        intent.putExtra("LOCALIZE_OPTION", Config.getLocalizeOption());
                        startActivity(intent);
                        break;
                    case 1:
                        // Start TextInputWordSegmentation Activity
                        intent = new Intent(MainActivity.this, TextInputWordSegmentationActivity.class);
                        intent.putExtra("LOCALIZE_OPTION", Config.getLocalizeOption());
                        startActivity(intent);
                        break;
                    case 2:
                        // Start TextInputNLIAnalysis Activity
                        intent = new Intent(MainActivity.this, TextInputNLIAnalysisActivity.class);
                        intent.putExtra("LOCALIZE_OPTION", Config.getLocalizeOption());
                        startActivity(intent);
                        break;
                    default:
                        break;
                }
            }
        }
    };
}
