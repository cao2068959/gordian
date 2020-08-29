package com.chy.gordian.processor;


import com.chy.gordian.annotation.Parasitic;
import com.chy.gordian.processor.visitor.ParasiticVisitor;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import java.util.ArrayList;
import java.util.Set;

@SupportedAnnotationTypes("com.chy.gordian.annotation.Parasitic")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class ParasiticProcessor extends AbstractProcessor {

    JavacElements elementUtils;

    TreeMaker treeMaker;

    JavacTrees trees;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        Context context = ((JavacProcessingEnvironment) processingEnv).getContext();
        treeMaker = TreeMaker.instance(context);
        elementUtils = (JavacElements) processingEnv.getElementUtils();
        trees = (JavacTrees) Trees.instance(processingEnv);

    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        for (Element element : roundEnv.getElementsAnnotatedWith(Parasitic.class)) {
            ArrayList<String> gordianNames = getGordianNames(element);
            Parasitic parasiticAnnotation = element.getAnnotation(Parasitic.class);
            JCTree tree = elementUtils.getTree(element);
            tree.accept(new ParasiticVisitor(treeMaker, elementUtils, gordianNames, parasiticAnnotation));
        }
        return true;
    }

    private ArrayList<String> getGordianNames(Element element) {
        ArrayList<String> result = new ArrayList<String>();
        for (AnnotationMirror annotationMirror : element.getAnnotationMirrors()) {
            DeclaredType annotationType = annotationMirror.getAnnotationType();
            if (!Parasitic.class.getName().equals(annotationType.toString())) {
                continue;
            }
            annotationMirror.getElementValues().entrySet().stream()
                    .filter(e -> "gordians".equals(e.getKey().getSimpleName().toString()))
                    .forEach(e -> {
                        AnnotationValue annotationValue = e.getValue();
                        Object value = annotationValue.getValue();
                        if (value instanceof List) {
                            List<Attribute> values = (List) value;
                            values.stream().forEach(a -> {
                                result.add(a.getValue().toString());
                            });
                        }
                    });
        }
        return result;
    }

}
