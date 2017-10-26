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

package ai.olami.android;

import ai.olami.cloudService.APIResponse;

public interface IKeepRecordingSpeechRecognizerListener {
    /**
     * Callback when the recognize state changes.
     *
     * @param state - Recognize state.
     */
    void onRecognizeStateChange(
            KeepRecordingSpeechRecognizer.RecognizeState state
    );

    /**
     * Callback when the results of speech recognition changes.
     *
     * @param response - API response with all kinds of results.
     */
    void onRecognizeResultChange(APIResponse response);

    /**
     * Callback when the volume of voice input changes.
     *
     * @param volumeValue - The volume level of voice input.
     */
    void onRecordVolumeChange(int volumeValue);

    /**
     * Callback when a server error occurs.
     *
     * @param response - API response with error message.
     */
    void onServerError(APIResponse response);

    /**
     * Callback when a error occurs.
     *
     * @param error - Error type.
     */
    void onError(KeepRecordingSpeechRecognizer.Error error);

    /**
     * Callback when a exception occurs.
     *
     * @param e - Exception.
     */
    void onException(Exception e);
}
