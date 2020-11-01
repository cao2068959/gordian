package com.chy.gordian.processor.visitor;


import com.chy.gordian.annotation.Parasitic;
import com.chy.gordian.common.JCTool;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;

import java.util.ArrayList;

public class ParasiticVisitor extends TreeTranslator {


    private ArrayList<String> gordianNames;
    private Parasitic parasiticAnnotation;
    private JavacElements elementUtils;
    private TreeMaker treeMaker;
    private int index = 0;

    public ParasiticVisitor(TreeMaker treeMaker, JavacElements elementUtils, ArrayList<String> gordianNames, Parasitic parasiticAnnotation) {
        this.treeMaker = treeMaker;
        this.elementUtils = elementUtils;
        this.gordianNames = gordianNames;
        this.parasiticAnnotation = parasiticAnnotation;
    }

    @Override
    public void visitMethodDef(JCTree.JCMethodDecl jcMethodDecl) {
        treeMaker.pos = jcMethodDecl.pos;

        int len = gordianNames.size();
        for (String gordianName : gordianNames) {
            boolean last = false;
            if (len - 1 == index) {
                last = true;
            }
            doTamperMethod(jcMethodDecl, gordianName, last);
            index++;
        }

        super.visitMethodDef(jcMethodDecl);
    }

    /**
     * 开始修改方法体
     *
     * @param jcMethodDecl
     */
    private void doTamperMethod(JCTree.JCMethodDecl jcMethodDecl, String gordianName, boolean last) {
        ListBuffer methodBody = new ListBuffer();

        //先获取返回值
        JCTree.JCExpression restype = jcMethodDecl.restype;
        //是否有返回值
        boolean haveReturn = !"void".equals(restype.type.toString());

        JCTree.JCBlock newBody = bodyHandle(haveReturn, jcMethodDecl.body);
        //把原来的代码全部放入一个 lambda 里面
        JCTree.JCLambda lambda = treeMaker.Lambda(List.nil(), newBody);
        //把lambda 赋值到一个接口的右变  Parasitifer chyParasitifer = ()->{.....}
        JCTree.JCVariableDecl chyParasitifer = createVar(varName("chyParasitifer"), "com.chy.gordian.extend.Parasitifer",
                lambda, Flags.LAMBDA_METHOD);
        methodBody.add(chyParasitifer);


        //获取 gordian 的实例对象
        JCTree.JCExpression gordianInstance = getGordianInstance(gordianName);
        // 变成  Gordian gordianVar = 根据getGordianInstance 获取的方法，有可能是直接 new
        JCTree.JCVariableDecl gordianVar = createVar(varName("gordianVar"), "com.chy.gordian.extend.Gordian", gordianInstance);
        methodBody.add(gordianVar);

        //生成 gordianVar.control(()->{....})
        JCTree.JCExpressionStatement execControl = execMethod(varName("gordianVar"), "control",
                List.of(treeMaker.Ident(elementUtils.getName(varName("chyParasitifer")))));

        // 生成 Object result = gordianVar.control(()->{....})
        JCTree.JCVariableDecl result = createVar(varName("result"), "java.lang.Object", execControl.expr);
        ListBuffer tryBlockList = methodBody;

        if (last) {
            tryBlockList = new ListBuffer();
        }

        tryBlockList.add(result);

        //如果有返回值，就加上return 语句
        if (haveReturn) {
            //强转和方法的返回值一致
            JCTree.JCTypeCast jcTypeCast = treeMaker.TypeCast(JCTool.memberAccess(treeMaker, elementUtils, restype.type.toString()),
                    treeMaker.Ident(result.name));
            JCTree.JCReturn jreturn = treeMaker.Return(jcTypeCast);
            tryBlockList.add(jreturn);
        }

        //或者这个方法本身抛出的所有异常
        List<JCTree.JCExpression> allThrows = jcMethodDecl.getThrows();

        //只有最外层的执行 才需要 try catch 去转换异常类型
        if (last) {
            JCTree.JCTry jcTry = tryCatchExecControll(tryBlockList.toList(), allThrows);
            methodBody.add(jcTry);

        }

        jcMethodDecl.body = treeMaker.Block(0, methodBody.toList());
    }


