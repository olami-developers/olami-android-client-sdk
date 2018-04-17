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

import android.media.AudioRecord;
import android.os.Environment;

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

import ai.olami.android.jni.Codec;
import ai.olami.cloudService.APIConfiguration;
import ai.olami.cloudService.APIResponse;
import ai.olami.cloudService.CookieSet;
import ai.olami.cloudService.SpeechRecognizer;
import ai.olami.cloudService.SpeechResult;
import ai.olami.cloudService.NLIConfig;

public class RecorderSpeechRecognizer extends SpeechRecognizerBase{

    private final static String TAG = "OLAMI_RSR";

    private static RecorderSpeechRecognizer mRecorderSpeechRecognizer = null;
    private static AudioRecordManager mAudioRecordManager = null;

    private IRecorderSpeechRecognizerListener mListener = null;
    private CookieSet mCookie = null;
    private SpeechRecognizer mRecognizer = null;
    private AudioRecord mRecord = null;
    private NLIConfig mNLIConfig = null;

    private boolean mSendCallback = true;
    private boolean mGetting = false;
    private boolean mCancel = false;
    private boolean mIsFinal = false;
    private boolean mCapturedVoiceBegin = false;
    private boolean mSaveRecordToFile = false;
    private boolean mAutoStopRecordingFlag = true;

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

    private Codec mSpeexEncoder = null;

    private RecordState mRecordState = null;
    private RecognizeState mRecognizeState = null;

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

        setFrameSize(mRecognizer.getAudioFrameSize());
        setRecordDataSize(RECORD_FRAMES * getFrameSize());

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

