package org.scalasbt.ipcsocket;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Like sbt's server: one thread reads with a short timeout while another writes. A timed-out read
 * must not fail the writes, and must not lose data that arrives afterwards. The writes must send
 * the data they were given.
 */
public class TimeoutWhileWritingTest extends BaseSocketSetup {
  @Test(timeout = 60000)
  public void writesSurviveReadTimeouts() throws Exception {
    // only named pipes implement SO_TIMEOUT
    if (!isWin) return;
    withSocket(
        sock -> {
          ServerSocket serverSocket = newServerSocket(sock);
          SocketPair pair = SocketPair.connect(this, serverSocket, sock);
          try (Socket server = pair.peer;
              Socket client = pair.client) {
            server.setSoTimeout(50);
            final int n = 2000;
            CompletableFuture<Void> reader =
                CompletableFuture.runAsync(
                    () -> {
                      try {
                        InputStream in = server.getInputStream();
                        int expected = 0;
                        while (expected < 20) {
                          try {
                            int b = in.read();
                            assertEquals("message lost after a read timeout", expected, b);
                            expected++;
                          } catch (SocketTimeoutException e) {
                          }
                        }
                      } catch (IOException e) {
                        throw new RuntimeException(e);
                      }
                    });
            CompletableFuture<Void> clientWriter =
                CompletableFuture.runAsync(
                    () -> {
                      try {
                        OutputStream out = client.getOutputStream();
                        for (int i = 0; i < 20; i++) {
                          Thread.sleep(120);
                          out.write(i);
                        }
                      } catch (Exception e) {
                        throw new RuntimeException(e);
                      }
                    });
            CompletableFuture<byte[]> clientReader =
                CompletableFuture.supplyAsync(
                    () -> {
                      try {
                        byte[] buf = new byte[n * 100];
                        new DataInputStream(client.getInputStream()).readFully(buf);
                        return buf;
                      } catch (IOException e) {
                        throw new RuntimeException(e);
                      }
                    });
            // The writes fill the pipe, so some complete later, after WriteFile has returned
            OutputStream out = server.getOutputStream();
            byte[] chunk = new byte[100];
            for (int i = 0; i < n; i++) {
              Arrays.fill(chunk, (byte) i);
              out.write(chunk);
              if (i % 100 == 0) Thread.sleep(10);
            }
            byte[] received = clientReader.get(30, TimeUnit.SECONDS);
            for (int i = 0; i < n * 100; i++) {
              if (received[i] != (byte) (i / 100)) {
                fail("wrong data received at byte " + i);
              }
            }
            clientWriter.get(30, TimeUnit.SECONDS);
            reader.get(30, TimeUnit.SECONDS);
          } catch (Exception e) {
            throw new IOException(e);
          } finally {
            serverSocket.close();
          }
        });
  }
}
