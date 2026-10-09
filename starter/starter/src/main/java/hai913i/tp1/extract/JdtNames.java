package hai913i.tp1.extract;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;

/**Translates JDT bindings into stable textual names for the fact model*/
public final class JdtNames {

    private JdtNames() {
    }

    //Qualified name after erasure
    public static String typeName(ITypeBinding type) {
        return type.getErasure().getQualifiedName();
    }

    public static List<String> parameterTypes(IMethodBinding method) {
        List<String> names = new ArrayList<>();
        for (ITypeBinding parameter : method.getMethodDeclaration().getParameterTypes()) {
            names.add(typeName(parameter));
        }
        return names;
    }

    public static String methodId(IMethodBinding method) {
        IMethodBinding declaration = method.getMethodDeclaration();
        return methodId(typeName(declaration.getDeclaringClass()), declaration.getName(),
                parameterTypes(declaration));
    }
    public static String methodId(String typeName, String methodName, List<String> parameterTypes) {
        return typeName + "#" + methodName + "(" + String.join(",", parameterTypes) + ")";
    }


}
