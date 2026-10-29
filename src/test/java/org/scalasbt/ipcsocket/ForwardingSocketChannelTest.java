package org.scalasbt.ipcsocket;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

/** The channel that wraps our sockets must honour the ByteBuffer contract. */
public class ForwardingSocketChannelTest extends BaseSocketSetup {
  interface ChannelTest {
    void run(SocketChannel channel, Socket peer) throws IOException;
  }

  private void withChannel(ChannelTest test) throws IOException, InterruptedException {
    withSocket(
        sock -> {
          ServerSocket serverSocket = newServerSocket(sock);
          try {
            SocketPair pair = SocketPair.connect(this, serverSocket, sock);
            SocketChannel channel = SocketChannels.fromSocket(pair.client);
            Socket peer = pair.peer;
            try {
              test.run(channel, peer);
            } finally {
              channel.close();
              peer.close();
            }
          } finally {
            serverSocket.close();
          }
        });
  }

  private static String readExactly(Socket peer, int n) throws IOException {
    byte[] bs = new byte[n];
    new DataInputStream(peer.getInputStream()).readFully(bs);
    return new String(bs, StandardCharsets.UTF_8);
  }

  @Test(timeout = 10000)
  public void readAtEndOfStreamReturnsMinusOne() throws IOException, InterruptedException {
    // Windows named pipes report a closed peer as an IOException, not as end of stream
    if (isWin) return;
    withChannel(
        (channel, peer) -> {
          peer.close();
          assertEquals(-1, channel.read(ByteBuffer.allocate(16)));
        });
  }

  @Test(timeout = 10000)
  public void readStoresAtBufferPosition() throws IOException, InterruptedException {
    withChannel(
        (channel, peer) -> {
          peer.getOutputStream().write("abc".getBytes(StandardCharsets.UTF_8));
          ByteBuffer buf = ByteBuffer.allocate(16);
          buf.put((byte) 'x').put((byte) 'y');
          int n = 0;
          while (n < 3) n += channel.read(buf);
          assertEquals(5, buf.position());
          assertEquals("xyabc", new String(buf.array(), 0, 5, StandardCharsets.UTF_8));
        });
  }

  @Test(timeout = 10000)
  public void writeSendsOnlyRemainingBytes() throws IOException, InterruptedException {
    withChannel(
        (channel, peer) -> {
          ByteBuffer buf = ByteBuffer.allocate(16);
          buf.put("hi".getBytes(StandardCharsets.UTF_8)).flip();
          while (buf.hasRemaining()) channel.write(buf);
          ByteBuffer direct = ByteBuffer.allocateDirect(8);
          direct.put((byte) '!').flip();
          assertEquals(1, channel.write(direct));
          assertFalse(direct.hasRemaining());
          assertEquals("hi!", readExactly(peer, 3));
        });
  }

  @Test(timeout = 10000)
  public void gatheringWriteSendsAllBuffers() throws IOException, InterruptedException {
    withChannel(
        (channel, peer) -> {
          ByteBuffer[] bufs = {
            ByteBuffer.wrap("skip".getBytes(StandardCharsets.UTF_8)),
            ByteBuffer.wrap("ab".getBytes(StandardCharsets.UTF_8)),
            ByteBuffer.wrap("cd".getBytes(StandardCharsets.UTF_8))
          };
          assertEquals(4, channel.write(bufs, 1, 2));
          assertEquals("abcd", readExactly(peer, 4));
        });
  }

  @Test(timeout = 10000)
  public void scatteringReadSkipsFullBuffers() throws IOException, InterruptedException {
    withChannel(
        (channel, peer) -> {
          peer.getOutputStream().write("z".getBytes(StandardCharsets.UTF_8));
          ByteBuffer[] bufs = {ByteBuffer.allocate(0), ByteBuffer.allocate(4)};
          assertEquals(1, channel.read(bufs, 0, 2));
          assertEquals('z', bufs[1].get(0));
        });
  }
}
