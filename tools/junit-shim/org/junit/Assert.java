package org.junit;
public class Assert {
  public static void assertEquals(long a,long b){ if(a!=b) throw new AssertionError("expected "+a+" but was "+b); }
  public static void assertEquals(double a,double b,double eps){ if(Math.abs(a-b)>eps || Double.isNaN(b)) throw new AssertionError("expected "+a+" but was "+b); }
  public static void assertEquals(Object a,Object b){ if(!a.equals(b)) throw new AssertionError("expected "+a+" but was "+b); }
  public static void assertTrue(boolean c){ if(!c) throw new AssertionError("expected true"); }
  public static void assertFalse(boolean c){ if(c) throw new AssertionError("expected false"); }
}
