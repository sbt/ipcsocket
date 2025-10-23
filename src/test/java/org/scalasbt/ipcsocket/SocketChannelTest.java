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

  @Test
  public void testEchoServer() throws IOException, InterruptedException {
    System.out.println("SocketChannelTest#testEchoServer(" + Boolean.toString(useJNI()) + ")");
    withSocket(
        sock -> {
          String line = echoServerTest(sock, ServerSocketChannels.isWin ? 0 : 100);
          assertEquals("echo did not return the content", "hello", line);
        });
  }

  @Test
  public void testTimeout() throws IOException, InterruptedException {
    System.out.println("SocketChannelTest#testTimeout(" + Boolean.toString(useJNI()) + ")");
    withSocket(
        sock -> {
          if (isJava17Plus() && !ServerSocketChannels.isWin) {
            String line = echoServerTest(sock, 6000);
            assertEquals("echo did not timeout", "<timeout>", line);
          }
        });
  }

  private String echoServerTest(String sock, int sleepBeforeSend)
      throws IOException, InterruptedException {
    ServerSocketChannel serverSocket = ServerSocketChannels.newServerSocketChannel(sock, useJNI());
    CompletableFuture<Boolean> server =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                EchoServer echo = new EchoServer(serverSocket);
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
    try {
      line =
          SocketChannels.readLine(client, isJava17Plus() && !ServerSocketChannels.isWin ? 500 : 0);
    } catch (SocketTimeoutException e) {
      line = "<timeout>";
    }
    client.close();
    server.cancel(true);
    serverSocket.close();
    return line;
  }
}
