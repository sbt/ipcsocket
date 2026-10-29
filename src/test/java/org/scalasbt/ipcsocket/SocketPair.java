package org.scalasbt.ipcsocket;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * A connected client and the server's socket for it. A named pipe instance only exists while the
 * server is in accept(), so the client connects after accept() has started.
 */
final class SocketPair {
  final Socket client;
  final Socket peer;

  private SocketPair(Socket client, Socket peer) {
    this.client = client;
    this.peer = peer;
  }

  static SocketPair connect(BaseSocketSetup setup, ServerSocket server, String name)
      throws IOException {
    CompletableFuture<Socket> accept =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                return server.accept();
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            });
    long deadline = System.currentTimeMillis() + 10000;
    Socket client;
    while (true) {
      try {
        client = setup.newClientSocket(name);
        break;
      } catch (IOException e) {
        if (System.currentTimeMillis() > deadline) throw e;
        try {
          Thread.sleep(20);
        } catch (InterruptedException ie) {
          throw new IOException(ie);
        }
      }
    }
    try {
      return new SocketPair(client, accept.get(10, TimeUnit.SECONDS));
    } catch (Exception e) {
      client.close();
      throw new IOException(e);
    }
  }
}
