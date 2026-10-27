package org.scalasbt.ipcsocket;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import org.junit.Test;
import static org.junit.Assert.*;

/** A read that times out must not consume or lose data that arrives later. */
public class ReadTimeoutTest extends BaseSocketSetup {
  @Test(timeout = 20000)
  public void readAfterTimeoutGetsData() throws IOException, InterruptedException {
    // only named pipes implement SO_TIMEOUT
    if (!isWin) return;
    withSocket(
        sock -> {
          ServerSocket serverSocket = newServerSocket(sock);
          SocketPair pair = SocketPair.connect(this, serverSocket, sock);
          try (Socket client = pair.client;
              Socket peer = pair.peer) {
            client.setSoTimeout(100);
            for (int i = 0; i < 20; i++) {
              try {
                client.getInputStream().read(new byte[16]);
                fail("read returned without data");
              } catch (SocketTimeoutException e) {
              }
              System.gc();
            }
            peer.getOutputStream().write(new byte[] {1, 2, 3});
            client.setSoTimeout(5000);
            byte[] buf = new byte[16];
            assertEquals(3, client.getInputStream().read(buf));
            assertArrayEquals(new byte[] {1, 2, 3}, java.util.Arrays.copyOf(buf, 3));
          } finally {
            serverSocket.close();
          }
        });
  }
}
