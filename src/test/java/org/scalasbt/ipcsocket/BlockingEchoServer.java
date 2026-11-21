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
              String rawLine;
              do {
                ByteBuffer inBytes = SocketChannels.readAll(clientChannel);
                rawLine = new String(inBytes.array(), "UTF-8");
                final String line =
                    rawLine.replace("\n", "").replace("\r", "").replace("\u001a", "");
                // System.out.println("server: " + line);
                ByteBuffer outBytes = inBytes.duplicate();
                do {
                  clientChannel.write(outBytes);
                  // System.out.println("server: wrote " + writtenBytes + " bytes");
                } while (outBytes.remaining() > 0);
              } while (!rawLine.contains("\u001a") && !rawLine.contains("\n"));
            } catch (IOException e) {
              e.printStackTrace();
            }
            return true;
          });
    }
  }
}
