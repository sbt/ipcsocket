#include "errno.h"
#include "jni.h"
#include "stdio.h"
#include "stdlib.h"
#include "string.h"
#include "sys/socket.h"
#include "sys/types.h"
#include "sys/un.h"
#include "unistd.h"

#include "org_scalasbt_ipcsocket_JNIUnixDomainSocketLibraryProvider.h"
#define UNUSED __attribute__((unused))
#define THROW_ON_ERROR(res)                                                    \
  do {                                                                         \
    int err = errno;                                                           \
    errno = 0;                                                                 \
    return err ? -err : (int)res;                                              \
  } while (0);

static void native_sockaddr(JNIEnv *env, struct sockaddr_un *native,
                            jobject array, jint len) {
  memset(native, 0, sizeof(struct sockaddr_un));
  if (array) {
    jbyte *bytes = (*env)->GetByteArrayElements(env, (jbyteArray)array, 0);
    memcpy(native->sun_path, bytes, len);
    (*env)->ReleaseByteArrayElements(env, (jbyteArray)array, bytes, JNI_ABORT);
  }
  native->sun_family = 1;
#ifdef __APPLE__
  native->sun_len = len + 2;
#endif
}

jint JNICALL
Java_org_scalasbt_ipcsocket_JNIUnixDomainSocketLibraryProvider_socketNative(
    UNUSED JNIEnv *env, UNUSED jclass clazz, jint domain, jint type,
    jint protocol) {
  errno = 0;
  int res = socket(domain, type, protocol);
  THROW_ON_ERROR(res)
}

jint JNICALL
Java_org_scalasbt_ipcsocket_JNIUnixDomainSocketLibraryProvider_bindNative(
    UNUSED JNIEnv *env, UNUSED jclass clazz, jint fd, jbyteArray path,
    jint len) {
  errno = 0;
  struct sockaddr_un addr;
  const struct sockaddr *sa = (struct sockaddr *)&addr;
  native_sockaddr(env, &addr, path, len);
  int res = bind(fd, sa, sizeof(struct sockaddr_un));
  THROW_ON_ERROR(res)
}

jint JNICALL
Java_org_scalasbt_ipcsocket_JNIUnixDomainSocketLibraryProvider_listenNative(
    UNUSED JNIEnv *env, UNUSED jclass clazz, jint fd, jint backlog) {
  errno = 0;
  int res = listen(fd, backlog);
  THROW_ON_ERROR(res)
}

jint JNICALL
Java_org_scalasbt_ipcsocket_JNIUnixDomainSocketLibraryProvider_acceptNative(
    UNUSED JNIEnv *env, UNUSED jclass clazz, jint fd) {
  errno = 0;
  struct sockaddr_un addr;
  native_sockaddr(env, &addr, NULL, 0);
  socklen_t l = 0;
  int res = accept(fd, (struct sockaddr *)&addr, &l);
  THROW_ON_ERROR(res);
}

jint JNICALL
Java_org_scalasbt_ipcsocket_JNIUnixDomainSocketLibraryProvider_connectNative(
    UNUSED JNIEnv *env, UNUSED jclass clazz, jint fd, jbyteArray path,
    jint len) {
  struct sockaddr_un addr;
  native_sockaddr(env, &addr, path, len);
  errno = 0;
  int res = connect(fd, (struct sockaddr *)&addr, sizeof(struct sockaddr_un));
  THROW_ON_ERROR(res);
}

jint JNICALL
Java_org_scalasbt_ipcsocket_JNIUnixDomainSocketLibraryProvider_readNative(
    UNUSED JNIEnv *env, UNUSED jclass clazz, jint fd, jbyteArray buffer,
    jint offset, jint len) {
  errno = 0;
  jbyte *bytes = malloc(len);
  int bytes_read = read(fd, bytes, len);
  (*env)->SetByteArrayRegion(env, buffer, offset, bytes_read, bytes);
  free(bytes);
  THROW_ON_ERROR(bytes_read);
}

jint JNICALL
Java_org_scalasbt_ipcsocket_JNIUnixDomainSocketLibraryProvider_writeNative(
    UNUSED JNIEnv *env, UNUSED jclass clazz, jint fd, jbyteArray buffer,
    jint offset, jint len) {
  errno = 0;
  jbyte *bytes = (*env)->GetByteArrayElements(env, buffer, 0);
  int bytes_written = write(fd, bytes + offset, len);
  (*env)->ReleaseByteArrayElements(env, buffer, bytes, JNI_ABORT);
  THROW_ON_ERROR(bytes_written);
}

jint JNICALL
Java_org_scalasbt_ipcsocket_JNIUnixDomainSocketLibraryProvider_closeNative(
    UNUSED JNIEnv *env, UNUSED jclass clazz, jint fd) {
  errno = 0;
  int res = close(fd);
  THROW_ON_ERROR(res);
}

jint JNICALL
Java_org_scalasbt_ipcsocket_JNIUnixDomainSocketLibraryProvider_shutdownNative(
    UNUSED JNIEnv *env, UNUSED jclass clazz, jint fd, jint how) {
  errno = 0;
  int res = shutdown(fd, how);
  THROW_ON_ERROR(res);
}

jstring JNICALL
Java_org_scalasbt_ipcsocket_JNIUnixDomainSocketLibraryProvider_errString(
    JNIEnv *env, UNUSED jobject object, jint code) {
  const char *err = strerror(code);
  return (*env)->NewStringUTF(env, err);
}
