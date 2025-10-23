package org.scalasbt.ipcsocket;

import java.io.File;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CompletableFuture;

public class EchoServer {
  static boolean isJava17Plus() {
    return SocketChannels.isJava17Plus();
  }

  private final ServerSocketChannel serverSocketChannel;
  private final int READ_TIMEOUT_MILI = 5000;

  public EchoServer(ServerSocketChannel serverSocketChannel) {
    this.serverSocketChannel = serverSocketChannel;
  }

  public void run() throws IOException {
    while (true) {
      SocketChannel clientChannel = serverSocketChannel.accept();
      System.out.println("accepted: " + clientChannel.toString());
      CompletableFuture.supplyAsync(
          () -> {
            try {
              clientChannel.configureBlocking(false);
              try {
                ByteBuffer inBytes =
                    SocketChannels.readAll(
                        clientChannel,
                        isJava17Plus() && !ServerSocketChannels.isWin ? READ_TIMEOUT_MILI : 0);
                final String line =
                    new String(inBytes.array(), "UTF-8").replace("\n", "").replace("\r", "");
                System.out.println("server: " + line);
                clientChannel.write(inBytes);
              } catch (SocketTimeoutException e) {
                System.out.println("server read timeout");
                return false;
              }
            } catch (IOException e) {
              e.printStackTrace();
            }
            return true;
          });
    }
  }
}
