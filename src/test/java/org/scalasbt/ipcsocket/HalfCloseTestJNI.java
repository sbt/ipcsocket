package org.scalasbt.ipcsocket;

public class HalfCloseTestJNI extends HalfCloseTest {
  @Override
  boolean useJNI() {
    return true;
  }
}
