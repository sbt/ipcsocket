package org.scalasbt.ipcsocket;

import java.io.DataInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.Test;
import static org.junit.Assert.*;

/** Closing a socket must not wait for the peer to read, and must not discard what it wrote. */
public class ServerSideCloseTest extends BaseSocketSetup {
  @Test(timeout = 30000)
  public void closeWithUnreadData() throws Exception {
    withSocket(
        sock -> {
          ServerSocket serverSocket = newServerSocket(sock);
          SocketPair pair = SocketPair.connect(this, serverSocket, sock);
          try (Socket client = pair.client) {
            Socket peer = pair.peer;
            peer.getOutputStream().write(new byte[] {1, 2, 3, 4});
            CompletableFuture<Void> close =
                CompletableFuture.runAsync(
                    () -> {
                      try {
                        peer.close();
                      } catch (Exception e) {
                        throw new RuntimeException(e);
                      }
                    });
            boolean hung = false;
            try {
              close.get(5, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
              hung = true;
            }
            byte[] buf = new byte[4];
            new DataInputStream(client.getInputStream()).readFully(buf);
            assertFalse("close waited for the peer to read", hung);
            assertArrayEquals(new byte[] {1, 2, 3, 4}, buf);
          } catch (Exception e) {
            throw new java.io.IOException(e);
          } finally {
            serverSocket.close();
          }
        });
  }
}
