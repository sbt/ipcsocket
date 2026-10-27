package org.scalasbt.ipcsocket;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.Test;
import static org.junit.Assert.*;

/** Closing a socket is how a thread blocked on it is stopped. */
public class CloseWakesBlockedTest extends BaseSocketSetup {
  interface Blocking {
    void run() throws IOException;
  }

  private static void assertCloseWakes(Blocking blocking, Blocking close) throws Exception {
    CompletableFuture<Void> blocked =
        CompletableFuture.runAsync(
            () -> {
              try {
                blocking.run();
              } catch (Exception e) {
                // woken, or close() came first
              }
            });
    Thread.sleep(500);
    close.run();
    try {
      blocked.get(5, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      fail("close did not wake the blocked thread");
    }
  }

  @Test(timeout = 20000)
  public void closeWakesRead() throws Exception {
    if (isWin) return;
    withSocket(
        sock -> {
          ServerSocket serverSocket = newServerSocket(sock);
          try (Socket client = newClientSocket(sock);
              Socket peer = serverSocket.accept()) {
            assertCloseWakes(() -> client.getInputStream().read(), client::close);
          } catch (Exception e) {
            throw new IOException(e);
          } finally {
            serverSocket.close();
          }
        });
  }

  @Test(timeout = 20000)
  public void closeWakesAccept() throws Exception {
    if (isWin) return;
    withSocket(
        sock -> {
          ServerSocket serverSocket = newServerSocket(sock);
          try {
            assertCloseWakes(() -> serverSocket.accept().close(), serverSocket::close);
          } catch (Exception e) {
            throw new IOException(e);
          }
        });
  }
}
