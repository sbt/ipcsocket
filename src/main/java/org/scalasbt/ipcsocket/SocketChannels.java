package org.scalasbt.ipcsocket;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.ProtocolFamily;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public abstract class SocketChannels {

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
    if (ServerSocketChannels.isJava17Plus()) {
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

  /** Utility function to read until newline. */
  public static String readLine(SocketChannel channel) throws IOException {
    int readBytes;
    byte b;
    final List<Byte> values = new ArrayList<>();
    final int bufSize = 1;
    final ByteBuffer buf = ByteBuffer.allocate(bufSize);
    do {
      buf.rewind();
      readBytes = channel.read(buf);
      if (readBytes == 1) {
        b = buf.get(0);
        values.add(b);
      } else {
        b = 0;
      }
    } while (readBytes > 0 && b != '\n');
    ByteBuffer buf2 = ByteBuffer.allocate(values.size());
    for (int i = 0; i < values.size(); i++) {
      buf2.put(values.get(i));
    }
    return new String(buf2.array(), StandardCharsets.UTF_8).replace("\n", "").replace("\r", "");
  }

  /** Utility function to read what's in the channel. */
  public static ByteBuffer readAll(SocketChannel channel) throws IOException {
    int readBytes;
    final List<Byte> values = new ArrayList<>();
    final int bufSize = 1024 * 1024;
    final ByteBuffer buf = ByteBuffer.allocate(bufSize);
    do {
      buf.rewind();
      readBytes = channel.read(buf);
      if (readBytes > 0) {
        for (int i = 0; i < readBytes; i++) {
          values.add(buf.get(i));
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

  private static final class ForwardingSocketChannel extends SocketChannel {
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
    public final <A1> SocketChannel setOption(SocketOption<A1> name, A1 value) throws IOException {
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
      return Collections.EMPTY_SET;
    }

    @Override
    public final <A1> A1 getOption(SocketOption<A1> name) {
      return null;
    }
  }
}
