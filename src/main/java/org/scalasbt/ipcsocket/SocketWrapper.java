package org.scalasbt.ipcsocket;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.ProtocolFamily;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.ServerSocketChannel;
import java.util.Arrays;

public abstract class SocketWrapper {
  private SocketWrapper() {}

  public abstract void write(int value) throws IOException;

  public abstract void write(byte[] value) throws IOException;

  public abstract void write(byte[] b, int offset, int len) throws IOException;

  public abstract void close() throws IOException;

  public abstract int read() throws IOException;

  public abstract void flush() throws IOException;

  public static SocketWrapper fromSocket(Socket socket) {
    return new SocketImpl(socket);
  }

  public static SocketWrapper fromByteChannel(ByteChannel channel) {
    return new ByteChannelImpl(channel);
  }

  private static final class ByteChannelImpl extends SocketWrapper {
    private final ByteChannel channel;

    private ByteChannelImpl(ByteChannel channel) {
      this.channel = channel;
    }

    @Override
    public void write(int value) throws IOException {
      channel.write(ByteBuffer.wrap(new byte[] {(byte) value}));
    }

    @Override
    public void write(byte[] value) throws IOException {
      channel.write(ByteBuffer.wrap(value));
    }

    @Override
    public void write(byte[] b, int offset, int len) throws IOException {
      channel.write(ByteBuffer.wrap(b, offset, len));
    }

    @Override
    public void close() throws IOException {
      channel.close();
    }

    @Override
    public int read() throws IOException {
      final ByteBuffer buf = ByteBuffer.allocate(1);
      int n;
      do {
        n = channel.read(buf);
      } while (n == 0);

      if (-1 == n) {
        return -1;
      } else {
        return buf.get(0) & 0xff;
      }
    }

    @Override
    public void flush() {}
  }

  private static final class SocketImpl extends SocketWrapper {
    private final Socket socket;

    private SocketImpl(Socket socket) {
      this.socket = socket;
    }

    @Override
    public void write(int value) throws IOException {
      socket.getOutputStream().write(value);
    }

    @Override
    public void write(byte[] value) throws IOException {
      socket.getOutputStream().write(value);
    }

    @Override
    public void write(byte[] b, int offset, int len) throws IOException {
      socket.getOutputStream().write(b, offset, len);
    }

    @Override
    public void close() throws IOException {
      socket.getOutputStream().close();
      socket.getInputStream().close();
    }

    @Override
    public int read() throws IOException {
      return socket.getInputStream().read();
    }

    @Override
    public void flush() throws IOException {
      socket.getOutputStream().flush();
    }
  }
}
