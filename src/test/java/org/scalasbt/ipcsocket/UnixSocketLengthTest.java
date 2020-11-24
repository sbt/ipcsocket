package org.scalasbt.ipcsocket;

import org.junit.Test;
import static org.junit.Assert.*;

public class UnixSocketLengthTest {
  final boolean isWin = System.getProperty("os.name", "").toLowerCase().startsWith("win");
  final boolean isLinux = System.getProperty("os.name", "").toLowerCase().startsWith("linux");

  @Test
  public void testJNIMaxSocketLength() {
    if (!isWin) {
      int length = UnixDomainSocketLibraryProvider.maxSocketLength(true);
      int expectedLength = isLinux ? 108 : 104;
      assert (length == expectedLength);
    }
  }

  @Test
  public void testJNAMaxSocketLength() {
    if (!isWin) {
      int length = UnixDomainSocketLibraryProvider.maxSocketLength(false);
      assert (length == 104);
    }
  }
}
