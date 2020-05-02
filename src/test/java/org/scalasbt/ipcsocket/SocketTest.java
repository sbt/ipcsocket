package org.scalasbt.ipcsocket;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;
import static org.junit.Assert.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.Random;

public class SocketTest {
  boolean useJNI() {
    return false;
  }

  final boolean isWin = System.getProperty("os.name", "").toLowerCase().startsWith("win");

  interface MayThrow {
    void accept(String string) throws IOException, InterruptedException;
  }

  final void withSocket(final MayThrow consumer) throws IOException, InterruptedException {
    Random rand = new Random();
    Path tempDir = isWin ? null : Files.createTempDirectory("ipcsocket");
    Path socketPath = tempDir != null ? tempDir.resolve("foo" + rand.nextInt() + ".sock") : null;
    String socket =
        socketPath != null ? socketPath.toString() : "\\\\.\\pipe\\ipcsockettest" + rand.nextInt();
    try {
      consumer.accept(socket);
    } finally {
      if (socketPath != null) Files.deleteIfExists(socketPath);
      if (tempDir != null) Files.deleteIfExists(socketPath);
    }
  }

  ServerSocket newServerSocket(String socketName) throws IOException {
    return isWin
        ? new Win32NamedPipeServerSocket(useJNI(), socketName)
        : new UnixDomainServerSocket(socketName, useJNI());
  }

  Socket newClientSocket(String socketName) throws IOException {
    return isWin
        ? new Win32NamedPipeSocket(socketName, useJNI())
        : new UnixDomainSocket(socketName, useJNI());
  }

  @Test
  public void testAssertEquals() throws IOException, InterruptedException {
    withSocket(
        sock -> {
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
          PrintWriter out = new PrintWriter(client.getOutputStream(), true);
          BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
          out.println("hello");
          String line = in.readLine();
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

  @Test
  public void shortReadWrite() throws IOException, InterruptedException {
    withSocket(
        sock -> {
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
}
