package org.scalasbt.ipcsocket;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.sun.jna.*;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.WinBase.SECURITY_ATTRIBUTES;
import com.sun.jna.platform.win32.WinBase.OVERLAPPED;
import com.sun.jna.platform.win32.WinError;

class JNAWin32NamedPipeLibraryProvider implements Win32NamedPipeLibraryProvider {
  static final Win32NamedPipeLibrary delegate = Win32NamedPipeLibrary.INSTANCE;
  static final Win32NamedPipeLibraryProvider instance = new JNAWin32NamedPipeLibraryProvider();

  static Win32NamedPipeLibraryProvider instance() {
    return instance;
  }

  private JNAWin32NamedPipeLibraryProvider() {};

  private static class JNAHandle implements Handle {
    final HANDLE handle;

    private JNAHandle(final HANDLE handle) {
      this.handle = handle;
    }

    static Handle make(final HANDLE handle) throws IOException {
      if (handle == Win32NamedPipeLibrary.INVALID_HANDLE_VALUE) {
        throw new IOException("Invalid handle");
      }
      return new JNAHandle(handle);
    }
  }

  private static class JNAOverlapped implements Overlapped {
    final OVERLAPPED overlapped;

    JNAOverlapped(final OVERLAPPED overlapped) {
      this.overlapped = overlapped;
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
      int lpSecurityAttributes)
      throws IOException {
    SECURITY_ATTRIBUTES sa = Win32SecurityLibrary.createSecurityWithLogonDacl(lpSecurityAttributes);
    return JNAHandle.make(
        delegate.CreateNamedPipe(
            lpName,
            dwOpenMode,
            dwPipeMode,
            nMaxInstances,
            nOutBufferSize,
            nInBufferSize,
            nDefaultTimeOut,
            sa));
  }

  @Override
  public Handle CreateFile(String pipeName) throws IOException {
    HANDLE handle =
        delegate.CreateFile(
            pipeName,
            WinNT.GENERIC_READ | WinNT.GENERIC_WRITE,
            0, // no sharing
            null, // default security attributes
            WinNT.OPEN_EXISTING,
            WinNT.FILE_FLAG_OVERLAPPED, // need overlapped for true asynchronous read/write access
            null); // no template file
    if (handle == Win32NamedPipeLibrary.INVALID_HANDLE_VALUE)
      throw new IOException("Couldn't open pipe for " + pipeName + " (" + GetLastError() + ")");
    return JNAHandle.make(handle);
  }

  @Override
  public int ConnectNamedPipe(Handle hNamedPipe, Overlapped lpOverlapped) {
    HANDLE pipe = getHandle(hNamedPipe);
    OVERLAPPED op = getOverlap(lpOverlapped);
    if (delegate.ConnectNamedPipe(pipe, op.getPointer())) {
      return -1;
    } else {
      return GetLastError();
    }
  }

  @Override
  public boolean DisconnectNamedPipe(Handle handle) {
    HANDLE pipe = getHandle(handle);
    return delegate.DisconnectNamedPipe(pipe);
  }

  @Override
  public int read(
      Handle waitable,
      Handle hFile,
      byte[] buffer,
      int offset,
      int len,
      boolean requireStrictLength)
      throws IOException {
    HANDLE readerWaitable = getHandle(waitable);
    HANDLE handle = getHandle(hFile);
    Memory readBuffer = new Memory(len);

    OVERLAPPED olap = new OVERLAPPED();
    olap.hEvent = readerWaitable;
    olap.write();

    boolean immediate = delegate.ReadFile(handle, readBuffer, len, null, olap.getPointer());
    if (!immediate) {
      int lastError = delegate.GetLastError();
      if (lastError != WinError.ERROR_IO_PENDING) {
        throw new IOException("ReadFile() failed: " + lastError);
      }
    }

    IntByReference r = new IntByReference();
    if (!delegate.GetOverlappedResult(handle, olap.getPointer(), r, true)) {
      int lastError = delegate.GetLastError();
      throw new IOException("GetOverlappedResult() failed for read operation: " + lastError);
    }
    int actualLen = r.getValue();
    if (requireStrictLength && (actualLen != len)) {
      throw new IOException(
          "ReadFile() read less bytes than requested: expected "
              + len
              + " bytes, but read "
              + actualLen
              + " bytes");
    }
    byte[] byteArray = readBuffer.getByteArray(0, actualLen);
    System.arraycopy(byteArray, 0, buffer, offset, actualLen);
    return actualLen;
  }

