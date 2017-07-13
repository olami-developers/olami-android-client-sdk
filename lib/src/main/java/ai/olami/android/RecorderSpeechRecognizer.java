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

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import ai.olami.cloudService.APIConfiguration;
import ai.olami.cloudService.APIResponse;
import ai.olami.cloudService.CookieSet;
import ai.olami.cloudService.SpeechRecognizer;
import ai.olami.cloudService.SpeechResult;

public class RecorderSpeechRecognizer {

    public final static String TAG = "OLAMI_RSR";

    private static final int SAMPLE_RATE_44100 = 44100;
    private static final int SAMPLE_RATE_16000 = 16000;
    private static final int RECORD_FRAMES = 6;
    private static final int FRAME_LENGTH_MILLISECONDS = SpeechRecognizer.AUDIO_LENGTH_MILLISECONDS_PER_FRAME;
    private static final int RESERVED_INPUT_LENGTH_MILLISECONDS = 1000;
    private static final int VAD_TAIL_SILENCE_LEVEL = 5;

    private static RecorderSpeechRecognizer mRecorderSpeechRecognizer = null;

    private IRecorderSpeechRecognizerListener mListener = null;
    private CookieSet mCookie = null;
    private SpeechRecognizer mRecognizer = null;
    private AudioRecord mRecord = null;
    private int mAudioRecordOptionChannels = -1;
    private int mAudioRecordOptionBitsPerFrame = -1;
    private int mFrameSize = 320;
    private int mRecordDataSize = 0;
    private int mMinUploadAudioLengthMilliseconds = RECORD_FRAMES * FRAME_LENGTH_MILLISECONDS;
    private int mUploadAudioLengthMilliseconds = 300;
    private int mMinFrequencyToGettingResult = 300;
    private int mFrequencyToGettingResult = 300;
    private int mVADEndMilliseconds = 3000;

    private boolean mSendCallback = true;
    private boolean mGetting = false;
    private boolean mCancel = false;
    private boolean mIsFinal = false;
    private boolean mCapturedVoiceBegin = false;
    private boolean mSaveRecordToFile = false;

    private BlockingQueue mRecordDataQueue = null;

    private Thread mRecorderThread = null;
    private Thread mSenderThread = null;
    private Thread mGetterThread = null;

    private File mRecordFile = null;
    private OutputStream mOutputStream = null;
    private BufferedOutputStream mBufferedOutputStream = null;
    private DataOutputStream mDataOutputStream = null;
    private String mRecordFilePath = Environment.getExternalStorageDirectory().getPath();
    private String mRecordFileName = "OLAMI-mic-record.pcm";

    private VoiceVolume mVoiceVolume = new VoiceVolume();

    private RecordState mRecordState;
    private RecognizeState mRecognizeState;

    /**
     * Recording state
     */
    public enum RecordState {
        STOPPED,
        INITIALIZING,
        INITIALIZED,
        RECORDING,
        STOPPING,
        ERROR
    }

    /**
     * Recognize process state
     */
    public enum RecognizeState {
        STOPPED,
        PROCESSING,
        COMPLETED,
        ERROR
    }

    /**
     * Error type
     */
    public enum Error {
        UNKNOWN
    }

    private RecorderSpeechRecognizer(
            IRecorderSpeechRecognizerListener listener,
            SpeechRecognizer recognizer
    ) {
        setListener(listener);
        setRecognizer(recognizer);

        switch (SpeechRecognizer.AUDIO_CHANNELS) {
            case 1:
                mAudioRecordOptionChannels = AudioFormat.CHANNEL_IN_MONO;
                break;
            default:
                mAudioRecordOptionChannels = AudioFormat.CHANNEL_IN_MONO;
                break;
        }

        switch (SpeechRecognizer.AUDIO_BITS_PER_SAMPLE) {
            case 16:
                mAudioRecordOptionBitsPerFrame = AudioFormat.ENCODING_PCM_16BIT;
                break;
            default:
                mAudioRecordOptionBitsPerFrame = AudioFormat.ENCODING_PCM_16BIT;
                break;
        }

        mFrameSize = mRecognizer.getAudioFrameSize();
        mRecordDataSize = RECORD_FRAMES * mFrameSize;

        initState();
    }

