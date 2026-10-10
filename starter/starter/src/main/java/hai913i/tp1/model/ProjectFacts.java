package hai913i.tp1.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Project fact model ( independent of JDT) filled by the visitors, then SORTED once so that two runs
 * give exactly the same output
 */
public class ProjectFacts {

    public static final String INTERNAL = "interne";
    public static final String EXTERNAL = "externe";
    public static final String UNRESOLVED = "non resolu";

    private final List<Path> sourceFiles = new ArrayList<>();
    private final List<TypeFact> types = new ArrayList<>();
    private final List<CallFact> calls = new ArrayList<>();

    public void addSourceFile(Path file) {
        sourceFiles.add(file);
    }

    public void addType(TypeFact type) {
        types.add(type);
    }

    public void addCall(CallFact call) {
        calls.add(call);
    }

    /** Sorts files, types (by name) and calls (by caller, then line). */
    public void sort() {
        sourceFiles.sort(null);
        types.sort((a, b) -> a.name().compareTo(b.name()));
        calls.sort((a, b) -> {
            int byCaller = a.callerId().compareTo(b.callerId());
            return byCaller != 0 ? byCaller : Integer.compare(a.line(), b.line());
        });
    }

    public List<TypeFact> types() {
        return types;
    }

    public List<CallFact> calls() {
        return calls;
    }

    /** The type with this qualified name, or null. */
    public TypeFact findType(String name) {
        for (TypeFact type : types) {
            if (type.name().equals(name)) {
                return type;
            }
        }
        return null;
    }
}
