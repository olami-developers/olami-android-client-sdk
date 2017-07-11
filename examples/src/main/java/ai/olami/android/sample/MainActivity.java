package ai.olami.android.sample;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.LocaleList;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
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
    public final static String TAG = "MainActivity";

    private EditText appKeyEditText;
    private EditText appSecretEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // 產生範例選擇的清單
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
        // 判斷Config檔案當中的key是否空，若為空則要跳出對話視窗讓使用者輸入
        String appKey = RecognizerConfiguration.getAppKey();
        String appSecret = RecognizerConfiguration.getAppSecret();
        if (appKey.isEmpty() || appSecret.isEmpty()) {
            onCreateConfigurationDialog().show();
        }
        // 根據系統的語言決定要選擇的ASR伺服器，除了繁體使用台灣伺服器，其餘皆使用上海的ASR伺服器
        String systemLanguage = getSystemLanguage();
        if (systemLanguage.equals("zh-TW")) {
            RecognizerConfiguration.setLocalizeOption(APIConfiguration.LOCALIZE_OPTION_TRADITIONAL_CHINESE);
        } else {
            RecognizerConfiguration.setLocalizeOption(APIConfiguration.LOCALIZE_OPTION_SIMPLIFIED_CHINESE);
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
                        // 使用者完成輸入Appkey和AppSecret
                        RecognizerConfiguration.setAppKey(userAppKeyInput);
                        RecognizerConfiguration.setAppSecret(userAppSecret);
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
        // 手動切換語言和伺服器位址
        if (id == R.id.setting_changeCN) {
            switchLanguage(this, "china");
            RecognizerConfiguration.setLocalizeOption(APIConfiguration.LOCALIZE_OPTION_SIMPLIFIED_CHINESE);
        } else if (id == R.id.setting_changeTW){
            switchLanguage(this, "taiwan");
            RecognizerConfiguration.setLocalizeOption(APIConfiguration.LOCALIZE_OPTION_TRADITIONAL_CHINESE);
        }

        return super.onOptionsItemSelected(item);
    }

    // 手動切換應用程式的語言和伺服器位址
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
        } else { //default language: mainland
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

            String appKey = RecognizerConfiguration.getAppKey();
            String appSecret = RecognizerConfiguration.getAppSecret();
            if (appKey.isEmpty() || appSecret.isEmpty()) {
                onCreateConfigurationDialog().show();
            } else {
                Intent intent;
                switch (position){
                    case 0:
                        // Start SpeechInput Activity
                        intent = new Intent(MainActivity.this, SpeechInputActivity.class);
                        startActivity(intent);
                        break;
                    case 1:
                        // Start TextInputWordSegmentation Activity
                        intent = new Intent(MainActivity.this, TextInputWordSegmentationActivity.class);
                        startActivity(intent);
                        break;
                    case 2:
                        // Start TextInputNLIAnalysis Activity
                        intent = new Intent(MainActivity.this, TextInputNLIAnalysisActivity.class);
                        startActivity(intent);
                        break;
                    default:
                        break;
                }
            }
        }
    };
}
