#include "com_example_vod_MainActivity.h"
#include <sys/types.h>
#include <sys/stat.h>
#include <stdio.h>
#include <stdlib.h>
#include <fcntl.h>
#include <sys/ioctl.h>

#include <string.h>
#include <unistd.h>
#include <jni.h>
#include "android/log.h"

static const char *TAG = "io_test";
#define LOGI(fmt, args...) __android_log_print(ANDROID_LOG_INFO, TAG, fmt, ##args)

#ifdef __cplusplus
extern "C" {
#endif

#define GS_IOC_MAGIC 0x55

#define NR_CAM_LED        0x01
#define NR_RELAY_IN1    0x02
#define NR_RELAY_IN2    0x03
#define NR_RELAY_OUT1    0x04
#define NR_RELAY_OUT2    0x05
#define NR_RELAY_DRV    0x06

//#define NR_TEST_LED		0x01


#define IOCTL_CAM_LED    _IOW (GS_IOC_MAGIC, NR_CAM_LED, int)
#define IOCTL_RELAY_IN1    _IOR (GS_IOC_MAGIC, NR_RELAY_IN1, int)
#define IOCTL_RELAY_IN2        _IOR (GS_IOC_MAGIC, NR_RELAY_IN2, int)
#define IOCTL_RELAY_OUT1    _IOW (GS_IOC_MAGIC, NR_RELAY_OUT1, int)
#define IOCTL_RELAY_OUT2    _IOW (GS_IOC_MAGIC, NR_RELAY_OUT2, int)
#define IOCTL_RELAY_DRV    _IOW (GS_IOC_MAGIC, NR_RELAY_DRV, int)

//#define IOCTL_TEST_LED   	_IOW (GS_IOC_MAGIC, NR_TEST_LED, int)

#define ON 1
#define OFF 0

#define DEVICE_NAME    "/dev/glorystar_class"
int fd, num;

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    void *venv;
    fd = open(DEVICE_NAME, O_RDWR);
    if (fd == -1) {
        LOGI("open failed!");
    }
    if ((*vm)->GetEnv(vm, (void **) &venv, JNI_VERSION_1_4) != JNI_OK) {
        return -1;
    }
    LOGI("fd=%d", fd);
    return JNI_VERSION_1_4;
}

/*
 * Class:     com_example_vod_MainActivity
 * Method:    output
 * Signature: (I)V
 */
JNIEXPORT jint JNICALL Java_com_example_smartpropertykiosk_MainActivity_output
        (JNIEnv *env, jobject arg, jint tag) {
    if (fd != -1) {
        if (tag == 1) {
            LOGI("test 1");
            ioctl(fd, IOCTL_CAM_LED, ON);
        } else if (tag == 2) {
            LOGI("test 2");
            ioctl(fd, IOCTL_CAM_LED, OFF);
        } else if (tag == 3) {
            ioctl(fd, IOCTL_RELAY_OUT1, ON);
        } else if (tag == 4) {
            ioctl(fd, IOCTL_RELAY_OUT1, OFF);
        } else if (tag == 5) {
            ioctl(fd, IOCTL_RELAY_OUT2, ON);
        } else if (tag == 6) {
            ioctl(fd, IOCTL_RELAY_OUT2, OFF);
        }
    }
}

/*
 * Class:     com_example_vod_MainActivity
 * Method:    input
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_com_example_smartpropertykiosk_MainActivity_input
        (JNIEnv *env, jobject arg, jint tag) {
    if (fd != -1) {
        ioctl(fd, IOCTL_RELAY_DRV, ON);
        if (tag == 1) {
            num = ioctl(fd, IOCTL_RELAY_IN1);
            return num;
        } else if (tag == 2) {
            num = ioctl(fd, IOCTL_RELAY_IN2);
            return num;
        }
    }
}

#ifdef __cplusplus
}
#endif


