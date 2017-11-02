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
import android.util.Log;

import ai.olami.cloudService.SpeechRecognizer;

public class AudioRecordManager {
    private final static String TAG = "AudioRecordManager";

    private static AudioRecord mAudioRecord = null;
    private static AudioRecordManager mAudioRecordFactory = null;

    public static final int SAMPLE_RATE_44100 = 44100;
    public static final int SAMPLE_RATE_16000 = 16000;

    private void AudioRecordManager() {

    }

    /**
     * Create a KeepRecordingSpeechRecognizer instance by a specified speech recognizer.
     *
     * @return AudioRecordManager instance.
     */
    public static AudioRecordManager create() throws Exception{
        if (mAudioRecordFactory == null) {
            mAudioRecordFactory = new AudioRecordManager();
            mAudioRecordFactory.initializeAudioRecord();
        }
        return mAudioRecordFactory;
    }

    /**
     * Convert audio sample rate from 44100 to 16000.
     *
     * @param from - Source buffer.
     * @param to - Target buffer.
     */
    public static void convert441To16(short[] from, byte[] to) {
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

    /**
     * Get AudioRecord.
     *
     * @return AudioRecord object instance.
     */
    public AudioRecord getAudioRecord() {
        if (mAudioRecord != null && mAudioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
            return mAudioRecord;
        } else {
            return null;
        }
    }

    /**
     * Enable microphone then start the voice recording.
     *
     * @throws Exception There is something wrong.
     */
    public void startRecording() throws Exception {
        if (mAudioRecord == null) {
            initializeAudioRecord();
        }
        mAudioRecord.startRecording();
    }

    /**
     * Get the normal supported sample rate
     *
     * @return Sample rate
     */
    public static int getSampleRateConfig() {
        return SAMPLE_RATE_44100;
    }

    /**
     * Get the normal supported audio channel setting
     *
     * @return Audio channels
     */
    public static int getAudioChannelConfig() {
        switch (SpeechRecognizer.AUDIO_CHANNELS) {
            case 1:
                return AudioFormat.CHANNEL_IN_MONO;
        }

        return AudioFormat.CHANNEL_IN_MONO;
    }

    /**
     * Get the normal supported audio data encoding
     *
     * @return Audio data encoding
     */
    public static int getAudioFormatConfig() {
        switch (SpeechRecognizer.AUDIO_CHANNELS) {
            case 16:
                return AudioFormat.ENCODING_PCM_16BIT;
        }

        return AudioFormat.ENCODING_PCM_16BIT;
    }

    private void initializeAudioRecord() throws Exception {
        int minBufferSize = AudioRecord.getMinBufferSize(
                getSampleRateConfig(),
                getAudioChannelConfig(),
                getAudioFormatConfig());

        if (mAudioRecord == null) {
            mAudioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    getSampleRateConfig(),
                    getAudioChannelConfig(),
                    getAudioFormatConfig(),
                    minBufferSize * 4);
        }

        Log.i(TAG, "AudioRecord select sample rate is : "+ mAudioRecord.getSampleRate());

        // Waiting for AudioRecord initialized
        int retry = 0;
        while ((mAudioRecord.getState() != AudioRecord.STATE_INITIALIZED) && (retry < 4)) {
            Thread.sleep(500);
            retry++;
        }

        // Check AudioRecord is initialized or not
        if (mAudioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            if (mAudioRecord.getState() == AudioRecord.STATE_UNINITIALIZED) {
                throw new UnsupportedOperationException("Init AudioRecord failed. Permission issue?");
            } else {
                throw new UnknownError("Failed to initialize AudioRecord.");
            }
        }
    }

    /**
     * Stop and release resource.
     *
     */
    public void stopAndRelease() {
        if ((mAudioRecord != null) && (mAudioRecord.getState() != AudioRecord.STATE_UNINITIALIZED)) {
            try {
                mAudioRecord.stop();
                mAudioRecord.release();
            } catch (Exception e) {
                Log.e(TAG, "stopAndRelease() Exception: " + e.getMessage());
            }
        }
        mAudioRecord = null;
    }
}
