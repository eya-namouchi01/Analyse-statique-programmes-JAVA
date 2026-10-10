package hai913i.tp1.report;

import hai913i.tp1.model.*;

import java.io.PrintStream;

/** Prints the structure (A2) and the calls (A3). Only reads the fact model*/
public final class FactsPrinter {

    private FactsPrinter() {
    }

    public static void printStructure(ProjectFacts facts, PrintStream out) {
        out.println("" + "-------Structure");
        int classes = 0;
        int interfaces = 0;
        int enums = 0;
        int records = 0;
        int methods = 0;
        int constructors = 0;
        int fields = 0;

        for (TypeFact type : facts.types()) {
            out.println();
            out.println(type.name() + "  [" + type.kind() + ", paquetage " + type.packageName() + "]");
            out.println("  superclasses : " + listOrNone(type.superclasses()));
            out.println("  interfaces : " + listOrNone(type.interfaces()));
            out.println("  attributs (" + type.fields().size() + ")");
            for (FieldFact field : type.fields()) {
                out.println("    " + field.visibility() + " " + field.type() + " " + field.name());
            }
            out.println("  methodes (" + type.methods().size() + ")");
            for (MethodFact method : type.methods()) {
                String label = method.constructor() ? "constructeur" : "methode";
                out.println("    " + label + " " + method.name() + "(" + String.join(", ", method.parameterTypes())
                        + ") : " + method.parameterTypes().size() + " parametre(s)");
                if (method.constructor()) {
                    constructors++;
                }
            }

            switch (type.kind()) {
                case "classe" -> classes++;
                case "interface" -> interfaces++;
                case "enumeration" -> enums++;
                default -> records++;
            }
            methods += type.methods().size();
            fields += type.fields().size();
        }

        out.println();
        out.println("-------Resume de la structure");
        out.println("Types : " + facts.types().size() + " (" + classes + " classes, " + interfaces
                + " interfaces, " + enums + " enumerations, " + records + " records)");
        out.println("Methodes " + methods + " dont " + constructors + " constructeurs");
        out.println("Attributs : " + fields);
    }

    public static void printCalls(ProjectFacts facts, PrintStream out) {
        out.println("------- Appels");
        int superCalls = 0;
        int internal = 0;
        int external = 0;
        int unresolved = 0;
        String previousCaller = "";

        for (CallFact call : facts.calls()) {
            if (!call.callerId().equals(previousCaller)) {
                out.println();
                out.println(call.callerId());
                previousCaller = call.callerId();
            }
            String target = call.targetId() == null ? "non resolue" : call.targetId() + " (" + call.status() + ")";
            out.println("    ligne " + call.line() + " : " + call.name() + ", receveur " + call.receiver()
                    + ", cible " + target);

            if (call.superCall()) {
                superCalls++;
            }
            if (call.status().equals(ProjectFacts.INTERNAL)) {
                internal++;
            } else if (call.status().equals(ProjectFacts.EXTERNAL)) {
                external++;
            } else {
                unresolved++;
            }
        }

        int total = facts.calls().size();
        out.println();
        out.println("------- Resume des appels");
        out.println("Appels: " + total + " (" + (total - superCalls) + " MethodInvocation, " + superCalls + " SuperMethodInvocation)");
        out.println("Internes : " + internal);
        out.println("Externes : " + external);
        out.println("Non resolus : " + unresolved);
    }

    private static String listOrNone(java.util.List<String> names) {
        return names.isEmpty() ? "(aucune)" : String.join(", ", names);
    }
}
