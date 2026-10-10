package hai913i.tp1;

import hai913i.tp1.extract.FactExtractor;
import hai913i.tp1.model.FieldFact;
import hai913i.tp1.model.ProjectFacts;
import hai913i.tp1.model.TypeFact;
import hai913i.tp1.parse.JdtParser;
import hai913i.tp1.parse.ProjectSources;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Counting conventions of TP section 4.1, checked on small hand-written sources
 * (annex 1, section 5, point 2). Independent of the validation project.
 */
class StructureConventionsTest {

    @TempDir
    Path project;

    @Test
    void recordIsCountedButNotItsComponentsOrAccessors() throws IOException {
        ProjectFacts facts = analyze(Map.of("demo/Point.java",
                "package demo; public record Point(int x, int y) { int sum() { return x + y; } }"));
        TypeFact point = facts.findType("demo.Point");
        assertEquals("record", point.kind());
        assertEquals(0, point.fields().size());
        assertEquals(1, point.methods().size());
        assertEquals(List.of("java.lang.Record"), point.superclasses());
    }

    @Test
    void localAndAnonymousClassesAreNotCounted() throws IOException {
        ProjectFacts facts = analyze(Map.of("demo/Host.java", """
                package demo;
                class Host {
                    Runnable task = new Runnable() { public void run() { } };
                    void work() {
                        class Local { void m() { } }
                        new Thread(new Runnable() { public void run() { } }).start();
                    }
                }
                """));
        assertEquals(1, facts.types().size());
        assertEquals(1, facts.findType("demo.Host").methods().size());
        assertEquals(1, facts.findType("demo.Host").fields().size());
    }

    @Test
    void annotationTypeIsNotCounted() throws IOException {
        ProjectFacts facts = analyze(Map.of(
                "demo/Marker.java", "package demo; @interface Marker { String value(); }",
                "demo/User.java", "package demo; class User { }"));
        assertEquals(1, facts.types().size());
        assertEquals("classe", facts.types().get(0).kind());
    }

    @Test
    void enumConstantsAndTheirBodiesAreNotCounted() throws IOException {
        ProjectFacts facts = analyze(Map.of("demo/Op.java", """
                package demo;
                enum Op {
                    PLUS { int apply(int a, int b) { return a + b; } },
                    MINUS { int apply(int a, int b) { return a - b; } };
                    int apply(int a, int b) { return 0; }
                }
                """));
        TypeFact op = facts.findType("demo.Op");
        assertEquals(1, facts.types().size());
        assertEquals("enumeration", op.kind());
        assertEquals(0, op.fields().size());
        assertEquals(1, op.methods().size());
        assertEquals(List.of("java.lang.Enum"), op.superclasses());
    }

    @Test
    void oneFieldDeclarationCanDeclareSeveralAttributes() throws IOException {
        ProjectFacts facts = analyze(Map.of("demo/Pair.java", "package demo; class Pair { int a, b; }"));
        assertEquals(2, facts.findType("demo.Pair").fields().size());
    }

    @Test
    void interfaceMembersWithoutModifierArePublicImplicitly() throws IOException {
        ProjectFacts facts = analyze(Map.of("demo/Shape.java",
                "package demo; interface Shape { int SIDES = 0; double area(); }"));
        TypeFact shape = facts.findType("demo.Shape");
        assertEquals("interface", shape.kind());
        FieldFact sides = shape.fields().get(0);
        assertEquals("public_implicite", sides.visibility());
        assertEquals(1, shape.methods().size());
        assertEquals(List.of(), shape.superclasses());
    }

    @Test
    void nestedTypeMembersStayInTheirOwnType() throws IOException {
        ProjectFacts facts = analyze(Map.of("demo/Outer.java", """
                package demo;
                class Outer {
                    int x;
                    void f() { }
                    static class Inner {
                        int y, z;
                        void g() { }
                        void h() { }
                    }
                }
                """));
        assertEquals(2, facts.types().size());
        assertEquals(1, facts.findType("demo.Outer").methods().size());
        assertEquals(1, facts.findType("demo.Outer").fields().size());
        assertEquals(2, facts.findType("demo.Outer.Inner").methods().size());
        assertEquals(2, facts.findType("demo.Outer.Inner").fields().size());
    }

    @Test
    void typeInDefaultPackageHasNoPackagePrefix() throws IOException {
        ProjectFacts facts = analyze(Map.of("Plain.java", "class Plain { void m() { } }"));
        assertEquals("(défaut)", facts.findType("Plain").packageName());
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
