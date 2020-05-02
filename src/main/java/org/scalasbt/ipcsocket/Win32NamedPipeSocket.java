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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;

public class Win32NamedPipeSocket extends Socket {
  private static Handle createFile(String pipeName, boolean useJNI) throws IOException {
    final Win32NamedPipeLibraryProvider provider = Win32NamedPipeLibraryProvider.get(useJNI);
    return provider.CreateFile(pipeName);
  }

  private static CloseCallback emptyCallback() {
    return new CloseCallback() {
      public void onNamedPipeSocketClose(Handle handle) throws IOException {}
    };
  }

  static final boolean DEFAULT_REQUIRE_STRICT_LENGTH = false;
  private final Handle handle;
  private final CloseCallback closeCallback;
  private final boolean requireStrictLength;
  private final InputStream is;
  private final OutputStream os;
  private final Handle readerWaitable;
  private final Handle writerWaitable;
  private final Win32NamedPipeLibraryProvider provider;

  interface CloseCallback {
    void onNamedPipeSocketClose(Handle handle) throws IOException;
  }

  /**
   * The doc for InputStream#read(byte[] b, int off, int len) states that "An attempt is made to
   * read as many as len bytes, but a smaller number may be read." However, using
   * requireStrictLength, NGWin32NamedPipeSocketInputStream can require that len matches up exactly
   * the number of bytes to read.
   */
  public Win32NamedPipeSocket(
      Handle handle, CloseCallback closeCallback, boolean requireStrictLength) throws IOException {
    this(handle, closeCallback, requireStrictLength, false);
  }
  /**
   * The doc for InputStream#read(byte[] b, int off, int len) states that "An attempt is made to
   * read as many as len bytes, but a smaller number may be read." However, using
   * requireStrictLength, NGWin32NamedPipeSocketInputStream can require that len matches up exactly
   * the number of bytes to read.
   */
  public Win32NamedPipeSocket(
      Handle handle, CloseCallback closeCallback, boolean requireStrictLength, boolean useJNI)
      throws IOException {
    this.provider =
        useJNI
            ? JNIWin32NamedPipeLibraryProvider.instance()
            : JNAWin32NamedPipeLibraryProvider.instance();
    this.handle = handle;
    this.closeCallback = closeCallback;
    this.requireStrictLength = requireStrictLength;
    this.readerWaitable = provider.CreateEvent(true, false, null);
    writerWaitable = provider.CreateEvent(true, false, null);
    this.is = new Win32NamedPipeSocketInputStream(handle);
    this.os = new Win32NamedPipeSocketOutputStream(handle);
  }

  Win32NamedPipeSocket(Handle handle, CloseCallback closeCallback) throws IOException {
    this(handle, closeCallback, DEFAULT_REQUIRE_STRICT_LENGTH, false);
  }

  public Win32NamedPipeSocket(String pipeName) throws IOException {
    this(createFile(pipeName, false), emptyCallback(), DEFAULT_REQUIRE_STRICT_LENGTH, false);
  }

  public Win32NamedPipeSocket(String pipeName, boolean useJNI) throws IOException {
    this(createFile(pipeName, useJNI), emptyCallback(), DEFAULT_REQUIRE_STRICT_LENGTH, useJNI);
  }

  @Override
  public InputStream getInputStream() {
    return is;
  }

  @Override
  public OutputStream getOutputStream() {
    return os;
  }

  @Override
  public void close() throws IOException {
    closeCallback.onNamedPipeSocketClose(handle);
  }

  @Override
  public void shutdownInput() throws IOException {}

  @Override
  public void shutdownOutput() throws IOException {}

  private class Win32NamedPipeSocketInputStream extends InputStream {
    private final Handle handle;

    Win32NamedPipeSocketInputStream(Handle handle) {
      this.handle = handle;
    }

    @Override
    public int read() throws IOException {
      int result;
      byte[] b = new byte[1];
      if (read(b) == 0) {
        result = -1;
      } else {
        result = 0xFF & b[0];
      }
      return result;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      return provider.read(readerWaitable, handle, b, off, len, requireStrictLength);
    }
  }

  private class Win32NamedPipeSocketOutputStream extends OutputStream {
    private final Handle handle;

    Win32NamedPipeSocketOutputStream(Handle handle) {
      this.handle = handle;
    }

    @Override
    public void write(int b) throws IOException {
      write(new byte[] {(byte) (0xFF & b)});
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      provider.write(writerWaitable, handle, b, off, len);
    }
  }
}
