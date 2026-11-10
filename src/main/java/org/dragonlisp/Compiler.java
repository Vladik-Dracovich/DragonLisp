package org.dragonlisp;

import java.nio.file.*;
import java.util.*;
import javax.tools.*;

/** DragonLisp 1.2 hybrid ahead-of-time compiler.
 * Lowers supported expressions into Java source, which javac compiles into
 * executable JVM bytecode. Unsupported forms are evaluated by the runtime.
 * This is NOT an ASM compiler and does not emit optimized primitive bytecode.
 */
public final class Compiler {
  private Compiler(){}
  private static int lowered=0, fallback=0, serial=0;
  private static String q(String s){return "\""+s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t")+"\"";}
  private static String src(Object x){
    if(x instanceof Main.Sym s)return s.name();
    if(x instanceof String s)return Main.show(s);
    if(x instanceof List<?> l){List<String> a=new ArrayList<>();for(Object v:l)a.add(src(v));return "("+String.join(" ",a)+")";}
    return Main.show(x);
  }
  private static String fallback(Object x,String env){fallback++;return "org.dragonlisp.RuntimeSupport.evaluate("+env+","+q(src(x))+")";}
  private static String lower(Object x,String env){
    String rt="org.dragonlisp.RuntimeSupport.";
    if(x instanceof Main.Sym s){lowered++;return rt+"lookup("+env+","+q(s.name())+")";}
    if(x instanceof String s){lowered++;return q(s);}
    if(x instanceof Long || x instanceof Integer || x instanceof java.math.BigInteger){lowered++;return rt+"integer("+q(x.toString())+")";}
    if(x instanceof java.math.BigDecimal){lowered++;return rt+"decimal("+q(x.toString())+")";}
    if(x instanceof Main.Ratio r){lowered++;return rt+"rational("+q(r.numerator().toString())+","+q(r.denominator().toString())+")";}
    if(x==Main.NIL){lowered++;return rt+"nil()";}
    if(!(x instanceof List<?> a)||a.isEmpty())return fallback(x,env);
    if(!(a.get(0) instanceof Main.Sym op))return fallback(x,env);
    String name=op.name();
    if(name.equals("quote")&&a.size()==2)return fallback(x,env);
    if(name.equals("if")&&(a.size()==3||a.size()==4)){
      lowered++;return "("+rt+"isTrue("+lower(a.get(1),env)+") ? "+lower(a.get(2),env)+" : "+(a.size()==4?lower(a.get(3),env):rt+"nil()")+")";
    }
    if(name.equals("progn")){
      StringBuilder b=new StringBuilder();int id=serial++;
      b.append("seq").append(id).append("(").append(env).append(")");
      // Separate helper method emitted through helpers list.
      StringBuilder method=new StringBuilder("private static Object seq"+id+"(Object e){Object result="+rt+"nil();");
      for(int i=1;i<a.size();i++)method.append("result=").append(lower(a.get(i),"e")).append(";");
      method.append("return result;}");helpers.add(method.toString());lowered++;return b.toString();
    }
    if(name.equals("defparameter")&&a.size()==3&&a.get(1) instanceof Main.Sym sym){lowered++;return rt+"define("+env+","+q(sym.name())+","+lower(a.get(2),env)+")";}
    if(name.equals("setq")&&a.size()==3&&a.get(1) instanceof Main.Sym sym){lowered++;return rt+"update("+env+","+q(sym.name())+","+lower(a.get(2),env)+")";}
    if(name.equals("let*")||name.equals("let")){
      if(a.size()<2||!(a.get(1) instanceof List<?> bindings))return fallback(x,env);
      for(Object b:bindings){if(!(b instanceof List<?> bl)||bl.size()!=2||!(bl.get(0) instanceof Main.Sym))return fallback(x,env);}
      int id=serial++;StringBuilder method=new StringBuilder("private static Object let"+id+"(Object outer){Object local="+rt+"child(outer);");
      int bindingIndex=0;
      for(Object b:bindings){List<?> bl=(List<?>)b;String var=((Main.Sym)bl.get(0)).name();
        if(name.equals("let")){method.append("Object binding"+bindingIndex+"="+lower(bl.get(1),"outer")+";");}
        else method.append(rt+"bind(local,"+q(var)+","+lower(bl.get(1),"local")+");");
        bindingIndex++;
      }
      if(name.equals("let")){bindingIndex=0;for(Object b:bindings){List<?> bl=(List<?>)b;String var=((Main.Sym)bl.get(0)).name();method.append(rt+"bind(local,"+q(var)+",binding"+bindingIndex+");");bindingIndex++;}}
      method.append("Object result="+rt+"nil();");for(int i=2;i<a.size();i++)method.append("result="+lower(a.get(i),"local")+";");
      method.append("return result;}");helpers.add(method.toString());lowered++;return "let"+id+"("+env+")";
    }
    if(Set.of("+","-","*","/","=","<",">","<=",">=","print","princ","list","car","cdr","cons","length","not","null","equal","type-of","numerator","denominator").contains(name)){
      StringJoiner args=new StringJoiner(",");for(int i=1;i<a.size();i++)args.add(lower(a.get(i),env));
      lowered++;return rt+"call("+rt+"lookup("+env+","+q(name)+")"+(a.size()>1?","+args:"")+")";
    }
    return fallback(x,env);
  }
  private static final List<String> helpers=new ArrayList<>();
  public static void main(String[] args)throws Exception{
    if(args.length!=3||!args[0].equals("--compile")){System.err.println("Usage: ./compile.sh file.dlisp ClassName");System.exit(2);}
    if(!args[2].matches("[A-Za-z_$][A-Za-z0-9_$]*")||Set.of("class","public","void","static","new","int","return").contains(args[2]))throw new IllegalArgumentException("Invalid class name");
    String code=Files.readString(Path.of(args[1]));List<Object> forms=new Main.Reader(code).all();
    lowered=0;fallback=0;serial=0;helpers.clear();
    StringBuilder body=new StringBuilder("Object env=org.dragonlisp.RuntimeSupport.environment();Object result=null;\n");
    for(Object form:forms)body.append("result=").append(lower(form,"env")).append(";\n");
    String className=args[2];StringBuilder java=new StringBuilder("public final class "+className+" {\n");
    java.append("public static void main(String[] args){\n").append(body).append("}\n");
    for(int i=0;i<helpers.size();i++)java.append(helpers.get(i)).append('\n');
    java.append("}\n");
    Path out=Path.of("build/compiled");Files.createDirectories(out);Path generated=out.resolve(className+".java");Files.writeString(generated,java);
    JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();if(compiler==null)throw new IllegalStateException("JDK required");
    int status=compiler.run(null,System.out,System.err,"-encoding","UTF-8","-cp","build/classes","-d",out.toString(),generated.toString());
    if(status!=0)throw new IllegalStateException("Compilation failed: "+status);
    System.out.println("DragonLisp 1.2: "+forms.size()+" forms, "+lowered+" lowered nodes, "+fallback+" runtime fallback nodes");
    System.out.println("Generated "+out.resolve(className+".class"));
    System.out.println("Backend: javac-generated JVM bytecode; ASM direct backend NOT implemented");
  }
}
