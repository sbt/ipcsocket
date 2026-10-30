package org.scalasbt.ipcsocket;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.Test;
import static org.junit.Assert.*;

public class NativeLoaderTest {
  final boolean isWin = System.getProperty("os.name", "").toLowerCase().startsWith("win");

  @Test
  public void runtimeDirMustBePrivate() throws IOException {
    if (isWin) return;
    Path parent = Files.createTempDirectory("ipcsocket-loader");
    Path mine = parent.resolve("mine");
    Path shared = parent.resolve("shared");
    try {
      assertTrue(NativeLoader.isPrivateDirectory(mine));
      assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(mine)));
      Files.createDirectory(shared);
      Files.setPosixFilePermissions(shared, PosixFilePermissions.fromString("rwxrwxrwx"));
      assertFalse(NativeLoader.isPrivateDirectory(shared));
    } finally {
      Files.deleteIfExists(mine);
      Files.deleteIfExists(shared);
      Files.deleteIfExists(parent);
    }
  }
}
