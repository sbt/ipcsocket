package org.scalasbt.ipcsocket.duplex;

import static org.junit.Assert.assertEquals;
import java.util.concurrent.Executors;
import org.junit.Test;
import org.scalasbt.ipcsocket.BaseSocketSetup;

public class DuplexTest extends BaseSocketSetup {

    @Test
    public void testDuplexCommunication() throws Exception {
        withSocket(socketName -> {

            var pool = Executors.newFixedThreadPool(2);

            // start server
            int serverSendMessages = 15;
            var server = new DuplexServer(socketName, serverSendMessages);
            pool.execute(() -> server.startAndAwait());
            
            // wait for pipe to be instantiated
            Thread.sleep(2000);

            // start client
            int clientSendMessages = 7;
            var client = new DuplexClient(socketName, clientSendMessages);
            pool.execute(() -> client.startAndAwait());

            // wait client and server to terminate
            Thread.sleep((Math.max(serverSendMessages, clientSendMessages) + 1) * 1000);
            pool.shutdown();

            assertEquals(serverSendMessages, client.receiver.receivedMessages);
            assertEquals(clientSendMessages, server.receiver.receivedMessages);
        });
    }
}
