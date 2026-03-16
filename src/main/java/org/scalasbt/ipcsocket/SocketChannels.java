package org.scalasbt.ipcsocket;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.ProtocolFamily;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class SocketChannels {
  static boolean isJava17Plus() {
    return ServerSocketChannels.isJava17Plus();
  }

  public static SocketChannel newSocketChannel(final String pathName, final boolean jni)
      throws IOException {
    if (ServerSocketChannels.isWin) {
      return SocketChannels.fromSocket(new Win32NamedPipeSocket(pathName, jni));
    } else {
      return newUnixDomainSocket(pathName, jni);
    }
  }

  public static SocketChannel newUnixDomainSocket(final String pathName, final boolean jni)
      throws IOException {
    SocketChannel result;
    if (isJava17Plus()) {
      try {
        result = newJdkUnixDomainSocket(pathName);
      } catch (ReflectiveOperationException e) {
        throw new IOException("failed to create " + pathName, e);
      }
    } else {
      result = SocketChannels.fromSocket(new UnixDomainSocket(pathName, jni));
    }
    return result;
  }

  /**
   * This checks if the channel is available for reading. Currently this is only supported for JDK
   * 17 Unix Domain Sockets only.
   */
  public static int available(SocketChannel channel) throws IOException {
    if (isJava17Plus() && !ServerSocketChannels.isWin) {
      if (channel.isBlocking()) {
        throw new IOException("unsupported operation");
      } else {
        try (Selector sel = Selector.open()) {
          channel.register(sel, SelectionKey.OP_READ);
          final int numOfKeys = sel.selectNow();
          if (numOfKeys == 0) {
            return 0;
          } else {
            return 1;
          }
        }
      }
    } else {
      return channel.socket().getInputStream().available();
    }
  }

  /** Utility function to read until newline from a blocking channel. */
  public static String readLine(SocketChannel channel) throws IOException {
    return readLine(channel, 0);
  }

  /** Utility function to read until newline from a non-blocking channel. */
  public static String readLine(SocketChannel channel, int readTimeoutMillis) throws IOException {
    int readBytes;
    byte b;
    final List<Byte> values = new ArrayList<>();
    final int bufSize = 1;
    final ByteBuffer buf = ByteBuffer.allocate(bufSize);
    do {
      try (Selector sel = Selector.open()) {
        buf.rewind();
        int numOfKeys = -1;
        if (isJava17Plus() && !ServerSocketChannels.isWin) {
          if (!channel.isBlocking()) {
            channel.register(sel, SelectionKey.OP_READ);
            numOfKeys = sel.select(readTimeoutMillis);
          }
        } else {
          if (channel.supportedOptions().contains(SO_TIMEOUT)) {
            channel.setOption(SO_TIMEOUT, Integer.valueOf(readTimeoutMillis));
          }
        }
        if (numOfKeys == 0) {
          throw new SocketTimeoutException(
              "readLine timed out after " + Integer.toString(readTimeoutMillis) + " msec");
        } else {
          readBytes = channel.read(buf);
        }
        if (readBytes == 1) {
          b = buf.get(0);
          values.add(b);
        } else {
          b = 0;
        }
      }
    } while (readBytes > 0 && b != '\n');
    ByteBuffer buf2 = ByteBuffer.allocate(values.size());
    for (int i = 0; i < values.size(); i++) {
      buf2.put(values.get(i));
    }
    return new String(buf2.array(), StandardCharsets.UTF_8).replace("\n", "").replace("\r", "");
  }

  /** Utility function to read all buffer from a blocking channel. */
  public static ByteBuffer readAll(SocketChannel channel) throws IOException {
    return readAll(channel, 0);
  }

  /** Utility function to read all buffer from a non-blocking channel. */
  public static ByteBuffer readAll(SocketChannel channel, int readTimeoutMillis)
      throws IOException {
    int readBytes;
    final List<Byte> values = new ArrayList<>();
    final int bufSize = 1024 * 1024;
    final ByteBuffer buf = ByteBuffer.allocate(bufSize);
    do {
      try (Selector sel = Selector.open()) {
        buf.rewind();
        int numOfKeys = -1;
        if (isJava17Plus() && !ServerSocketChannels.isWin) {
          if (!channel.isBlocking()) {
            channel.register(sel, SelectionKey.OP_READ);
            numOfKeys = sel.select(readTimeoutMillis);
          }
        } else {
          // if (readTimeoutMillis > 0) {
          //   throw new IOException("timeout requires JDK 17 and non-Windows");
          // }
          // The following operation gets blocked on JDK 8
          // channel.register(sel, SelectionKey.OP_READ);
          // numOfKeys = sel.select(readTimeoutMilis);
          if (channel.supportedOptions().contains(SO_TIMEOUT)) {
            channel.setOption(SO_TIMEOUT, Integer.valueOf(readTimeoutMillis));
          }
        }
        if (numOfKeys == 0) {
          throw new SocketTimeoutException(
              "readAll timed out after " + Integer.toString(readTimeoutMillis) + " msec");
        } else {
          readBytes = channel.read(buf);
        }
        if (readBytes > 0) {
          for (int i = 0; i < readBytes; i++) {
            values.add(buf.get(i));
          }
        }
      }
    } while (readBytes == bufSize);
    ByteBuffer buf2 = ByteBuffer.allocate(values.size());
    for (int i = 0; i < values.size(); i++) {
      buf2.put(values.get(i));
    }
    buf2.rewind();
    return buf2;
  }

  static SocketChannel newJdkUnixDomainSocket(final String pathName)
      throws ReflectiveOperationException, IOException {
    final SocketAddress address = ServerSocketChannels.unixDomainSocketAddress(pathName);
    final ProtocolFamily protocolFamily = ServerSocketChannels.unixProtocolFamily();
    final Method openMethod = SocketChannel.class.getMethod("open", java.net.ProtocolFamily.class);
    final SocketChannel socketChannel = (SocketChannel) openMethod.invoke(null, protocolFamily);
    socketChannel.connect(address);
    socketChannel.finishConnect();
    return socketChannel;
  }

  public static SocketChannel fromSocket(Socket socket) {
    return new ForwardingSocketChannel(socket);
  }

  static final class ForwardingSocketChannel extends SocketChannel {
    private final Socket socket;

    private ForwardingSocketChannel(Socket socket) {
      super(SelectorProvider.provider());
      this.socket = socket;
    }

    @Override
    public String toString() {
      return "ForwardingSocketChannel(" + this.socket.toString() + ")";
    }

    @Override
    public Socket socket() {
      return this.socket;
    }

    @Override
    public SocketAddress getLocalAddress() {
      return this.socket.getLocalSocketAddress();
    }

    @Override
    public SocketAddress getRemoteAddress() {
      return this.socket.getLocalSocketAddress();
    }

    @Override
    public final int write(ByteBuffer buffer) throws IOException {
      byte[] bs = buffer.array();
      this.socket.getOutputStream().write(bs);
      return bs.length;
    }

    @Override
    public final long write(ByteBuffer buffers[], int offset, int length) throws IOException {
      if (length == 0 || buffers.length == 0) {
        return 0;
      } else {

        return write(buffers[offset]);
      }
    }

    @Override
    public final int read(ByteBuffer buffer) throws IOException {
      byte[] bs = new byte[buffer.remaining()];
      int size = this.socket.getInputStream().read(bs);
      buffer.put(bs, buffer.position(), size);
      return size;
    }

    @Override
    public final long read(ByteBuffer buffers[], int offset, int length) throws IOException {
      if (length == 0 || buffers.length == 0) {
        return 0;
      } else {
        return read(buffers[offset]);
      }
    }

    @Override
    /** Ignore connect. Assume a connected socket. */
    public final boolean connect(SocketAddress address) throws IOException {
      return true;
    }

    @Override
    public final boolean finishConnect() throws IOException {
      return true;
    }

    @Override
    public final boolean isConnectionPending() {
      return false;
    }

    @Override
    public final boolean isConnected() {
      return socket.isConnected();
    }

    @Override
    public final SocketChannel shutdownOutput() throws IOException {
      this.socket.shutdownOutput();
      return this;
    }

    @Override
    public final SocketChannel shutdownInput() throws IOException {
      this.socket.shutdownInput();
      return this;
    }

    @Override
    public final SocketChannel bind(SocketAddress local) throws IOException {
      this.socket.bind(local);
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
      Set<SocketOption<?>> set = new HashSet();
      set.add(SO_TIMEOUT);
      return set;
    }

    @Override
    public final <A1> A1 getOption(SocketOption<A1> name) throws SocketException {
      if (name == SO_TIMEOUT) {
        return (A1) Integer.valueOf(this.socket.getSoTimeout());
      }
      return null;
    }

    @Override
    public final <A1> SocketChannel setOption(SocketOption<A1> name, A1 value) throws IOException {
      if (name == SO_TIMEOUT) {
        Integer i = (Integer) value;
        this.socket.setSoTimeout(i);
      }
      return this;
    }
  }

  public static final SocketOption<Integer> SO_TIMEOUT =
      new CustomSocketOption<Integer>("SO_TIMEOUT", Integer.class);

  private static class CustomSocketOption<A1> implements SocketOption<A1> {
    private final String name;
    private final Class<A1> type;

    CustomSocketOption(String name, Class<A1> type) {
      this.name = name;
      this.type = type;
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public Class<A1> type() {
      return type;
    }

    @Override
    public String toString() {
      return name;
    }
  }
}
