package hai913i.tp1.extract;

import hai913i.tp1.parse.JdtParser;
import org.eclipse.jdt.core.dom.*;

import java.util.List;

public class AstPrinter extends ASTVisitor {
    private int depth = 0;

    @Override
    public boolean preVisit2(ASTNode node) {
        System.out.println("  ".repeat(depth) + node.getClass().getSimpleName());
        depth++;
        return true;
    }

    @Override
    public void postVisit(ASTNode node) {
        depth--;
    }

    @Override
    public boolean visit(TypeDeclaration node) {
        System.out.println("  ".repeat(depth) + "----------> " + (node.isInterface() ? "interface " : "classe ") + node.getName().getIdentifier());
        return true;
    }

    @Override
    public boolean visit(EnumDeclaration node) {
        System.out.println("  ".repeat(depth) + "----------> " +"enumeration " + node.getName().getIdentifier());
        return true;
    }

    @Override
    public boolean visit(RecordDeclaration node) {
        System.out.println("  ".repeat(depth) + "----------> " +"record " + node.getName().getIdentifier());
        return true;
    }

    @Override
    public boolean visit(FieldDeclaration node) {
        for (Object o : node.fragments()) {
            VariableDeclarationFragment fragment = (VariableDeclarationFragment) o;
            System.out.println("  ".repeat(depth) + "----------> " +"attribut " + fragment.getName().getIdentifier());
        }
        return true;
    }

    @Override
    public boolean visit(MethodDeclaration node) {
        System.out.println("  ".repeat(depth) + "----------> " +(node.isConstructor() ? "constructeur " : "methode ") + node.getName().getIdentifier());
        return true;
    }

    @Override
    public boolean visit(MethodInvocation node) {
        System.out.println("  ".repeat(depth) + "----------> " +"appel " + node.getName().getIdentifier() + "(...)");
        return true;
    }

    @Override
    public boolean visit(SuperMethodInvocation node) {
        System.out.println("  ".repeat(depth) + "----------> " +"appel super." + node.getName().getIdentifier() + "(...)");
        return true;
    }

    @Override
    public boolean visit(SuperConstructorInvocation node) {
        System.out.println("  ".repeat(depth) + "----------> " +"appel super(...) du constructeur");
        return true;
    }

    public static void displayTrees( List<JdtParser.ParsedFile> parsedFiles) {

        for (JdtParser.ParsedFile pFile : parsedFiles) {
            System.out.println("=== " + pFile.path() + " ===");

            pFile.unit().accept(new AstPrinter());
        }
    }
    public static void print(JdtParser.ParsedFile file) {
        System.out.println("=== " + file.path() + " ===");
        file.unit().accept(new AstPrinter());
    }
}
