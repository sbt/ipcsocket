package org.scalasbt.ipcsocket;

import java.io.IOException;

class JNIWin32NamedPipeLibraryProvider implements Win32NamedPipeLibraryProvider {
  static {
    NativeLoader.load();
  }

  private static final JNIWin32NamedPipeLibraryProvider instance =
      new JNIWin32NamedPipeLibraryProvider();

  static JNIWin32NamedPipeLibraryProvider instance() {
    return instance;
  }

  static class JNIHandle implements Handle {
    final long pointer;

    JNIHandle(long pointer) throws IOException {
      if (pointer < 0) throw new IOException("Bad pointer: " + pointer);
      this.pointer = pointer;
    }
  }

  static class JNIOverlapped implements Overlapped {
    final long pointer;

    JNIOverlapped(long pointer) {
      if (pointer < 0) throw new IllegalStateException("Bad pointer");
      this.pointer = pointer;
    }
  }

  @Override
  public Handle CreateNamedPipe(
      String lpName,
      int dwOpenMode,
      int dwPipeMode,
      int nMaxInstances,
      int nOutBufferSize,
      int nInBufferSize,
      int nDefaultTimeOut,
      int lpSecurityAttributes,
      int securityLevel)
      throws IOException {
    return new JNIHandle(
        CreateNamedPipeNative(
            lpName,
            dwOpenMode,
            dwPipeMode,
            nMaxInstances,
            nOutBufferSize,
            nInBufferSize,
            nDefaultTimeOut,
            lpSecurityAttributes,
            securityLevel));
  }

  native long CreateNamedPipeNative(
      String lpName,
      int dwOpenMode,
      int dwPipeMode,
      int nMaxInstances,
      int nOutBufferSize,
      int nInBufferSize,
      int nDefaultTimeOut,
      int lpSecurityAttributes,
      int securityLevel)
      throws IOException;

  @Override
  public Handle CreateFile(String pipeName) throws IOException {
    return new JNIHandle(CreateFileNative(pipeName));
  }

  native long CreateFileNative(String pipeName) throws IOException;

  @Override
  public int ConnectNamedPipe(Handle hNamedPipe, Overlapped lpOverlapped) {
    return ConnectNamedPipeNative(getHandlePointer(hNamedPipe), getOverlappedPointer(lpOverlapped));
  }

  native int ConnectNamedPipeNative(long handlePointer, long overlappedPointer);

  @Override
  public boolean DisconnectNamedPipe(Handle handle) {
    return DisconnectNamedPipe(getHandlePointer(handle));
  }

  native boolean DisconnectNamedPipe(long handlePointer);

  @Override
  public int read(
      Handle waitable,
      Handle hFile,
      byte[] buffer,
      int offset,
      int len,
      boolean requireStrictLength)
      throws IOException {
    return readNative(
        getHandlePointer(waitable),
        getHandlePointer(hFile),
        buffer,
        offset,
        len,
        requireStrictLength);
  }

  native int readNative(
      long waitable, long hFile, byte[] buffer, int offset, int len, boolean requireStrictLength)
      throws IOException;

  @Override
  public void write(Handle waitable, Handle hFile, byte[] lpBuffer, int offset, int len)
      throws IOException {
    writeNative(getHandlePointer(waitable), getHandlePointer(hFile), lpBuffer, offset, len);
  }

  native void writeNative(
      long waitablePointer, long hFilePointer, byte[] lpBuffer, int offset, int len)
      throws IOException;

  @Override
  public boolean CloseHandle(Handle handle) {
    return CloseHandleNative(getHandlePointer(handle));
  }

  native boolean CloseHandleNative(long pointer);

  @Override
  public boolean GetOverlappedResult(Handle hFile, Overlapped lpOverlapped) {
    return GetOverlappedResultNative(getHandlePointer(hFile), getOverlappedPointer(lpOverlapped));
  }

  native boolean GetOverlappedResultNative(long handlePointer, long overlappedPointer);

  @Override
  public boolean CancelIoEx(Handle handle) {
    return CancelIoEx(getHandlePointer(handle));
  }

  native boolean CancelIoEx(long pointer);

  @Override
  public Handle CreateEvent(boolean bManualReset, boolean bInitialState, String lpName)
      throws IOException {
    return new JNIHandle(CreateEventNative(bManualReset, bInitialState, lpName));
  }

  native long CreateEventNative(boolean bManualReset, boolean bInitialState, String lpName);

  @Override
  public int WaitForSingleObject(Handle hHandle, int dwMilliseconds) {
    return WaitForSingleObjectNative(getHandlePointer(hHandle), dwMilliseconds);
  }

  native int WaitForSingleObjectNative(long pointer, int dwMilliseconds);

  @Override
  public native int GetLastError();

  @Override
  public Overlapped NewOverlapped(Handle hEvent) {
    return new JNIOverlapped(NewOverlappedNative(getHandlePointer(hEvent)));
  }

  native long NewOverlappedNative(long handle);

  @Override
  public void DeleteOverlapped(Overlapped overlapped) {
    DeleteOverlappedNative(getOverlappedPointer(overlapped));
  }

  native void DeleteOverlappedNative(long overlapped);

  native String getErrorMessage(int errorCode);

  // Constants:
  @Override
  public native int ERROR_IO_PENDING();

  @Override
  public native int ERROR_NO_DATA();

  @Override
  public native int ERROR_PIPE_CONNECTED();

  @Override
  public native int FILE_ALL_ACCESS();

  @Override
  public native int FILE_FLAG_FIRST_PIPE_INSTANCE();

  @Override
  public native int FILE_FLAG_OVERLAPPED();

  @Override
  public native int FILE_GENERIC_READ();

  @Override
  public native int GENERIC_READ();

  @Override
  public native int GENERIC_WRITE();

  @Override
  public native int PIPE_ACCESS_DUPLEX();

  private long getHandlePointer(Handle handle) {
    if (handle instanceof JNIHandle) {
      return ((JNIHandle) handle).pointer;
    } else {
      throw new IllegalStateException("Invalid handle " + handle + " of type " + handle.getClass());
    }
  }

  private long getOverlappedPointer(Overlapped overlapped) {
    if (overlapped instanceof JNIOverlapped) return ((JNIOverlapped) overlapped).pointer;
    else
      throw new IllegalStateException(
          "Invalid overlapped " + overlapped + " of type " + overlapped.getClass());
  }
}
