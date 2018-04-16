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

#include <jni.h>
#include <string>
#include <speex/speex.h>

#ifdef __cplusplus
extern "C" {
#endif

static SpeexBits ebits, dbits;
void *enc_state;
void *dec_state;
static int dec_frame_size;
static int enc_frame_size;


JNIEXPORT jint JNICALL
Java_ai_olami_android_jni_Codec_open(JNIEnv *env, jobject instance, jint mode, jint quality) {
    int tmp;
    speex_bits_init(&ebits);
    speex_bits_init(&dbits);

    if (mode == 0) {
        enc_state = speex_encoder_init(&speex_nb_mode);
        dec_state = speex_decoder_init(&speex_nb_mode);
    }
    else if (mode == 1) {
        enc_state = speex_encoder_init(&speex_wb_mode);
        dec_state = speex_decoder_init(&speex_wb_mode);
    }
    else if (mode == 2) {
        enc_state = speex_encoder_init(&speex_uwb_mode);
        dec_state = speex_decoder_init(&speex_uwb_mode);
    }

    tmp = quality;
    speex_encoder_ctl(enc_state, SPEEX_SET_QUALITY, &tmp);
    speex_encoder_ctl(enc_state, SPEEX_GET_FRAME_SIZE, &enc_frame_size);
    speex_decoder_ctl(dec_state, SPEEX_GET_FRAME_SIZE, &dec_frame_size);

    return (jint)0;

}

JNIEXPORT jint JNICALL
Java_ai_olami_android_jni_Codec_getFrameSize(JNIEnv *env, jobject instance) {
    return (jint)enc_frame_size;
}

JNIEXPORT jint JNICALL
Java_ai_olami_android_jni_Codec_decode(JNIEnv *env, jobject instance, jbyteArray encoded,
                                            jshortArray lin, jint size) {
    jbyte buffer[dec_frame_size];
    jshort output_buffer[dec_frame_size];
    jsize encoded_length = size;

    env->GetByteArrayRegion(encoded, 0, encoded_length, buffer);
    speex_bits_read_from(&dbits, (char *)buffer, encoded_length);
    speex_decode_int(dec_state, &dbits, output_buffer);
    env->SetShortArrayRegion(lin, 0, dec_frame_size,
                             output_buffer);

    return (jint)dec_frame_size;
}

JNIEXPORT jint JNICALL
Java_ai_olami_android_jni_Codec_encode(JNIEnv *env, jobject instance, jshortArray lin,
                                            jint offset, jint size, jbyteArray encoded) {
    jshort buffer[enc_frame_size];
    jbyte output_buffer[enc_frame_size];
    int nFrames = size / enc_frame_size;
    int i, tot_bytes = 0;
    int encodedLen = 0;

    for (i = 0; i < nFrames; i++) {
        env->GetShortArrayRegion(lin, offset + i*enc_frame_size, enc_frame_size, buffer);
        speex_bits_reset(&ebits);
        speex_encode_int(enc_state, buffer, &ebits);
        encodedLen = speex_bits_write(&ebits, (char *)output_buffer, enc_frame_size);
        env->SetByteArrayRegion(encoded, tot_bytes, encodedLen, output_buffer);
        tot_bytes += encodedLen;
    }
    return (jint)tot_bytes;
}

JNIEXPORT void JNICALL
Java_ai_olami_android_jni_Codec_close(JNIEnv *env, jobject instance) {
    speex_bits_destroy(&ebits);
    speex_bits_destroy(&dbits);
    speex_decoder_destroy(dec_state);
    speex_encoder_destroy(enc_state);
}

#ifdef __cplusplus
}
#endif