package org.scalasbt.ipcsocket;

import java.io.IOException;

interface Win32NamedPipeLibraryProvider {
  Handle CreateNamedPipe(
      String lpName,
      int dwOpenMode,
      int dwPipeMode,
      int nMaxInstances,
      int nOutBufferSize,
      int nInBufferSize,
      int nDefaultTimeOut,
      int lpSecurityAttributes)
      throws IOException;

  Handle CreateFile(String pipeName) throws IOException;

  int ConnectNamedPipe(Handle hNamedPipe, Overlapped lpOverlapped);

  boolean DisconnectNamedPipe(Handle handle);

  int read(
      Handle waitable,
      Handle hFile,
      byte[] buffer,
      int offset,
      int len,
      boolean requireStrictLength)
      throws IOException;

  void write(Handle waitable, Handle hFile, byte[] lpBuffer, int offset, int len)
      throws IOException;

  boolean CloseHandle(Handle handle);

  boolean GetOverlappedResult(Handle hFile, Overlapped lpOverlapped);

  boolean CancelIoEx(Handle handle);

  Handle CreateEvent(boolean bManualReset, boolean bInitialState, String lpName) throws IOException;

  int WaitForSingleObject(Handle hHandle, int dwMilliseconds);

  int GetLastError();

  Overlapped NewOverlapped(Handle hEvent);

  void DeleteOverlapped(Overlapped overlapped);

  // Constants:
  int ERROR_IO_PENDING();

  int ERROR_NO_DATA();

  int ERROR_PIPE_CONNECTED();

  int FILE_ALL_ACCESS();

  int FILE_FLAG_FIRST_PIPE_INSTANCE();

  int FILE_FLAG_OVERLAPPED();

  int FILE_GENERIC_READ();

  int GENERIC_READ();

  int GENERIC_WRITE();

  int PIPE_ACCESS_DUPLEX();

  static Win32NamedPipeLibraryProvider get(boolean useJNI) {
    return useJNI
        ? JNIWin32NamedPipeLibraryProvider.instance()
        : JNAWin32NamedPipeLibraryProvider.instance();
  }
}

interface Handle {}

interface Overlapped {}
