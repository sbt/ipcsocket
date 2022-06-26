package org.scalasbt.ipcsocket.duplex;

import static org.junit.Assert.assertEquals;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.Test;
import org.scalasbt.ipcsocket.BaseSocketSetup;

public class DuplexTest extends BaseSocketSetup {

  @Test
  public void testDuplexCommunication() throws Exception {
    withSocket(
        socketName -> {
          ExecutorService pool = Executors.newFixedThreadPool(2);

          // start server
          int serverSendMessages = 15;
          DuplexServer server = new DuplexServer(socketName, serverSendMessages);
          pool.execute(() -> server.startAndAwait());

          // wait for pipe to be instantiated
          Thread.sleep(2000);

          // start client
          int clientSendMessages = 7;
          DuplexClient client = new DuplexClient(socketName, clientSendMessages);
          pool.execute(() -> client.startAndAwait());

          // wait client and server to terminate
          Thread.sleep((Math.max(serverSendMessages, clientSendMessages) + 3) * 1000);
          pool.shutdown();

          assertEquals(serverSendMessages, client.receiver.receivedMessages);
          assertEquals(clientSendMessages, server.receiver.receivedMessages);
        });
  }
}
