package hai913i.tp1.extract;

import hai913i.tp1.model.CallFact;
import hai913i.tp1.model.ProjectFacts;
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
    private final ProjectFacts facts;

    private MethodDeclaration currentMethod;
    private String currentCaller;
    private String currentTypeName;
    private String currentSuperName;

    public CallVisitor(ProjectFacts facts) {
        this.facts = facts;
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
            addCall(node.getName(), receiver, target, false);
        }
        return true; // in case the arguments contain other calls
    }

    @Override
    public boolean visit(SuperMethodInvocation node) {
        if (currentMethod != null) {
            addCall(node.getName(), currentSuperName, node.resolveMethodBinding(), true);
        }
        return true;
    }

    private void addCall(SimpleName name, String receiver, IMethodBinding target, boolean superCall) {
        CompilationUnit unit = (CompilationUnit) name.getRoot();
        int line = unit.getLineNumber(name.getStartPosition());

        String targetId = null;
        String status;
        if (target == null) {
            status = ProjectFacts.UNRESOLVED; //library missing from the classpath
        } else {
            targetId = JdtNames.methodId(target);
            status = target.getDeclaringClass().isFromSource() ? ProjectFacts.INTERNAL : ProjectFacts.EXTERNAL;
        }
        facts.addCall(new CallFact(currentCaller, name.getIdentifier(), receiver, targetId, line, status, superCall));
    }
}
