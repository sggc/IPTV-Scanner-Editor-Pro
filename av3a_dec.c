/*
 * AV3A (Audio Vivid / AVS3-P3) decoder wrapper for FFmpeg
 * Uses the libav3a decoder library from ijkplayer-av3a
 *
 * This file is injected into FFmpeg's libavcodec during the mpv build.
 * It wraps the C API from avs3_decoder_interface.h as an FFmpeg AVCodec.
 */
#include "avcodec.h"
#include "codec_internal.h"
#include "decode.h"
#include "libavutil/log.h"

#include "avs3_decoder_interface.h"

typedef struct AV3AContext {
    AVS3DecoderHandle decoder;
    int first_frame;
} AV3AContext;

static av_cold int av3a_decode_init(AVCodecContext *avctx)
{
    AV3AContext *s = avctx->priv_data;
    s->decoder = avs3_create_decoder();
    s->first_frame = 1;
    if (!s->decoder) {
        av_log(avctx, AV_LOG_ERROR, "AV3A: Failed to create decoder\n");
        return AVERROR(EINVAL);
    }
    av_log(avctx, AV_LOG_INFO, "AV3A: Decoder created successfully\n");
    return 0;
}

static int av3a_decode_frame(AVCodecContext *avctx, AVFrame *frame,
                              int *got_frame_ptr, AVPacket *avpkt)
{
    AV3AContext *s = avctx->priv_data;
    int ret;

    if (!avpkt->size)
        return 0;

    /* Parse the AV3A frame header */
    int header_consumed = 0;
    int result = parse_header(s->decoder, avpkt->data, avpkt->size,
                               s->first_frame, &header_consumed, NULL);
    if (result != AVS3_TRUE || header_consumed <= 0 || header_consumed >= avpkt->size) {
        av_log(avctx, AV_LOG_DEBUG, "AV3A: parse_header failed (result=%d consumed=%d size=%d)\n",
               result, header_consumed, avpkt->size);
        *got_frame_ptr = 0;
        return avpkt->size;
    }

    /* Determine output parameters from decoder state */
    int channels = s->decoder->numChansOutput > 0 ? s->decoder->numChansOutput : 2;
    int sample_rate = s->decoder->outputFs > 0 ? s->decoder->outputFs : 48000;

    /* Allocate output frame — PCM S16, estimate max samples */
    int max_output_bytes = 64 * 1024;
    int max_samples = max_output_bytes / (channels * 2);

    frame->format = AV_SAMPLE_FMT_S16;
    frame->nb_samples = max_samples;
    if ((ret = ff_get_buffer(avctx, frame, 0)) < 0) {
        av_log(avctx, AV_LOG_ERROR, "AV3A: ff_get_buffer failed: %d\n", ret);
        return ret;
    }

    /* Decode the payload */
    int output_bytes = 0;
    int payload_consumed = 0;
    result = avs3_decode(s->decoder,
                          avpkt->data + header_consumed,
                          avpkt->size - header_consumed,
                          frame->data[0],
                          &output_bytes,
                          &payload_consumed);

    if (result != AVS3_TRUE || output_bytes <= 0) {
        av_log(avctx, AV_LOG_DEBUG, "AV3A: decode failed (result=%d output=%d)\n",
               result, output_bytes);
        *got_frame_ptr = 0;
        return avpkt->size;
    }

    /* Update codec context with actual output parameters */
    if (s->decoder->outputFs > 0) {
        avctx->sample_rate = s->decoder->outputFs;
    }
    if (s->decoder->numChansOutput > 0) {
        av_channel_layout_uninit(&avctx->ch_layout);
        av_channel_layout_default(&avctx->ch_layout, s->decoder->numChansOutput);
    }

    /* Set actual number of decoded samples */
    channels = s->decoder->numChansOutput > 0 ? s->decoder->numChansOutput : channels;
    frame->nb_samples = output_bytes / (channels * 2);
    *got_frame_ptr = 1;
    s->first_frame = 0;

    av_log(avctx, AV_LOG_DEBUG, "AV3A: decoded %d bytes (%d samples, %d ch, %d Hz)\n",
           output_bytes, frame->nb_samples, channels, avctx->sample_rate);

    return avpkt->size;
}

static av_cold void av3a_decode_close(AVCodecContext *avctx)
{
    AV3AContext *s = avctx->priv_data;
    if (s->decoder) {
        avs3_destroy_decoder(s->decoder);
        s->decoder = NULL;
    }
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
