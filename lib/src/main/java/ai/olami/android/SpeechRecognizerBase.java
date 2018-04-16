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

import ai.olami.cloudService.SpeechRecognizer;

public class SpeechRecognizerBase {

    public static final int RECOGNIZE_RESULT_TYPE_STT = 0;
    public static final int RECOGNIZE_RESULT_TYPE_ALL = 1;
    public static final int RECOGNIZE_RESULT_TYPE_NLI = 2;

    public static final int AUDIO_COMPRESS_LIBRARY_TYPE_JAVA = 1;
    public static final int AUDIO_COMPRESS_LIBRARY_TYPE_CPP = 2;

    protected static final String SDK_TYPE = "android";

    protected static final int RECORD_FRAMES = 6;
    protected final int FRAME_LENGTH_MILLISECONDS = SpeechRecognizer.AUDIO_LENGTH_MILLISECONDS_PER_FRAME;
    protected final int RESERVED_INPUT_LENGTH_MILLISECONDS = 1000;
    protected final int INSTANT_NOISE_LENGTH_MILLISECONDS = 1000;
    protected final int VAD_TAIL_SILENCE_LEVEL = 5;

    private int mRecognizeResultType = RECOGNIZE_RESULT_TYPE_STT;
    private int mAudioCompressLibraryType = AUDIO_COMPRESS_LIBRARY_TYPE_CPP;

    private int mFrameSize = 320;
    private int mRecordDataSize = RECORD_FRAMES * mFrameSize;
    private int mMinUploadAudioLengthMilliseconds = RECORD_FRAMES * FRAME_LENGTH_MILLISECONDS;
    private int mUploadAudioLengthMilliseconds = 300;
    private int mMinFrequencyToGettingResult = 100;
    private int mFrequencyToGettingResult = 100;
    private int mVADEndMilliseconds = 2000;
    private int mSilenceLevel = VAD_TAIL_SILENCE_LEVEL;

    public int getAudioCompressLibraryType() {
        return mAudioCompressLibraryType;
    }

    protected int getRecognizeResultType() {
        return mRecognizeResultType;
    }

    protected int getSilenceLevel() {
        return mSilenceLevel;
    }

    protected void setSilenceLevel(int level) {
        mSilenceLevel = level;
    }

    protected int getFrameSize() {
        return mFrameSize;
    }

    protected void setFrameSize(int size) {
        mFrameSize = size;
    }

    protected int getRecordDataSize() {
        return mRecordDataSize;
    }

    protected void setRecordDataSize(int size) {
        mRecordDataSize = size;
    }

    protected int getMinUploadAudioLengthMilliseconds() {
        return mMinUploadAudioLengthMilliseconds;
    }

    protected void setMinUploadAudioLengthMilliseconds(int milliseconds) {
        mMinUploadAudioLengthMilliseconds = milliseconds;
    }

    protected int getUploadAudioLengthMilliseconds() {
        return mUploadAudioLengthMilliseconds;
    }

    protected void setUploadAudioLengthMilliseconds(int milliseconds) {
        mUploadAudioLengthMilliseconds = milliseconds;
    }

    protected int getMinFrequencyToGettingResult() {
        return mMinFrequencyToGettingResult;
    }

    protected void setMinFrequencyToGettingResult(int frequency) {
        mMinFrequencyToGettingResult = frequency;
    }

    protected int getFrequencyToGettingResult() {
        return mFrequencyToGettingResult;
    }

    protected void setFrequencyToGettingResult(int frequency) {
        mFrequencyToGettingResult = frequency;
    }

    protected int getVADEndMilliseconds() {
        return mVADEndMilliseconds;
    }

    protected void setVADEndMilliseconds (int milliseconds) {
        mVADEndMilliseconds = milliseconds;
    }

    /**
     * Set audio length in milliseconds to upload,
     * then the recognizer client will upload parts of audio once every milliseconds you set.
     *
     * @param milliseconds - How long of the audio in milliseconds do you want to upload once.
     */
    public void setSpeechUploadLength(int milliseconds) {
        if (getUploadAudioLengthMilliseconds() < getMinUploadAudioLengthMilliseconds()) {
            throw new IllegalArgumentException("The length in milliseconds cannot be less than "
                    + getMinUploadAudioLengthMilliseconds());
        }
        setUploadAudioLengthMilliseconds(milliseconds);
    }

    /**
     * Set the frequency in milliseconds of the recognition result query,
     * then the recognizer client will query the result once every milliseconds you set.
     *
     * @param milliseconds - How long in milliseconds do you want to query once.
     */
    public void setResultQueryFrequency(int milliseconds) {
        if (getFrequencyToGettingResult() < getMinFrequencyToGettingResult()) {
            throw new IllegalArgumentException("The frequency in milliseconds cannot be less than "
                    + getMinFrequencyToGettingResult());
        }
        setFrequencyToGettingResult(milliseconds);
    }


    /**
     * Set length of end time of the VAD in milliseconds to stop voice recording automatically.
     *
     * @param milliseconds - length of end time in milliseconds for the speech input idle.
     */
    public void setLengthOfVADEnd(int milliseconds) {
        setVADEndMilliseconds(milliseconds);
    }

    /**
     * Set level of silence volume of the VAD to stop voice recording automatically.
     *
     * @param level - level for the silence volume.
     */
    public void setSilenceLevelOfVADTail(int level) {
        if (level < 0) {
            throw new IllegalArgumentException("The level cannot be less than 0");
        }
        setSilenceLevel(level);
    }

    /**
     * Set type of the recognition results of the query.
     *
     * @param type - Type of the recognition results:
     *               RECOGNIZE_RESULT_TYPE_STT to get result of Speech-To-Text.
     *               RECOGNIZE_RESULT_TYPE_ALL to get results of the all types.
     *               RECOGNIZE_RESULT_TYPE_NLI to get results of Speech-To-Text and NLI.
     */
    public void setRecognizeResultType(int type) {
        if ((type >= 0) && (type <= 2)) {
            mRecognizeResultType = type;
        } else {
            throw new IllegalArgumentException("Illegal Argument [type]: " + type);
        }
    }

    /**
     * Set type of the audio compression library.
     *
     * @param type - Type of the recognition results:
     *               RECOGNIZE_RESULT_TYPE_STT to get result of Speech-To-Text.
     *               RECOGNIZE_RESULT_TYPE_ALL to get results of the all types.
     *               RECOGNIZE_RESULT_TYPE_NLI to get results of Speech-To-Text and NLI.
     */
    public void setAudioCompressLibraryType(int type) {
        switch (type) {
            case AUDIO_COMPRESS_LIBRARY_TYPE_JAVA:
                break;
            case AUDIO_COMPRESS_LIBRARY_TYPE_CPP:
                break;
            default:
                throw new IllegalArgumentException("Illegal library type code.");
        }
        mAudioCompressLibraryType = type;
    }

}
