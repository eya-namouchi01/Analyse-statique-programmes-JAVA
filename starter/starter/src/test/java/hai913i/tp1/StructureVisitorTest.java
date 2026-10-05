package hai913i.tp1;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import hai913i.tp1.extract.StructureVisitor;
import hai913i.tp1.parse.JdtParser;
import hai913i.tp1.parse.JdtParser.ParsedFile;
import hai913i.tp1.parse.ProjectSources;

/** Point de contrôle A2 sur le projet de validation. */
class StructureVisitorTest {

    private static final Path VALIDATION = Path.of("src/test/resources/validation");

    private static StructureVisitor structure;

    @BeforeAll
    static void analyze() throws Exception {
        // L'affichage n'intéresse pas le test : il est jeté.
        structure = new StructureVisitor(new PrintStream(OutputStream.nullOutputStream()));
        for (ParsedFile file : JdtParser.parse(ProjectSources.of(VALIDATION), List.of())) {
            file.unit().accept(structure);
        }
    }

    @Test
    void countsTypesByKind() {
        assertEquals(15, structure.typeCount());
        assertEquals(12, structure.typeCount("classe"));
        assertEquals(2, structure.typeCount("interface"));
        assertEquals(1, structure.typeCount("enumeration"));
    }

    @Test
    void countsMethodsConstructorsAndFields() {
        assertEquals(59, structure.methodCount());
        assertEquals(9, structure.constructorCount());
        assertEquals(21, structure.fieldCount());
    }

    @Test
    void nestedTypeMembersAreNotMixed() {
        assertEquals(6, structure.methodCount("library.service.LoanService"));
        assertEquals(5, structure.fieldCount("library.service.LoanService"));
        assertEquals(4, structure.methodCount("library.service.LoanService.Loan"));
        assertEquals(3, structure.fieldCount("library.service.LoanService.Loan"));
    }

    @Test
    void superclassChainComesFromBindings() {
        assertEquals(List.of("java.lang.Exception", "java.lang.Throwable"),
                structure.superclasses("library.service.LoanException"));
    }

    @Test
    void itemFieldVisibilities() {
        String item = "library.model.Item";
        assertEquals("public", structure.fieldVisibility(item, "MAX_LOAN_DAYS"));
        assertEquals("protected", structure.fieldVisibility(item, "id"));
        assertEquals("protected", structure.fieldVisibility(item, "title"));
        assertEquals("private", structure.fieldVisibility(item, "borrower"));
        assertEquals("package", structure.fieldVisibility(item, "category"));
    }

    @Test
    void enumConstantsAreNotFields() {
        assertEquals(1, structure.fieldCount("library.model.Category"));
    }
}
