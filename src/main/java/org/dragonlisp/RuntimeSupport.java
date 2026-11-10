package org.dragonlisp;

import java.util.*;
import java.lang.invoke.*;
import java.util.concurrent.ConcurrentHashMap;
/** Public ABI for directly lowered JVM expressions. */
public final class RuntimeSupport {
  private RuntimeSupport() {}
  public static Object environment(){ return Main.standard(); }
  public static Object nil(){ return Main.NIL; }
  public static Object truth(){ return Main.TRUE; }
  public static Object symbol(String name){ return new Main.Sym(name); }
  public static Object integer(String n){ return Main.pack(new java.math.BigInteger(n)); }
  public static Object decimal(String n){ return new java.math.BigDecimal(n); }
  public static Object rational(String n,String d){ return Main.ratio(new java.math.BigInteger(n),new java.math.BigInteger(d)); }
  public static Object lookup(Object env,String name){ return ((Main.Env)env).get(name); }
  public static Object child(Object env){ return new Main.Env((Main.Env)env); }
  public static Object bind(Object env,String name,Object value){ ((Main.Env)env).set(name,value);return value; }
  public static Object define(Object env,String name,Object value){ Main.root((Main.Env)env).set(name,value);return Main.symbolFor(name); }
  public static Object update(Object env,String name,Object value){ ((Main.Env)env).update(name,value);return value; }
  public static boolean isTrue(Object value){ return Main.truth(value); }
  public static Object call(Object function,Object... values){
    if(!(function instanceof Main.Fn fn))throw new IllegalArgumentException("Not callable: "+Main.show(function));
    return fn.call(Arrays.asList(values));
  }
  private static final Map<String,MethodHandle> METHODS=new ConcurrentHashMap<>();
  private static MethodHandle method(String className,String name){
    return METHODS.computeIfAbsent(className+"#"+name,key->{
      try {
        Class<?> klass=Class.forName(className);
        return MethodHandles.publicLookup().findStatic(klass,name,
          MethodType.methodType(Object.class,Object.class,Object[].class));
      }catch(ReflectiveOperationException ex){throw new IllegalStateException("Cannot link compiled closure "+key,ex);}
    });
  }
  public static Object restList(Object[] args,int from){
    return Main.asLispList(Arrays.asList(args).subList(from,args.length));
  }
  /** Cached method-handle dispatch, with captured lexical environment. */
  public static Object compiledClosure(Object captured,String className,String name,int arity,boolean hasRest){
    MethodHandle handle=method(className,name);
    return (Main.Fn)args->{
      if(args.size()<arity||(!hasRest&&args.size()!=arity))
        throw Main.err("Expected "+arity+(hasRest?" or more":"")+" arguments, got "+args.size());
      try{return (Object)handle.invokeExact(captured,args.toArray());}
      catch(RuntimeException|Error ex){throw ex;}
      catch(Throwable ex){throw new IllegalStateException("Compiled closure failed",ex);}
    };
  }
  public static Object registerClosure(Object targetEnv,String name,Object captured,String className,String method,int arity,boolean hasRest){
    Object fn=compiledClosure(captured,className,method,arity,hasRest);
    Main.root((Main.Env)targetEnv).set(name,fn);
    return Main.symbolFor(name);
  }
  public static Object registerCompiled(Object env,String name,String className,String method,int arity){
    try {
      Class<?> klass=Class.forName(className);
      java.lang.reflect.Method target=klass.getMethod(method,Object.class,Object[].class);
      Main.Fn fn=args->{
        if(args.size()!=arity)throw Main.err("Expected "+arity+" arguments, got "+args.size());
        try{return target.invoke(null,env,args.toArray());}
        catch(java.lang.reflect.InvocationTargetException ex){
          Throwable cause=ex.getCause();
          if(cause instanceof RuntimeException re)throw re;
          throw new IllegalStateException("Compiled function failed",cause);
        }catch(ReflectiveOperationException ex){throw new IllegalStateException("Compiled function invocation failed",ex);}
      };
      Main.root((Main.Env)env).set(name,fn);
      return Main.symbolFor(name);
    }catch(ReflectiveOperationException ex){throw new IllegalStateException("Compiled function registration failed",ex);}
  }
  public static Object evaluate(Object env,String source){
    Object result=Main.NIL;
    for(Object form:new Main.Reader(source).all())result=Main.eval(form,(Main.Env)env);
    return result;
  }
}
