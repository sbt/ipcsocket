package org.scalasbt.ipcsocket;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.ProtocolFamily;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.net.StandardProtocolFamily;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class ServerSocketChannels {

  static final boolean isWin = System.getProperty("os.name", "").toLowerCase().startsWith("win");

  public static ServerSocketChannel newServerSocketChannel(final String pathName, final boolean jni)
      throws IOException {
    if (isWin) {
      return ServerSocketChannels.fromServerSocket(
          new Win32NamedPipeServerSocket(pathName, jni, Win32SecurityLevel.LOGON_DACL));
    } else {
      return newUnixDomainSocket(pathName, jni);
    }
  }

  public static ServerSocketChannel newUnixDomainSocket(final String pathName, final boolean jni)
      throws IOException {
    ServerSocketChannel result;
    if (isJava17Plus()) {
      try {
        result = newJdkUnixDomainSocket(pathName);
      } catch (ReflectiveOperationException e) {
        throw new IOException("failed to create " + pathName, e);
      }
    } else {
      result = ServerSocketChannels.fromServerSocket(new UnixDomainServerSocket(pathName, jni));
    }
    return result;
  }

  static boolean isJava17Plus() {
    return getJavaVersion() >= 17;
  }

  private static int getJavaVersion() {
    List<Integer> versions =
        Arrays.asList(System.getProperty("java.specification.version").split("\\."))
            .stream()
            .map(Integer::parseInt)
            .collect(Collectors.toList());
    if (versions.size() >= 2) {
      int major = versions.get(0);
      int minor = versions.get(1);
      if (major == 1) {
        return minor;
      } else {
        return major;
      }
    } else if (versions.size() >= 1) {
      return versions.get(0);
    } else {
      return 0;
    }
  }

  static ServerSocketChannel newJdkUnixDomainSocket(final String pathName)
      throws ReflectiveOperationException, IOException {
    final SocketAddress address = unixDomainSocketAddress(pathName);
    final ProtocolFamily protocolFamily = unixProtocolFamily();
    final Method openMethod =
        ServerSocketChannel.class.getMethod("open", java.net.ProtocolFamily.class);
    final ServerSocketChannel serverSocketChannel =
        (ServerSocketChannel) openMethod.invoke(null, protocolFamily);
    serverSocketChannel.bind(address);
    return serverSocketChannel;
  }

  static SocketAddress unixDomainSocketAddress(final String pathName)
      throws ReflectiveOperationException {
    final Class<?> clazz = Class.forName("java.net.UnixDomainSocketAddress");
    final Method method = clazz.getMethod("of", String.class);
    return (SocketAddress) method.invoke(null, pathName);
  }

  static ProtocolFamily unixProtocolFamily() {
    return Arrays.stream(StandardProtocolFamily.class.getEnumConstants())
        .filter(a -> "UNIX".equals(a.name()))
        .findFirst()
        .orElseThrow(() -> new RuntimeException("not found UNIX value in StandardProtocolFamily"));
  }

  public static ServerSocketChannel fromServerSocket(final ServerSocket socket) {
    return new ForwardingServerSocketChannel(socket);
  }

  private static final class ForwardingServerSocketChannel extends ServerSocketChannel {
    private final ServerSocket socket;

    ForwardingServerSocketChannel(ServerSocket socket) {
      super(SelectorProvider.provider());
      this.socket = socket;
    }

    @Override
    public String toString() {
      return "ForwardingServerSocketChannel(" + this.socket.toString() + ")";
    }

    @Override
    public SocketAddress getLocalAddress() {
      return this.socket.getLocalSocketAddress();
    }

    @Override
    public ServerSocket socket() {
      return this.socket;
    }

    @Override
    public SocketChannel accept() throws IOException {
      return SocketChannels.fromSocket(this.socket.accept());
    }

    @Override
    public final <A1> ServerSocketChannel setOption(SocketOption<A1> name, A1 value)
        throws IOException {
      return this;
    }

    @Override
    public final ServerSocketChannel bind(SocketAddress local, int backlog) throws IOException {
      this.socket.bind(local, backlog);
      return this;
    }

    @Override
    protected final void implConfigureBlocking(boolean block) throws IOException {}

    @Override
    protected final void implCloseSelectableChannel() throws IOException {
      this.socket.close();
    }

    @Override
    public final Set<SocketOption<?>> supportedOptions() {
      return Collections.EMPTY_SET;
    }

    @Override
    public final <A1> A1 getOption(SocketOption<A1> name) {
      return null;
    }
  }
}
