package org.scalasbt.ipcsocket;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;
import static org.junit.Assert.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class SocketTest extends BaseSocketSetup {

  @Test
  public void testAssertEquals() throws IOException, InterruptedException {
    withSocket(
        sock -> {
          System.out.println("SocketTest#testAssertEquals(" + Boolean.toString(useJNI()) + ")");

          ServerSocketChannel serverSocket =
              ServerSocketChannels.newServerSocketChannel(sock, useJNI());

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
          client.write(ByteBuffer.wrap("hello\n".getBytes("UTF-8")));
          Thread.sleep(100);
          String line = SocketChannels.readLine(client);
          client.close();
          server.cancel(true);
          serverSocket.close();
          assertEquals("echo did not return the content", line, "hello");
        });
  }

  @Test
  public void throwIOExceptionOnMissingFile() throws IOException, InterruptedException {
    withSocket(
        sock -> {
          System.out.println(
              "SocketTest#throwIOExceptionOnMissingFile(" + Boolean.toString(useJNI()) + ")");

          boolean caughtIOException = false;
          Files.deleteIfExists(Paths.get(sock));
          try {
            Socket client = newClientSocket(sock);
            client.getInputStream().read();
          } catch (final IOException e) {
            caughtIOException = true;
          }
          assertTrue("No io exception was caught", caughtIOException);
        });
  }

  /* Uncomment when it works on Windows with useJNI true
    @Test
    public void shortReadWrite() throws IOException, InterruptedException {
      withSocket(
          sock -> {
            System.out.println("SocketTest#shortReadWrite(" + Boolean.toString(useJNI()) + ")");

            ServerSocket serverSocket = newServerSocket(sock);

            CompletableFuture<Boolean> server =
                CompletableFuture.supplyAsync(
                    () -> {
                      try {
                        EchoServer echo = new EchoServer(serverSocket);
                        echo.run();
                      } catch (IOException e) {
                      }
                      return true;
                    });
            Thread.sleep(100);

            Socket client = newClientSocket(sock.toString());
            OutputStream out = client.getOutputStream();
            InputStream in = client.getInputStream();
            String printed = "hellofoo\n";
            byte[] printedBytes = printed.getBytes();
            out.write(printedBytes, 0, 4);
            out.write(printedBytes, 4, 5);
            out.flush();
            byte[] buf = new byte[16];
            assertEquals("Did not read 4 bytes", in.read(buf, 0, 4), 4);
            assertEquals("Did not read 5 bytes", in.read(buf, 4, 6), 5);
            String line = new String(buf, 0, printed.length());
            client.close();
            server.cancel(true);
            serverSocket.close();
            assertEquals("echo did not return the content", line, printed);
          });
    }
  */

  @Test
  public void testToString() throws IOException, InterruptedException {
    if (!isWin) {
      withSocket(
          sock -> {
            System.out.println("SocketTest#testToString(" + Boolean.toString(useJNI()) + ")");
            ServerSocket serverSocket = newServerSocket(sock);
            Socket client = newClientSocket(sock);
            try {
              assertTrue(client.toString().startsWith("UnixDomainSocket(path ="));
            } finally {
              client.close();
              serverSocket.close();
            }
          });
    }
  }
}
