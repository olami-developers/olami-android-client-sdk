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

import ai.olami.cloudService.APIConfiguration;

public class Config {

    // * Replace your APP KEY with this variable.
    private static String mAppKey = "*****your-app-key*****";
    public static void setAppKey(String appKey) {
        mAppKey = appKey;
    }
    public static String getAppKey() {
        return mAppKey;
    }

    // * Replace your APP SECRET with this variable.
    private static String mAppSecret = "*****your-app-secret*****";
    public static void setAppSecret(String appSecret) {
        mAppSecret = appSecret;
    }
    public static String getAppSecret() {
        return mAppSecret;
    }

    // * Replace the localize option you want with this variable.
    // * - Use LOCALIZE_OPTION_SIMPLIFIED_CHINESE for China
    // * - Use LOCALIZE_OPTION_TRADITIONAL_CHINESE for Taiwan
    private static int mLocalizeOption = APIConfiguration.LOCALIZE_OPTION_SIMPLIFIED_CHINESE;
//    private static int mLocalizeOption = APIConfiguration.LOCALIZE_OPTION_TRADITIONAL_CHINESE;
    public static void setLocalizeOption(int localizeOption) {
        mLocalizeOption = localizeOption;
    }
    public static int getLocalizeOption() {
        return mLocalizeOption;
    }

}
