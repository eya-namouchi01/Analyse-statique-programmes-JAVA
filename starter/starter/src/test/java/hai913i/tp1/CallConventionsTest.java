package hai913i.tp1;

import hai913i.tp1.extract.FactExtractor;
import hai913i.tp1.model.CallFact;
import hai913i.tp1.model.ProjectFacts;
import hai913i.tp1.parse.JdtParser;
import hai913i.tp1.parse.ProjectSources;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Call conventions of TP section 4.4, checked on small hand-written sources
 * (annex 1, section 5, point 2). Independent of the validation project.
 */
class CallConventionsTest {

    @TempDir
    Path project;

    @Test
    void callsInLambdaAreAttributedToTheEnclosingMethod() throws IOException {
        List<CallFact> calls = analyze(Map.of("demo/A.java", """
                package demo;
                import java.util.List;
                class A {
                    void f(List<String> names) { names.forEach(s -> s.trim()); }
                }
                """)).calls();
        assertEquals(2, calls.size());
        for (CallFact call : calls) {
            assertEquals("demo.A#f(java.util.List)", call.callerId());
        }
    }

    @Test
    void callsInAnonymousClassAreAttributedToTheEnclosingMethod() throws IOException {
        List<CallFact> calls = analyze(Map.of("demo/A.java", """
                package demo;
                class A {
                    void f() {
                        Runnable r = new Runnable() { public void run() { g(); } };
                    }
                    void g() { }
                }
                """)).calls();
        assertEquals(1, calls.size());
        assertEquals("demo.A#f()", calls.get(0).callerId());
        assertEquals("demo.A#g()", calls.get(0).targetId());
    }

    @Test
    void methodReferenceIsNotACall() throws IOException {
        List<CallFact> calls = analyze(Map.of("demo/A.java", """
                package demo;
                import java.util.List;
                class A {
                    void f(List<String> names) { names.forEach(System.out::println); }
                }
                """)).calls();
        assertEquals(1, calls.size());
        assertEquals("forEach", calls.get(0).name());
    }

    @Test
    void callInFieldInitializerIsIgnored() throws IOException {
        List<CallFact> calls = analyze(Map.of("demo/A.java", "package demo; class A { int x = Math.abs(-1); }")).calls();
        assertEquals(0, calls.size());
    }

    @Test
    void constructorCallsAreNotCounted() throws IOException {
        List<CallFact> calls = analyze(Map.of("demo/A.java", """
                package demo;
                class A {
                    A() { this(1); }
                    A(int n) { super(); }
                    A make() { return new A(); }
                }
                """)).calls();
        assertEquals(0, calls.size());
    }

    @Test
    void superCallReceiverIsTheDirectSuperclass() throws IOException {
        List<CallFact> calls = analyze(Map.of(
                "demo/A.java", "package demo; class A { void m() { } }",
                "demo/B.java", "package demo; class B extends A { void m() { super.m(); } }")).calls();
        assertEquals(1, calls.size());
        assertTrue(calls.get(0).superCall());
        assertEquals("demo.A", calls.get(0).receiver());
        assertEquals("demo.A#m()", calls.get(0).targetId());
    }

    @Test
    void receiverWithoutExpression() throws IOException {
        List<CallFact> calls = analyze(Map.of("demo/A.java", """
                package demo;
                class A {
                    static int k() { return 1; }
                    void g() { }
                    void f() { k(); g(); }
                }
                """)).calls();
        assertEquals(2, calls.size());
        for (CallFact call : calls) {
            assertEquals("demo.A", call.receiver());
        }
    }

    @Test
    void callThroughInterfaceTargetsTheInterfaceMethod() throws IOException {
        List<CallFact> calls = analyze(Map.of(
                "demo/I.java", "package demo; interface I { void m(); }",
                "demo/C.java", "package demo; class C implements I { public void m() { } }",
                "demo/U.java", "package demo; class U { void f(I i, C c) { i.m(); c.m(); } }")).calls();
        assertEquals(2, calls.size());
        assertEquals("demo.I", calls.get(0).receiver());
        assertEquals("demo.I#m()", calls.get(0).targetId());
        assertEquals("demo.C", calls.get(1).receiver());
        assertEquals("demo.C#m()", calls.get(1).targetId());
    }

    @Test
    void genericTypesAreErasedAndLibraryCallsAreExternal() throws IOException {
        List<CallFact> calls = analyze(Map.of("demo/A.java", """
                package demo;
                import java.util.List;
                class A {
                    void f(List<String> names) { names.add("x"); }
                }
                """)).calls();
        assertEquals("java.util.List", calls.get(0).receiver());
        assertEquals("java.util.List#add(java.lang.Object)", calls.get(0).targetId());
        assertEquals(ProjectFacts.EXTERNAL, calls.get(0).status());
    }

    @Test
    void missingLibraryCallIsUnresolvedAndAnalysisContinues() throws IOException {
        // Same situation as resources/robustness/missing-dep: org.apache.commons.lang3 is not supplied.
        List<CallFact> calls = analyze(Map.of(
                "demo/Formatter.java",
                "package demo; public class Formatter { public String decorate(String t) { return t; } }",
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
                        """)).calls();
        assertEquals(2, calls.size());
        CallFact capitalize = calls.get(0); // line 6
        assertEquals("capitalize", capitalize.name());
        assertEquals(ProjectFacts.UNRESOLVED, capitalize.status());
        assertNull(capitalize.targetId());
        CallFact decorate = calls.get(1);   // line 7
        assertEquals(ProjectFacts.INTERNAL, decorate.status());
        assertEquals("demo.Formatter#decorate(java.lang.String)", decorate.targetId());
    }

    /** Writes the sources under project/src, then runs the real pipeline: JdtParser, then FactExtractor. */
    private ProjectFacts analyze(Map<String, String> sources) throws IOException {
        for (Map.Entry<String, String> source : sources.entrySet()) {
            Path file = project.resolve("src").resolve(source.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, source.getValue());
        }
        return FactExtractor.extract(JdtParser.parse(ProjectSources.of(project), List.of()));
    }
}
