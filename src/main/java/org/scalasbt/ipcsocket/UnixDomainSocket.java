/*

Copyright 2004-2015, Martian Software, Inc.

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

import java.nio.ByteBuffer;

import java.net.Socket;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implements a {@link Socket} backed by a native Unix domain socket.
 *
 * <p>Instances of this class always return {@code null} for {@link Socket#getInetAddress()}, {@link
 * Socket#getLocalAddress()}, {@link Socket#getLocalSocketAddress()}, {@link
 * Socket#getRemoteSocketAddress()}.
 */
public class UnixDomainSocket extends Socket {
  private final UnixDomainSocketLibraryProvider provider;
  private final ReferenceCountedFileDescriptor fd;
  private final InputStream is;
  private final OutputStream os;
  private final String path;
  private static final int SHUT_RD = 0;
  private static final int SHUT_WR = 1;

  /** Creates a Unix domain socket backed by a file path. */
  public UnixDomainSocket(String path, boolean useJNI) throws IOException {
    try {
      this.path = path;
      provider = UnixDomainSocketLibraryProvider.get(useJNI);
      AtomicInteger fd =
          new AtomicInteger(
              provider.socket(
                  UnixDomainSocketLibrary.PF_LOCAL, UnixDomainSocketLibrary.SOCK_STREAM, 0));
      int socketFd = fd.get();
      provider.connect(socketFd, path.getBytes(), path.length());
      this.fd = new ReferenceCountedFileDescriptor(socketFd, provider);
      this.is = new UnixDomainSocketInputStream();
      this.os = new UnixDomainSocketOutputStream();
    } catch (NativeErrorException e) {
      throw new IOException(e);
    }
  }

  public UnixDomainSocket(String path) throws IOException {
    this(path, false);
  }

  /** Creates a Unix domain socket backed by a native file descriptor. */
  public UnixDomainSocket(int fd, boolean useJNI) {
    provider = UnixDomainSocketLibraryProvider.get(useJNI);
    this.path = null;
    this.fd = new ReferenceCountedFileDescriptor(fd, provider);
    this.is = new UnixDomainSocketInputStream();
    this.os = new UnixDomainSocketOutputStream();
  }

  public UnixDomainSocket(int fd) {
    this(fd, false);
  }

  public InputStream getInputStream() {
    return is;
  }

  public OutputStream getOutputStream() {
    return os;
  }

  public void shutdownInput() throws IOException {
    doShutdown(SHUT_RD);
  }

  public void shutdownOutput() throws IOException {
    doShutdown(SHUT_WR);
  }

  private void doShutdown(int how) throws IOException {
    try {
      int socketFd = fd.acquire();
      if (socketFd != -1) {
        provider.shutdown(socketFd, how);
      }
    } catch (NativeErrorException e) {
      throw new IOException(e);
    } finally {
      fd.release();
    }
  }

  public void close() throws IOException {
    super.close();
    // This might not close the FD right away. In case we are about
    // to read or write on another thread, it will delay the close
    // until the read or write completes, to prevent the FD from
    // being re-used for a different purpose and the other thread
    // reading from a different FD.
    fd.close();
  }

  private class UnixDomainSocketInputStream extends InputStream {
    public int read() throws IOException {
      byte[] buf = new byte[1];
      int result;
      if (doRead(buf, 0, 1) == 0) {
        result = -1;
      } else {
        // Make sure to & with 0xFF to avoid sign extension
        result = 0xFF & buf[0];
      }
      return result;
    }

    public int read(byte[] b, int off, int len) throws IOException {
      if (len == 0) {
        return 0;
      }
      int result = doRead(b, off, len);
      if (result == 0) {
        try {
          provider.close(fd.acquire());
        } catch (final NativeErrorException e) {
          throw new IOException(
              "Error closing " + fd.acquire() + (path == null ? "" : " for " + path));
        }
        result = -1;
      }
      return result;
    }

    private int doRead(byte[] buf, int offset, int len) throws IOException {
      try {
        int fdToRead = fd.acquire();
        if (fdToRead == -1) {
          return -1;
        }
        return provider.read(fdToRead, buf, offset, len);
      } catch (NativeErrorException e) {
        throw new IOException(e);
      } finally {
        fd.release();
      }
    }
  }

  private class UnixDomainSocketOutputStream extends OutputStream {

    public void write(int b) throws IOException {
      doWrite(new byte[] {(byte) (0xFF & b)}, 0, 1);
    }

    public void write(byte[] b, int off, int len) throws IOException {
      if (len == 0) {
        return;
      }
      doWrite(b, off, len);
    }

    private void doWrite(byte[] b, int off, int len) throws IOException {
      try {
        int fdToWrite = fd.acquire();
        if (fdToWrite == -1) {
          return;
        }
        int ret = provider.write(fdToWrite, b, off, len);
        if (ret != len) {
          // This shouldn't happen with standard blocking Unix domain sockets.
          throw new IOException(
              "Could not write "
                  + len
                  + " bytes as requested "
                  + "(wrote "
                  + ret
                  + " bytes instead)");
        }
      } catch (NativeErrorException e) {
        throw new IOException(e);
      } finally {
        fd.release();
      }
    }
  }
}
