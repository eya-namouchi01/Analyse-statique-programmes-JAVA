package hai913i.tp1.extract;

import hai913i.tp1.model.ProjectFacts;
import hai913i.tp1.parse.JdtParser.ParsedFile;

import java.util.List;

/**
 * Orchestrator : applies the two visitors to every compilation unit and returns a fact model that does not depend on JDT
 * */
public final class FactExtractor {

    private FactExtractor() {
    }

    public static ProjectFacts extract(List<ParsedFile> files) {
        ProjectFacts facts = new ProjectFacts();
        for (ParsedFile file : files) {
            facts.addSourceFile(file.path());
            file.unit().accept(new StructureVisitor(facts));
            file.unit().accept(new CallVisitor(facts));
        }
        facts.sort();
        return facts;
    }
}
