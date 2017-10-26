package ai.olami.android;

import ai.olami.cloudService.SpeechRecognizer;

public class SpeechRecognizerBase {

    protected static final String SDK_TYPE = "android";

    protected static final int RECORD_FRAMES = 6;
    protected final int FRAME_LENGTH_MILLISECONDS = SpeechRecognizer.AUDIO_LENGTH_MILLISECONDS_PER_FRAME;
    protected final int RESERVED_INPUT_LENGTH_MILLISECONDS = 1000;
    protected final int VAD_TAIL_SILENCE_LEVEL = 5;

    private int mFrameSize = 320;
    private int mRecordDataSize = RECORD_FRAMES * mFrameSize;
    private int mMinUploadAudioLengthMilliseconds = RECORD_FRAMES * FRAME_LENGTH_MILLISECONDS;
    private int mUploadAudioLengthMilliseconds = 300;
    private int mMinFrequencyToGettingResult = 300;
    private int mFrequencyToGettingResult = 300;
    private int mVADEndMilliseconds = 3000;

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
}
