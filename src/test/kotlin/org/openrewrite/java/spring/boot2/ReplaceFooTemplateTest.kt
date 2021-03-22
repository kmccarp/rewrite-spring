package org.openrewrite.java.spring.boot2

import org.junit.jupiter.api.Test
import org.openrewrite.Parser
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

class ReplaceFooTemplateTest : JavaRecipeTest {

    override val parser: Parser<*>?
        get() = JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .dependsOn(listOf(Parser.Input.fromString(ReplaceFooTemplate.FOO_CLASS.trimIndent())))
            .build()

    override val recipe: ReplaceFooTemplate
        get() = ReplaceFooTemplate()

    @Test
    fun testReplaceFakeClassTemplate() = assertChanged(
        before = """
            package com.mycompany.api;
            import com.mycompany.Foo;
            public class MyClass {
                public void myMethod() {
                    Foo.foo(1);
                    Foo.foo(2);
                    Foo.foo(3);
                }
            } 
        """,
        after = """
            package com.mycompany.api;
            import com.mycompany.Foo;
            public class MyClass {
                public void myMethod() {
                    Foo.bar(1).baz(2).baz(3);
                }
            } 
        """
    )

}