    private String varName(String name) {
        return name + "_" + index;
    }

    /**
     * 把最后 执行 control() 方法给 try catch 一下,然后处理异常
     */
    private JCTree.JCTry tryCatchExecControll(List tryBlockList, List<JCTree.JCExpression> allThrows) {

        ListBuffer catchBody = new ListBuffer();
        for (JCTree.JCExpression mthrow : allThrows) {
            //把 e 强转成和 抛出的异常一致的类型
            JCTree.JCTypeCast jcTypeCast = treeMaker.TypeCast(mthrow, treeMaker.Ident(elementUtils.getName("e")));
            // if(e instanceof XXXExcption){ throw (XXXExcption) e }
            JCTree.JCStatement ifTypeAndThrow = treeMaker.If(treeMaker.TypeTest(treeMaker.Ident(elementUtils.getName("e")), mthrow),
                    treeMaker.Throw(jcTypeCast), null);
            catchBody.add(ifTypeAndThrow);
        }

        //强转成 RuntimeException 类型 兜底策略
        JCTree.JCTypeCast jcTypeCastRuntimeException = treeMaker.TypeCast(JCTool.memberAccess(treeMaker, elementUtils, "java.lang.RuntimeException"),
                treeMaker.Ident(elementUtils.getName("$ex")));
        //抛出 RuntimeException 类型的异常
        catchBody.add(treeMaker.Throw(jcTypeCastRuntimeException));


        //JCTree.JCExpression varType = JCTool.memberAccess(treeMaker, elementUtils, "java.lang.Throwable");
        JCTree.JCExpression varType = chainDots(-1, null, null, "java.lang.Throwable".split("\\."));

        Name $ex = elementUtils.getName("$ex");
        JCTree.JCVariableDecl jcVariableDecl = treeMaker.VarDef(treeMaker.Modifiers(Flags.FINAL | Flags.PARAMETER), $ex, varType, null);

        JCTree.JCCatch aCatch = treeMaker.Catch(jcVariableDecl, treeMaker.Block(0, catchBody.toList()));
        JCTree.JCTry result = treeMaker.Try(treeMaker.Block(0, tryBlockList), List.of(aCatch), null);
        return result;
    }


    public  JCTree.JCExpression chainDots( int pos, String elem1, String elem2, String... elems) {
        assert elems != null;
        if (pos != -1) treeMaker = treeMaker.at(pos);
        JCTree.JCExpression e = null;
        if (elem1 != null) e = treeMaker.Ident(elementUtils.getName(elem1));
        if (elem2 != null) e = e == null ? treeMaker.Ident(elementUtils.getName(elem2)) : treeMaker.Select(e, elementUtils.getName(elem2));
        for (int i = 0 ; i < elems.length ; i++) {
            e = e == null ? treeMaker.Ident(elementUtils.getName(elems[i])) : treeMaker.Select(e, elementUtils.getName(elems[i]));
        }

        assert e != null;

        return e;
    }


    /**
     * 去扫描方法体，对原来的方法做一些修改
     * <p>
     * 1 .修改所有的变量，重新定义了一遍, 因为lambda 不能访问被修改的变量的缘故
     * <p>
     * 2 .如果原方法不存在返回值，那么
     * 1 . 在原方法体 后面加上 return null;
     * 2 . 原方体里写了 return --> return null
     * <p>
     * 如果原方法本来就存在了返回值，则不作任何处理
     */
    private JCTree.JCBlock bodyHandle(Boolean havaResult, JCTree.JCBlock body) {
        if (havaResult) {
            return body;
        }

        return doBodyHandle(body, true);
    }


