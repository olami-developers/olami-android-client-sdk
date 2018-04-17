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
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import ai.olami.android.jni.Codec;
import ai.olami.cloudService.APIConfiguration;
import ai.olami.cloudService.APIResponse;
import ai.olami.cloudService.CookieSet;
import ai.olami.cloudService.NLIConfig;
import ai.olami.cloudService.SpeechRecognizer;
import ai.olami.cloudService.SpeechResult;

public class KeepRecordingSpeechRecognizer extends SpeechRecognizerBase {

    private final static String TAG = "KRSR";

    private static KeepRecordingSpeechRecognizer mKeepRecordingSpeechRecognizer = null;
    private static AudioRecordManager mAudioRecordManager = null;

    private IKeepRecordingSpeechRecognizerListener mCallback = null;
    private SpeechRecognizer mRecognizer = null;
    private AudioRecord mAudioRecord = null;

    private CookieSet mCookie = null;
    private NLIConfig mNLIConfig = null;

    private BlockingQueue mRecordDataQueue = null;

    private boolean mSendCallback = false;
    private boolean mRecording = false;
    private boolean mRecordStopped = false;
    private boolean mGetting = false;
    private boolean mCancel = false;
    private boolean mIsFinal = false;
    private boolean mCapturedVoiceBegin = false;
    private boolean mSaveRecordToFile = false;

    private int mRecognizerTimeout = 5000;

    private Thread mRecorderThread = null;
    private Thread mSenderThread = null;
    private Thread mGetterThread = null;

    private File mRecordFile = null;
    private OutputStream mOutputStream = null;
    private BufferedOutputStream mBufferedOutputStream = null;
    private DataOutputStream mDataOutputStream = null;
    private String mRecordFilePath = Environment.getExternalStorageDirectory().getPath() +"/musicbox/";
    private String mRecordFileName = "OLAMI-mic-record.pcm";

    private VoiceVolume mVoiceVolume = new VoiceVolume();

    private Codec mSpeexEncoder = null;

    private RecognizeState mRecognizeState = null;

    /**
     * Recognize process state
     */
    public enum RecognizeState {
        INITIALIZING,
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

    private KeepRecordingSpeechRecognizer(
            IKeepRecordingSpeechRecognizerListener listener,
            SpeechRecognizer recognizer
    ) {
        setListener(listener);
        setRecognizer(recognizer);

        setFrameSize(mRecognizer.getAudioFrameSize());
        setRecordDataSize(RECORD_FRAMES * getFrameSize());
    }

    /**
     * Create a KeepRecordingSpeechRecognizer instance.
     *
     * @param recognizeListener - The specified callback listener.
     * @param config - API configurations.
     * @return KeepRecordingSpeechRecognizer instance.
     */
    public static KeepRecordingSpeechRecognizer create(
            IKeepRecordingSpeechRecognizerListener recognizeListener,
            APIConfiguration config
    ) throws Exception{
        return create(recognizeListener, new SpeechRecognizer(config));
    }

    /**
     * Create a KeepRecordingSpeechRecognizer instance by a specified speech recognizer.
     *
     * @param recognizeListener - The specified callback listener.
     * @param recognizer - Configured speech recognizer.
     * @return IotRecorderSpeechRecognizer instance.
     */
    public static KeepRecordingSpeechRecognizer create(
            IKeepRecordingSpeechRecognizerListener recognizeListener,
            SpeechRecognizer recognizer
    ) throws Exception {
        if (mKeepRecordingSpeechRecognizer == null) {
            mKeepRecordingSpeechRecognizer = new KeepRecordingSpeechRecognizer(recognizeListener, recognizer);
        } else {
            mKeepRecordingSpeechRecognizer.stopRecordingAndReleaseResources();
            mKeepRecordingSpeechRecognizer = new KeepRecordingSpeechRecognizer(recognizeListener, recognizer);
        }

        mKeepRecordingSpeechRecognizer.changeRecognizeState(RecognizeState.INITIALIZING);

        if (mAudioRecordManager == null) {
            mAudioRecordManager = AudioRecordManager.create();
        }

        mKeepRecordingSpeechRecognizer.initRecognizeState();

        return mKeepRecordingSpeechRecognizer;
    }

