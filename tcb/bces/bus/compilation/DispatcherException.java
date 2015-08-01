package tcb.bces.bus.compilation;

/**
 * This exception is thrown if something with the dispatcher goes wrong.
 * 
 * @author TCB
 *
 */
public class DispatcherException extends RuntimeException {
	public DispatcherException(String msg) {
		super(msg);
	}
	public DispatcherException(String msg, Exception cause) {
		super(msg, cause);
	}
	private static final long serialVersionUID = 1L;
}
