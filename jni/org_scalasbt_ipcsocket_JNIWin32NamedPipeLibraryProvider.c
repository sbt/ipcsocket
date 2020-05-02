#include "org_scalasbt_ipcsocket_JNIWin32NamedPipeLibraryProvider.h"
#include <jni.h>
#include <windows.h>
#include <winerror.h>
#include <winnt.h>

#define UNUSED __attribute__((unused))
#define SECURITY_DESCRIPTOR_SIZE 64 * 1024
#define HANDLE_OR_ERROR(h)                                                     \
  (h == (jlong)INVALID_HANDLE_VALUE) ? -((jlong)GetLastError()) : (jlong)h
#define DEBUG 0
#define THROW_IO(prefix, ...)                                                  \
  do {                                                                         \
    char _buf[1024];                                                           \
    snprintf(_buf, 1024, prefix ? prefix : "%s", __VA_ARGS__);                 \
    jclass exClass = (*env)->FindClass(env, "java/io/IOException");            \
    if (exClass != NULL) {                                                     \
      (*env)->ThrowNew(env, exClass, _buf);                                    \
    }                                                                          \
  } while (0);

#define FILL_ERROR(prefix, buf)                                                \
  do {                                                                         \
    char err[sizeof(buf)];                                                     \
    FormatMessage(FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS,  \
                  NULL, GetLastError(),                                        \
                  MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT), err, sizeof(buf), \
                  NULL);                                                       \
    size_t len = strnlen(err, sizeof(err));                                    \
    if (err[len - 2] == '\r')                                                  \
      err[len - 2] = '\0';                                                     \
    snprintf(buf, sizeof(buf), prefix ? prefix : "%s (error code %ld)", err,   \
             GetLastError());                                                  \
  } while (0);

static int createSecurityWithLogonDacl(PSECURITY_ATTRIBUTES pSA,
                                       DWORD accessMask);

BOOL WINAPI CancelIoEx(_In_ HANDLE hFile, _In_opt_ LPOVERLAPPED lpOverlapped);

jlong JNICALL
Java_org_scalasbt_ipcsocket_JNIWin32NamedPipeLibraryProvider_CreateNamedPipeNative(
    JNIEnv *env, UNUSED jobject object, jstring lpName, jint dwOpenMode,
    jint dwPipeMode, jint nMaxInstances, jint nOutBufferSize,
    jint nIntBufferSize, jint nDefaultTimeout, jint lpSecurityAttributes) {
  PSECURITY_ATTRIBUTES pSA = HeapAlloc(GetProcessHeap(), HEAP_ZERO_MEMORY,
                                       sizeof(SECURITY_ATTRIBUTES));
  int result = createSecurityWithLogonDacl(pSA, lpSecurityAttributes);
  if (!result) {
    LPCWSTR name = (LPCWSTR)(*env)->GetStringChars(env, lpName, 0);

    jlong handle = (jlong)(
        CreateNamedPipeW(name, dwOpenMode, dwPipeMode, nMaxInstances,
                         nOutBufferSize, nIntBufferSize, nDefaultTimeout, pSA));
    if (handle == (jlong)INVALID_HANDLE_VALUE) {
      char buf[512];
      FILL_ERROR(NULL, buf);
      THROW_IO("Couldn't create named pipe for %s (%s)",
               (*env)->GetStringUTFChars(env, lpName, 0), buf);
    }
    return handle;
  } else {
    char buf[512];
    FILL_ERROR("Couldn't create security acl -- %s (error code %ld)", buf);
    jclass exClass = (*env)->FindClass(env, "java/io/IOException");
    if (exClass != NULL) {
      return (*env)->ThrowNew(env, exClass, buf);
    }
    return -1;
  }
}

