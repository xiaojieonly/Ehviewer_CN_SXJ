#ifndef EHVIEWER_EHVIEWER_H
#define EHVIEWER_EHVIEWER_H

#define EH_UNUSED(x) (void)x
#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG ,__VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG ,__VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG ,__VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG ,__VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG ,__VA_ARGS__)
#define LOGF(...) __android_log_print(ANDROID_LOG_FATAL, LOG_TAG ,__VA_ARGS__)

#define madvise_log_if_error(addr, len, advice) \
if (madvise(addr, len, advice))                 \
    LOGE("%s%p%s%zu%s%d%s%s%s", "madvise addr:", addr, "len:", len, "with advice ", advice, " failed with error: ", strerror(errno), ", Ignored")

#endif /* EHVIEWER_EHVIEWER_H */
