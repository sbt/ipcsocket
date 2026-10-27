package org.scalasbt.ipcsocket;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Reading to EOF closes the socket's file descriptor. Closing the socket afterwards must not close
 * that descriptor number again: by then the OS may have given the number to another file.
 */
public class CloseAfterEofTest extends BaseSocketSetup {
  @Test
  public void closeAfterEofLeavesOtherFilesAlone() throws IOException, InterruptedException {
    if (isWin) return;
    withSocket(
        sock -> {
          ServerSocket serverSocket = newServerSocket(sock);
          Path file = Files.createTempFile("ipcsocket", ".txt");
          try {
            Files.write(file, "hello".getBytes());
            Socket client = newClientSocket(sock);
            serverSocket.accept().close();
            InputStream in = client.getInputStream();
            assertEquals("read at EOF", -1, in.read(new byte[16], 0, 16));
            // POSIX gives a new file the lowest free number: the one the socket just released
            try (FileInputStream other = new FileInputStream(file.toFile())) {
              client.close();
              byte[] buf = new byte[16];
              assertEquals("the other file is still readable", 5, other.read(buf));
            }
          } finally {
            serverSocket.close();
            Files.deleteIfExists(file);
          }
        });
  }
}
