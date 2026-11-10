import java.lang.reflect.*;
public class Runner { public static void main(String[] a) throws Exception {
  Class<?> c=Class.forName("com.yash.upsc400.engine.EngineTest"); Object o=c.getDeclaredConstructor().newInstance(); int pass=0,fail=0;
  for(Method m: c.getDeclaredMethods()){ if(m.isAnnotationPresent(org.junit.Test.class)){ try{ m.invoke(o); pass++; System.out.println("PASS "+m.getName()); }catch(InvocationTargetException e){ fail++; System.out.println("FAIL "+m.getName()+": "+e.getCause()); } } }
  System.out.println("passed "+pass+" failed "+fail); System.exit(fail==0?0:1);} }
