package hai913i.tp1.extract;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclarationStatement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

public class StructureVisitor extends ASTVisitor {

    private static final String DEFAULT_PACKAGE = "(défaut)";

    private final PrintStream out;

    //Checkpoint A2 result variables
    private final Map<String, Integer> typesByKind = new LinkedHashMap<>();
    private final Map<String, Integer> methodsByType = new LinkedHashMap<>();
    private final Map<String, Integer> fieldsByType = new LinkedHashMap<>();
    private final Map<String, List<String>> superclassesByType = new LinkedHashMap<>();
    private final Map<String, String> fieldVisibilities = new LinkedHashMap<>(); // "Type#attribut" -> visibilité
    private int constructorCount;

    public StructureVisitor(PrintStream out) {
        this.out = out;
        for (String kind : List.of("classe", "interface", "enumeration", "record")) {
            typesByKind.put(kind, 0);
        }
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

    // Pruning: the visitor only descends into member types.
    //Annotation types are not counted; the other nodes are pruned because their subtrees may contain local or anonymous classes (members are read in bodyDeclarations())
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

    /**Handling a type: extraction, then display*/
    private void handleType(AbstractTypeDeclaration node, String kind) {
        ITypeBinding binding = node.resolveBinding();
        String packageName = packageName(node);
        String name = binding != null ? JdtNames.typeName(binding) : astQualifiedName(node, packageName);
        boolean inInterface = kind.equals("interface");
        List<String> superclasses = superclasses(node, binding);
        List<String> interfaces = interfaces(binding);

        out.println();
        out.println(name + "  [" + kind + ", paquetage " + packageName + "]");
        out.println("  superclasses : " + listOrNone(superclasses));
        out.println("  interfaces: " + listOrNone(interfaces));

        List<String> fieldLines = new ArrayList<>();
        List<String> methodLines = new ArrayList<>();
        int constructors = 0;
        for (Object member : node.bodyDeclarations()) {
            if (member instanceof FieldDeclaration field) {
                String visibility = visibility(field.getModifiers(), inInterface);
                for (Object o : field.fragments()) {
                    VariableDeclarationFragment fragment = (VariableDeclarationFragment) o;
                    String fieldName = fragment.getName().getIdentifier();
                    fieldLines.add("    " + visibility + " " + fieldType(field, fragment) + " " + fieldName);
                    fieldVisibilities.put(name + "#" + fieldName, visibility);
                }
            } else if (member instanceof MethodDeclaration method) {
                List<String> parameterTypes = parameterTypes(method);
                String label = method.isConstructor() ? "constructeur" : "methode";
                methodLines.add("    " + label + " " + method.getName().getIdentifier()
                        + "(" + String.join(", ", parameterTypes) + ") : "
                        + parameterTypes.size() + " parametre(s)");
                if (method.isConstructor()) {
                    constructors++;
                }
            }
            // Member types: visited separately by their own visit. Initializer: not counted.
        }

        out.println("  attributs (" + fieldLines.size() + ")");
        fieldLines.forEach(out::println);
        out.println("  methodes (" + methodLines.size() + ")");
        methodLines.forEach(out::println);

        typesByKind.merge(kind, 1, Integer::sum);
        methodsByType.put(name, methodLines.size());
        fieldsByType.put(name, fieldLines.size());
        superclassesByType.put(name, superclasses);
        constructorCount += constructors;
    }

    /** Access to results (for the checkpoint test)*/
    public void printSummary() {
        out.println();
        out.println("-------Resume de la structure");
        out.println("Types : " + typeCount() + " (" + typesByKind.get("classe") + " classes, "
                + typesByKind.get("interface") + " interfaces, " + typesByKind.get("enumeration")
                + " enumerations, " + typesByKind.get("record") + " records)");
        out.println("Methodes  : " + methodCount() + " dont " + constructorCount + " constructeurs");
        out.println("Attributs : " + fieldCount());
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

    private static String listOrNone(List<String> names) {
        return names.isEmpty() ? "(aucune)" : String.join(", ", names);
    }

    public int typeCount() {
        return typesByKind.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int typeCount(String kind) {
        return typesByKind.getOrDefault(kind, 0);
    }

    public int methodCount() {
        return methodsByType.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int constructorCount() {
        return constructorCount;
    }

    public int fieldCount() {
        return fieldsByType.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int methodCount(String type) {
        return methodsByType.getOrDefault(type, -1);
    }

    public int fieldCount(String type) {
        return fieldsByType.getOrDefault(type, -1);
    }

    public List<String> superclasses(String type) {
        return superclassesByType.getOrDefault(type, List.of());
    }

    public String fieldVisibility(String type, String field) {
        return fieldVisibilities.get(type + "#" + field);
    }
}