  @Override
  public void write(Handle waitable, Handle hHandle, byte[] b, int off, int len)
      throws IOException {
    HANDLE writerWaitable = getHandle(waitable);
    HANDLE handle = getHandle(hHandle);
    ByteBuffer data = ByteBuffer.wrap(b, off, len);

    OVERLAPPED olap = new OVERLAPPED();
    olap.hEvent = writerWaitable;
    olap.write();

    boolean immediate = delegate.WriteFile(handle, data, len, null, olap.getPointer());
    if (!immediate) {
      int lastError = delegate.GetLastError();
      if (lastError != WinError.ERROR_IO_PENDING) {
        throw new IOException("WriteFile() failed: " + lastError);
      }
    }
    IntByReference written = new IntByReference();
    if (!delegate.GetOverlappedResult(handle, olap.getPointer(), written, true)) {
      int lastError = delegate.GetLastError();
      throw new IOException("GetOverlappedResult() failed for write operation: " + lastError);
    }
    if (written.getValue() != len) {
      throw new IOException("WriteFile() wrote less bytes than requested");
    }
  }

  @Override
  public boolean CloseHandle(Handle handle) {
    return delegate.CloseHandle(getHandle(handle));
  }

  @Override
  public boolean GetOverlappedResult(Handle hFile, Overlapped lpOverlapped) {
    HANDLE handle = getHandle(hFile);
    OVERLAPPED op = getOverlap(lpOverlapped);
    return delegate.GetOverlappedResult(handle, op.getPointer(), new IntByReference(), true);
  }

  @Override
  public boolean CancelIoEx(Handle hHandle) {
    return delegate.CancelIoEx(getHandle(hHandle), null);
  }

  @Override
  public Handle CreateEvent(boolean bManualReset, boolean bInitialState, String lpName)
      throws IOException {
    return JNAHandle.make(delegate.CreateEvent(null, bManualReset, bInitialState, lpName));
  }

  @Override
  public int WaitForSingleObject(Handle hHandle, int dwMilliseconds) {
    return delegate.WaitForSingleObject(getHandle(hHandle), dwMilliseconds);
  }

  @Override
  public int GetLastError() {
    return delegate.GetLastError();
  }

  @Override
  public Overlapped NewOverlapped(Handle hEvent) {
    HANDLE handle = getHandle(hEvent);
    OVERLAPPED op = new OVERLAPPED();
    op.hEvent = handle;
    op.write();
    return new JNAOverlapped(op);
  }

  @Override
  public void DeleteOverlapped(Overlapped op) {}

  @Override
  public int ERROR_IO_PENDING() {
    return WinError.ERROR_IO_PENDING;
  }

  @Override
  public int ERROR_NO_DATA() {
    return WinError.ERROR_NO_DATA;
  }

  @Override
  public int ERROR_PIPE_CONNECTED() {
    return WinError.ERROR_NO_DATA;
  }

  @Override
  public int FILE_ALL_ACCESS() {
    return WinNT.FILE_ALL_ACCESS;
  }

  @Override
  public int FILE_FLAG_FIRST_PIPE_INSTANCE() {
    return delegate.FILE_FLAG_FIRST_PIPE_INSTANCE;
  }

  @Override
  public int FILE_FLAG_OVERLAPPED() {
    return WinNT.FILE_FLAG_OVERLAPPED;
  }

  @Override
  public int FILE_GENERIC_READ() {
    return WinNT.FILE_GENERIC_READ;
  }

  @Override
  public int GENERIC_READ() {
    return WinNT.GENERIC_READ;
  }

  @Override
  public int GENERIC_WRITE() {
    return WinNT.GENERIC_WRITE;
  }

  @Override
  public int PIPE_ACCESS_DUPLEX() {
    return delegate.PIPE_ACCESS_DUPLEX;
  }

  private HANDLE getHandle(Handle handle) {
    if (handle instanceof JNAHandle) return ((JNAHandle) handle).handle;
    else
      throw new IllegalStateException(
          "Incompatible handle " + handle + " of type: " + handle.getClass());
  }

  private OVERLAPPED getOverlap(Overlapped overlapped) {
    if (overlapped instanceof JNAOverlapped) return ((JNAOverlapped) overlapped).overlapped;
    else
      throw new IllegalStateException("Incompatible overlapped of type: " + overlapped.getClass());
  }
}
