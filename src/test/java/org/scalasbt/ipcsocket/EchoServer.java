package org.scalasbt.ipcsocket;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.InputStreamReader;
import java.io.BufferedReader;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class EchoServer {
  public static String readLine(SocketChannel client) throws IOException {
    int res;
    final List<Byte> values = new ArrayList<>();
    Byte b;
    do {
      final ByteBuffer buf = ByteBuffer.allocate(1);
      res = client.read(buf);
      if (res > 0) {
        b = buf.get(0);
        values.add(b);
      } else {
        b = 0;
      }
    } while (res > 0 && b != '\n');
    ByteBuffer buf2 = ByteBuffer.allocate(values.size());
    for (int i = 0; i < values.size(); i++) {
      buf2.put(values.get(i));
    }
    return new String(buf2.array(), StandardCharsets.UTF_8).replace("\n", "").replace("\r", "");
  }

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
              String line = EchoServer.readLine(clientChannel);
              System.out.println("server: " + line);
              clientChannel.write(ByteBuffer.wrap((line + "\n").getBytes("UTF-8")));
            } catch (IOException e) {
              e.printStackTrace();
            }
            return true;
          });
    }
  }
}