jlong JNICALL
Java_org_scalasbt_ipcsocket_JNIWin32NamedPipeLibraryProvider_CreateFileNative(
    JNIEnv *env, UNUSED jobject object, jstring lpName) {
  LPCWSTR name = (LPCWSTR)(*env)->GetStringChars(env, lpName, 0);

  HANDLE handle =
      CreateFileW(name, GENERIC_READ | GENERIC_WRITE,
                  0,    // no sharing
                  NULL, // default security attributes
                  OPEN_EXISTING,
                  FILE_FLAG_OVERLAPPED, // need overlapped for true
                                        // asynchronous read/write access
                  NULL);                // no template file
  if (handle == INVALID_HANDLE_VALUE) {
    char buf[512];
    FILL_ERROR(NULL, buf);
    THROW_IO("Couldn't open file %s (%s)",
             (*env)->GetStringUTFChars(env, lpName, 0), buf);
  }
  return (jlong)handle;
}
jint JNICALL
Java_org_scalasbt_ipcsocket_JNIWin32NamedPipeLibraryProvider_ConnectNamedPipeNative(
    UNUSED JNIEnv *env, UNUSED jobject object, jlong handlePointer,
    jlong overlappedPointer) {
  jboolean result =
      ConnectNamedPipe((HANDLE)handlePointer, (LPOVERLAPPED)overlappedPointer);
  return result ? -1 : (jint)GetLastError();
}

jboolean JNICALL
Java_org_scalasbt_ipcsocket_JNIWin32NamedPipeLibraryProvider_DisconnectNamedPipe(
    UNUSED JNIEnv *env, UNUSED jobject object, jlong handlePointer) {
  return DisconnectNamedPipe((HANDLE)handlePointer);
}

jint JNICALL
Java_org_scalasbt_ipcsocket_JNIWin32NamedPipeLibraryProvider_readNative(
    JNIEnv *env, UNUSED jobject object, jlong waitable, jlong hFile,
    jbyteArray buffer, jint offset, jint length, jboolean strict) {
  HANDLE handle = (HANDLE)hFile;
  OVERLAPPED olap = {0};
  olap.hEvent = (HANDLE)waitable;

  DWORD bytes_read = 0;
  LPVOID read_buffer = HeapAlloc(GetProcessHeap(), HEAP_ZERO_MEMORY, length);
  BOOL immediate = ReadFile(handle, read_buffer, length, &bytes_read, &olap);
  if (!immediate) {
    if (GetLastError() != ERROR_IO_PENDING) {
      char buf[256];
      FILL_ERROR("ReadFile() failed: %s (error code %ld)", buf);
      THROW_IO(NULL, buf);
    }
  }

  if (!GetOverlappedResult(handle, &olap, &bytes_read, TRUE)) {
    char buf[256];
    FILL_ERROR(
        "GetOverlappedResult() failed for read operation: %s (error code %ld)",
        buf);
    THROW_IO(NULL, buf);
  }
  if (strict && (bytes_read != (DWORD)length)) {
    char buf[256];
    snprintf(buf, 256,
             "ReadFile() read less bytes than requested: expected %d bytes, "
             "but read %ld bytes",
             length, bytes_read);
    THROW_IO(NULL, buf);
  }
  (*env)->SetByteArrayRegion(env, buffer, offset, bytes_read, read_buffer);
  HeapFree(GetProcessHeap(), 0, read_buffer);
  return bytes_read;
  return -1;
}

/*
 * Class:     org_scalasbt_ipcsocket_JNIWin32NamedPipeLibraryProvider
 * Method:    writeNative
 * Signature: (JJ[BII)V
 */
