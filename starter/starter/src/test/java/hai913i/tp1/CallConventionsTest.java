package hai913i.tp1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import hai913i.tp1.extract.CallVisitor;
import hai913i.tp1.extract.CallVisitor.CallInfo;
import hai913i.tp1.parse.JdtParser;
import hai913i.tp1.parse.JdtParser.ParsedFile;
import hai913i.tp1.parse.ProjectSources;

/**Test independent of the validation project*/

class CallConventionsTest {

    @TempDir
    Path project;

    @Test
    void callsInLambdaAreAttributedToTheEnclosingMethod() throws IOException {
        CallVisitor v = analyze(Map.of("demo/A.java", """
                package demo;
                import java.util.List;
                class A {
                    void f(List<String> names) { names.forEach(s -> s.trim()); }
                }
                """));
        assertEquals(2, v.calls().size());
        v.calls().forEach(c -> assertEquals("demo.A#f(java.util.List)", c.caller()));
    }

    @Test
    void callsInAnonymousClassAreAttributedToTheEnclosingMethod() throws IOException {
        CallVisitor v = analyze(Map.of("demo/A.java", """
                package demo;
                class A {
                    void f() {
                        Runnable r = new Runnable() { public void run() { g(); } };
                    }
                    void g() { }
                }
                """));
        assertEquals(1, v.calls().size());
        CallInfo call = v.calls().get(0);
        assertEquals("demo.A#f()", call.caller());
        assertEquals("demo.A#g()", call.target());
    }

    @Test
    void methodReferenceIsNotACall() throws IOException {
        CallVisitor v = analyze(Map.of("demo/A.java", """
                package demo;
                import java.util.List;
                class A {
                    void f(List<String> names) { names.forEach(System.out::println); }
                }
                """));
        assertEquals(1, v.calls().size());
        assertEquals("forEach", v.calls().get(0).name());
    }

    @Test
    void callInFieldInitializerIsIgnored() throws IOException {
        CallVisitor v = analyze(Map.of("demo/A.java", "package demo; class A { int x = Math.abs(-1); }"));
        assertEquals(0, v.calls().size());
    }

    @Test
    void constructorCallsAreNotCounted() throws IOException {
        CallVisitor v = analyze(Map.of("demo/A.java", """
                package demo;
                class A {
                    A() { this(1); }
                    A(int n) { super(); }
                    A make() { return new A(); }
                }
                """));
        assertEquals(0, v.calls().size());
    }

    @Test
    void superCallReceiverIsTheDirectSuperclass() throws IOException {
        CallVisitor v = analyze(Map.of(
                "demo/A.java", "package demo; class A { void m() { } }",
                "demo/B.java", "package demo; class B extends A { void m() { super.m(); } }"));
        assertEquals(1, v.superInvocationCount());
        CallInfo call = v.calls().get(0);
        assertEquals("demo.A", call.receiver());
        assertEquals("demo.A#m()", call.target());
    }

    @Test
    void receiverWithoutExpression() throws IOException {
        CallVisitor v = analyze(Map.of("demo/A.java", """
                package demo;
                class A {
                    static int k() { return 1; }
                    void g() { }
                    void f() { k(); g(); }
                }
                """));
        assertEquals(2, v.calls().size());
        v.calls().forEach(c -> assertEquals("demo.A", c.receiver()));
    }

    @Test
    void callThroughInterfaceTargetsTheInterfaceMethod() throws IOException {
        CallVisitor v = analyze(Map.of(
                "demo/I.java", "package demo; interface I { void m(); }",
                "demo/C.java", "package demo; class C implements I { public void m() { } }",
                "demo/U.java", "package demo; class U { void f(I i, C c) { i.m(); c.m(); } }"));
        assertEquals(2, v.calls().size());
        assertEquals("demo.I", v.calls().get(0).receiver());
        assertEquals("demo.I#m()", v.calls().get(0).target());
        assertEquals("demo.C", v.calls().get(1).receiver());
        assertEquals("demo.C#m()", v.calls().get(1).target());
    }

    @Test
    void genericTypesAreErasedAndLibraryCallsAreExternal() throws IOException {
        CallVisitor v = analyze(Map.of("demo/A.java", """
                package demo;
                import java.util.List;
                class A {
                    void f(List<String> names) { names.add("x"); }
                }
                """));
        CallInfo call = v.calls().get(0);
        assertEquals("java.util.List", call.receiver());
        assertEquals("java.util.List#add(java.lang.Object)", call.target());
        assertEquals("externe", call.status());
    }

    @Test
    void missingLibraryCallIsUnresolvedAndAnalysisContinues() throws IOException {
        // Same situation as resources/robustness/missing-dep: org.apache.commons.lang3 is not supplied.
        CallVisitor v = analyze(Map.of(
                "demo/Formatter.java", "package demo; public class Formatter { public String decorate(String t) { return t; } }",
                "demo/Greeter.java", """
                        package demo;
                        import org.apache.commons.lang3.StringUtils;
                        public class Greeter {
                            private final Formatter formatter = new Formatter();
                            public String greet(String name) {
                                String clean = StringUtils.capitalize(name);
                                return formatter.decorate(clean);
                            }
                        }
                        """));
        assertEquals(2, v.calls().size());
        CallInfo capitalize = find(v, "capitalize");
        assertEquals("non resolu", capitalize.status());
        assertNull(capitalize.target());
        CallInfo decorate = find(v, "decorate");
        assertEquals("interne", decorate.status());
        assertEquals("demo.Formatter#decorate(java.lang.String)", decorate.target());
    }

    private static CallInfo find(CallVisitor v, String name) {
        return v.calls().stream().filter(c -> c.name().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("call not found: " + name));
    }

    private CallVisitor analyze(Map<String, String> sources) throws IOException {
        for (Map.Entry<String, String> source : sources.entrySet()) {
            Path file = project.resolve("src").resolve(source.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, source.getValue());
        }
        CallVisitor visitor = new CallVisitor(new PrintStream(OutputStream.nullOutputStream()));
        List<ParsedFile> files = new java.util.ArrayList<>(JdtParser.parse(ProjectSources.of(project), List.of()));
        files.sort(java.util.Comparator.comparing(ParsedFile::path));
        for (ParsedFile file : files) {
            file.unit().accept(visitor);
        }
        return visitor;
    }
}
