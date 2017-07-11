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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class VoiceVolume {

    public static final int VOLUME_LEVEL = 12;

    /**
     * Get audio volume from audio buffer.
     *
     * @param data - Audio buffer.
     * @return Volume.
     */
    public int getVoiceVolume(byte[] data){
        ByteBuffer byteBuffer = ByteBuffer.wrap(data);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);

        int v = 0;
        for (int i = 0 ; i < data.length ; i+=2) {
            if (byteBuffer.getShort(i) > v) {
                v = Math.abs(byteBuffer.getShort(i));
            }
        }
        return v;
    }

    /**
     * Get normalize audio volume level.
     *
     * @param volume - Volume.
     * @return Volume level.
     */
    public int getNormalizeVolume(int volume) {
        int nowVolumeMax = 10000;

        final int MIN_VOLUME = 1;
        final int MAX_VOLUME = 32767;
        if (volume > nowVolumeMax) {
            nowVolumeMax = (int) (volume * 1.5);
        }
        if (nowVolumeMax > MAX_VOLUME) {
            nowVolumeMax = MAX_VOLUME;
        }

        int v = volume - MIN_VOLUME;
        if(v < 0){
            v = 0;
        } else if (v > (nowVolumeMax - MIN_VOLUME)) {
            v = (nowVolumeMax - MIN_VOLUME);
        }

        int normalizeVolume = (int) ((v / (float) (nowVolumeMax - MIN_VOLUME + 1)) * (VOLUME_LEVEL + 1));

        return normalizeVolume;
    }
}
