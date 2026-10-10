package hai913i.tp1.extract;

import java.util.ArrayList;
import java.util.List;

import hai913i.tp1.model.FieldFact;
import hai913i.tp1.model.MethodFact;
import hai913i.tp1.model.ProjectFacts;
import hai913i.tp1.model.TypeFact;
import org.eclipse.jdt.core.dom.*;

public class StructureVisitor extends ASTVisitor {

    private static final String DEFAULT_PACKAGE = "(défaut)";
    private final ProjectFacts facts;

    public StructureVisitor(ProjectFacts facts) {
        this.facts = facts;
    }

    @Override
    public boolean visit(TypeDeclaration node) {
        if (isLocal(node)) {
            return false;
        }
        handleType(node, node.isInterface() ? "interface" : "classe");
        return true;
    }

    @Override
    public boolean visit(EnumDeclaration node) {
        if (isLocal(node)) {
            return false;
        }
        handleType(node, "enumeration");
        return true;
    }

    @Override
    public boolean visit(RecordDeclaration node) {
        if (isLocal(node)) {
            return false;
        }
        handleType(node, "record");
        return true;
    }

    @Override
    public boolean visit(AnnotationTypeDeclaration node) {
        return false;
    }

    @Override
    public boolean visit(MethodDeclaration node) {
        return false;
    }

    @Override
    public boolean visit(FieldDeclaration node) {
        return false;
    }

    @Override
    public boolean visit(Initializer node) {
        return false;
    }

    @Override
    public boolean visit(EnumConstantDeclaration node) {
        return false;
    }

    /**Handling a type: extraction into the model*/
    private void handleType(AbstractTypeDeclaration node, String kind) {
        ITypeBinding binding = node.resolveBinding();
        String packageName = packageName(node);
        String name = binding != null ? JdtNames.typeName(binding) : astQualifiedName(node, packageName);
        boolean inInterface = kind.equals("interface");

        List<FieldFact> fields = new ArrayList<>();
        List<MethodFact> methods = new ArrayList<>();
        for (Object member : node.bodyDeclarations()) {
            if (member instanceof FieldDeclaration field) {
                String visibility = visibility(field.getModifiers(), inInterface);
                for (Object o : field.fragments()) {
                    VariableDeclarationFragment fragment = (VariableDeclarationFragment) o;
                    fields.add(new FieldFact(fragment.getName().getIdentifier(), fieldType(field, fragment), visibility));
                }
            } else if (member instanceof MethodDeclaration method) {
                methods.add(methodFact(method, name));
            }
            // Member types: visited separately by their own visit. Initializer: not counted.
        }

        facts.addType(new TypeFact(name, packageName, kind, superclasses(node, binding), interfaces(binding), fields, methods));
    }
    private static MethodFact methodFact(MethodDeclaration method, String typeName) {
        String name = method.getName().getIdentifier();
        List<String> parameterTypes = parameterTypes(method);
        // Same rule as the call targets of CallVisitor (JdtNames), so that calls and methods match in B3.
        String id = JdtNames.methodId(typeName, name, parameterTypes);
        return new MethodFact(id, name, parameterTypes, method.isConstructor(), bodyLines(method));
    }

    private static int bodyLines(MethodDeclaration method) {
        Block body = method.getBody();
        if (body == null) {
            return 0; // abstract or interface method
        }
        CompilationUnit unit = (CompilationUnit) method.getRoot();
        int first = unit.getLineNumber(body.getStartPosition());
        int last = unit.getLineNumber(body.getStartPosition() + body.getLength() - 1);
        return last - first + 1;
    }


    private static List<String> superclasses(AbstractTypeDeclaration node, ITypeBinding binding) {
        List<String> chain = new ArrayList<>();
        if (binding == null) {
            //Binding null => only the direct written superclass is known
            if (node instanceof TypeDeclaration type && type.getSuperclassType() != null) {
                chain.add(type.getSuperclassType().toString());
            }
            return chain;
        }
        for (ITypeBinding s = binding.getSuperclass(); s != null; s = s.getSuperclass()) {
            String superName = JdtNames.typeName(s);
            if (superName.equals("java.lang.Object")) {
                break;
            }
            chain.add(superName);
        }
        return chain;
    }

    private static List<String> interfaces(ITypeBinding binding) {
        List<String> names = new ArrayList<>();
        if (binding != null) {
            for (ITypeBinding i : binding.getInterfaces()) {
                names.add(JdtNames.typeName(i));
            }
        }
        return names;
    }

    private static String fieldType(FieldDeclaration field, VariableDeclarationFragment fragment) {
        IVariableBinding variable = fragment.resolveBinding();
        return variable != null
                ? variable.getType().getQualifiedName()
                : field.getType().toString() + "[]".repeat(fragment.getExtraDimensions());
    }

    private static String visibility(int modifiers, boolean inInterface) {
        if (Modifier.isPublic(modifiers)) {
            return "public";
        }
        if (Modifier.isProtected(modifiers)) {
            return "protected";
        }
        if (Modifier.isPrivate(modifiers)) {
            return "private";
        }
        return inInterface ? "public_implicite" : "package";
    }

    /**Parameter types: qualified names after erasure, or the source text when no binding*/
    private static List<String> parameterTypes(MethodDeclaration method) {
        IMethodBinding binding = method.resolveBinding();
        if (binding != null) {
            return JdtNames.parameterTypes(binding);
        }
        List<String> names = new ArrayList<>();
        for (Object o : method.parameters()) {
            SingleVariableDeclaration p = (SingleVariableDeclaration) o;
            String type = p.getType().toString() + "[]".repeat(p.getExtraDimensions());
            names.add(p.isVarargs() ? type + "[]" : type);
        }
        return names;
    }

    private static String packageName(ASTNode node) {
        CompilationUnit unit = (CompilationUnit) node.getRoot();
        return unit.getPackage() == null ? DEFAULT_PACKAGE : unit.getPackage().getName().getFullyQualifiedName();
    }

    /**Qualified name rebuilt from the AST when binding is missing */
    private static String astQualifiedName(AbstractTypeDeclaration node, String packageName) {
        StringBuilder name = new StringBuilder(node.getName().getIdentifier());
        for (ASTNode p = node.getParent(); p != null; p = p.getParent()) {
            if (p instanceof AbstractTypeDeclaration enclosing) {
                name.insert(0, enclosing.getName().getIdentifier() + ".");
            }
        }
        return packageName.equals(DEFAULT_PACKAGE) ? name.toString() : packageName + "." + name;
    }

    /** Loca l class: declared as a statement inside a method body (not counted)*/
    private static boolean isLocal(AbstractTypeDeclaration node) {
        return node.getParent() instanceof TypeDeclarationStatement;
    }

}
