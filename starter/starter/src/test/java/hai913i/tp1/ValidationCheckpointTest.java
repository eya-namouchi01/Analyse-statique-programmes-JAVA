package hai913i.tp1;

import hai913i.tp1.extract.FactExtractor;
import hai913i.tp1.model.*;
import hai913i.tp1.parse.JdtParser;
import hai913i.tp1.parse.ProjectSources;
import hai913i.tp1.report.FactsPrinter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checkpoints A2, A3 and B1 on the validation project (copied into src/test/resources/validation).
 * Every number is computed from the fact model, without analyzing the code again.
 */
class ValidationCheckpointTest {

    private static final Path VALIDATION = Path.of("src/test/resources/validation");

    private static ProjectFacts facts;

    @BeforeAll
    static void analyze() throws Exception {
        facts = extract();
    }

    private static ProjectFacts extract() throws Exception {
        return FactExtractor.extract(JdtParser.parse(ProjectSources.of(VALIDATION), List.of()));
    }

    // ----- A2: structure -----

    @Test
    void a2CountsTypesByKind() {
        assertEquals(15, facts.types().size());
        assertEquals(12, countKind("classe"));
        assertEquals(2, countKind("interface"));
        assertEquals(1, countKind("enumeration"));
    }

    @Test
    void a2CountsMethodsConstructorsAndFields() {
        int methods = 0;
        int constructors = 0;
        int fields = 0;
        for (TypeFact type : facts.types()) {
            methods += type.methods().size();
            fields += type.fields().size();
            for (MethodFact method : type.methods()) {
                if (method.constructor()) {
                    constructors++;
                }
            }
        }
        assertEquals(59, methods);
        assertEquals(9, constructors);
        assertEquals(21, fields);
    }

    @Test
    void a2NestedTypeMembersAreNotMixed() {
        assertEquals(6, facts.findType("library.service.LoanService").methods().size());
        assertEquals(5, facts.findType("library.service.LoanService").fields().size());
        assertEquals(4, facts.findType("library.service.LoanService.Loan").methods().size());
        assertEquals(3, facts.findType("library.service.LoanService.Loan").fields().size());
    }

    @Test
    void a2SuperclassChain() {
        assertEquals(List.of("java.lang.Exception", "java.lang.Throwable"),
                facts.findType("library.service.LoanException").superclasses());
    }

    @Test
    void a2ItemFieldVisibilities() {
        TypeFact item = facts.findType("library.model.Item");
        assertEquals("public", visibility(item, "MAX_LOAN_DAYS"));
        assertEquals("protected", visibility(item, "id"));
        assertEquals("protected", visibility(item, "title"));
        assertEquals("private", visibility(item, "borrower"));
        assertEquals("package", visibility(item, "category"));
    }

    // ----- A3: calls -----

    @Test
    void a3CountsCalls() {
        int superCalls = 0;
        for (CallFact call : facts.calls()) {
            if (call.superCall()) {
                superCalls++;
            }
        }
        assertEquals(94, facts.calls().size());
        assertEquals(1, superCalls);
        assertEquals(46, countStatus(ProjectFacts.INTERNAL));
        assertEquals(48, countStatus(ProjectFacts.EXTERNAL));
        assertEquals(0, countStatus(ProjectFacts.UNRESOLVED));
    }

    @Test
    void a3ReceiverIsTheStaticTypeOfTheExpression() {
        CallFact call = findCall("library.service.LoanService#borrow(library.model.Member,java.lang.String,int)",
                "checkOut", 36);
        assertEquals("library.model.Loanable", call.receiver());
        assertEquals("library.model.Loanable#checkOut(library.model.Member)", call.targetId());
    }

    @Test
    void a3InheritedMethodTargetsItsDeclaringClass() {
        CallFact call = findCall("library.app.LibraryApp#main(java.lang.String[])", "getTitle", 32);
        assertEquals("library.model.Book", call.receiver());
        assertEquals("library.model.Item#getTitle()", call.targetId());
    }

    @Test
    void a3SuperCallAndLambda() {
        assertEquals("library.model.Item", findCall("library.model.Book#describe()", "describe", 32).receiver());
        assertEquals("library.model.Item",
                findCall("library.service.Catalog#available()", "isAvailable", 37).receiver());
    }

    // ----- B1: the model itself -----

    @Test
    void b1TwoRunsGiveTheSameOutput() throws Exception {
        assertEquals(print(facts), print(extract()));
    }

    @Test
    void b1CallsToAnotherFileAreResolved() {
        boolean found = false;
        for (CallFact call : facts.calls()) {
            if (call.callerId().startsWith("library.service.LoanService#")
                    && call.targetId() != null && call.targetId().startsWith("library.service.Catalog#")) {
                found = true;
            }
        }
        assertTrue(found, "no resolved call from LoanService to Catalog");
    }

    @Test
    void b1EveryInternalTargetIsAMethodOfTheModel() {
        // The identifiers of A2 (declarations) and A3 (targets) follow the same rule: B3 will rely on it.
        for (CallFact call : facts.calls()) {
            if (call.status().equals(ProjectFacts.INTERNAL)) {
                assertTrue(isMethodOfModel(call.targetId()), "unknown target: " + call.targetId());
            }
        }
    }

    // ----- helpers -----

    private static int countKind(String kind) {
        int count = 0;
        for (TypeFact type : facts.types()) {
            if (type.kind().equals(kind)) {
                count++;
            }
        }
        return count;
    }

    private static int countStatus(String status) {
        int count = 0;
        for (CallFact call : facts.calls()) {
            if (call.status().equals(status)) {
                count++;
            }
        }
        return count;
    }

    private static String visibility(TypeFact type, String fieldName) {
        for (FieldFact field : type.fields()) {
            if (field.name().equals(fieldName)) {
                return field.visibility();
            }
        }
        return null;
    }

    private static CallFact findCall(String caller, String name, int line) {
        for (CallFact call : facts.calls()) {
            if (call.callerId().equals(caller) && call.name().equals(name) && call.line() == line) {
                return call;
            }
        }
        throw new AssertionError("call not found: " + caller + " / " + name + " line " + line);
    }

    private static boolean isMethodOfModel(String id) {
        for (TypeFact type : facts.types()) {
            for (MethodFact method : type.methods()) {
                if (method.id().equals(id)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String print(ProjectFacts model) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes);
        FactsPrinter.printStructure(model, out);
        FactsPrinter.printCalls(model, out);
        out.flush();
        return bytes.toString();
    }
}
