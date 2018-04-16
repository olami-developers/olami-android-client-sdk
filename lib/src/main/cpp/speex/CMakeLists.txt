# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.4.1)

SET(SOURCE_FILES
    libspeex/kiss_fft.c libspeex/kiss_fftr.c
    libspeex/cb_search.c 	libspeex/exc_10_32_table.c 	libspeex/exc_8_128_table.c
    libspeex/filters.c 	libspeex/gain_table.c 	libspeex/hexc_table.c 	libspeex/high_lsp_tables.c 	libspeex/lsp.c
    libspeex/ltp.c 	libspeex/speex.c 	libspeex/stereo.c 	libspeex/vbr.c 	libspeex/vq.c libspeex/bits.c libspeex/exc_10_16_table.c
    libspeex/exc_20_32_table.c libspeex/exc_5_256_table.c libspeex/exc_5_64_table.c libspeex/gain_table_lbr.c libspeex/hexc_10_32_table.c
    libspeex/lpc.c libspeex/lsp_tables_nb.c libspeex/modes.c libspeex/modes_wb.c libspeex/nb_celp.c libspeex/quant_lsp.c libspeex/sb_celp.c
    libspeex/speex_callbacks.c libspeex/speex_header.c libspeex/window.c
    speex_jni.cpp
)
INCLUDE_DIRECTORIES(include)
set(CMAKE_BUILD_TYPE Release)
SET(CMAKE_C_FLAGS_DEBUG "-DFLOATING_POINT -DUSE_KISS_FFT -DEXPORT=\"\" -UHAVE_CONFIG_H")
SET(CMAKE_C_FLAGS_RELEASE "-DFLOATING_POINT -DUSE_KISS_FFT -DEXPORT=\"\" -UHAVE_CONFIG_H")

add_library( speexjni SHARED ${SOURCE_FILES})
# find_library( log-lib log )
# target_link_libraries( speex ${log-lib} )