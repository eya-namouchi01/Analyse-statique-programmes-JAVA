package hai913i.tp1.model;

import java.util.List;

/** A counted type: class, interface, enumeration or record */
public record TypeFact(String name, String packageName, String kind, List<String> superclasses,
        List<String> interfaces, List<FieldFact> fields, List<MethodFact> methods) {
}
