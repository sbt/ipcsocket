package org.scalasbt.ipcsocket;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CompletableFuture;
import org.junit.Test;
import static org.junit.Assert.*;

public class SocketChannelTest extends BaseSocketSetup {
  static boolean isJava17Plus() {
    return SocketChannels.isJava17Plus();
  }

  /** Test the non-blocking echo server using JDK 17 Unix Domain Socket. */
  @Test
  public void testNonBlockingEchoServer() throws IOException, InterruptedException {
    System.out.println(
        "SocketChannelTest#testNonBlockingEchoServer(" + Boolean.toString(useJNI()) + ")");
    withSocket(
        sock -> {
          if (isJava17Plus() || ServerSocketChannels.isWin) {
            String line = nonBlockingEchoServerTest(sock, "hello", 100, 600);
            assertEquals("echo did not return the content", "hello", line);
          }
        });
  }

  /** Test the non-blocking echo server using JDK 17 Unix Domain Socket. */
  @Test
  public void testTimeout() throws IOException, InterruptedException {
    System.out.println("SocketChannelTest#testTimeout(" + Boolean.toString(useJNI()) + ")");
    withSocket(
        sock -> {
          if (isJava17Plus() || ServerSocketChannels.isWin) {
            String line = nonBlockingEchoServerTest(sock, "hello", 6000, 600);
            assertEquals("echo did not timeout", "<unavailable>", line);
          }
        });
  }

  /*
  @Test
  public void testNonBlockingLargeMessage() throws IOException, InterruptedException {
    System.out.println(
        "SocketChannelTest#testNonBlockingLargeMessage(" + Boolean.toString(useJNI()) + ")");
    withSocket(
        sock -> {
          StringBuilder sb = new StringBuilder();
          for (int i = 0; i < 1024 * 1024; i++) {
            sb.append("a");
          }
          String message = sb.toString();
          String line = nonBlockingEchoServerTest(sock, message, 100, 600);
          assertEquals("echo did not return the content", message, line);
        });
  }
  */

  /** Test the blocking echo server. */
  @Test
  public void testBlockingEchoServer() throws IOException, InterruptedException {
    System.out.println(
        "SocketChannelTest#testBlockingEchoServer(" + Boolean.toString(useJNI()) + ")");
    withSocket(
        sock -> {
          String line = blockingEchoServerTest(sock, "hello");
          assertEquals("echo did not return the content", "hello", line);
        });
  }

  @Test
  public void testBlockingLargeMessage() throws IOException, InterruptedException {
    System.out.println(
        "SocketChannelTest#testBlockingLargeMessage(" + Boolean.toString(useJNI()) + ")");
    withSocket(
        sock -> {
          StringBuilder sb = new StringBuilder();
          for (int i = 0; i < 1024 * 1024; i++) {
            sb.append("a");
          }
          String message = sb.toString();
          String line = blockingEchoServerTest(sock, message);
          assertEquals("echo did not return the content", message, line);
        });
  }

  private String nonBlockingEchoServerTest(
      String sock, String message, int sleepBeforeSend, int sleepBeforeReceive)
      throws IOException, InterruptedException {
    ServerSocketChannel serverSocket = ServerSocketChannels.newServerSocketChannel(sock, useJNI());
    CompletableFuture<Boolean> server =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                NonBlockingEchoServer echo = new NonBlockingEchoServer(serverSocket);
                echo.run();
              } catch (IOException e) {
                // e.printStackTrace();
              }
              return true;
            });
    Thread.sleep(100);
    SocketChannel client = SocketChannels.newSocketChannel(sock.toString(), useJNI());
    System.out.println("client: " + client.toString());
    Thread.sleep(sleepBeforeSend);
    ByteBuffer buf = ByteBuffer.wrap((message + "\n").getBytes("UTF-8"));
    client.configureBlocking(false);
    CompletableFuture.supplyAsync(
        () -> {
          try {
            do {
              int written = client.write(buf);
              // System.out.println("client: wrote " + Integer.toString(written) + " bytes");
            } while (buf.remaining() > 0);
          } catch (IOException e) {
            e.printStackTrace();
          }
          return true;
        });
    Thread.sleep(100);

    String line;
    int ready = 0;
    ready = SocketChannels.available(client);
    System.out.println("client: " + Integer.toString(ready) + " bytes ready");
    if (ready <= 0) {
      Thread.sleep(100);
      ready = SocketChannels.available(client);
      System.out.println("client: " + Integer.toString(ready) + " bytes ready");
    }
    if (ready <= 0) {
      Thread.sleep(sleepBeforeReceive);
      ready = SocketChannels.available(client);
      System.out.println("client: " + Integer.toString(ready) + " bytes ready");
    }
    if (ready > 0) {
      try {
        line = SocketChannels.readLine(client, 1000);
      } catch (SocketTimeoutException e) {
        line = "<timeout>";
      }
    } else {
      line = "<unavailable>";
    }
    client.close();
    server.cancel(true);
    serverSocket.close();
    return line;
  }

  private String blockingEchoServerTest(String sock, String message)
      throws IOException, InterruptedException {
    ServerSocketChannel serverSocket = ServerSocketChannels.newServerSocketChannel(sock, useJNI());
    CompletableFuture<Boolean> server =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                BlockingEchoServer echo = new BlockingEchoServer(serverSocket);
                echo.run();
              } catch (IOException e) {
                // e.printStackTrace();
              }
              return true;
            });
    Thread.sleep(100);
    SocketChannel client = SocketChannels.newSocketChannel(sock.toString(), useJNI());
    client.configureBlocking(false);
    System.out.println("client: " + client.toString());
    ByteBuffer buf = ByteBuffer.wrap((message + "\n").getBytes("UTF-8"));
    CompletableFuture.supplyAsync(
        () -> {
          do {
            try {
              int written = client.write(buf);
              if (written > 0) {
                System.out.println("client: wrote " + Integer.toString(written) + " bytes");
              } else {
                try {
                  Thread.sleep(1);
                } catch (Exception e2) {
                }
              }
            } catch (IOException e) {
              e.printStackTrace();
            }
          } while (buf.remaining() > 0);
          return true;
        });
    Thread.sleep(600);
    int readyBytes = 0;
    int attempt = 0;
    while (readyBytes <= 0 && attempt <= 4) {
      readyBytes = SocketChannels.available(client);
      System.out.println("client: " + Integer.toString(readyBytes) + " bytes ready");
      Thread.sleep(500);
      attempt++;
    }
    String line;
    if (readyBytes > 0) {
      line = SocketChannels.readLine(client);
    } else {
      line = "<unavailable>";
    }
    client.close();
    server.cancel(true);
    serverSocket.close();
    return line;
  }
}
