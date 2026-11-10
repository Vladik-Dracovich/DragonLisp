package org.dragonlisp;

import java.io.*;
import java.math.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** DragonLisp 0.9: small, dynamically typed Lisp interpreter for the JVM. */
public final class Main {
  record Sym(String name) { public String toString(){return name;} }
  static final Object NIL = new Object(){public String toString(){return "NIL";}};
  static final Object TRUE = new Object(){public String toString(){return "T";}};
  static final java.util.concurrent.atomic.AtomicLong GENSYM=new java.util.concurrent.atomic.AtomicLong();
  static final MathContext MC = MathContext.DECIMAL128;
  /** Exact reduced rational number. */
  record Ratio(BigInteger numerator, BigInteger denominator) {
    Ratio { if(denominator.signum()==0)throw err("Division by zero");
      if(denominator.signum()<0){numerator=numerator.negate();denominator=denominator.negate();}
      BigInteger gcd=numerator.gcd(denominator);numerator=numerator.divide(gcd);denominator=denominator.divide(gcd);
    }
    public String toString(){return numerator+"/"+denominator;}
  }
  static Object ratio(BigInteger n,BigInteger d){Ratio r=new Ratio(n,d);return r.denominator().equals(BigInteger.ONE)?pack(r.numerator()):r;}
  static Ratio rational(Object x){if(x instanceof Ratio r)return r;return new Ratio(integer(x),BigInteger.ONE);}
  static boolean rationalArgs(List<Object> a){return a.stream().allMatch(x->x instanceof Ratio||x instanceof BigInteger||x instanceof Long||x instanceof Integer);}
  static Object rationalArithmetic(String op,List<Object> a){
    if((op.equals("-")||op.equals("/"))&&a.isEmpty())throw err(op+" requires arguments");
    Ratio r=op.equals("*")?new Ratio(BigInteger.ONE,BigInteger.ONE):op.equals("+")?new Ratio(BigInteger.ZERO,BigInteger.ONE):rational(a.get(0));
    if(a.size()==1&&op.equals("-"))return ratio(r.numerator().negate(),r.denominator());
    if(a.size()==1&&op.equals("/"))return ratio(r.denominator(),r.numerator());
    for(int i=(op.equals("-")||op.equals("/"))?1:0;i<a.size();i++){
      Ratio n=rational(a.get(i));BigInteger x=r.numerator(),y=r.denominator(),u=n.numerator(),v=n.denominator();
      r=switch(op){case "+"->new Ratio(x.multiply(v).add(u.multiply(y)),y.multiply(v));
        case "-"->new Ratio(x.multiply(v).subtract(u.multiply(y)),y.multiply(v));
        case "*"->new Ratio(x.multiply(u),y.multiply(v));
        default->new Ratio(x.multiply(v),y.multiply(u));};
    }
    return ratio(r.numerator(),r.denominator());
  }
  static final Map<String,Class<?>> JAVA_IMPORTS=new HashMap<>();
  static Class<?> javaClass(Object o){
    if(o instanceof Class<?> c)return c;
    if(o instanceof String primitive){switch(primitive){case "int":return int.class;case "long":return long.class;case "double":return double.class;case "float":return float.class;case "boolean":return boolean.class;case "byte":return byte.class;case "short":return short.class;case "char":return char.class;}}
    String name=o instanceof Sym sy?sy.name():String.valueOf(o);
    try{return Class.forName(name);}catch(ClassNotFoundException ignored){}
    Class<?> imported=JAVA_IMPORTS.get(name.toLowerCase(Locale.ROOT));if(imported!=null)return imported;
    try{return Class.forName("java.lang."+name);}catch(ClassNotFoundException ignored){}
    throw err("Java class not found: "+name);
  }
  static Object coerceJava(Object v,Class<?> t){
    if(v==NIL){if(t.isPrimitive())throw err("NIL cannot be passed to primitive "+t.getName());return null;}
    if(v==TRUE&& (t==boolean.class||t==Boolean.class))return true;
    if(v instanceof BigInteger||v instanceof BigDecimal||v instanceof Ratio||v instanceof Number){
      if(t==int.class||t==Integer.class)return integer(v).intValueExact();
      if(t==long.class||t==Long.class)return integer(v).longValueExact();
      if(t==double.class||t==Double.class)return decimal(v).doubleValue();
      if(t==float.class||t==Float.class)return decimal(v).floatValue();
      if(t==short.class||t==Short.class)return integer(v).shortValueExact();
      if(t==byte.class||t==Byte.class)return integer(v).byteValueExact();
      if(t==BigInteger.class)return integer(v);
      if(t==BigDecimal.class)return decimal(v);
    }
    if(t==String.class&&v instanceof String)return v;
    if(t.isInstance(v))return v;
    throw err("Cannot convert "+show(v)+" to "+t.getName());
  }
  static Object invokeJava(Class<?> type,Object target,String method,List<Object> args){
    ArrayList<Method> matches=new ArrayList<>();for(Method m:type.getMethods())if(m.getName().equals(method)&&m.getParameterCount()==args.size()&&!m.isVarArgs()&&(target!=null||Modifier.isStatic(m.getModifiers())))matches.add(m);
    matches.sort(Comparator.comparingInt(m->{int score=0;for(int i=0;i<args.size();i++){Class<?> t=m.getParameterTypes()[i];Object v=args.get(i);if((v instanceof Long||v instanceof Integer||v instanceof BigInteger)&&(t==double.class||t==float.class||t==Double.class||t==Float.class))score+=10;}return score;}));
    for(Method m:matches){try{Object[] values=new Object[args.size()];for(int i=0;i<values.length;i++)values[i]=coerceJava(args.get(i),m.getParameterTypes()[i]);Object result=m.invoke(target,values);return result==null?NIL:result;}
      catch(LispError|IllegalArgumentException ignored){}catch(InvocationTargetException ex){throw err("Java exception: "+ex.getCause());}catch(IllegalAccessException ignored){}
    }
    throw err("No compatible Java method: "+type.getName()+"."+method+"/"+args.size());
  }
  static Object constructJava(Class<?> type,List<Object> args){
    for(Constructor<?> c:type.getConstructors())if(c.getParameterCount()==args.size()&&!c.isVarArgs()){
      try{Object[] values=new Object[args.size()];for(int i=0;i<values.length;i++)values[i]=coerceJava(args.get(i),c.getParameterTypes()[i]);return c.newInstance(values);}
      catch(LispError|IllegalArgumentException ignored){}catch(InvocationTargetException ex){throw err("Java exception: "+ex.getCause());}catch(ReflectiveOperationException ignored){}
    }throw err("No compatible Java constructor: "+type.getName()+"/"+args.size());
  }

