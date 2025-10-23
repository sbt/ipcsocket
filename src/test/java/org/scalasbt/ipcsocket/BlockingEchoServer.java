package org.scalasbt.ipcsocket;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CompletableFuture;

public class BlockingEchoServer {
  private final ServerSocketChannel serverSocketChannel;

  public BlockingEchoServer(ServerSocketChannel serverSocketChannel) {
    this.serverSocketChannel = serverSocketChannel;
  }

  public void run() throws IOException {
    while (true) {
      SocketChannel clientChannel = serverSocketChannel.accept();
      System.out.println("accepted: " + clientChannel.toString());
      CompletableFuture.supplyAsync(
          () -> {
            try {
              clientChannel.configureBlocking(true);
              ByteBuffer inBytes = SocketChannels.readAll(clientChannel);
              final String line =
                  new String(inBytes.array(), "UTF-8").replace("\n", "").replace("\r", "");
              System.out.println("server: " + line);
              clientChannel.write(inBytes);
            } catch (IOException e) {
              e.printStackTrace();
            }
            return true;
          });
    }
  }
}
