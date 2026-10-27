package org.scalasbt.ipcsocket;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.AccCtrl;
import com.sun.jna.platform.win32.Advapi32;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase.SECURITY_ATTRIBUTES;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class Win32SecurityLibraryTest {
  interface SecurityApi extends Library {
    SecurityApi INSTANCE = Native.load("advapi32", SecurityApi.class);

    boolean GetSecurityDescriptorDacl(
        Pointer sd, IntByReference present, PointerByReference dacl, IntByReference defaulted);

    boolean IsValidAcl(Pointer acl);
  }

  static boolean isWin() {
    return System.getProperty("os.name", "").toLowerCase().startsWith("win");
  }

  /**
   * The pipe's DACL grants access to a single SID. The JNI provider used to create LOGON_DACL pipes
   * with the default DACL, which grants access to several.
   */
  @Test
  public void pipeDaclHasOneAce() throws IOException {
    assumeTrue(isWin());
    JNIWin32NamedPipeLibraryProvider jni = JNIWin32NamedPipeLibraryProvider.instance();
    for (int level : new int[] {Win32SecurityLevel.OWNER_DACL, Win32SecurityLevel.LOGON_DACL}) {
      String name = "\\\\.\\pipe\\ipcsocket-dacl-" + System.nanoTime();
      int openMode =
          Win32NamedPipeLibrary.FILE_FLAG_FIRST_PIPE_INSTANCE
              | Win32NamedPipeLibrary.PIPE_ACCESS_DUPLEX;

      HANDLE jna =
          Win32NamedPipeLibrary.INSTANCE.CreateNamedPipe(
              name + "-jna",
              openMode,
              0,
              1,
              1024,
              1024,
              0,
              Win32SecurityLibrary.createSecurityWithDacl(WinNT.FILE_GENERIC_READ, level));
      assertNotEquals(
          "JNA CreateNamedPipe failed with error " + Native.getLastError(),
          Win32NamedPipeLibrary.INVALID_HANDLE_VALUE,
          jna);
      try {
        assertEquals("JNA, security level " + level, 1, aceCount(jna));
      } finally {
        Kernel32.INSTANCE.CloseHandle(jna);
      }

      Handle h =
          jni.CreateNamedPipe(
              name + "-jni", openMode, 0, 1, 1024, 1024, 0, jni.FILE_GENERIC_READ(), level);
      try {
        HANDLE handle =
            new HANDLE(new Pointer(((JNIWin32NamedPipeLibraryProvider.JNIHandle) h).pointer));
        assertEquals("JNI, security level " + level, 1, aceCount(handle));
      } finally {
        jni.CloseHandle(h);
      }
    }
  }

  static int aceCount(HANDLE pipe) {
    PointerByReference dacl = new PointerByReference();
    PointerByReference sd = new PointerByReference();
    int error =
        Advapi32.INSTANCE.GetSecurityInfo(
            pipe,
            AccCtrl.SE_OBJECT_TYPE.SE_KERNEL_OBJECT,
            WinNT.DACL_SECURITY_INFORMATION,
            null,
            null,
            dacl,
            null,
            sd);
    assertEquals("GetSecurityInfo error", 0, error);
    try {
      return dacl.getValue().getShort(4) & 0xFFFF;
    } finally {
      Kernel32.INSTANCE.LocalFree(sd.getValue());
    }
  }

  /** CreateNamedPipe failed with ERROR_INVALID_ACL (1336) when the GC freed the DACL. */
  @Test
  public void daclSurvivesGc() {
    assumeTrue(isWin());
    SECURITY_ATTRIBUTES sa =
        Win32SecurityLibrary.createSecurityWithDacl(
            WinNT.FILE_GENERIC_READ, Win32SecurityLevel.OWNER_DACL);

    IntByReference present = new IntByReference();
    PointerByReference dacl = new PointerByReference();
    assertTrue(
        SecurityApi.INSTANCE.GetSecurityDescriptorDacl(
            sa.lpSecurityDescriptor, present, dacl, new IntByReference()));
    assertEquals(1, present.getValue());
    Pointer acl = dacl.getValue();
    int aclSize = acl.getShort(2) & 0xFFFF;

    // Overwrite freed blocks of the ACL's size, so a freed ACL becomes invalid.
    List<Memory> spray = new ArrayList<>();
    for (int round = 0; round < 10; round++) {
      System.gc();
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      for (int i = 0; i < 5000; i++) {
        Memory m = new Memory(aclSize);
        m.setMemory(0, aclSize, (byte) 0xFF);
        spray.add(m);
      }
    }
    assertTrue("DACL is invalid after GC", SecurityApi.INSTANCE.IsValidAcl(acl));

    String name = "\\\\.\\pipe\\ipcsocket-dacl-" + System.nanoTime();
    HANDLE h =
        Win32NamedPipeLibrary.INSTANCE.CreateNamedPipe(
            name,
            Win32NamedPipeLibrary.FILE_FLAG_FIRST_PIPE_INSTANCE
                | Win32NamedPipeLibrary.PIPE_ACCESS_DUPLEX,
            0,
            1,
            1024,
            1024,
            0,
            sa);
    int error = Native.getLastError();
    assertNotEquals(
        "CreateNamedPipe failed with error " + error,
        Win32NamedPipeLibrary.INVALID_HANDLE_VALUE,
        h);
    Kernel32.INSTANCE.CloseHandle(h);
  }
}
