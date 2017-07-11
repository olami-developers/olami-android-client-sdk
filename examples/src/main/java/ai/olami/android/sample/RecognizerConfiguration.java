package ai.olami.android.sample;


public class RecognizerConfiguration {
    // * Replace your APP KEY with this variable.
    private static String mAppKey = "";
    public static void setAppKey(String appKey) {
        mAppKey = appKey;
    }
    public static String getAppKey() {
        return mAppKey;
    }

    // * Replace your APP SECRET with this variable.
    public static String mAppSecret = "";
    public static void setAppSecret(String appSecret) {
        mAppSecret = appSecret;
    }
    public static String getAppSecret() {
        return mAppSecret;
    }

    // * Setting localize option
    private static int mLocalizeOption = ai.olami.cloudService.APIConfiguration.LOCALIZE_OPTION_TRADITIONAL_CHINESE;
    public static void setLocalizeOption(int localizeOption) {
        mLocalizeOption = localizeOption;
    }
    public static int getLocalizeOption() {
        return mLocalizeOption;
    }
}
