package hai913i.tp1;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import hai913i.tp1.extract.CallVisitor;
import hai913i.tp1.extract.CallVisitor.CallInfo;
import hai913i.tp1.parse.JdtParser;
import hai913i.tp1.parse.JdtParser.ParsedFile;
import hai913i.tp1.parse.ProjectSources;

/** A3 checkpoint on the validation project (copied into src/test/resources/validation). */
class CallVisitorTest {

    private static final Path VALIDATION = Path.of("src/test/resources/validation");

    private static CallVisitor calls;

    @BeforeAll
    static void analyze() throws Exception {
        calls = new CallVisitor(new PrintStream(OutputStream.nullOutputStream()));
        for (ParsedFile file : JdtParser.parse(ProjectSources.of(VALIDATION), List.of())) {
            file.unit().accept(calls);
        }
    }

    @Test
    void countsCallsByKindAndStatus() {
        assertEquals(94, calls.calls().size());
        assertEquals(93, calls.methodInvocationCount());
        assertEquals(1, calls.superInvocationCount());
        assertEquals(46, calls.internalCount());
        assertEquals(48, calls.externalCount());
        assertEquals(0, calls.unresolvedCount());
    }

    @Test
    void receiverIsTheStaticTypeOfTheExpression() {
        CallInfo call = find("library.service.LoanService#borrow(library.model.Member,java.lang.String,int)",
                "checkOut", 36);
        assertEquals("library.model.Loanable", call.receiver());
        assertEquals("library.model.Loanable#checkOut(library.model.Member)", call.target());
    }

    @Test
    void inheritedMethodTargetsItsDeclaringClass() {
        CallInfo call = find("library.app.LibraryApp#main(java.lang.String[])", "getTitle", 32);
        assertEquals("library.model.Book", call.receiver());
        assertEquals("library.model.Item#getTitle()", call.target());
    }

    @Test
    void superCallReceiverIsTheDirectSuperclass() {
        CallInfo call = find("library.model.Book#describe()", "describe", 32);
        assertEquals("library.model.Item", call.receiver());
    }

    @Test
    void callInLambdaIsAttributedToTheEnclosingMethod() {
        CallInfo call = find("library.service.Catalog#available()", "isAvailable", 37);
        assertEquals("library.model.Item", call.receiver());
    }

    private static CallInfo find(String caller, String name, int line) {
        return calls.calls().stream()
                .filter(c -> c.caller().equals(caller) && c.name().equals(name) && c.line() == line)
                .findFirst()
                .orElseThrow(() -> new AssertionError("call not found: " + caller + " / " + name + " line " + line));
    }
}
