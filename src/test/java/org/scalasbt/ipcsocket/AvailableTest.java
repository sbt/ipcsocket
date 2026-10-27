package org.scalasbt.ipcsocket;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import org.junit.Test;

public class AvailableTest extends BaseSocketSetup {
  /**
   * available() calls ioctl(FIONREAD), which is variadic. On macOS arm64, called like a regular
   * function, ioctl wrote the count to a random address and available() returned 0.
   */
  @Test
  public void availableCountsPendingBytes() throws IOException, InterruptedException {
    assumeFalse(isWin);
    withSocket(
        sock -> {
          try (ServerSocket server = newServerSocket(sock);
              Socket client = newClientSocket(sock);
              Socket accepted = server.accept()) {
            OutputStream out = accepted.getOutputStream();
            out.write(new byte[] {1, 2, 3});
            out.flush();

            InputStream in = client.getInputStream();
            long deadline = System.currentTimeMillis() + 5000;
            while (in.available() == 0 && System.currentTimeMillis() < deadline) Thread.sleep(10);
            for (int i = 0; i < 1000; i++) assertEquals(3, in.available());
          }
        });
  }
}