    /**
     * 递归去扫描
     */
    private JCTree.JCBlock doBodyHandle(JCTree.JCBlock block, boolean isOutlayer) {
        if (block == null) {
            return null;
        }

        ListBuffer<JCTree.JCStatement> result = new ListBuffer<>();

        for (JCTree.JCStatement statement : block.getStatements()) {
            //如果这个语句是 if 那么 继续扫描他的方法块
            if (statement instanceof JCTree.JCIf) {
                JCTree.JCIf jcif = (JCTree.JCIf) statement;
                jcif.thenpart = doBodyHandle((JCTree.JCBlock) jcif.thenpart, false);
                jcif.elsepart = doBodyHandle((JCTree.JCBlock) jcif.elsepart, false);
                result.add(statement);
                continue;
            }

            //如果是代码块
            if (statement instanceof JCTree.JCBlock) {
                JCTree.JCBlock jcBlock = (JCTree.JCBlock) statement;
                JCTree.JCBlock newjcBlock = doBodyHandle(jcBlock, false);
                result.add(newjcBlock);
                continue;
            }

            //如果是 while 语句
            if (statement instanceof JCTree.JCWhileLoop) {
                JCTree.JCWhileLoop jcWhileLoop = (JCTree.JCWhileLoop) statement;
                jcWhileLoop.body = doBodyHandle((JCTree.JCBlock) jcWhileLoop.body, false);
                result.add(statement);
                continue;
            }


            //如果是 return 语句
            if (statement instanceof JCTree.JCReturn) {
                JCTree.JCReturn jCReturn = (JCTree.JCReturn) statement;
                jCReturn.expr = returnNone();
                result.add(statement);
                continue;
            }

            //如果是 变量语句，那么复制一遍
            if (statement instanceof JCTree.JCVariableDecl) {
                JCTree.JCVariableDecl jcVariableDecl = (JCTree.JCVariableDecl) statement;
                JCTree.JCVariableDecl newjcVariableDecl = copyVar(jcVariableDecl);
                result.add(newjcVariableDecl);
                continue;
            }

            result.add(statement);
        }

        if (isOutlayer) {
            result.add(treeMaker.Return(returnNone()));
        }

        return treeMaker.Block(0, result.toList());

    }

    private JCTree.JCVariableDecl copyVar(JCTree.JCVariableDecl oriVar) {
        return treeMaker.VarDef(
                oriVar.getModifiers(),
                oriVar.getName(),
                oriVar.vartype,
                oriVar.getInitializer()
        );
    }


    private JCTree.JCLiteral returnNone() {
        return treeMaker.Literal(TypeTag.BOT, null);
    }


    /**
     * 创建一个变量
     */
    private JCTree.JCVariableDecl createVar(String varName, String varClass, JCTree.JCExpression varValue, Long modifiers) {
        return treeMaker.VarDef(
                treeMaker.Modifiers(modifiers),
                elementUtils.getName(varName),
                JCTool.memberAccess(treeMaker, elementUtils, varClass),
                varValue
        );
    }

    /**
     * 创建一个变量
     */
    private JCTree.JCVariableDecl createVar(String varName, String varClass, JCTree.JCExpression varValue) {
        return createVar(varName, varClass, varValue, Flags.PARAMETER);
    }


    /**
     * @param methodInstanceName 方法所在对象的名称 / 全路径的类名称
     * @param methodName         方法名称
     * @param param              参数
     * @return
     */
    private JCTree.JCExpressionStatement execMethod(String methodInstanceName, String methodName, List<JCTree.JCExpression> param) {
        JCTree.JCExpression left;
        if (methodInstanceName.contains(".")) {
            left = JCTool.memberAccess(treeMaker, elementUtils, methodInstanceName);
        } else {
            left = treeMaker.Ident(elementUtils.getName(methodInstanceName));
        }

        return treeMaker.Exec(
                treeMaker.Apply(
                        com.sun.tools.javac.util.List.nil(),
                        treeMaker.Select(left, // . 左边的内容
                                elementUtils.getName(methodName) // . 右边的内容
                        ),
                        param // 方法中的内容
                )
        );
    }


    private JCTree.JCExpression getGordianInstance(String gordianName) {
        boolean factoryMode = parasiticAnnotation.factoryMode();
        if (factoryMode) {
            return execMethod("com.chy.gordian.factory.FactoryGate", "getInstance", List.of(treeMaker.Literal(gordianName))).expr;
        }

        //非工厂模式，直接把注解里面传进来的 Gordian 实现类给new出来
        return treeMaker.NewClass(null, List.nil(), JCTool.memberAccess(treeMaker, elementUtils, gordianName),
                List.nil(), null);
    }


}