void JNICALL
Java_org_scalasbt_ipcsocket_JNIWin32NamedPipeLibraryProvider_writeNative(
    JNIEnv *env, UNUSED jobject object, jlong waitable, jlong hHandle,
    jbyteArray buffer, jint offset, jint length) {
  HANDLE handle = (HANDLE)hHandle;
  OVERLAPPED olap;
  olap.hEvent = (HANDLE)waitable;

  jbyte *bytes = (*env)->GetByteArrayElements(env, buffer, 0);
  BOOL immediate =
      WriteFile(handle, bytes + (DWORD)offset, (DWORD)length, NULL, &olap);
  if (!immediate) {
    if (GetLastError() != ERROR_IO_PENDING) {
      char buf[256];
      FILL_ERROR("ReadFile() failed: %s (error code %ld)", buf);
      THROW_IO(NULL, buf);
    }
  }
  DWORD bytes_written = 0;
  if (!GetOverlappedResult(handle, &olap, &bytes_written, TRUE)) {
    char buf[256];
    FILL_ERROR(
        "GetOverlappedResult() failed for write operation: %s (error code %ld)",
        buf);
    THROW_IO(NULL, buf);
  }
  if (bytes_written != (DWORD)length) {
    char buf[256];
    snprintf(buf, 256,
             "WriteFile() wrote less bytes than requested: expected %d bytes, "
             "but wrote %ld bytes",
             length, bytes_written);
    THROW_IO(NULL, buf);
  }
  (*env)->ReleaseByteArrayElements(env, buffer, bytes, JNI_ABORT);
}

JNIEXPORT jboolean JNICALL
Java_org_scalasbt_ipcsocket_JNIWin32NamedPipeLibraryProvider_CloseHandleNative(
    UNUSED JNIEnv *env, UNUSED jobject object, jlong handlePointer) {
  return CloseHandle((HANDLE)handlePointer);
}

JNIEXPORT jboolean JNICALL
Java_org_scalasbt_ipcsocket_JNIWin32NamedPipeLibraryProvider_GetOverlappedResultNative(
    UNUSED JNIEnv *env, UNUSED jobject object, jlong handlePointer,
    jlong overlappedPointer) {
  DWORD len = 0;
  return GetOverlappedResult((HANDLE)handlePointer,
                             (LPOVERLAPPED)overlappedPointer, &len, TRUE);
}

JNIEXPORT jboolean JNICALL
Java_org_scalasbt_ipcsocket_JNIWin32NamedPipeLibraryProvider_CancelIoEx(
    UNUSED JNIEnv *env, UNUSED jobject object, jlong handlePointer) {
  return CancelIoEx((HANDLE)handlePointer, NULL);
}

JNIEXPORT jlong JNICALL
Java_org_scalasbt_ipcsocket_JNIWin32NamedPipeLibraryProvider_CreateEventNative(
    JNIEnv *env, UNUSED jobject object, jboolean manualReset,
    jboolean initialState, jstring lpName) {
  LPCWSTR name =
      lpName ? (LPCWSTR)(*env)->GetStringChars(env, lpName, 0) : NULL;
  HANDLE handle = CreateEventW(NULL, manualReset, initialState, name);
  if (handle == INVALID_HANDLE_VALUE) {
    char buf[512];
    FILL_ERROR(NULL, buf);
    THROW_IO("Couldn't create event %s (%s)",
             (*env)->GetStringUTFChars(env, lpName, 0), buf);
  }
  return (jlong)handle;
}

jint JNICALL
Java_org_scalasbt_ipcsocket_JNIWin32NamedPipeLibraryProvider_WaitForSingleObjectNative(
    UNUSED JNIEnv *env, UNUSED jobject object, jlong handlePointer, jint wait) {
  return WaitForSingleObject((HANDLE)handlePointer, wait);
}

jint JNICALL
Java_org_scalasbt_ipcsocket_JNIWin32NamedPipeLibraryProvider_GetLastError(
    UNUSED JNIEnv *env, UNUSED jobject object) {
  return GetLastError();
}

jlong JNICALL
Java_org_scalasbt_ipcsocket_JNIWin32NamedPipeLibraryProvider_NewOverlappedNative(
    UNUSED JNIEnv *env, UNUSED jobject object, jlong handlePointer) {
  HANDLE handle = (HANDLE)handlePointer;
  UNUSED LPOVERLAPPED op =
      HeapAlloc(GetProcessHeap(), HEAP_ZERO_MEMORY, sizeof(OVERLAPPED));
  op->hEvent = handle;
  return (jlong)op;
}