  static final class LispError extends RuntimeException { LispError(String s){super(s);} }
  static final class Env {
    final Env parent; final Map<String,Object> values=new HashMap<>(); final Map<String,String> types=new HashMap<>();
    Env(Env p){parent=p;}
    Object get(String k){if(values.containsKey(k))return values.get(k);if(parent!=null)return parent.get(k);throw err("Unbound symbol: "+k);}
    void set(String k,Object v){check(k,v);values.put(k,v);}
    void declare(String k,String t){types.put(k,t);if(values.containsKey(k))check(k,values.get(k));}
    void check(String k,Object v){if(types.containsKey(k)&&!matchesType(v,types.get(k)))throw err("Type violation for "+k+": expected "+types.get(k)+", got "+show(v));}
    void update(String k,Object v){if(values.containsKey(k)){check(k,v);values.put(k,v);return;}if(parent!=null){parent.update(k,v);return;}throw err("Unbound symbol: "+k);}
  }
  interface Fn { Object call(List<Object> args); }
  static final class Closure implements Fn {
    final List<String> fixed; final String rest; final List<Object> body; final Env captured; final boolean macro;
    Closure(Object params,List<Object> body,Env env,boolean macro){
      List<Object> raw=list(params); List<String> f=new ArrayList<>();String r=null;
      for(int i=0;i<raw.size();i++){
        String s=sym(raw.get(i));if(s.equals("&rest")||s.equals("&body")){
          if(i+2!=raw.size())throw err("&rest requires exactly one final parameter");r=sym(raw.get(++i));break;
        }f.add(s);
      }
      fixed=List.copyOf(f);rest=r;this.body=List.copyOf(body);captured=env;this.macro=macro;
    }
    public Object call(List<Object> args){
      if(args.size()<fixed.size()||(rest==null&&args.size()!=fixed.size()))throw err("Expected "+fixed.size()+(rest==null?"":" or more")+" arguments, got "+args.size());
      Env e=new Env(captured);for(int i=0;i<fixed.size();i++)e.set(fixed.get(i),args.get(i));
      if(rest!=null)e.set(rest,asLispList(args.subList(fixed.size(),args.size())));
      for(Object form:body){if(isDeclare(form))applyDeclarations(form,e);else break;}
      return evalBody(body,e);
    }
    public String toString(){return macro?"#<MACRO>":"#<FUNCTION>";}
  }
  static Sym symbolFor(String s){return new Sym(s);}
  static LispError err(String s){return new LispError(s);}
  static boolean truth(Object x){return x!=NIL&&!Boolean.FALSE.equals(x)&&!(x instanceof List<?> l&&l.isEmpty());}
  static Object asLispList(List<?> xs){return xs.isEmpty()?NIL:new ArrayList<>(xs);}
  static String show(Object x){
    if(x==NIL)return "NIL";if(x==TRUE)return "T";
    if(x==null)return "NIL";if(x instanceof Class<?> c)return "#<JAVA-CLASS "+c.getName()+">";
    if(x instanceof String s)return "\""+s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n")+"\"";
    if(x instanceof List<?> a)return "("+String.join(" ",a.stream().map(Main::show).toList())+")";
    return String.valueOf(x);
  }
  static String sym(Object x){if(x instanceof Sym s)return s.name();throw err("Expected symbol: "+show(x));}
  @SuppressWarnings("unchecked") static List<Object> list(Object x){if(x==NIL)return List.of();if(x instanceof List<?> l)return (List<Object>)l;throw err("Expected list: "+show(x));}
  static void arity(List<?> a,int n){if(a.size()!=n)throw err("Expected "+n+" arguments, got "+a.size());}
  static void formArity(List<?> a,int n){if(a.size()!=n+1)throw err("Expected "+n+" operands");}
  static Env root(Env e){while(e.parent!=null)e=e.parent;return e;}
  static Object evalBody(List<Object> forms,Env e){Object v=NIL;for(Object f:forms){if(isDeclare(f)){applyDeclarations(f,e);continue;}v=eval(f,e);}return v;}
  static boolean matchesType(Object x,String type){return switch(type){
    case "t" -> true; case "integer","fixnum","bignum" -> x instanceof BigInteger||x instanceof Long||x instanceof Integer;
    case "number","real" -> x instanceof Number||x instanceof Ratio;
    case "rational" -> x instanceof Ratio||x instanceof BigInteger||x instanceof Long||x instanceof Integer;
    case "ratio" -> x instanceof Ratio; case "float","double-float" -> x instanceof BigDecimal||x instanceof Double||x instanceof Float;
    case "string" -> x instanceof String;case "symbol" -> x instanceof Sym;
    case "list" -> x==NIL||x instanceof List;case "null" -> x==NIL;case "function" -> x instanceof Fn;default -> throw err("Unknown type declaration: "+type);
  };}
  static void applyDeclarations(Object form,Env e){List<Object> d=list(form);for(int i=1;i<d.size();i++){
    List<Object> clause=list(d.get(i));if(clause.isEmpty())continue;
    if(sym(clause.get(0)).equals("type")){if(clause.size()<3)throw err("type declaration requires type and variables");
      String type=sym(clause.get(1));for(int j=2;j<clause.size();j++)e.declare(sym(clause.get(j)),type);}
  }}
  static boolean isDeclare(Object x){return x instanceof List<?> l&&!l.isEmpty()&&l.get(0) instanceof Sym s&&s.name().equals("declare");}
  static Object eval(Object x,Env e){
    if(x instanceof Sym s)return e.get(s.name());
    if(!(x instanceof List<?>))return x;
    List<Object> a=list(x);if(a.isEmpty())return NIL;
    if(a.get(0) instanceof Sym s){switch(s.name()){
      case "quote":formArity(a,1);return quoted(a.get(1));
      case "if":if(a.size()<3||a.size()>4)throw err("if expects 2 or 3 operands");return truth(eval(a.get(1),e))?eval(a.get(2),e):a.size()==4?eval(a.get(3),e):NIL;
      case "progn":return evalBody(a.subList(1,a.size()),e);
      case "defparameter":case "defvar":formArity(a,2);Object v=eval(a.get(2),e);root(e).set(sym(a.get(1)),v);return new Sym(sym(a.get(1)));
      case "setq":formArity(a,2);Object nv=eval(a.get(2),e);e.update(sym(a.get(1)),nv);return nv;
      case "lambda":if(a.size()<3)throw err("lambda requires a body");return new Closure(a.get(1),a.subList(2,a.size()),e,false);
      case "defun":if(a.size()<4)throw err("defun requires a body");root(e).set(sym(a.get(1)),new Closure(a.get(2),a.subList(3,a.size()),e,false));return a.get(1);
      case "defmacro":if(a.size()<4)throw err("defmacro requires a body");root(e).set(sym(a.get(1)),new Closure(a.get(2),a.subList(3,a.size()),e,true));return a.get(1);
      case "let":case "let*":{
        if(a.size()<2)throw err("let requires bindings");Env local=new Env(e);boolean sequential=s.name().equals("let*");
        for(Object b:list(a.get(1))){List<Object> bind=b instanceof Sym?List.of(b):list(b);if(bind.size()<1||bind.size()>2)throw err("Invalid let binding");Object value=bind.size()==2?eval(bind.get(1),sequential?local:e):NIL;local.set(sym(bind.get(0)),value);}
        return evalBody(a.subList(2,a.size()),local);
      }
      case "and":{Object result=TRUE;for(int i=1;i<a.size();i++){result=eval(a.get(i),e);if(!truth(result))return NIL;}return result;}
      case "or":{for(int i=1;i<a.size();i++){Object result=eval(a.get(i),e);if(truth(result))return result;}return NIL;}
      case "when":if(a.size()<2)throw err("when requires condition");return truth(eval(a.get(1),e))?evalBody(a.subList(2,a.size()),e):NIL;
      case "unless":if(a.size()<2)throw err("unless requires condition");return !truth(eval(a.get(1),e))?evalBody(a.subList(2,a.size()),e):NIL;
      case "java/import":case "import":{
        formArity(a,1);String name=a.get(1) instanceof Sym sy?sy.name():String.valueOf(a.get(1));
        try{Class<?> cls=Class.forName(name);JAVA_IMPORTS.put(cls.getSimpleName().toLowerCase(Locale.ROOT),cls);return cls;}
        catch(ClassNotFoundException ex){throw err("Java class not found: "+name);}
      }
      case "declare":applyDeclarations(a,e);return NIL;
      case "declaim":return NIL; // global proclamations reserved for compiler
      case "macroexpand-1":formArity(a,1);return macroexpand(eval(a.get(1),e),e,false);
      case "macroexpand":formArity(a,1);return macroexpand(eval(a.get(1),e),e,true);
      case "load":formArity(a,1);try{String filename=String.valueOf(eval(a.get(1),e));Object value=NIL;for(Object form:new Reader(Files.readString(Path.of(filename))).all())value=eval(form,e);return value;}catch(IOException ex){throw err("Load failed: "+ex.getMessage());}
      case "quasiquote":formArity(a,1);return quasi(a.get(1),e,1);
      case "unquote":case "unquote-splicing":throw err("unquote outside quasiquote");
    }}
    Object f=eval(a.get(0),e);if(!(f instanceof Fn fn))throw err("Not callable: "+show(f));
    if(f instanceof Closure c&&c.macro)return eval(c.call(a.subList(1,a.size())),e);
    List<Object> args=new ArrayList<>();for(int i=1;i<a.size();i++)args.add(eval(a.get(i),e));return fn.call(args);
  }
  static Object macroexpand(Object form,Env e,boolean all){Object current=form;for(int depth=0;depth<(all?100:1);depth++){
    if(!(current instanceof List<?> l)||l.isEmpty()||!(l.get(0) instanceof Sym sy))return current;
    Object fn;try{fn=e.get(sy.name());}catch(LispError ex){return current;}
    if(!(fn instanceof Closure c)||!c.macro)return current;
    current=c.call(new ArrayList<>(l.subList(1,l.size())));
  }return current;}
  static List<?> listOrAtomQuote(Object o){return o instanceof List<?> l?l:List.of(o);}
  static Object quoted(Object x){return x instanceof List<?> l?asLispList(l):x;}
  static Object quasi(Object x,Env e,int depth){
    if(!(x instanceof List<?> raw))return x;
    List<Object> a=list(raw);
    if(!a.isEmpty()&&a.get(0) instanceof Sym s){
      if(s.name().equals("unquote")&&depth==1){formArity(a,1);return eval(a.get(1),e);}
      if(s.name().equals("quasiquote")){formArity(a,1);return List.of(a.get(0),quasi(a.get(1),e,depth+1));}
      if(s.name().equals("unquote")){formArity(a,1);return List.of(a.get(0),quasi(a.get(1),e,depth-1));}
    }
    List<Object> result=new ArrayList<>();for(Object item:a){
      if(depth==1&&item instanceof List<?> il&&!il.isEmpty()&&il.get(0) instanceof Sym ss&&ss.name().equals("unquote-splicing")){
        List<Object> splice=list(item);formArity(splice,1);result.addAll(list(eval(splice.get(1),e)));
      }else result.add(quasi(item,e,depth));
    }return asLispList(result);
  }
  static BigDecimal decimal(Object x){if(x instanceof Ratio r)return new BigDecimal(r.numerator()).divide(new BigDecimal(r.denominator()),MC);if(x instanceof BigInteger b)return new BigDecimal(b);if(x instanceof BigDecimal b)return b;if(x instanceof Number n)return BigDecimal.valueOf(n.doubleValue());throw err("Expected number: "+show(x));}
  static boolean floats(List<Object> a){return a.stream().anyMatch(x->x instanceof BigDecimal||x instanceof Double||x instanceof Float);}
  static BigInteger integer(Object x){if(x instanceof BigInteger b)return b;if(x instanceof Long l)return BigInteger.valueOf(l);if(x instanceof Integer i)return BigInteger.valueOf(i);throw err("Expected integer: "+show(x));}
  static Object pack(BigInteger n){return n.bitLength()<63?n.longValue():n;}
  static Object arithmetic(String op,List<Object> a){
    if((op.equals("-")||op.equals("/"))&&a.isEmpty())throw err(op+" requires arguments");
    if(rationalArgs(a))return rationalArithmetic(op,a);
    if(!floats(a)&&!op.equals("/")){
      BigInteger r=op.equals("*")?BigInteger.ONE:op.equals("+")?BigInteger.ZERO:integer(a.get(0));
      int start=op.equals("-")?1:0;
      if(op.equals("-")&&a.size()==1)return pack(r.negate());
      for(int i=start;i<a.size();i++){BigInteger n=integer(a.get(i));r=switch(op){case "+"->r.add(n);case "*"->r.multiply(n);default->r.subtract(n);};}return pack(r);
    }
    BigDecimal r=op.equals("*")?BigDecimal.ONE:op.equals("+")?BigDecimal.ZERO:decimal(a.get(0));
    int start=(op.equals("-")||op.equals("/"))?1:0;
    if(op.equals("-")&&a.size()==1)return r.negate();
    if(op.equals("/")&&a.size()==1)return BigDecimal.ONE.divide(r,MC);
    for(int i=start;i<a.size();i++){BigDecimal n=decimal(a.get(i));r=switch(op){case "+"->r.add(n,MC);case "*"->r.multiply(n,MC);case "-"->r.subtract(n,MC);default->r.divide(n,MC);};}
    return r.stripTrailingZeros();
  }
  static void builtin(Env e,String name,Fn fn){e.set(name,fn);}
  static Env standard(){
    Env e=new Env(null);e.set("nil",NIL);e.set("t",TRUE);
    for(String op:List.of("+","-","*","/"))builtin(e,op,a->arithmetic(op,a));
    for(String op:List.of("=","<",">","<=",">="))builtin(e,op,a->{for(int i=1;i<a.size();i++){int cmp=decimal(a.get(i-1)).compareTo(decimal(a.get(i)));boolean ok=switch(op){case "="->cmp==0;case "<"->cmp<0;case ">"->cmp>0;case "<="->cmp<=0;default->cmp>=0;};if(!ok)return NIL;}return TRUE;});
    builtin(e,"list",a->asLispList(a));builtin(e,"car",a->{arity(a,1);List<Object> l=list(a.get(0));return l.isEmpty()?NIL:l.get(0);});
    builtin(e,"cdr",a->{arity(a,1);List<Object> l=list(a.get(0));return asLispList(l.isEmpty()?List.of():l.subList(1,l.size()));});
    builtin(e,"cons",a->{arity(a,2);List<Object> r=new ArrayList<>();r.add(a.get(0));r.addAll(list(a.get(1)));return r;});
    builtin(e,"append",a->{List<Object> r=new ArrayList<>();for(Object x:a)r.addAll(list(x));return asLispList(r);});
    builtin(e,"length",a->{arity(a,1);return (long)list(a.get(0)).size();});
    builtin(e,"reverse",a->{arity(a,1);List<Object> r=new ArrayList<>(list(a.get(0)));Collections.reverse(r);return asLispList(r);});
    builtin(e,"nth",a->{arity(a,2);int i=integer(a.get(0)).intValueExact();List<Object> l=list(a.get(1));return i<0||i>=l.size()?NIL:l.get(i);});
    builtin(e,"null",a->{arity(a,1);return truth(a.get(0))?NIL:TRUE;});
    builtin(e,"not",a->{arity(a,1);return truth(a.get(0))?NIL:TRUE;});
    builtin(e,"eq",a->{arity(a,2);return a.get(0)==a.get(1)||(a.get(0) instanceof Sym&&a.get(0).equals(a.get(1)))?TRUE:NIL;});
    builtin(e,"equal",a->{arity(a,2);return Objects.equals(a.get(0),a.get(1))?TRUE:NIL;});
    builtin(e,"type-of",a->{arity(a,1);Object x=a.get(0);return new Sym(x==NIL?"null":x==TRUE?"boolean":x instanceof Ratio?"ratio":x instanceof BigInteger||x instanceof Long||x instanceof Integer?"integer":x instanceof Number?"float":x instanceof String?"string":x instanceof Sym?"symbol":x instanceof List?"list":x instanceof Fn?"function":"object");});
    builtin(e,"typep",a->{arity(a,2);String type=sym(a.get(1));Object x=a.get(0);boolean yes=switch(type){case "integer","fixnum","bignum"->x instanceof BigInteger||x instanceof Long||x instanceof Integer;case "number","real","rational"->x instanceof Number||x instanceof Ratio;case "ratio"->x instanceof Ratio;case "float","double-float"->x instanceof BigDecimal||x instanceof Double;case "string"->x instanceof String;case "symbol"->x instanceof Sym;case "list"->x==NIL||x instanceof List;case "function"->x instanceof Fn;case "null"->x==NIL;case "t"->true;default->false;};return yes?TRUE:NIL;});
    builtin(e,"mapcar",a->{if(a.isEmpty()||!(a.get(0) instanceof Fn fn))throw err("mapcar requires function");
      List<List<Object>> lists=new ArrayList<>();int n=Integer.MAX_VALUE;for(int i=1;i<a.size();i++){List<Object> l=list(a.get(i));lists.add(l);n=Math.min(n,l.size());}
      if(lists.isEmpty())throw err("mapcar requires list");List<Object> out=new ArrayList<>();for(int i=0;i<n;i++){List<Object> args=new ArrayList<>();for(List<Object> l:lists)args.add(l.get(i));out.add(fn.call(args));}return asLispList(out);});
    builtin(e,"reduce",a->{arity(a,2);if(!(a.get(0) instanceof Fn fn))throw err("reduce requires function");List<Object> l=list(a.get(1));if(l.isEmpty())throw err("reduce on empty list");Object r=l.get(0);for(int i=1;i<l.size();i++)r=fn.call(List.of(r,l.get(i)));return r;});
    builtin(e,"gensym",a->{if(a.size()>1)throw err("gensym accepts zero or one argument");return new Sym((a.isEmpty()?"g":String.valueOf(a.get(0)))+"__"+GENSYM.incrementAndGet());});
    builtin(e,"java/array",a->{if(a.isEmpty())throw err("java/array requires class");Class<?> c=javaClass(a.get(0));Object arr=Array.newInstance(c,a.size()-1);for(int i=1;i<a.size();i++)Array.set(arr,i-1,coerceJava(a.get(i),c));return arr;});
    builtin(e,"java/aget",a->{arity(a,2);return Array.get(a.get(0),integer(a.get(1)).intValueExact());});
    builtin(e,"java/aset",a->{arity(a,3);Object arr=a.get(0);Object v=coerceJava(a.get(2),arr.getClass().getComponentType());Array.set(arr,integer(a.get(1)).intValueExact(),v);return a.get(2);});
    builtin(e,"java/field",a->{arity(a,2);Object o=a.get(0);Class<?> c=o instanceof Class<?> cls?cls:o.getClass();try{Field f=c.getField(String.valueOf(a.get(1)));Object v=f.get(o instanceof Class<?>?null:o);return v==null?NIL:v;}catch(ReflectiveOperationException ex){throw err("Java field: "+ex.getMessage());}});
    builtin(e,"numerator",a->{arity(a,1);return pack(rational(a.get(0)).numerator());});
    builtin(e,"denominator",a->{arity(a,1);return pack(rational(a.get(0)).denominator());});
    builtin(e,"java/new",a->{if(a.isEmpty())throw err("java/new requires class");return constructJava(javaClass(a.get(0)),a.subList(1,a.size()));});
    builtin(e,"java/static",a->{if(a.size()<2)throw err("java/static requires class and method");return invokeJava(javaClass(a.get(0)),null,String.valueOf(a.get(1) instanceof Sym sy?sy.name():a.get(1)),a.subList(2,a.size()));});
    builtin(e,"java/call",a->{if(a.size()<2)throw err("java/call requires object and method");Object obj=a.get(0);if(obj==NIL)throw err("Cannot invoke method on NIL");return invokeJava(obj.getClass(),obj,String.valueOf(a.get(1) instanceof Sym sy?sy.name():a.get(1)),a.subList(2,a.size()));});
    builtin(e,"funcall",a->{if(a.isEmpty())throw err("funcall requires function");if(!(a.get(0) instanceof Fn f))throw err("Not callable");return f.call(a.subList(1,a.size()));});
    builtin(e,"apply",a->{if(a.size()<2)throw err("apply requires function and arguments");if(!(a.get(0) instanceof Fn f))throw err("Not callable");List<Object> args=new ArrayList<>(a.subList(1,a.size()-1));args.addAll(list(a.get(a.size()-1)));return f.call(args);});
    builtin(e,"print",a->{for(Object x:a)System.out.println(show(x));return a.isEmpty()?NIL:a.get(a.size()-1);});
    builtin(e,"princ",a->{for(Object x:a)System.out.print(x instanceof String?x:show(x));return a.isEmpty()?NIL:a.get(a.size()-1);});
    builtin(e,"terpri",a->{arity(a,0);System.out.println();return NIL;});
    builtin(e,"error",a->{throw err(a.isEmpty()?"Explicit error":String.valueOf(a.get(0)));});
    return e;
  }
  static final class Reader {
    final String src;int i=0;Reader(String s){src=s;}
    void skip(){while(i<src.length()){char c=src.charAt(i);if(Character.isWhitespace(c)){i++;continue;}if(c==';'){while(i<src.length()&&src.charAt(i)!='\n')i++;continue;}break;}}
    Object read(){skip();if(i>=src.length())throw err("Unexpected end of input");char c=src.charAt(i++);
      if(c=='('){List<Object> a=new ArrayList<>();while(true){skip();if(i>=src.length())throw err("Unclosed list");if(src.charAt(i)==')'){i++;return asLispList(a);}a.add(read());}}
      if(c==')')throw err("Unexpected )");
      if(c=='\'')return List.of(new Sym("quote"),read());
      if(c=='`')return List.of(new Sym("quasiquote"),read());
      if(c==','){boolean splice=i<src.length()&&src.charAt(i)=='@';if(splice)i++;return List.of(new Sym(splice?"unquote-splicing":"unquote"),read());}
      if(c=='"'){StringBuilder b=new StringBuilder();while(i<src.length()){char v=src.charAt(i++);if(v=='"')return b.toString();if(v=='\\'){if(i>=src.length())throw err("Unterminated escape");char n=src.charAt(i++);b.append(switch(n){case 'n'->'\n';case 't'->'\t';case 'r'->'\r';default->n;});}else b.append(v);}throw err("Unterminated string");}
      int start=i-1;while(i<src.length()&&!Character.isWhitespace(src.charAt(i))&&"();'`,".indexOf(src.charAt(i))<0)i++;
      String t=src.substring(start,i);if(t.isEmpty())throw err("Unexpected token");
      if(t.matches("[+-]?[0-9]+/[0-9]+")){String[] parts=t.split("/");return ratio(new BigInteger(parts[0]),new BigInteger(parts[1]));}
      if(t.matches("[+-]?[0-9]+")){try{return Long.parseLong(t);}catch(NumberFormatException ignored){return new BigInteger(t);}}
      if(t.matches("[+-]?(?:[0-9]+\\.[0-9]*|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?")||t.matches("[+-]?[0-9]+[eE][+-]?[0-9]+"))return new BigDecimal(t);
      return new Sym(t.toLowerCase(Locale.ROOT));
    }
    List<Object> all(){List<Object> out=new ArrayList<>();while(true){skip();if(i>=src.length())return out;out.add(read());}}
  }
  /** Public runtime entry for bootstrap-generated JVM classes. */
  public static void executeCompiled(String encodedSource, String filename) {
    String source=new String(Base64.getDecoder().decode(encodedSource),java.nio.charset.StandardCharsets.UTF_8);
    Env env=standard();int index=0;
    for(Object form:new Reader(source).all()){
      index++;
      try{eval(form,env);}catch(LispError ex){throw err(filename+" (top-level form "+index+"): "+ex.getMessage());}
    }
  }
  public static void main(String[] args)throws Exception{
    Env e=standard();if(args.length>0){for(String file:args){List<Object> forms=new Reader(Files.readString(Path.of(file))).all();int index=0;for(Object x:forms){index++;try{eval(x,e);}catch(LispError ex){throw err(file+" (top-level form "+index+"): "+ex.getMessage());}}}return;}
    System.out.println("DragonLisp 1.2 | Java 21 | :quit to exit");BufferedReader in=new BufferedReader(new InputStreamReader(System.in));StringBuilder buffer=new StringBuilder();
    while(true){System.out.print(buffer.length()==0?"dragon> ":"   ...> ");String line=in.readLine();if(line==null||line.trim().equals(":quit"))break;buffer.append(line).append('\n');
      try{for(Object x:new Reader(buffer.toString()).all())System.out.println("=> "+show(eval(x,e)));buffer.setLength(0);}
      catch(RuntimeException ex){if(Set.of("Unclosed list","Unterminated string","Unexpected end of input").contains(ex.getMessage()))continue;System.err.println("Error: "+ex.getMessage());buffer.setLength(0);}
    }
  }
}
