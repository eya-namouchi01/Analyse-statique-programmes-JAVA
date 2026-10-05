package hai913i.tp1;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import hai913i.tp1.extract.StructureVisitor;
import hai913i.tp1.parse.JdtParser;
import hai913i.tp1.parse.JdtParser.ParsedFile;
import hai913i.tp1.parse.ProjectSources;

/**
 * Checked on small hand-written sources
 * Independent of the validation project.
 */
class StructureConventionsTest {

    @TempDir
    Path project;

    @Test
    void recordIsCountedButNotItsComponentsOrAccessors() throws IOException {
        StructureVisitor v = analyze(Map.of("demo/Point.java",
                "package demo; public record Point(int x, int y) { int sum() { return x + y; } }"));
        assertEquals(1, v.typeCount("record"));
        assertEquals(0, v.fieldCount("demo.Point"));
        assertEquals(1, v.methodCount("demo.Point"));
        assertEquals(List.of("java.lang.Record"), v.superclasses("demo.Point"));
    }

    @Test
    void localAndAnonymousClassesAreNotCounted() throws IOException {
        StructureVisitor v = analyze(Map.of("demo/Host.java", """
                package demo;
                class Host {
                    Runnable task = new Runnable() { public void run() { } };
                    void work() {
                        class Local { void m() { } }
                        new Thread(new Runnable() { public void run() { } }).start();
                    }
                }
                """));
        assertEquals(1, v.typeCount());
        assertEquals(1, v.methodCount("demo.Host"));
        assertEquals(1, v.fieldCount("demo.Host"));
    }

    @Test
    void annotationTypeIsNotCounted() throws IOException {
        StructureVisitor v = analyze(Map.of(
                "demo/Marker.java", "package demo; @interface Marker { String value(); }",
                "demo/User.java", "package demo; class User { }"));
        assertEquals(1, v.typeCount());
        assertEquals(1, v.typeCount("classe"));
    }

    @Test
    void enumConstantsAndTheirBodiesAreNotCounted() throws IOException {
        StructureVisitor v = analyze(Map.of("demo/Op.java", """
                package demo;
                enum Op {
                    PLUS { int apply(int a, int b) { return a + b; } },
                    MINUS { int apply(int a, int b) { return a - b; } };
                    int apply(int a, int b) { return 0; }
                }
                """));
        assertEquals(1, v.typeCount());
        assertEquals(1, v.typeCount("enumeration"));
        assertEquals(0, v.fieldCount("demo.Op"));
        assertEquals(1, v.methodCount("demo.Op"));
        assertEquals(List.of("java.lang.Enum"), v.superclasses("demo.Op"));
    }

    @Test
    void oneFieldDeclarationCanDeclareSeveralAttributes() throws IOException {
        StructureVisitor v = analyze(Map.of("demo/Pair.java", "package demo; class Pair { int a, b; }"));
        assertEquals(2, v.fieldCount("demo.Pair"));
    }

    @Test
    void interfaceMembersWithoutModifierArePublicImplicitly() throws IOException {
        StructureVisitor v = analyze(Map.of("demo/Shape.java",
                "package demo; interface Shape { int SIDES = 0; double area(); }"));
        assertEquals(1, v.typeCount("interface"));
        assertEquals("public_implicite", v.fieldVisibility("demo.Shape", "SIDES"));
        assertEquals(1, v.methodCount("demo.Shape"));
        assertEquals(List.of(), v.superclasses("demo.Shape"));
    }

    @Test
    void nestedTypeMembersStayInTheirOwnType() throws IOException {
        StructureVisitor v = analyze(Map.of("demo/Outer.java", """
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
        assertEquals(2, v.typeCount());
        assertEquals(1, v.methodCount("demo.Outer"));
        assertEquals(1, v.fieldCount("demo.Outer"));
        assertEquals(2, v.methodCount("demo.Outer.Inner"));
        assertEquals(2, v.fieldCount("demo.Outer.Inner"));
    }

    @Test
    void typeInDefaultPackageHasNoPackagePrefix() throws IOException {
        StructureVisitor v = analyze(Map.of("Plain.java", "class Plain { void m() { } }"));
        assertEquals(1, v.methodCount("Plain"));
    }

    /** Writes the sources under project/src, then runs the real pipeline: JdtParser, then StructureVisitor. */
    private StructureVisitor analyze(Map<String, String> sources) throws IOException {
        for (Map.Entry<String, String> source : sources.entrySet()) {
            Path file = project.resolve("src").resolve(source.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, source.getValue());
        }
        StructureVisitor visitor = new StructureVisitor(new PrintStream(OutputStream.nullOutputStream()));
        for (ParsedFile file : JdtParser.parse(ProjectSources.of(project), List.of())) {
            file.unit().accept(visitor);
        }
        return visitor;
    }
}