void JNICALL
Java_org_scalasbt_ipcsocket_JNIWin32NamedPipeLibraryProvider_DeleteOverlappedNative(
    UNUSED JNIEnv *env, UNUSED jobject object, jlong overlappedPointer) {
  HeapFree(GetProcessHeap(), 0, (void *)overlappedPointer);
}

// Constants follow:
jint JNICALL
Java_org_scalasbt_ipcsocket_JNIWin32NamedPipeLibraryProvider_ERROR_1IO_1PENDING(
    UNUSED JNIEnv *env, UNUSED jobject object) {
  return ERROR_IO_PENDING;
};

jint JNICALL
Java_org_scalasbt_ipcsocket_JNIWin32NamedPipeLibraryProvider_ERROR_1NO_1DATA(
    UNUSED JNIEnv *env, UNUSED jobject object) {
  return ERROR_NO_DATA;
}

jint JNICALL
Java_org_scalasbt_ipcsocket_JNIWin32NamedPipeLibraryProvider_ERROR_1PIPE_1CONNECTED(
    UNUSED JNIEnv *env, UNUSED jobject object) {
  return ERROR_PIPE_CONNECTED;
}

jint JNICALL
Java_org_scalasbt_ipcsocket_JNIWin32NamedPipeLibraryProvider_FILE_1ALL_1ACCESS(
    UNUSED JNIEnv *env, UNUSED jobject object) {
  return FILE_ALL_ACCESS;
}

jint JNICALL
Java_org_scalasbt_ipcsocket_JNIWin32NamedPipeLibraryProvider_FILE_1FLAG_1FIRST_1PIPE_1INSTANCE(
    UNUSED JNIEnv *env, UNUSED jobject object) {
  return FILE_FLAG_FIRST_PIPE_INSTANCE;
}

jint JNICALL
Java_org_scalasbt_ipcsocket_JNIWin32NamedPipeLibraryProvider_FILE_1FLAG_1OVERLAPPED(
    UNUSED JNIEnv *env, UNUSED jobject object) {
  return FILE_FLAG_OVERLAPPED;
}

jint JNICALL
Java_org_scalasbt_ipcsocket_JNIWin32NamedPipeLibraryProvider_FILE_1GENERIC_1READ(
    UNUSED JNIEnv *env, UNUSED jobject object) {
  return FILE_GENERIC_READ;
}

jint JNICALL
Java_org_scalasbt_ipcsocket_JNIWin32NamedPipeLibraryProvider_GENERIC_1READ(
    UNUSED JNIEnv *env, UNUSED jobject object) {
  return GENERIC_READ;
}

jint JNICALL
Java_org_scalasbt_ipcsocket_JNIWin32NamedPipeLibraryProvider_GENERIC_1WRITE(
    UNUSED JNIEnv *env, UNUSED jobject object) {
  return GENERIC_WRITE;
}

jint JNICALL
Java_org_scalasbt_ipcsocket_JNIWin32NamedPipeLibraryProvider_PIPE_1ACCESS_1DUPLEX(
    UNUSED JNIEnv *env, UNUSED jobject object) {
  return PIPE_ACCESS_DUPLEX;
}

jstring JNICALL
Java_org_scalasbt_ipcsocket_JNIWin32NamedPipeLibraryProvider_getErrorMessage(
    JNIEnv *env, UNUSED jobject object, jint errorCode) {
  wchar_t buf[256];
  FormatMessageW(FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS,
                 NULL, errorCode, MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT),
                 buf, (sizeof(buf) / sizeof(wchar_t)), NULL);
  size_t len = wcsnlen(buf, 256);
  if (len > 0 && buf[len - 1] == '\n') {
    buf[len - 1] = '0';
    len--;
  }
  return (*env)->NewString(env, buf, len - 1);
}

