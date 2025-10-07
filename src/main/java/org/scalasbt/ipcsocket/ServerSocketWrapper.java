package org.scalasbt.ipcsocket;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.ProtocolFamily;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.StandardProtocolFamily;
import java.nio.channels.ServerSocketChannel;
import java.util.Arrays;

public abstract class ServerSocketWrapper {
  private ServerSocketWrapper() {}

  private static SocketAddress unixDomainSocketAddress(final String pathName)
      throws ReflectiveOperationException {
    final Class<?> clazz = Class.forName("java.net.UnixDomainSocketAddress");
    final Method method = clazz.getMethod("of", String.class);
    return (SocketAddress) method.invoke(null, pathName);
  }

  private static ProtocolFamily unixProtocolFamily() {
    return Arrays.stream(StandardProtocolFamily.class.getEnumConstants())
        .filter(a -> "UNIX".equals(a.name()))
        .findFirst()
        .orElseThrow(() -> new RuntimeException("not found UNIX value in StandardProtocolFamily"));
  }

  static ServerSocketWrapper newJdkUnixDomainSocket(final String pathName)
      throws ReflectiveOperationException, IOException {
    final SocketAddress address = unixDomainSocketAddress(pathName);
    final ProtocolFamily protocolFamily = unixProtocolFamily();
    final Method openMethod =
        ServerSocketChannel.class.getMethod("open", java.net.ProtocolFamily.class);
    final ServerSocketChannel serverSocketChannel =
        (ServerSocketChannel) openMethod.invoke(null, protocolFamily);
    serverSocketChannel.bind(address);
    return ServerSocketWrapper.fromServerSocketChannel(serverSocketChannel);
  }

  public static ServerSocketWrapper newUnixDomainSocket(
      final String pathName, final boolean jni, final boolean jdk) throws IOException {
    ServerSocketWrapper result;
    if (jdk) {
      try {
        result = newJdkUnixDomainSocket(pathName);
      } catch (ReflectiveOperationException e) {
        result = ServerSocketWrapper.fromServerSocket(new UnixDomainServerSocket(pathName, jni));
      }
    } else {
      result = ServerSocketWrapper.fromServerSocket(new UnixDomainServerSocket(pathName, jni));
    }
    return result;
  }

  public abstract void setSoTimeout(int timeout) throws SocketException;

  public abstract SocketWrapper accept() throws IOException;

  public abstract void close() throws IOException;

  public static ServerSocketWrapper fromServerSocket(final ServerSocket socket) {
    return new ServerSocketImpl(socket);
  }

  public static ServerSocketWrapper fromServerSocketChannel(final ServerSocketChannel channel) {
    return new ServerSocketChannelImpl(channel);
  }

  private static final class ServerSocketImpl extends ServerSocketWrapper {
    private final ServerSocket socket;

    ServerSocketImpl(ServerSocket socket) {
      this.socket = socket;
    }

    @Override
    public void setSoTimeout(int timeout) throws SocketException {
      socket.setSoTimeout(timeout);
    }

    @Override
    public SocketWrapper accept() throws IOException {
      return SocketWrapper.fromSocket(socket.accept());
    }

    @Override
    public void close() throws IOException {
      socket.close();
    }
  }

  private static final class ServerSocketChannelImpl extends ServerSocketWrapper {
    private final ServerSocketChannel channel;

    ServerSocketChannelImpl(ServerSocketChannel channel) {
      this.channel = channel;
    }

    @Override
    public void setSoTimeout(int timeout) throws SocketException {}

    @Override
    public SocketWrapper accept() throws IOException {
      return SocketWrapper.fromByteChannel(channel.accept());
    }

    @Override
    public void close() throws IOException {
      channel.close();
    }
  }
}
