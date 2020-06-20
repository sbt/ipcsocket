package org.scalasbt.ipcsocket;

public interface UnixDomainSocketLibraryProvider {
  int socket(int domain, int type, int protocol) throws NativeErrorException;

  int bind(int fd, byte[] address, int addressLen) throws NativeErrorException;

  int listen(int fd, int backlog) throws NativeErrorException;

  int accept(int fd) throws NativeErrorException;

  int connect(int fd, byte[] address, int len) throws NativeErrorException;

  int read(int fd, byte[] buffer, int offset, int len) throws NativeErrorException;

  int write(int fd, byte[] buffer, int offset, int len) throws NativeErrorException;

  int close(int fd) throws NativeErrorException;

  int shutdown(int fd, int how) throws NativeErrorException;

  static UnixDomainSocketLibraryProvider get(boolean useJNI) {
    return useJNI
        ? JNIUnixDomainSocketLibraryProvider.instance()
        : JNAUnixDomainSocketLibraryProvider.instance();
  }
}
