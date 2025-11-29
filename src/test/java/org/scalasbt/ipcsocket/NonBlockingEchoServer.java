package org.scalasbt.ipcsocket;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CompletableFuture;

public class NonBlockingEchoServer {
  private final ServerSocketChannel serverSocketChannel;
  private final int READ_TIMEOUT_MILI = 5000;

  public NonBlockingEchoServer(ServerSocketChannel serverSocketChannel) {
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
                String rawLine;
                do {
                  ByteBuffer inBytes = SocketChannels.readAll(clientChannel, READ_TIMEOUT_MILI);
                  rawLine = new String(inBytes.array(), "UTF-8");
                  final String line =
                      rawLine.replace("\n", "").replace("\r", "").replace("\u001a", "");
                  // System.out.println("server: " + line);
                  ByteBuffer outBytes = inBytes.duplicate();
                  do {
                    clientChannel.write(outBytes);
                  } while (outBytes.remaining() > 0);
                } while (!rawLine.contains("\u001a") && !rawLine.contains("\n"));
              } catch (SocketTimeoutException e) {
                // if readAll doesn't complete in READ_TIMEOUT_MILI,
                // SocketTimeoutException is thrown
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
