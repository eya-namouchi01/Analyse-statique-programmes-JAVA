package hai913i.tp1.visitor;

import hai913i.tp1.parse.JdtParser;
import hai913i.tp1.parse.ProjectSources;
import org.eclipse.jdt.core.dom.*;

import java.util.List;

public class Visitor extends ASTVisitor {
    private int depth = 0;

    @Override
    public boolean preVisit2(ASTNode node) {
        String information="";
        if (node instanceof TypeDeclaration type && !type.isInterface()){
            information = "<------------------------- La classe " +(type.getName().getIdentifier());
        }
        else if (node instanceof FieldDeclaration filed){
            for (Object attr : filed.fragments()){
                VariableDeclarationFragment fragment =
                        (VariableDeclarationFragment) attr;
                information += " <------------------------- Attribut : " + fragment.getName().getIdentifier();
            }
        }
        else if (node instanceof MethodDeclaration){
            information = "<----------------------- Methode de classe " +((MethodDeclaration) node).getName();
        }
        else if (node instanceof MethodInvocation){
            information = "<------------------------ Methode " +((MethodInvocation) node).getName();

        }        System.out.println("  ".repeat(depth)
                + node.getClass().getSimpleName()+ information);
        depth++;
        return true;
    }

    @Override
    public void postVisit(ASTNode node) {
        depth--;
    }

    public static void displayTrees(ProjectSources sources,
                                    List<String> classpath) {

        List<JdtParser.ParsedFile> parsedFiles =
                JdtParser.parse(sources, classpath);

        for (JdtParser.ParsedFile pFile : parsedFiles) {
            System.out.println("=== " + pFile.path() + " ===");

            pFile.unit().accept(new Visitor());
        }
    }
    public static void displayTrees( List<JdtParser.ParsedFile> parsedFiles) {

        for (JdtParser.ParsedFile pFile : parsedFiles) {
            System.out.println("=== " + pFile.path() + " ===");

            pFile.unit().accept(new Visitor());
        }
    }
}