static int createSecurityWithLogonDacl(PSECURITY_ATTRIBUTES pSA,
                                       DWORD accessMask) {
  int result = 0;
  // Direct port of SecurityLibrary.createSecurityWithLogonDacl
  PSECURITY_DESCRIPTOR pSD = HeapAlloc(GetProcessHeap(), HEAP_ZERO_MEMORY,
                                       SECURITY_DESCRIPTOR_MIN_LENGTH);
  if (!InitializeSecurityDescriptor(pSD, SECURITY_DESCRIPTOR_REVISION)) {
    wchar_t buf[256];
    FormatMessageW(FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS,
                   NULL, GetLastError(),
                   MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT), buf,
                   (sizeof(buf) / sizeof(wchar_t)), NULL);
    if (DEBUG)
      wprintf(L"Failed to initialize security descriptor %d %s\n",
              GetLastError(), buf);
    result = 1;
    goto psdfail;
  }
  GetLastError();

  PSID psid = NULL;
  HANDLE phToken;
  OpenProcessToken(GetCurrentProcess(), TOKEN_QUERY, &phToken);
  long unsigned int tokenInformationLength;
  GetTokenInformation(phToken, TokenGroups, NULL, 0, &tokenInformationLength);
  PTOKEN_GROUPS groups =
      (PTOKEN_GROUPS)HeapAlloc(GetProcessHeap(), HEAP_ZERO_MEMORY,
                               tokenInformationLength * sizeof(TOKEN_GROUPS));
  GetTokenInformation(phToken, TokenGroups, groups, tokenInformationLength,
                      &tokenInformationLength);
  GetLastError();
  for (DWORD i = 0; i < groups->GroupCount; ++i) {
    SID_AND_ATTRIBUTES a = groups->Groups[i];
    if ((a.Attributes & SE_GROUP_LOGON_ID) == SE_GROUP_LOGON_ID) {
      psid = a.Sid;
      break;
    }
  }
  if (psid == NULL)
    goto psidfail;

  DWORD cbAcl = sizeof(ACL) + sizeof(ACCESS_ALLOWED_ACE) + GetLengthSid(psid);
  cbAcl = (cbAcl + (sizeof(DWORD) - 1)) & 0xfffffffc;

  PACL pAcl = HeapAlloc(GetProcessHeap(), 0, cbAcl);
  if (!InitializeAcl(pAcl, cbAcl, ACL_REVISION)) {
    wchar_t buf[256];
    FormatMessageW(FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS,
                   NULL, GetLastError(),
                   MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT), buf,
                   (sizeof(buf) / sizeof(wchar_t)), NULL);
    if (DEBUG)
      wprintf(L"Failed to initialize acl %d %s\n", GetLastError(), buf);
    result = 1;
    goto exit;
  }
  if (!AddAccessAllowedAce(pAcl, ACL_REVISION, accessMask, psid)) {
    wchar_t buf[256];
    FormatMessageW(FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS,
                   NULL, GetLastError(),
                   MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT), buf,
                   (sizeof(buf) / sizeof(wchar_t)), NULL);
    if (DEBUG)
      wprintf(L"Failed to add access allowed ace %d %s\n", GetLastError(), buf);
    result = 1;
    goto exit;
  }
  if (!SetSecurityDescriptorDacl(pSD, TRUE, pAcl, FALSE)) {
    wchar_t buf[256];
    FormatMessageW(FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS,
                   NULL, GetLastError(),
                   MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT), buf,
                   (sizeof(buf) / sizeof(wchar_t)), NULL);
    if (DEBUG)
      wprintf(L"Failed to set security despcriptor %d %s\n", GetLastError(),
              buf);
    result = 1;
    pSA->nLength = 0;
    pSA->lpSecurityDescriptor = NULL;
    pSA->bInheritHandle = FALSE;
  } else {
    pSA->nLength = SECURITY_DESCRIPTOR_SIZE;
    pSA->lpSecurityDescriptor = pSD;
    pSA->bInheritHandle = FALSE;
  }

exit:
  HeapFree(GetProcessHeap(), 0, pAcl);
psidfail:
  HeapFree(GetProcessHeap(), 0, groups);
  CloseHandle(phToken);
psdfail:
  HeapFree(GetProcessHeap(), 0, pSD);

  return result;
}
