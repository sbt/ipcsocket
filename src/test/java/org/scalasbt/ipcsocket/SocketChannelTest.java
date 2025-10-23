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
          if (isJava17Plus() && !ServerSocketChannels.isWin) {
            String line = nonBlockingEchoServerTest(sock, 100, 600);
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
          if (isJava17Plus() && !ServerSocketChannels.isWin) {
            String line = nonBlockingEchoServerTest(sock, 6000, 600);
            assertEquals("echo did not timeout", "<unavailable>", line);
          }
        });
  }

  /** Test the blocking echo server. */
  @Test
  public void testBlockingEchoServer() throws IOException, InterruptedException {
    System.out.println(
        "SocketChannelTest#testBlockingEchoServer(" + Boolean.toString(useJNI()) + ")");
    withSocket(
        sock -> {
          String line = blockingEchoServerTest(sock);
          assertEquals("echo did not return the content", "hello", line);
        });
  }

  private String nonBlockingEchoServerTest(String sock, int sleepBeforeSend, int sleepBeforeReceive)
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
    client.write(ByteBuffer.wrap("hello\n".getBytes("UTF-8")));
    Thread.sleep(100);
    client.configureBlocking(false);
    String line;
    int ready = 0;
    ready = SocketChannels.available(client);
    System.out.println("client: " + Integer.toString(ready) + " bytes ready");
    Thread.sleep(100);
    ready = SocketChannels.available(client);
    System.out.println("client: " + Integer.toString(ready) + " bytes ready");
    Thread.sleep(sleepBeforeReceive);
    ready = SocketChannels.available(client);
    System.out.println("client: " + Integer.toString(ready) + " bytes ready");
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

  private String blockingEchoServerTest(String sock) throws IOException, InterruptedException {
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
    System.out.println("client: " + client.toString());
    client.write(ByteBuffer.wrap("hello\n".getBytes("UTF-8")));
    client.configureBlocking(false);
    Thread.sleep(600);
    final int ready = SocketChannels.available(client);
    System.out.println("client: " + Integer.toString(ready) + " bytes ready");
    String line;
    if (ready > 0) {
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
