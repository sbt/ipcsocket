package org.scalasbt.ipcsocket;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import static org.junit.Assert.*;

public class UnixSocketPathTest extends BaseSocketSetup {
  private static String repeat(char c, int n) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < n; i++) sb.append(c);
    return sb.toString();
  }

  @Test
  public void tooLongPathIsAnError() throws IOException {
    if (isWin) return;
    String path = "/tmp/" + repeat('x', 300) + ".sock";
    try {
      newServerSocket(path).close();
      fail("bind accepted a path longer than sun_path");
    } catch (IOException e) {
    }
    try {
      newClientSocket(path).close();
      fail("connect accepted a path longer than sun_path");
    } catch (IOException e) {
    }
  }

  @Test
  public void nonAsciiPath() throws IOException {
    if (isWin) return;
    Path dir = Files.createTempDirectory("ipcsocket-j\u00fcrgen");
    String path = dir.resolve("s\u00f6ck").toString();
    try {
      ServerSocket server = newServerSocket(path);
      try (Socket client = newClientSocket(path);
          Socket peer = server.accept()) {
        client.getOutputStream().write(42);
        assertEquals(42, peer.getInputStream().read());
      } finally {
        server.close();
      }
    } finally {
      Files.deleteIfExists(dir.resolve("s\u00f6ck"));
      Files.deleteIfExists(dir);
    }
  }

  @Test
  public void outOfBoundsWriteIsAnError() throws IOException, InterruptedException {
    if (isWin) return;
    withSocket(
        path -> {
          ServerSocket server = newServerSocket(path);
          try (Socket client = newClientSocket(path)) {
            client.getOutputStream().write(new byte[4], 2, 100);
            fail("write sent bytes past the end of the array");
          } catch (IndexOutOfBoundsException e) {
          } finally {
            server.close();
          }
        });
  }
}