    /**
     * Create a RecorderSpeechRecognizer instance.
     *
     * @param listener - The specified callback listener.
     * @param config - API configurations.
     * @return RecorderSpeechRecognizer instance.
     */
    public static RecorderSpeechRecognizer create(
            IRecorderSpeechRecognizerListener listener,
            APIConfiguration config
    ) {
        return create(listener, new SpeechRecognizer(config));
    }

    /**
     * Create a RecorderSpeechRecognizer instance by a specified speech recognizer.
     *
     * @param listener - The specified callback listener.
     * @param recognizer - Configured speech recognizer.
     * @return RecorderSpeechRecognizer instance.
     */
    public static RecorderSpeechRecognizer create(
            IRecorderSpeechRecognizerListener listener,
            SpeechRecognizer recognizer
    ) {
        if (mRecorderSpeechRecognizer == null) {
            mRecorderSpeechRecognizer = new RecorderSpeechRecognizer(listener, recognizer);
        } else {
            mRecorderSpeechRecognizer.release();
            mRecorderSpeechRecognizer.setListener(listener);
            mRecorderSpeechRecognizer.setRecognizer(recognizer);
        }

        return mRecorderSpeechRecognizer;
    }

    /**
     * Set callback listener.
     *
     * @param listener The specified callback listener.
     */
    public void setListener(IRecorderSpeechRecognizerListener listener) {
        mListener = listener;
    }

    /**
     * Set speech recognizer.
     *
     * @param recognizer - Configured speech recognizer.
     */
    public void setRecognizer(SpeechRecognizer recognizer) {
        mRecognizer = recognizer;
    }

    /**
     * Set the identification to identify the End-user.
     * This is helpful in some of NLU/NLI functions, such as context support.
     *
     * @param cusId - End-user identifier.
     */
    public void setEndUserIdentifier(String cusId) {
        mRecognizer.setEndUserIdentifier(cusId);
    }

    /**
     * Set timeout in milliseconds of each HTTP API request.
     * Note that each process may contain more than one request.
     *
     * @param milliseconds - Timeout in milliseconds.
     */
    public void setTimeout(int milliseconds) {
        mRecognizer.setTimeout(milliseconds);
    }

    /**
     * Set length of end time of the VAD in milliseconds to stop voice recording automatically.
     *
     * @param milliseconds - length of end time in milliseconds for the speech input idle.
     */
    public void setLengthOfVADEnd(int milliseconds) {
        mVADEndMilliseconds = milliseconds;
    }

    /**
     * Set audio length in milliseconds to upload,
     * then the recognizer client will upload parts of audio once every milliseconds you set.
     *
     * @param milliseconds - How long of the audio in milliseconds do you want to upload once.
     */
    public void setSpeechUploadLength(int milliseconds) {
        if (mUploadAudioLengthMilliseconds < mMinUploadAudioLengthMilliseconds) {
            throw new IllegalArgumentException("The length in milliseconds cannot be less than "
                    + mMinUploadAudioLengthMilliseconds);
        }
        mUploadAudioLengthMilliseconds = milliseconds;
    }

    /**
     * Set the frequency in milliseconds of the recognition result query,
     * then the recognizer client will query the result once every milliseconds you set.
     *
     * @param milliseconds - How long in milliseconds do you want to query once.
     */
    public void setResultQueryFrequency(int milliseconds) {
        if (mFrequencyToGettingResult < mMinFrequencyToGettingResult) {
            throw new IllegalArgumentException("The frequency in milliseconds cannot be less than "
                    + mMinFrequencyToGettingResult);
        }
        mFrequencyToGettingResult = milliseconds;
    }

