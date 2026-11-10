package org.dragonlisp;

import java.nio.file.*;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import static jdk.internal.org.objectweb.asm.Opcodes.*;

/** Direct JVM bytecode backend using the ASM API bundled with OpenJDK 21.
 * Unsupported language forms intentionally delegate to the interpreter.
 * This backend never emits Java source or invokes javac.
 */
public final class ASMCompiler {
  private static final String RT="org/dragonlisp/RuntimeSupport";
  private static final String OBJ="Ljava/lang/Object;";
  private static final String STR="Ljava/lang/String;";
  private static final String EVAL="(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;";
  private static int direct, fallback;
  private static int nextLocal;
  private static ClassWriter writer;
  private static String generatedClass;
  private static int functionIndex;
  private static Main.Env compileEnv;
  private static int expanded;
  private static final Set<String> compiledNames=new HashSet<>();
  private ASMCompiler() {}
  private static void rt(MethodVisitor mv,String method,String desc) {mv.visitMethodInsn(INVOKESTATIC,RT,method,desc,false);}
  private static String source(Object x) {
    if(x instanceof Main.Sym s)return s.name();
    if(x instanceof String s)return Main.show(s);
    if(x instanceof List<?> l){List<String> a=new ArrayList<>();for(Object v:l)a.add(source(v));return "("+String.join(" ",a)+")";}
    return Main.show(x);
  }
  private static void fallback(MethodVisitor mv,Object x,int env){
    fallback++;mv.visitVarInsn(ALOAD,env);mv.visitLdcInsn(source(x));rt(mv,"evaluate",EVAL);
  }
  private static void expr(MethodVisitor mv,Object x,int env){
    if(x==Main.NIL){direct++;rt(mv,"nil","()"+OBJ);return;}
    if(x instanceof String s){direct++;mv.visitLdcInsn(s);return;}
    if(x instanceof Main.Sym s){direct++;mv.visitVarInsn(ALOAD,env);mv.visitLdcInsn(s.name());rt(mv,"lookup","(Ljava/lang/Object;"+STR+")"+OBJ);return;}
    if(x instanceof Long||x instanceof Integer||x instanceof java.math.BigInteger){direct++;mv.visitLdcInsn(x.toString());rt(mv,"integer","("+STR+")"+OBJ);return;}
    if(x instanceof java.math.BigDecimal){direct++;mv.visitLdcInsn(x.toString());rt(mv,"decimal","("+STR+")"+OBJ);return;}
    if(x instanceof Main.Ratio r){direct++;mv.visitLdcInsn(r.numerator().toString());mv.visitLdcInsn(r.denominator().toString());rt(mv,"rational","("+STR+STR+")"+OBJ);return;}
    if(!(x instanceof List<?> a)||a.isEmpty()||!(a.get(0) instanceof Main.Sym op)){fallback(mv,x,env);return;}
    String name=op.name();
    if(compileEnv!=null && !name.equals("quote") && !name.equals("defmacro")){
      Object expansion=Main.macroexpand(x,compileEnv,true);
      if(expansion!=x && !expansion.equals(x)){
        expanded++;expr(mv,expansion,env);return;
      }
    }
    if(name.equals("lambda") && a.size()>=3 && (a.get(1) instanceof List<?> || a.get(1)==Main.NIL)){
      Parameters params=parameters(a.get(1)==Main.NIL?List.of():(List<?>)a.get(1));
      if(params!=null){
        direct++;
        String method="dragonLambda"+(functionIndex++);
        emitFunction(method,params,a.subList(2,a.size()));
        mv.visitVarInsn(ALOAD,env);
        mv.visitLdcInsn(generatedClass);
        mv.visitLdcInsn(method);
        pushInt(mv,params.fixed().size());
        mv.visitInsn(params.rest()==null?ICONST_0:ICONST_1);
        rt(mv,"compiledClosure","("+OBJ+STR+STR+"IZ)"+OBJ);
        return;
      }
    }
    if(name.equals("defun") && a.size()>=4 && a.get(1) instanceof Main.Sym fnName && (a.get(2) instanceof List<?> || a.get(2)==Main.NIL)){
      Parameters params=parameters(a.get(2)==Main.NIL?List.of():(List<?>)a.get(2));
      if(params!=null){
        direct++;compiledNames.add(fnName.name());
        String method="dragonFunction"+(functionIndex++);
        emitFunction(method,params,a.subList(3,a.size()));
        mv.visitVarInsn(ALOAD,env);
        mv.visitLdcInsn(fnName.name());
        mv.visitVarInsn(ALOAD,env);
        mv.visitLdcInsn(generatedClass);
        mv.visitLdcInsn(method);
        pushInt(mv,params.fixed().size());
        mv.visitInsn(params.rest()==null?ICONST_0:ICONST_1);
        rt(mv,"registerClosure","("+OBJ+STR+OBJ+STR+STR+"IZ)"+OBJ);
        return;
      }
    }

    if(name.equals("if")&&(a.size()==3||a.size()==4)){
      direct++;Label elseLabel=new Label(),end=new Label();expr(mv,a.get(1),env);rt(mv,"isTrue","("+OBJ+")Z");mv.visitJumpInsn(IFEQ,elseLabel);
      expr(mv,a.get(2),env);mv.visitJumpInsn(GOTO,end);mv.visitLabel(elseLabel);
      if(a.size()==4)expr(mv,a.get(3),env);else rt(mv,"nil","()"+OBJ);
      mv.visitLabel(end);return;
    }
    if(name.equals("quote")&&a.size()==2){
      direct++;mv.visitVarInsn(ALOAD,env);mv.visitLdcInsn(source(x));rt(mv,"evaluate",EVAL);return;
    }
    if(name.equals("when")&&a.size()>=2){
      direct++;Label skip=new Label(),end=new Label();expr(mv,a.get(1),env);rt(mv,"isTrue","("+OBJ+")Z");mv.visitJumpInsn(IFEQ,skip);
      body(mv,a,2,env);mv.visitJumpInsn(GOTO,end);mv.visitLabel(skip);rt(mv,"nil","()"+OBJ);mv.visitLabel(end);return;
    }
    if(name.equals("unless")&&a.size()>=2){
      direct++;Label skip=new Label(),end=new Label();expr(mv,a.get(1),env);rt(mv,"isTrue","("+OBJ+")Z");mv.visitJumpInsn(IFNE,skip);
      body(mv,a,2,env);mv.visitJumpInsn(GOTO,end);mv.visitLabel(skip);rt(mv,"nil","()"+OBJ);mv.visitLabel(end);return;
    }
    if(name.equals("and")||name.equals("or")){
      direct++;Label end=new Label();if(a.size()==1){rt(mv,name.equals("and")?"truth":"nil","()"+OBJ);return;}
      for(int i=1;i<a.size();i++){
        expr(mv,a.get(i),env);
        if(i<a.size()-1){mv.visitInsn(DUP);rt(mv,"isTrue","("+OBJ+")Z");mv.visitJumpInsn(name.equals("and")?IFEQ:IFNE,end);mv.visitInsn(POP);}
      }mv.visitLabel(end);return;
    }
    if(name.equals("progn")){
      direct++;rt(mv,"nil","()"+OBJ);
      for(int i=1;i<a.size();i++){mv.visitInsn(POP);expr(mv,a.get(i),env);}return;
    }
    if((name.equals("defparameter")||name.equals("setq"))&&a.size()==3&&a.get(1) instanceof Main.Sym sym){
      direct++;mv.visitVarInsn(ALOAD,env);mv.visitLdcInsn(sym.name());expr(mv,a.get(2),env);
      rt(mv,name.equals("setq")?"update":"define","(Ljava/lang/Object;"+STR+OBJ+")"+OBJ);return;
    }
    if((name.equals("let")||name.equals("let*"))&&a.size()>=2&&a.get(1) instanceof List<?> bindings){
      direct++;int local=nextLocal++;mv.visitVarInsn(ALOAD,env);rt(mv,"child","("+OBJ+")"+OBJ);mv.visitVarInsn(ASTORE,local);
      boolean sequential=name.equals("let*");
      // let evaluates initializers in the parent environment; let* in the new child.
      // For let, stage all values before binding to preserve parallel semantics.
      int[] staged=new int[bindings.size()];
      for(int i=0;i<bindings.size();i++){
        Object binding=bindings.get(i);List<?> pair=binding instanceof Main.Sym?List.of(binding):binding instanceof List<?> l?l:null;
        if(pair==null||pair.size()<1||pair.size()>2||!(pair.get(0) instanceof Main.Sym)){fallback(mv,x,env);return;}
        if(sequential){mv.visitVarInsn(ALOAD,local);mv.visitLdcInsn(((Main.Sym)pair.get(0)).name());
          if(pair.size()==2)expr(mv,pair.get(1),local);else rt(mv,"nil","()"+OBJ);
          rt(mv,"bind","("+OBJ+STR+OBJ+")"+OBJ);mv.visitInsn(POP);
        }else{
          staged[i]=nextLocal++;if(pair.size()==2)expr(mv,pair.get(1),env);else rt(mv,"nil","()"+OBJ);mv.visitVarInsn(ASTORE,staged[i]);
        }
      }
      if(!sequential)for(int i=0;i<bindings.size();i++){
        List<?> pair=bindings.get(i) instanceof Main.Sym?List.of(bindings.get(i)):(List<?>)bindings.get(i);
        mv.visitVarInsn(ALOAD,local);mv.visitLdcInsn(((Main.Sym)pair.get(0)).name());mv.visitVarInsn(ALOAD,staged[i]);
        rt(mv,"bind","("+OBJ+STR+OBJ+")"+OBJ);mv.visitInsn(POP);
      }
      body(mv,a,2,local);return;
    }
    if(Set.of("+","-","*","/","=","<",">","<=",">=","print","princ","list","car","cdr","cons","length","not","null","equal","type-of","numerator","denominator").contains(name)){
      direct++;mv.visitVarInsn(ALOAD,env);mv.visitLdcInsn(name);rt(mv,"lookup","(Ljava/lang/Object;"+STR+")"+OBJ);
      int n=a.size()-1;pushInt(mv,n);mv.visitTypeInsn(ANEWARRAY,"java/lang/Object");
      for(int i=0;i<n;i++){mv.visitInsn(DUP);pushInt(mv,i);expr(mv,a.get(i+1),env);mv.visitInsn(AASTORE);}
      rt(mv,"call","("+OBJ+"[Ljava/lang/Object;)"+OBJ);return;
    }
    if(compiledNames.contains(name)){
      direct++;mv.visitVarInsn(ALOAD,env);mv.visitLdcInsn(name);rt(mv,"lookup","(Ljava/lang/Object;"+STR+")"+OBJ);
      int n=a.size()-1;pushInt(mv,n);mv.visitTypeInsn(ANEWARRAY,"java/lang/Object");
      for(int i=0;i<n;i++){mv.visitInsn(DUP);pushInt(mv,i);expr(mv,a.get(i+1),env);mv.visitInsn(AASTORE);}
      rt(mv,"call","("+OBJ+"[Ljava/lang/Object;)"+OBJ);return;
    }
    // Generic calls are directly emitted; unknown special forms and macros
    // still need the evaluator, which knows their expansion semantics.
    if(Set.of("java/call","java/static","java/new","java/get","java/set","java/array","java/aget","java/aset","funcall","apply","mapcar","reduce","append","reverse","nth","listp","numberp","symbolp","stringp").contains(name)){
      direct++;mv.visitVarInsn(ALOAD,env);mv.visitLdcInsn(name);rt(mv,"lookup","(Ljava/lang/Object;"+STR+")"+OBJ);
      int n=a.size()-1;pushInt(mv,n);mv.visitTypeInsn(ANEWARRAY,"java/lang/Object");
      for(int i=0;i<n;i++){mv.visitInsn(DUP);pushInt(mv,i);expr(mv,a.get(i+1),env);mv.visitInsn(AASTORE);}
      rt(mv,"call","("+OBJ+"[Ljava/lang/Object;)"+OBJ);return;
    }
    fallback(mv,x,env);
  }
  private record Parameters(List<String> fixed,String rest) {}
  private static Parameters parameters(List<?> params){
    List<String> names=new ArrayList<>();String rest=null;
    for(int i=0;i<params.size();i++){
      if(!(params.get(i) instanceof Main.Sym sym))return null;
      String name=sym.name();
      if(name.equals("&rest")||name.equals("&body")){
        if(i+2!=params.size() || !(params.get(i+1) instanceof Main.Sym r))return null;
        rest=r.name();if(rest.startsWith("&")||names.contains(rest))return null;
        break;
      }
      if(name.startsWith("&")||names.contains(name))return null;
      names.add(name);
    }
    return new Parameters(List.copyOf(names),rest);
  }
  private static void emitFunction(String method,Parameters params,List<?> forms){
    MethodVisitor fm=writer.visitMethod(ACC_PUBLIC|ACC_STATIC,method,"(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;",null,null);
    fm.visitCode();
    int old=nextLocal;nextLocal=3;
    fm.visitVarInsn(ALOAD,0);rt(fm,"child","("+OBJ+")"+OBJ);fm.visitVarInsn(ASTORE,2);
    for(int i=0;i<params.fixed().size();i++){
      fm.visitVarInsn(ALOAD,2);fm.visitLdcInsn(params.fixed().get(i));fm.visitVarInsn(ALOAD,1);pushInt(fm,i);fm.visitInsn(AALOAD);
      rt(fm,"bind","("+OBJ+STR+OBJ+")"+OBJ);fm.visitInsn(POP);
    }
    if(params.rest()!=null){
      fm.visitVarInsn(ALOAD,2);fm.visitLdcInsn(params.rest());fm.visitVarInsn(ALOAD,1);
      pushInt(fm,params.fixed().size());rt(fm,"restList","([Ljava/lang/Object;I)"+OBJ);
      rt(fm,"bind","("+OBJ+STR+OBJ+")"+OBJ);fm.visitInsn(POP);
    }
    rt(fm,"nil","()"+OBJ);
    for(Object form:forms){fm.visitInsn(POP);expr(fm,form,2);}
    fm.visitInsn(ARETURN);fm.visitMaxs(0,0);fm.visitEnd();nextLocal=old;
  }
  private static void body(MethodVisitor mv,List<?> a,int start,int env){
    rt(mv,"nil","()"+OBJ);
    for(int i=start;i<a.size();i++){mv.visitInsn(POP);expr(mv,a.get(i),env);}
  }
  private static void pushInt(MethodVisitor mv,int i){if(i>=-1&&i<=5)mv.visitInsn(ICONST_0+i);else if(i<=127)mv.visitIntInsn(BIPUSH,i);else if(i<=32767)mv.visitIntInsn(SIPUSH,i);else mv.visitLdcInsn(i);}
  public static void main(String[] args)throws Exception {
    if(args.length!=3||!args[0].equals("--compile")){System.err.println("Usage: ./compile-asm.sh input.dlisp ClassName");System.exit(2);}
    String className=args[2];if(!className.matches("[A-Za-z_$][A-Za-z0-9_$]*"))throw new IllegalArgumentException("Invalid JVM class name");
    List<Object> forms=new Main.Reader(Files.readString(Path.of(args[1]))).all();direct=0;fallback=0;nextLocal=2;functionIndex=0;expanded=0;generatedClass=className;compileEnv=Main.standard();compiledNames.clear();
    ClassWriter cw=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);
    cw.visit(V17,ACC_PUBLIC|ACC_FINAL|ACC_SUPER,className,null,"java/lang/Object",null);
    writer=cw;
    MethodVisitor ctor=cw.visitMethod(ACC_PRIVATE,"<init>","()V",null,null);ctor.visitCode();ctor.visitVarInsn(ALOAD,0);ctor.visitMethodInsn(INVOKESPECIAL,"java/lang/Object","<init>","()V",false);ctor.visitInsn(RETURN);ctor.visitMaxs(0,0);ctor.visitEnd();
    MethodVisitor mv=cw.visitMethod(ACC_PUBLIC|ACC_STATIC,"main","([Ljava/lang/String;)V",null,null);mv.visitCode();
    rt(mv,"environment","()"+OBJ);mv.visitVarInsn(ASTORE,1);
    for(Object form:forms){
      if(form instanceof List<?> l && !l.isEmpty() && l.get(0) instanceof Main.Sym sy && sy.name().equals("defmacro")){
        Main.eval(form,compileEnv);
      }
      expr(mv,form,1);mv.visitInsn(POP);
    }
    mv.visitInsn(RETURN);mv.visitMaxs(0,0);mv.visitEnd();cw.visitEnd();
    Path out=Path.of("build/compiled");Files.createDirectories(out);Path file=out.resolve(className+".class");Files.write(file,cw.toByteArray());
    System.out.println("DragonLisp ASM backend: "+forms.size()+" forms; "+direct+" directly emitted nodes; "+fallback+" interpreter fallbacks; "+functionIndex+" compiled functions; "+expanded+" macro expansions");
    System.out.println("Generated "+file+" (direct ASM bytecode; no generated Java source)");
  }
}
