package hai913i.tp1.extract;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;


public class CallVisitor extends ASTVisitor {

    public record CallInfo(String caller, String name, String receiver, String target, int line, String status) {
    }

    private final PrintStream out;
    private final List<CallInfo> calls = new ArrayList<>();

    private int methodInvocations;
    private int superInvocations;
    private int internal;
    private int external;
    private int unresolved;

    private MethodDeclaration currentMethod;
    private String currentCaller;
    private String currentTypeName;
    private String currentSuperName;

    public CallVisitor(PrintStream out) {
        this.out = out;
    }

    @Override
    public boolean visit(MethodDeclaration node) {
        if (currentMethod == null && node.getParent() instanceof AbstractTypeDeclaration) {
            AbstractTypeDeclaration type = (AbstractTypeDeclaration) node.getParent();
            currentMethod = node;
            ITypeBinding typeBinding = type.resolveBinding();
            currentTypeName = typeBinding != null ? JdtNames.typeName(typeBinding) : "?";
            currentSuperName = typeBinding != null && typeBinding.getSuperclass() != null
                    ? JdtNames.typeName(typeBinding.getSuperclass()) : "?";
            IMethodBinding binding = node.resolveBinding();
            currentCaller = binding != null
                    ? JdtNames.methodId(binding)
                    : type.getName().getIdentifier() + "#" + node.getName().getIdentifier();
            out.println();
            out.println(currentCaller);
        }
        return true;
    }

    @Override
    public void endVisit(MethodDeclaration node) {
        if (node == currentMethod) {
            currentMethod = null;
        }
    }

    @Override
    public boolean visit(MethodInvocation node) {
        if (currentMethod != null) {
            methodInvocations++;
            IMethodBinding target = node.resolveMethodBinding();
            String receiver;
            if (node.getExpression() != null) {
                ITypeBinding type = node.getExpression().resolveTypeBinding();
                receiver = type != null ? JdtNames.typeName(type) : "?";
            } else if (target != null && Modifier.isStatic(target.getModifiers())) {
                receiver = JdtNames.typeName(target.getDeclaringClass());
            } else {
                receiver = currentTypeName;
            }
            addCall(node.getName(), receiver, target);
        }
        return true; // in case the arguments contain other calls
    }

    @Override
    public boolean visit(SuperMethodInvocation node) {
        if (currentMethod != null) {
            superInvocations++;
            addCall(node.getName(), currentSuperName, node.resolveMethodBinding());
        }
        return true;
    }

    private void addCall(SimpleName name, String receiver, IMethodBinding target) {
        CompilationUnit unit = (CompilationUnit) name.getRoot();
        int line = unit.getLineNumber(name.getStartPosition());

        String targetId = null;
        String status;
        if (target == null) {
            status = "non resolu";
            unresolved++;
        } else {
            targetId = JdtNames.methodId(target);
            if (target.getDeclaringClass().isFromSource()) {
                status = "interne";
                internal++;
            } else {
                status = "externe";
                external++;
            }
        }
        calls.add(new CallInfo(currentCaller, name.getIdentifier(), receiver, targetId, line, status));
        out.println("    ligne " + line + " : " + name.getIdentifier() + ", receveur " + receiver
                + ", cible " + (targetId == null ? "non resolue" : targetId + " (" + status + ")"));
    }

    public void printSummary() {
        out.println();
        out.println("------- Resume des appels");
        out.println("Appels      : " + calls.size() + " (" + methodInvocations + " MethodInvocation, " + superInvocations + " SuperMethodInvocation)");
        out.println("Internes: " + internal);
        out.println("Externes: " + external);
        out.println("Non resolus : " + unresolved);
    }

    //Access to results (for the checkpoint test)
    public List<CallInfo> calls() {
        return calls;
    }

    public int methodInvocationCount() {
        return methodInvocations;
    }

    public int superInvocationCount() {
        return superInvocations;
    }

    public int internalCount() {
        return internal;
    }

    public int externalCount() {
        return external;
    }

    public int unresolvedCount() {
        return unresolved;
    }
}