    /**
     * Get current recording state.
     *
     * @return - Recording state.
     */
    public RecordState getRecordState() {
        return mRecordState;
    }

    /**
     * Get current recognize process state.
     *
     * @return - Recognize process state.
     */
    public RecognizeState getRecognizeState() {
        return mRecognizeState;
    }

    /**
     * Enable microphone then start the voice recording and the recognize processing.
     *
     * @throws InterruptedException There is something wrong.
     * @throws IllegalStateException You are using this method in a wrong operation state.
     */
    public void start() throws InterruptedException, IllegalStateException {
        if (mRecordState != RecordState.STOPPED) {
            throw new IllegalStateException("The state of recording is not STOPPED.");
        }

        changeRecordState(RecordState.INITIALIZING);

        int wait = 0;
        while ((mRecognizeState != RecognizeState.STOPPED) && (mRecordDataQueue != null)) {
            if (wait >= 10) {
                changeRecordState(RecordState.ERROR);
                throw new InterruptedException("Threads handling state not correct.");
            }
            Thread.sleep(500);
            wait++;
        }

        mSendCallback = true;
        mCancel = false;
        mGetting = false;
        mIsFinal = false;
        mCapturedVoiceBegin = false;
        mCookie = new CookieSet();

        mRecordDataQueue = new LinkedBlockingQueue();

        // Init Recorder Thread
        mRecorderThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    doRecording();
                } catch (Exception e) {
                    changeRecordState(RecordState.ERROR);
                    mListener.onException(e);
                }
            }
        });
        mRecorderThread.start();

        // Init Sender Thread
        mSenderThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    doSending();
                } catch (Exception e) {
                    changeRecognizeState(RecognizeState.ERROR);
                    mListener.onException(e);
                }
            }
        });
        mSenderThread.start();

        // Init Getter Thread
        mGetterThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    doGetting();
                } catch (Exception e) {
                    changeRecognizeState(RecognizeState.ERROR);
                    mListener.onException(e);
                }
            }
        });
        mGetterThread.start();
    }

    /**
     * Stop the voice recorder and wait for the final recognition result.
     */
    public void stop() {
        changeRecordState(RecordState.STOPPING);
    }

    /**
     * Cancel all processes and give up to get recognition result.
     */
    public void cancel() {
        stop();
        mCancel = true;
    }

    /**
     * Stop or cancel all processes and then release resources.
     * This will not make any callback even if you use him to terminate any process.
     */
    public void release() {
        // Disable callback
        mSendCallback = false;
        // Force to cancel all processes.
        cancel();
        // Force to change state for re-init.
        initState();
    }

    private void initState() {
        changeRecordState(RecordState.STOPPED);
        changeRecognizeState(RecognizeState.STOPPED);
    }

    private void stopAndReleaseAudioRecord() {
        if ((mRecord != null) && (mRecord.getState() != AudioRecord.STATE_UNINITIALIZED)) {
            try {
                mRecord.stop();
                mRecord.release();
            } catch (Exception e) {
                Log.e(TAG, "stopAndReleaseAudioRecord() Exception: " + e.getMessage());
            }
        }
        mRecord = null;
    }

    /**
     * Enable/Disable to save the recorded audio to file.
     *
     * @param saveToFile - Set TRUE to enable, set FALSE to disable.
     * @param fileName - Name of the file you want to store the audio.
     */
    public void enableSaveRecordToFile(boolean saveToFile, String fileName) {
        mSaveRecordToFile = saveToFile;
        mRecordFileName = fileName;
    }

    private void doRecording() throws Exception {
        int minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE_44100,
                mAudioRecordOptionChannels,
                mAudioRecordOptionBitsPerFrame);
        mRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE_44100,
                mAudioRecordOptionChannels,
                mAudioRecordOptionBitsPerFrame,
                minBufferSize * 4);

        // Waiting for AudioRecord initialized
        int retry = 0;
        while ((mRecord.getState() != AudioRecord.STATE_INITIALIZED) && (retry < 4)) {
            Thread.sleep(500);
            retry++;
        }

        // Check AudioRecord is initialized or not
        if (mRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            throw new UnknownError("Failed to initialize AudioRecord.");
        } else {
            changeRecordState(RecordState.INITIALIZED);
            mRecord.startRecording();
            changeRecordState(RecordState.RECORDING);

            short[] audioData441 = new short[441 * RECORD_FRAMES];
            byte[] audioData = null;
            LinkedList<byte[]> tempInputs = new LinkedList<byte[]>();
            int reservedBlocks = (RESERVED_INPUT_LENGTH_MILLISECONDS / (RECORD_FRAMES * FRAME_LENGTH_MILLISECONDS));
            int vadTailBlocks = (mVADEndMilliseconds / (RECORD_FRAMES * FRAME_LENGTH_MILLISECONDS));
            int inputVolume = 0;
            int silence = 0;

            while (mRecordState == RecordState.RECORDING) {
                synchronized (mRecordDataQueue) {
                    if (mRecord.read(audioData441, 0, audioData441.length) == audioData441.length) {
                        audioData = new byte[mRecordDataSize];
                        convert441To16(audioData441, audioData);
                        saveRecordToFile(audioData, false);

                        inputVolume = getMicInputVolume(audioData);
                        mListener.onRecordVolumeChange(inputVolume);

                        if (!mCapturedVoiceBegin) {
                            if (inputVolume == 0) {
                                // Speech may not have started. Buffering silence audio as the head.
                                tempInputs.add(audioData);
                                if (tempInputs.size() > reservedBlocks) {
                                    tempInputs.removeFirst();
                                }
                            } else {
                                mCapturedVoiceBegin = true;
                                changeRecognizeState(RecognizeState.PROCESSING);
                                // Insert buffered silence audio into the beginning of the real speech input.
                                while (!tempInputs.isEmpty()) {
                                    mRecordDataQueue.put(tempInputs.poll());
                                }
                                // Then append the real speech data
                                mRecordDataQueue.put(audioData);
                            }
                        } else {
                            mRecordDataQueue.put(audioData);
                            if (inputVolume < VAD_TAIL_SILENCE_LEVEL) {
                                silence++;
                                if (silence > vadTailBlocks) {
                                    stop();
                                    break;
                                }
                            } else {
                                silence = 0;
                            }
                        }
                    }
                }
            };

            stopAndReleaseAudioRecord();
            saveRecordToFile(new byte[]{0}, true);
        }
    }

    private void doSending() throws Exception {
        int length = 0;
        mRecognizer.setAudioType(SpeechRecognizer.AUDIO_TYPE_PCM_RAW);
        mRecognizer.releaseAppendedAudio();

        while (!mCancel) {
            if (!mRecordDataQueue.isEmpty()) {
                byte[] audioData = (byte[]) mRecordDataQueue.take();
                mIsFinal = (isRecodingStopped() && (mRecordDataQueue.isEmpty()));
                length += ((audioData.length / mFrameSize) * FRAME_LENGTH_MILLISECONDS);
                mRecognizer.appendAudioFramesData(audioData);
                if ((length >= mUploadAudioLengthMilliseconds) || mIsFinal) {
                    APIResponse response = mRecognizer.flushToUploadAudio(mCookie, mIsFinal);
                    if (response.ok()) {
                        mGetting = true;
                        length = 0;
                    } else {
                        mRecognizer.releaseAppendedAudio();
                        recognizeResponseError(response);
                    }
                }
            } else {
                // Recorder stopped and the last audio sent at the same time, but mIsFinal = false.
                if (isRecodingStopped()) {
                    mIsFinal = true;
                    byte[] audioData = new byte[mRecordDataSize];
                    Arrays.fill(audioData, (byte) 0);
                    APIResponse response = mRecognizer.uploadAudio(mCookie, audioData, mIsFinal);
                    if (response.ok()) {
                        mGetting = true;
                    } else {
                        mRecognizer.releaseAppendedAudio();
                        recognizeResponseError(response);
                    }
                }
            }

            if (mIsFinal) {
                break;
            }
        }

        synchronized (mRecordDataQueue) {
            mRecordDataQueue.clear();
            mRecordDataQueue = null;
        }
    }

    private void doGetting() throws Exception {
        while (!mCancel) {
            if (mGetting) {
                Thread.sleep(mFrequencyToGettingResult);
                APIResponse response = mRecognizer.requestRecognitionWithAll(mCookie);
                if (response.ok() && response.hasData()) {
                    SpeechResult sttResult = response.getData().getSpeechResult();
                    if (mSendCallback) {
                        mListener.onRecognizeResultChange(response);
                    }
                    if (sttResult.complete()) {
                        changeRecognizeState(RecognizeState.COMPLETED);
                        break;
                    }
                } else {
                    recognizeResponseError(response);
                }
            }
        }
        changeRecognizeState(RecognizeState.STOPPED);
    }

    private void convert441To16(short[] from, byte[] to) {
        double ratio = (double) SAMPLE_RATE_44100 / (double) SAMPLE_RATE_16000;
        for (int i = 0; i < to.length / 2; i++) {
            double p = i * ratio;
            int m = (int) p;
            double delta = p - m;
            int n = m;
            if (delta != 0) {
                n = m + 1;
            }
            if (n >= from.length - 1) {
                n = from.length - 1;
            }

            short t = (short) (from[m] + (short) ((from[n] - from[m]) * delta));
            to[2 * i] = (byte) (t & 0x00ff);
            to[2 * i + 1] = (byte) ((t >> 8) & 0x00ff);
        }
    }

    private void changeRecordState(RecordState state) {
        mRecordState = state;

        if (mSendCallback || (state == RecordState.ERROR)) {
            mListener.onRecordStateChange(mRecordState);
        }

        if (mRecordState == RecordState.ERROR) {
            cancel();
        }
    }

    private void changeRecognizeState(RecognizeState state) {
        mRecognizeState = state;

        if (mSendCallback || (state == RecognizeState.ERROR)) {
            mListener.onRecognizeStateChange(mRecognizeState);
        }

        if (mRecognizeState == RecognizeState.STOPPED) {
            changeRecordState(RecordState.STOPPED);
        } else if (mRecognizeState == RecognizeState.ERROR) {
            cancel();
            changeRecognizeState(RecognizeState.STOPPED);
        }
    }

    private void recognizeResponseError(APIResponse response) {
        mListener.onServerError(response);
        changeRecognizeState(RecognizeState.ERROR);
    }

    private boolean isRecodingStopped() {
        if (mRecordState == RecordState.STOPPING) {
            // AudioRecord has been released, it means recorder thread is stopped.
            if (mRecord == null) {
                return true;
            } else {
                // Check AudioRecord state if recorder thread is still running.
                if (mRecord.getState() != AudioRecord.RECORDSTATE_RECORDING) {
                    return true;
                }
            }
        } else if (mRecordState == RecordState.STOPPED) {
            return true;
        }

        return false;
    }

    private void saveRecordToFile(byte[] buff, boolean isFinal) throws IOException {
        if (!mSaveRecordToFile) return;

        if (mRecordFile == null) {
            mRecordFile = new File(mRecordFilePath, mRecordFileName);
            mRecordFile.createNewFile();
            mOutputStream = new FileOutputStream(mRecordFile);
            mBufferedOutputStream = new BufferedOutputStream(mOutputStream);
            mDataOutputStream = new DataOutputStream(mBufferedOutputStream);
        }

        mDataOutputStream.write(buff);

        if (isFinal) {
            mDataOutputStream.close();
            mRecordFile = null;
        }
    }

    private int getMicInputVolume(byte[] data) {
        return (int) (mVoiceVolume.getNormalizeVolume(mVoiceVolume.getVoiceVolume(data)) * 2.5);
    }
}
