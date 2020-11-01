package com.chy.gordian.processor.visitor;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.List;

public class CatchVisitor extends TreeTranslator {


    @Override
    public void visitCatch(JCTree.JCCatch tree) {
        super.visitCatch(tree);
    }

    @Override
    public List<JCTree.JCCatch> translateCatchers(List<JCTree.JCCatch> trees) {
        return super.translateCatchers(trees);
    }
}
