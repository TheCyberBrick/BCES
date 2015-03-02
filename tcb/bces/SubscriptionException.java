package tcb.bces;

/**
 * This exception if thrown if something during the registration of a listener or
 * a subscribed method goes wrong.
 * 
 * @author TCB
 *
 */
public class SubscriptionException extends Exception {
	private final Exception cause;
	public SubscriptionException(String msg) {
		super(msg);
		this.cause = null;
	}
	public SubscriptionException(String msg, Exception cause) {
		super(msg);
		this.cause = cause;
	}
	public Exception getCause() {
		return this.cause;
	}
	private static final long serialVersionUID = 1L;
}
