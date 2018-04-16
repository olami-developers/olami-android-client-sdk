/*
	Copyright 2018, VIA Technologies, Inc. & OLAMI Team.

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

package ai.olami.android.jni;

public class Codec {
    static
    {
        try
        {
            System.loadLibrary("speexjni");
        } catch (UnsatisfiedLinkError e)
        {
            e.printStackTrace();
        }
    }

    private static void convertByte2Short(
            byte[] byData,
            int offset1,
            short[] sData,
            int offset2,
            int sizeFloat
    ) {
        if(byData.length - offset1 < 2 * sizeFloat) {
            throw new IllegalArgumentException("Insufficient Samples to convert to floats");
        } else if(sData.length - offset2 < sizeFloat) {
            throw new IllegalArgumentException("Insufficient float buffer to convert the samples");
        } else {
            for(int i = 0; i < sizeFloat; ++i) {
                sData[offset2 + i] = (short)(byData[offset1 + 2 * i] & 255 | byData[offset1 + 2 * i + 1] << 8);
            }

        }
    }

    public int encodeByte(
            byte lin[],
            int offset,
            int size,
            byte encoded[]
    ) {
        short[] sData = new short[size / 2];
        convertByte2Short(lin, offset, sData, 0, size / 2);
        return encode(sData, 0, size / 2, encoded);
    }

    public native int open(int mode, int quality);
    public native int getFrameSize();
    public native int decode(byte encoded[], short lin[], int size);
    public native int encode(short lin[], int offset, int size, byte encoded[]);
    public native void close();
}
