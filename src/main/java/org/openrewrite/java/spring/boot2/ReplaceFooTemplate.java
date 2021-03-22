package org.openrewrite.java.spring.boot2;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Parser;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Replace calls of Foo.foo(int) with a chained call of Foo.bar(int).baz(int);
 */
public class ReplaceFooTemplate extends Recipe {

    private static final MethodMatcher METHOD_MATCHER = new MethodMatcher("com.mycompany.Foo foo(..)");
    public static final String FOO_CLASS = "\n" +
        "                package com.mycompany;\n" +
        "                public class Foo {\n" +
        "                    public static void foo(int i) {}\n" +
        "                    public Foo bar(int i) { return null; }\n" +
        "                    public static Foo baz(int i) { return null;} \n" +
        "                }\n";

    @Override
    public String getDisplayName() {
        return getClass().getName();
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new ReplaceFakeClassVisitor();
    }

    private static class ReplaceFakeClassVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext executionContext) {
            J.MethodDeclaration m = super.visitMethodDeclaration(method, executionContext);
            List<Statement> statements = m.getBody().getStatements();
            List<Statement> newStatements = new ArrayList<>();
            List<Statement> collectedStatements = new ArrayList<>();
            for (Statement statement : statements) {
                if (isCollecting(collectedStatements)) {
                    if (isFooCall(statement)) {
                        collectedStatements.add(statement);
                    } else {
                        // TODO issue starts here. When coalescing collected statements to a single statement,
                        //      the template tries to replace the first statement.
                        //  this doesn't work because the template is trying to replace
                        //      a method invocation at the same time as adding it to a list of statements.
                        newStatements.add(coalesce(collectedStatements));
                        collectedStatements = new ArrayList<>();
                        newStatements.add(statement);
                    }
                } else {
                    if (isFooCall(statement)) {
                        collectedStatements.add(statement);
                    } else {
                        newStatements.add(statement);
                    }
                }
            }

            if (isCollecting(collectedStatements)) {
                J.MethodInvocation newMethodInvocation = coalesce(collectedStatements);
                newStatements.add(newMethodInvocation);
            }

            return m.withBody(m.getBody().withStatements(newStatements));
        }

        private J.MethodInvocation coalesce(List<Statement> collectedStatements) {
            Statement statement = collectedStatements.get(0);
            if (!(statement instanceof J.MethodInvocation)) {
                throw new IllegalArgumentException("Can only coalesce J.MethodInvocation");
            }
            J.MethodInvocation m = (J.MethodInvocation) statement;
            TemplateAndArgs templateAndArgs = buildTemplateAndArgs(collectedStatements);

            // TODO issue continues here. The template generates 0 elements even though the substituted source is correct.
            //      this causes an IllegalStateException because of m.getCoordinates().replace() requires exactly one
            //      generated element.
            return statement.withTemplate(
                template(templateAndArgs.template)
                    .javaParser(
                        JavaParser.fromJavaVersion()
                            .dependsOn(Collections.singletonList(Parser.Input.fromString(FOO_CLASS)))
                            .build())
                    .build(),
                m.getCoordinates().replace(),
                templateAndArgs.parameters
            );
        }

        private TemplateAndArgs buildTemplateAndArgs(List<Statement> collectedStatements) {
            StringBuilder templateBuilder = new StringBuilder("Foo");
            List<Expression> parameters = new ArrayList<>();
            boolean hasBar = false;

            for (Statement statement : collectedStatements) {
                if (!(statement instanceof J.MethodInvocation)) {
                    throw new IllegalArgumentException("Can only coalesce J.MethodInvocation");
                }
                J.MethodInvocation m = (J.MethodInvocation) statement;
                parameters.add(m.getArguments().get(0));
                templateBuilder.append(hasBar ? ".baz(#{})" : ".bar(#{})");
                hasBar = true;
            }

            return new TemplateAndArgs(templateBuilder.toString(), parameters);
        }

        private boolean isCollecting(List<Statement> collectedStatements) {
            return !collectedStatements.isEmpty();
        }

        private boolean isFooCall(Statement statement) {
            if (!(statement instanceof J.MethodInvocation)) {
                return false;
            }

            return METHOD_MATCHER.matches((J.MethodInvocation) statement);
        }

        private class TemplateAndArgs {
            private final String template;
            private final Expression[] parameters;

            public TemplateAndArgs(String template, List<Expression> parameters) {
                this.template = template;
                this.parameters = parameters.toArray(new Expression[0]);
            }
        }
    }
}