        if (mAudioRecordManager == null) {
            try {
                mAudioRecordManager = AudioRecordManager.create();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
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
        mRecognizer.setSdkType(SDK_TYPE);
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
    public void setApiRequestTimeout(int milliseconds) {
        mRecognizer.setTimeout(milliseconds);
    }

    /**
     * Enable or disable automatic stop voice recording.
     *
     * @param enable - Set FALSE to disable.
     */
    public void enableAutoStopRecording(boolean enable) {
        mAutoStopRecordingFlag = enable;
    }

    /**
     * Check if automatic stop voice recording is enabled.
     *
     * @return - TRUE for enabled.
     */
    public boolean isAutoStopRecordingEnabled() {
        return mAutoStopRecordingFlag;
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
        start(null);
    }

    /**
     * Enable microphone then start the voice recording and the recognize processing.
     *
     * @param nliConfig - NLIConfig object.
     *
     * @throws InterruptedException There is something wrong.
     * @throws IllegalStateException You are using this method in a wrong operation state.
     */
    public void start(NLIConfig nliConfig)
            throws InterruptedException, IllegalStateException {

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
        mNLIConfig = nliConfig;

        mRecordDataQueue = new LinkedBlockingQueue();

        if (mRecord == null) {
            try {
                mRecord = mAudioRecordManager.getAudioRecord();
            } catch (Exception e) {
                changeRecordState(RecordState.ERROR);
                mListener.onException(e);
            }
        }

        changeRecordState(RecordState.INITIALIZED);

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
     * Stop or cancel all processes and then stopRecordingAndReleaseResources resources.
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
        mAudioRecordManager.stopAndRelease();
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
        mAudioRecordManager.startRecording();
        mRecord = mAudioRecordManager.getAudioRecord();

        changeRecordState(RecordState.RECORDING);

        short[] audioData441 = new short[441 * RECORD_FRAMES];
        byte[] audioData = null;
        LinkedList<byte[]> tempInputs = new LinkedList<byte[]>();
        int reservedBlocks = (RESERVED_INPUT_LENGTH_MILLISECONDS / (RECORD_FRAMES * FRAME_LENGTH_MILLISECONDS));
        int instantNoiseBlocks = (INSTANT_NOISE_LENGTH_MILLISECONDS / (RECORD_FRAMES * FRAME_LENGTH_MILLISECONDS));
        int vadTailBlocks = (getVADEndMilliseconds() / (RECORD_FRAMES * FRAME_LENGTH_MILLISECONDS));
        int inputVolume = 0;
        int priInputVolume = 0;
        int silence = 0;
        int instantNoise = 0;

        while (mRecordState == RecordState.RECORDING) {
            synchronized (mRecordDataQueue) {
                if (mRecord.read(audioData441, 0, audioData441.length) == audioData441.length) {
                    audioData = new byte[getRecordDataSize()];
                    AudioRecordManager.convert441To16(audioData441, audioData);
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
                        if (inputVolume > getSilenceLevel()) {
                            if (silence == 0) {
                                instantNoise = 0;
                            }
                            if (silence > 0) {
                                if (instantNoise == 0) {
                                    instantNoise++;
                                } else {
                                    if ((instantNoise < instantNoiseBlocks)
                                            && (priInputVolume > inputVolume)) {
                                        instantNoise++;
                                    } else {
                                        silence = 0;
                                    }
                                }
                            }
                        } else {
                            silence++;
                        }
                        if (silence > vadTailBlocks) {
                            if (mAutoStopRecordingFlag) {
                                stop();
                                break;
                            }
                        }
                    }

                    priInputVolume = inputVolume;
                }
            }
        }

        stopAndReleaseAudioRecord();
        saveRecordToFile(new byte[]{0}, true);
    }

    private void doSending() throws Exception {
        int length = 0;
        mRecognizer.setAudioType(SpeechRecognizer.AUDIO_TYPE_PCM_RAW);
        mRecognizer.releaseAppendedAudio();

        while (!mCancel) {
            if (!mRecordDataQueue.isEmpty()) {
                byte[] audioData = (byte[]) mRecordDataQueue.take();
                mIsFinal = (isRecodingStopped() && (mRecordDataQueue.isEmpty()));
                length += ((audioData.length / getFrameSize()) * FRAME_LENGTH_MILLISECONDS);
                if (getAudioCompressLibraryType() == AUDIO_COMPRESS_LIBRARY_TYPE_CPP) {
                    if (mSpeexEncoder == null) {
                        mSpeexEncoder = new Codec();
                        mSpeexEncoder.open(1, 10);
                    }
                    byte[] encBuffer = new byte[audioData.length];
                    int encSize = mSpeexEncoder.encodeByte(audioData, 0, audioData.length, encBuffer);
                    mRecognizer.setAudioType(SpeechRecognizer.AUDIO_TYPE_PCM_SPEEX);
                    mRecognizer.appendSpeexAudioFramesData(encBuffer, encSize);
                } else {
                    mRecognizer.setAudioType(SpeechRecognizer.AUDIO_TYPE_PCM_RAW);
                    mRecognizer.appendAudioFramesData(audioData);
                }
                if ((length >= getUploadAudioLengthMilliseconds()) || mIsFinal) {
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
                    byte[] audioData = new byte[getRecordDataSize()];
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

        if (mSpeexEncoder != null) {
            mSpeexEncoder.close();
            mSpeexEncoder = null;
        }

        synchronized (mRecordDataQueue) {
            mRecordDataQueue.clear();
            mRecordDataQueue = null;
        }
    }

    private void doGetting() throws Exception {
        while (!mCancel) {
            if (mGetting) {
                Thread.sleep(getFrequencyToGettingResult());
                APIResponse response = null;
                switch (getRecognizeResultType()) {
                    case RECOGNIZE_RESULT_TYPE_ALL:
                        response = mRecognizer.requestRecognitionWithAll(mCookie, mNLIConfig);
                        break;
                    case RECOGNIZE_RESULT_TYPE_STT:
                        response = mRecognizer.requestRecognition(mCookie);
                        break;
                    case RECOGNIZE_RESULT_TYPE_NLI:
                        response = mRecognizer.requestRecognitionWithNLI(mCookie, mNLIConfig);
                        break;
                }
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
