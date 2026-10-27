package org.scalasbt.ipcsocket;

public class CloseAfterEofTestJNI extends CloseAfterEofTest {
  @Override
  boolean useJNI() {
    return true;
  }
}
