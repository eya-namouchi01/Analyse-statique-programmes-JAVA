package hai913i.tp1.model;

import java.util.List;

/**
 * A method or constructor written in a counted type:
 * id : unique identifier
 * parameterTypes : qualified names after erasure
 * bodyLines : physical lines of the body
 */
public record MethodFact(String id, String name, List<String> parameterTypes, boolean constructor, int bodyLines) {
}
