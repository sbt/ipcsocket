package org.scalasbt.ipcsocket;

class JNIUnixDomainSocketLibraryProvider implements UnixDomainSocketLibraryProvider {
  private static final JNIUnixDomainSocketLibraryProvider instance =
      new JNIUnixDomainSocketLibraryProvider();

  static final JNIUnixDomainSocketLibraryProvider instance() {
    return instance;
  }

  public int socket(int domain, int type, int protocol) throws NativeErrorException {
    return returnOrThrow(socketNative(domain, type, protocol), 0);
  }

  public int bind(int fd, byte[] address, int addressLen) throws NativeErrorException {
    return returnOrThrow(bindNative(fd, address, addressLen), 0);
  }

  public int listen(int fd, int backlog) throws NativeErrorException {
    return returnOrThrow(listenNative(fd, backlog), 0);
  }

  public int accept(int fd) throws NativeErrorException {
    return returnOrThrow(acceptNative(fd), 0);
  }

  public int connect(int fd, byte[] address, int len) throws NativeErrorException {
    return returnOrThrow(connectNative(fd, address, len), 0);
  }

  public int read(int fd, byte[] buffer, int offset, int len) throws NativeErrorException {
    return returnOrThrow(readNative(fd, buffer, offset, len), -1);
  }

  public int write(int fd, byte[] buffer, int offset, int len) throws NativeErrorException {
    return returnOrThrow(writeNative(fd, buffer, offset, len), 0);
  }

  public int close(int fd) throws NativeErrorException {
    return returnOrThrow(closeNative(fd), 0);
  }

  public int shutdown(int fd, int how) throws NativeErrorException {
    return returnOrThrow(shutdownNative(fd, how), 0);
  }

  private int returnOrThrow(int result, int threshold) throws NativeErrorException {
    if (result < threshold) {
      final String message = "Error " + (-result) + ": " + errString(-result);
      throw new NativeErrorException(-result, message);
    }
    return result;
  }

  native int socketNative(int domain, int type, int protocol);

  native int bindNative(int fd, byte[] address, int addressLen);

  native int listenNative(int fd, int backlog);

  native int acceptNative(int fd);

  native int connectNative(int fd, byte[] address, int len);

  native int readNative(int fd, byte[] buffer, int offset, int len);

  native int writeNative(int fd, byte[] buffer, int offset, int len);

  native int closeNative(int fd);

  native int shutdownNative(int fd, int how);

  native String errString(int error);

  static {
    NativeLoader.load();
  }
}
