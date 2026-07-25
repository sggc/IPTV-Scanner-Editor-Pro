/*
 * AV3A (Audio Vivid / AVS3-P3) decoder wrapper for FFmpeg
 * Uses dlopen/dlsym to dynamically load libavs3a_decoder.so at runtime.
 *
 * This file is injected into FFmpeg's libavcodec during the mpv build.
 * No compile-time dependency on libavs3a_decoder.so is needed.
 * The .so is shipped alongside libavcodec.so and loaded at runtime.
 *
 * API reverse-engineered from av3a_jni.cc (sggc/media repo):
 *   void* avs3_create_decoder(void);
 *   void  avs3_destroy_decoder(void* decoder);
 *   int   parse_header(void* decoder, const uint8_t* input, int input_size,
 *                       int first_frame, int* header_consumed, void* unknown);
 *   int   avs3_decode(void* decoder, const uint8_t* input, int input_size,
 *                      uint8_t* output, int* output_bytes, int* payload_consumed);
 *
 * The decoder struct has fields:
 *   int numChansOutput  (channel count, accessed after first decode)
 *   int outputFs        (output sample rate, accessed after first decode)
 * Since we don't have the header, we scan the struct for these values.
 */

#include "avcodec.h"
#include "codec_internal.h"
#include "decode.h"
#include "libavutil/log.h"

#include <dlfcn.h>
#include <stdlib.h>
#include <string.h>

/* AV3A decoder function pointer types */
typedef void* (*av3a_create_fn)(void);
typedef void  (*av3a_destroy_fn)(void*);
typedef int   (*av3a_parse_header_fn)(void*, const unsigned char*, int, int, int*, void*);
typedef int   (*av3a_decode_fn)(void*, const unsigned char*, int, unsigned char*, int*, int*);

typedef struct AV3AContext {
    void *lib_handle;
    void *decoder;
    av3a_create_fn      fn_create;
    av3a_destroy_fn     fn_destroy;
    av3a_parse_header_fn fn_parse_header;
    av3a_decode_fn      fn_decode;
    int first_frame;
    int channels;
    int sample_rate;
} AV3AContext;

/*
 * Try to find numChansOutput and outputFs in the decoder struct.
 * The JNI code accesses decoder->numChansOutput and decoder->outputFs.
 * Without the header, we scan the first 256 ints for reasonable values.
 */
static void av3a_probe_decoder_info(AVCodecContext *avctx, AV3AContext *s)
{
    s->channels = 2;
    s->sample_rate = 48000;

    if (!s->decoder) return;

    /* Scan the first 256 int-sized fields for sample rate + channel count */
    const int *ints = (const int *)s->decoder;
    for (int i = 0; i < 256; i++) {
        int val = ints[i];
        /* Check for common sample rates */
        if (val == 48000 || val == 44100 || val == 24000 ||
            val == 16000 || val == 8000  || val == 96000 ||
            val == 192000 || val == 32000 || val == 22050) {
            /* Look nearby for a valid channel count */
            for (int j = i - 4; j <= i + 4; j++) {
                if (j < 0 || j >= 256 || j == i) continue;
                int ch = ints[j];
                if (ch >= 1 && ch <= 64) {
                    s->sample_rate = val;
                    s->channels = ch;
                    av_log(avctx, AV_LOG_INFO,
                           "AV3A: probed sample_rate=%d channels=%d (offsets %d,%d)\n",
                           val, ch, i, j);
                    return;
                }
            }
        }
    }
    av_log(avctx, AV_LOG_WARNING,
           "AV3A: could not probe decoder info, using defaults (48000 Hz, 2ch)\n");
}

static av_cold int av3a_decode_init(AVCodecContext *avctx)
{
    AV3AContext *s = avctx->priv_data;

    s->lib_handle = dlopen("libavs3a_decoder.so", RTLD_LAZY);
    if (!s->lib_handle) {
        av_log(avctx, AV_LOG_ERROR, "AV3A: dlopen failed: %s\n", dlerror());
        return AVERROR_DECODER_NOT_FOUND;
    }

    s->fn_create      = (av3a_create_fn)      dlsym(s->lib_handle, "avs3_create_decoder");
    s->fn_destroy     = (av3a_destroy_fn)     dlsym(s->lib_handle, "avs3_destroy_decoder");
    s->fn_parse_header = (av3a_parse_header_fn) dlsym(s->lib_handle, "parse_header");
    s->fn_decode      = (av3a_decode_fn)      dlsym(s->lib_handle, "avs3_decode");

    if (!s->fn_create || !s->fn_destroy || !s->fn_parse_header || !s->fn_decode) {
        av_log(avctx, AV_LOG_ERROR, "AV3A: missing symbols in libavs3a_decoder.so\n");
        dlclose(s->lib_handle);
        s->lib_handle = NULL;
        return AVERROR_DECODER_NOT_FOUND;
    }

    s->decoder = s->fn_create();
    if (!s->decoder) {
        av_log(avctx, AV_LOG_ERROR, "AV3A: avs3_create_decoder returned NULL\n");
        dlclose(s->lib_handle);
        s->lib_handle = NULL;
        return AVERROR_DECODER_NOT_FOUND;
    }

    s->first_frame = 1;
    s->channels = 2;
    s->sample_rate = 48000;

    avctx->sample_fmt = AV_SAMPLE_FMT_S16;
    avctx->sample_rate = s->sample_rate;
    av_channel_layout_default(&avctx->ch_layout, s->channels);

    av_log(avctx, AV_LOG_INFO, "AV3A: decoder initialized (dlopen + dlsym)\n");
    return 0;
}