    /**
     * Set callback listener.
     *
     * @param listener The specified callback listener.
     */
    public void setListener(IKeepRecordingSpeechRecognizerListener listener) {
        mCallback = listener;
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
     * Set timeout in milliseconds of each recognize process (begin-to-end).
     * The recognize process will be cancelled if timeout and reset the state.
     *
     * @param milliseconds - Timeout in milliseconds. Default is 5000.
     */
    public void setRecognizerTimeout(int milliseconds) {
        mRecognizerTimeout = milliseconds;
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
     * Stop audio recording and release all resources.
     */
    public void stopRecordingAndReleaseResources() {
        // Disable callback
        mSendCallback = false;
        // Force to cancel all processes.
        cancelRecognizing();

        if (mAudioRecordManager != null) {
            mAudioRecordManager.stopAndRelease();
        }

        mAudioRecord = null;
        mAudioRecordManager = null;

        // Force to change state for re-startRecording.
        initRecognizeState();

        mKeepRecordingSpeechRecognizer = null;
    }

    /**
     * Enable microphone then start the voice recording.
     *
     * @throws Exception There is something wrong.
     */
    public void startRecording() throws Exception {
        mAudioRecordManager.startRecording();
        setAudioRecord(mAudioRecordManager.getAudioRecord());
    }

    /**
     * Start the recognize processing.
     * The voice recording must be started before you use this method.
     *
     * @throws InterruptedException There is something wrong.
     */
    public void startRecognizing() throws InterruptedException {
        startRecognizing(null);
    }

    /**
     * Start the recognize processing.
     * The voice recording must be started before you use this method.
     *
     * @param nliConfig - NLIConfig object.
     *
     * @throws InterruptedException There is something wrong.
     */
    public void startRecognizing(NLIConfig nliConfig) throws InterruptedException {

        int wait = 0;
        while (mRecognizeState != RecognizeState.STOPPED) {
            if (wait >= 10) {
                changeRecognizeState(RecognizeState.ERROR);
                throw new InterruptedException("Threads handling state not correct.");
            }
            Thread.sleep(500);
            wait++;
        }

        mSendCallback = true;
        mCancel = false;
        mRecording = true;
        mRecordStopped = false;
        mGetting = false;
        mIsFinal = false;
        mCapturedVoiceBegin = false;
        mCookie = new CookieSet();
        mNLIConfig = nliConfig;

        mRecordDataQueue = new LinkedBlockingQueue();

        changeRecognizeState(RecognizeState.PROCESSING);

        // Init Recorder Thread
        mRecorderThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    doRecording();
                } catch (Exception e) {
                    changeRecognizeState(RecognizeState.ERROR);
                    mCallback.onException(e);
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
                    mCallback.onException(e);
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
                    mCallback.onException(e);
                }
            }
        });
        mGetterThread.start();

