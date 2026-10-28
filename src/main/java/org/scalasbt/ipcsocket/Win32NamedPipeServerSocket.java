/*

Copyright 2004-2017, Martian Software, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

*/
package org.scalasbt.ipcsocket;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class Win32NamedPipeServerSocket extends ServerSocket {
  private static final String WIN32_PIPE_PREFIX = "\\\\.\\pipe\\";
  private static final int BUFFER_SIZE = 65535;
  private final LinkedBlockingQueue<Handle> openHandles;
  private final LinkedBlockingQueue<Handle> connectedHandles;
  private final Win32NamedPipeSocket.CloseCallback closeCallback;
  private final String path;
  private final int maxInstances;
  private final Handle lockHandle;
  private final boolean requireStrictLength;
  private final Win32NamedPipeLibraryProvider provider;
  private final boolean useJNI;
  private final int securityLevel;
  private final AtomicBoolean closed = new AtomicBoolean(false);

  public Win32NamedPipeServerSocket(String path) throws IOException {
    this(Win32NamedPipeLibrary.PIPE_UNLIMITED_INSTANCES, path);
  }

  public Win32NamedPipeServerSocket(String path, boolean useJNI, int securityLevel)
      throws IOException {
    this(
        Win32NamedPipeLibrary.PIPE_UNLIMITED_INSTANCES,
        path,
        Win32NamedPipeSocket.DEFAULT_REQUIRE_STRICT_LENGTH,
        useJNI,
        securityLevel);
  }

  /**
   * The doc for InputStream#read(byte[] b, int off, int len) states that "An attempt is made to
   * read as many as len bytes, but a smaller number may be read." However, using
   * requireStrictLength, Win32NamedPipeSocketInputStream can require that len matches up exactly
   * the number of bytes to read.
   */
  @Deprecated
  public Win32NamedPipeServerSocket(String path, boolean requireStrictLength) throws IOException {
    this(Win32NamedPipeLibrary.PIPE_UNLIMITED_INSTANCES, path, requireStrictLength);
  }

  public Win32NamedPipeServerSocket(int maxInstances, String path) throws IOException {
    this(maxInstances, path, Win32NamedPipeSocket.DEFAULT_REQUIRE_STRICT_LENGTH);
  }

  /**
   * The doc for InputStream#read(byte[] b, int off, int len) states that "An attempt is made to
   * read as many as len bytes, but a smaller number may be read." However, using
   * requireStrictLength, NGWin32NamedPipeSocketInputStream can require that len matches up exactly
   * the number of bytes to read.
   */
  public Win32NamedPipeServerSocket(int maxInstances, String path, boolean requireStrictLength)
      throws IOException {
    this(maxInstances, path, requireStrictLength, false, Win32SecurityLevel.LOGON_DACL);
  }
  /**
   * The doc for InputStream#read(byte[] b, int off, int len) states that "An attempt is made to
   * read as many as len bytes, but a smaller number may be read." However, using
   * requireStrictLength, NGWin32NamedPipeSocketInputStream can require that len matches up exactly
   * the number of bytes to read.
   */
  public Win32NamedPipeServerSocket(
      int maxInstances, String path, boolean requireStrictLength, boolean useJNI, int securityLevel)
      throws IOException {
    this.securityLevel = securityLevel;
    this.useJNI = useJNI;
    this.provider =
        useJNI
            ? JNIWin32NamedPipeLibraryProvider.instance()
            : JNAWin32NamedPipeLibraryProvider.instance();
    this.openHandles = new LinkedBlockingQueue<>();
    this.connectedHandles = new LinkedBlockingQueue<>();
    this.closeCallback =
        handle -> {
          if (connectedHandles.remove(handle)) {
            closeConnectedPipe(handle);
          }
          if (openHandles.remove(handle)) {
            closeOpenPipe(handle);
          }
        };
    this.maxInstances = maxInstances;
    this.requireStrictLength = requireStrictLength;
    if (!path.startsWith(WIN32_PIPE_PREFIX)) {
      this.path = WIN32_PIPE_PREFIX + path;
    } else {
      this.path = path;
    }
    String lockPath = this.path + "_lock";
    try {
      lockHandle =
          provider.CreateNamedPipe(
              lockPath,
              provider.FILE_FLAG_FIRST_PIPE_INSTANCE() | provider.PIPE_ACCESS_DUPLEX(),
              0,
              1,
              BUFFER_SIZE,
              BUFFER_SIZE,
              0,
              provider.FILE_GENERIC_READ(),
              securityLevel);
    } catch (final IOException e) {
      throw new IOException("Could not create lock for " + lockPath, e);
    }
    if (!provider.DisconnectNamedPipe(lockHandle)) {
      throw new IOException(String.format("Could not disconnect lock %d", provider.GetLastError()));
    }
  }

  public void bind(SocketAddress endpoint, int backlog) throws IOException {
    throw new IOException("Win32 named pipes do not support bind(), pass path to constructor");
  }

  @Override
  public boolean isBound() {
    return true;
  }

  @Override
  public boolean isClosed() {
    return closed.get();
  }

  // There is no TCP address; the base class would open a TCP socket to look one up
  @Override
  public int getLocalPort() {
    return -1;
  }

  @Override
  public InetAddress getInetAddress() {
    return null;
  }

  @Override
  public SocketAddress getLocalSocketAddress() {
    return null;
  }

  public Socket accept() throws IOException {
    if (closed.get()) {
      throw new SocketException("Socket is closed");
    }
    Handle handle;
    try {
      handle =
          provider.CreateNamedPipe(
              path,
              provider.PIPE_ACCESS_DUPLEX() | provider.FILE_FLAG_OVERLAPPED(),
              0,
              maxInstances,
              BUFFER_SIZE,
              BUFFER_SIZE,
              0,
              provider.FILE_ALL_ACCESS(),
              securityLevel);
    } catch (final IOException e) {
      throw new IOException("Could not create named pipe " + path, e);
    }
    openHandles.add(handle);
    // a close() since the check above may have missed the handle; then nothing would end accept
    if (closed.get()) {
      if (openHandles.remove(handle)) {
        closeOpenPipe(handle);
      }
      throw new SocketException("Socket is closed");
    }

    Handle connWaitable = provider.CreateEvent(true, false, null);
    Overlapped overlapped = provider.NewOverlapped(connWaitable);
    try {

      int connectError = provider.ConnectNamedPipe(handle, overlapped);
      if (connectError == -1 || connectError == provider.ERROR_PIPE_CONNECTED()) {
        return connected(handle);
      } else if (connectError == provider.ERROR_NO_DATA()) {
        // Client has connected and disconnected between CreateNamedPipe() and ConnectNamedPipe()
        // connection is broken, but it is returned it avoid loop here.
        // Actual error will happen for NGSession when it will try to read/write from/to pipe
        return connected(handle);
      } else if (connectError == provider.ERROR_IO_PENDING()) {
        if (!provider.GetOverlappedResult(handle, overlapped)) {
          int error = provider.GetLastError();
          throw connectFailed(
              handle, "GetOverlappedResult() failed for connect operation: " + error);
        }
        return connected(handle);
      } else {
        throw connectFailed(handle, "ConnectNamedPipe() failed with: " + connectError);
      }
    } finally {
      provider.DeleteOverlapped(overlapped);
      provider.CloseHandle(connWaitable);
    }
  }

  /**
   * Closes a pipe that failed to connect, unless close() has taken it and so caused the failure.
   */
  private IOException connectFailed(Handle handle, String message) throws IOException {
    if (!openHandles.remove(handle)) {
      return new SocketException("Socket is closed");
    }
    closeOpenPipe(handle);
    return new IOException(message);
  }

  /**
   * Hands a connected pipe to a new socket, unless close() has already taken the handle: by then it
   * is closed, and Windows may have given its value to an unrelated object.
   */
  private Socket connected(Handle handle) throws IOException {
    if (!openHandles.remove(handle)) {
      throw new SocketException("Socket is closed");
    }
    connectedHandles.add(handle);
    return new Win32NamedPipeSocket(handle, closeCallback, requireStrictLength, useJNI);
  }

  public void close() throws IOException {
    // Releases the TCP socket the base class creates for options such as setSoTimeout. Before
    // JDK 21, the base close() does nothing once isClosed() returns true, so call it first.
    super.close();
    // A second close would close lockHandle again, by then maybe another object's handle
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    try {
      List<Handle> handlesToClose = new ArrayList<>();
      openHandles.drainTo(handlesToClose);
      for (Handle handle : handlesToClose) {
        closeOpenPipe(handle);
      }

      List<Handle> handlesToDisconnect = new ArrayList<>();
      connectedHandles.drainTo(handlesToDisconnect);
      for (Handle handle : handlesToDisconnect) {
        closeConnectedPipe(handle);
      }
    } finally {
      provider.CloseHandle(lockHandle);
    }
  }

  private void closeOpenPipe(Handle handle) throws IOException {
    provider.CancelIoEx(handle);
    provider.CloseHandle(handle);
  }

  private void closeConnectedPipe(Handle handle) throws IOException {
    // Not FlushFileBuffers and DisconnectNamedPipe: the flush waits until the client has read
    // everything, and the disconnect discards what it hasn't. After a plain close, the client can
    // still read the data, then sees a broken pipe.
    provider.CloseHandle(handle);
  }
}
