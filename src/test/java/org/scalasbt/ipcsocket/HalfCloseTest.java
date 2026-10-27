package org.scalasbt.ipcsocket;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import org.junit.Test;
import static org.junit.Assert.*;

public class HalfCloseTest extends BaseSocketSetup {
  private static int drain(InputStream in) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buf = new byte[64];
    int n;
    while ((n = in.read(buf, 0, buf.length)) != -1) out.write(buf, 0, n);
    return out.size();
  }

  /** Send a request, shut down output, then read the response. */
  @Test(timeout = 10000)
  public void respondAfterEndOfRequest() throws IOException, InterruptedException {
    // named pipes don't support shutdownOutput
    if (isWin) return;
    withSocket(
        sock -> {
          ServerSocket serverSocket = newServerSocket(sock);
          try (Socket client = newClientSocket(sock);
              Socket peer = serverSocket.accept()) {
            client.getOutputStream().write(new byte[] {1, 2, 3});
            client.shutdownOutput();
            assertEquals(3, drain(peer.getInputStream()));
            peer.getOutputStream().write(new byte[] {4, 5});
            peer.shutdownOutput();
            assertEquals(2, drain(client.getInputStream()));
          } finally {
            serverSocket.close();
          }
        });
  }

  @Test(timeout = 10000)
  public void writeAfterCloseThrows() throws IOException, InterruptedException {
    if (isWin) return;
    withSocket(
        sock -> {
          ServerSocket serverSocket = newServerSocket(sock);
          try (Socket peer = newClientSocket(sock)) {
            Socket client = newClientSocket(sock);
            client.close();
            try {
              client.getOutputStream().write(1);
              fail("write to a closed socket succeeded");
            } catch (SocketException e) {
            }
          } finally {
            serverSocket.close();
          }
        });
  }
}