        // Check to see if recognize process timeout.
        checkRecognizeTimeout();

    }

    /**
     * Stop the recognize process and wait for the final recognition result.
     *
     */
    public void stopRecognizing() {
        if (mRecording) {
            mRecording = false;

            // Waite for the recording stopped.
            while (!mRecordStopped) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    mCallback.onException(e);
                }
            }
        }
    }

    /**
     * Cancel all processes and give up to get recognition result.
     *
     */
    public void cancelRecognizing() {
        mSendCallback = false;

        stopRecognizing();
        mCancel = true;

        changeRecognizeState(RecognizeState.STOPPED);
    }

    /**
     * Get AudioRecord that used by the KeepRecordingSpeechRecognizer instance.
     *
     */
    public AudioRecord getAudioRecord() {
        return mAudioRecord;
    }

    /**
     * Set AudioRecord for the KeepRecordingSpeechRecognizer instance.
     *
     */
    public void setAudioRecord(AudioRecord audioRecord) {
        mAudioRecord = audioRecord;
    }

    /**
     * Enable/Disable to save the recorded audio to file.
     *
     * @param saveToFile - Set TRUE to enable, set FALSE to disable.
     */
    public void enableSaveRecordToFIle(boolean saveToFile) {
        enableSaveRecordToFile(saveToFile, getDateTime() +".pcm");
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

        while (mRecording) {
            synchronized (mRecordDataQueue) {
                if (mAudioRecord.read(audioData441, 0, audioData441.length) == audioData441.length) {
                    audioData = new byte[getRecordDataSize()];
                    AudioRecordManager.convert441To16(audioData441, audioData);
                    saveRecordToFile(audioData, false);

                    inputVolume = getMicInputVolume(audioData);
                    mCallback.onRecordVolumeChange(inputVolume);

                    if (!mCapturedVoiceBegin) {
                        if (inputVolume == 0) {
                            // Speech may not have started. Buffering silence audio as the head.
                            tempInputs.add(audioData);
                            if (tempInputs.size() > reservedBlocks) {
                                tempInputs.removeFirst();
                            }
                        } else {
                            mCapturedVoiceBegin = true;
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
                            break;
                        }
                    }

                    priInputVolume = inputVolume;
                }
            }
        }

        mRecordStopped = true;

        saveRecordToFile(new byte[]{0}, true);
        stopRecognizing();
    }

    private void doSending() throws Exception {
        int length = 0;
        mRecognizer.releaseAppendedAudio();

        while (!mCancel) {
            if (mRecordDataQueue != null) {
                if (!mRecordDataQueue.isEmpty()) {
                    byte[] audioData = (byte[]) mRecordDataQueue.take();
                    mIsFinal = (isRecognizerStopped() && (mRecordDataQueue.isEmpty()));
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
                    if (isRecognizerStopped()) {
                        mIsFinal = true;
                        mRecognizer.setAudioType(SpeechRecognizer.AUDIO_TYPE_PCM_RAW);
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
        }

        if (mSpeexEncoder != null) {
            mSpeexEncoder.close();
            mSpeexEncoder = null;
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
                        mCallback.onRecognizeResultChange(response);
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

    private boolean isRecognizerStopped() {
        if (mRecording) {
            return false;
        } else {
            return true;
        }
    }

    private void changeRecognizeState(RecognizeState state) {
        mRecognizeState = state;

        if (mSendCallback || (state == RecognizeState.ERROR)) {
            mCallback.onRecognizeStateChange(mRecognizeState);
        }

        if (mRecognizeState == RecognizeState.ERROR) {
            cancelRecognizing();
        }
    }

    private void recognizeResponseError(APIResponse response) {
        mCallback.onServerError(response);
        changeRecognizeState(RecognizeState.ERROR);
    }

    private void initRecognizeState() {
        changeRecognizeState(RecognizeState.STOPPED);
    }

    private int getMicInputVolume(byte[] data) {
        return (int) (mVoiceVolume.getNormalizeVolume(mVoiceVolume.getVoiceVolume(data)) * 2.5);
    }

    private void checkRecognizeTimeout() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                int count = 0;
                while(count < (mRecognizerTimeout / 100)) {
                    if (mRecognizeState == RecognizeState.STOPPED) {
                        break;
                    }

                    if (mCapturedVoiceBegin && !mRecordStopped) {
                        continue;
                    }

                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        mCallback.onException(e);
                    }

                    if (count >= (mRecognizerTimeout / 100) - 1) {
                        if (mRecognizeState != RecognizeState.STOPPED) {
                            cancelRecognizing();
                            mCallback.onRecognizeStateChange(RecognizeState.STOPPED);
                        }
                    }
                    count++;
                }
            }
        }).start();
    }

    private String getDateTime()
    {
        String ret = "";
        Date date = new Date();
        SimpleDateFormat dtFormat = new SimpleDateFormat("yyyyMMdd-HHmmss.SSS");
        ret = dtFormat.format(date);
        return ret;
    }
}