static int av3a_decode_frame(AVCodecContext *avctx, AVFrame *frame,
                              int *got_frame_ptr, AVPacket *avpkt)
{
    AV3AContext *s = avctx->priv_data;
    uint8_t temp_output[65536]; /* 64 KB stack buffer (matches JNI minimum) */

    *got_frame_ptr = 0;

    if (!avpkt->data || avpkt->size <= 0)
        return 0;

    /* Step 1: parse header */
    int header_consumed = 0;
    s->fn_parse_header(s->decoder, avpkt->data, avpkt->size,
                       s->first_frame ? 1 : 0, &header_consumed, NULL);

    if (header_consumed <= 0 || header_consumed >= avpkt->size) {
        av_log(avctx, AV_LOG_DEBUG,
               "AV3A: parse_header: consumed=%d size=%d\n",
               header_consumed, avpkt->size);
        return avpkt->size;
    }

    /* Step 2: decode payload into temp buffer */
    int output_bytes = 0;
    int payload_consumed = 0;
    s->fn_decode(s->decoder,
                 avpkt->data + header_consumed,
                 avpkt->size - header_consumed,
                 temp_output, &output_bytes, &payload_consumed);

    if (output_bytes <= 0) {
        av_log(avctx, AV_LOG_DEBUG,
               "AV3A: decode: output=%d payload_consumed=%d\n",
               output_bytes, payload_consumed);
        return avpkt->size;
    }

    /* Step 3: probe decoder info after first successful decode */
    if (s->first_frame) {
        av3a_probe_decoder_info(avctx, s);
        avctx->sample_rate = s->sample_rate;
        av_channel_layout_uninit(&avctx->ch_layout);
        av_channel_layout_default(&avctx->ch_layout, s->channels);
        s->first_frame = 0;
        av_log(avctx, AV_LOG_INFO,
               "AV3A: format set to %d Hz, %d ch\n",
               s->sample_rate, s->channels);
    }

    /* Step 4: allocate FFmpeg frame and copy decoded data */
    int bytes_per_sample = 2; /* S16 */
    int nb_samples = output_bytes / (s->channels * bytes_per_sample);
    if (nb_samples <= 0) {
        av_log(avctx, AV_LOG_WARNING,
               "AV3A: invalid nb_samples=%d (output=%d ch=%d)\n",
               nb_samples, output_bytes, s->channels);
        return avpkt->size;
    }

    frame->nb_samples = nb_samples;
    frame->format = AV_SAMPLE_FMT_S16;
    int ret = ff_get_buffer(avctx, frame, 0);
    if (ret < 0) {
        av_log(avctx, AV_LOG_ERROR, "AV3A: ff_get_buffer failed: %d\n", ret);
        return ret;
    }

    memcpy(frame->data[0], temp_output, output_bytes);

    *got_frame_ptr = 1;
    av_log(avctx, AV_LOG_DEBUG,
           "AV3A: decoded %d bytes → %d samples (%d ch, %d Hz)\n",
           output_bytes, nb_samples, s->channels, s->sample_rate);

    return avpkt->size;
}

static av_cold int av3a_decode_close(AVCodecContext *avctx)
{
    AV3AContext *s = avctx->priv_data;

    if (s->decoder && s->fn_destroy) {
        s->fn_destroy(s->decoder);
        s->decoder = NULL;
    }
    if (s->lib_handle) {
        dlclose(s->lib_handle);
        s->lib_handle = NULL;
    }

    return 0;
}

const FFCodec ff_av3a_decoder = {
    .p.name         = "av3a",
    .p.long_name    = NULL_IF_CONFIG_SMALL("AVS3-P3 Audio Vivid (AV3A)"),
    .p.type         = AVMEDIA_TYPE_AUDIO,
    .p.id           = AV_CODEC_ID_AV3A,
    .priv_data_size = sizeof(AV3AContext),
    .init           = av3a_decode_init,
    .close          = av3a_decode_close,
    FF_CODEC_DECODE_CB(av3a_decode_frame),
    .p.capabilities = AV_CODEC_CAP_DR1 | AV_CODEC_CAP_DELAY,
    .p.sample_fmts  = (const enum AVSampleFormat[]){ AV_SAMPLE_FMT_S16, AV_SAMPLE_FMT_NONE },
};
