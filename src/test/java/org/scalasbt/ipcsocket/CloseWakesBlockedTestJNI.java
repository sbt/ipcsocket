package org.scalasbt.ipcsocket;

public class CloseWakesBlockedTestJNI extends CloseWakesBlockedTest {
  @Override
  boolean useJNI() {
    return true;
  }
}
