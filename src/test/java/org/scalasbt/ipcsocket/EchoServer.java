package org.scalasbt.ipcsocket;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CompletableFuture;

public class EchoServer {
  private final ServerSocketChannel serverSocketChannel;

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
              ByteBuffer inBytes = SocketChannels.readAll(clientChannel);
              String line =
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
