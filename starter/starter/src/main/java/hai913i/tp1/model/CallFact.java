package hai913i.tp1.model;

/**
 * A call found in the body of a counted method
 *
 * callerId : identifier of the calling method
 * receiver : static type of the receiver
 * targetId : identifier of the resolved target, or null when the call is unresolved
 * status : INTERNAL/EXTERNAL/UNRESOLVED
 * superCall true for super.m()
 */
public record CallFact(String callerId, String name, String receiver, String targetId, int line, String status, boolean superCall) {
}
