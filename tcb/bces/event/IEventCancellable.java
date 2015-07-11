package tcb.bces.event;

/**
 * This interface must be implemented in order to create a functional and valid cancellable event.
 * 
 * @author TCB
 *
 */
public interface IEventCancellable extends IEvent {
	/**
	 * Sets event cancelled state to true
	 */
	void setCancelled();

	/**
	 * Sets event cancelled state
	 * @param cancelled Boolean
	 */
	void setCancelled(boolean cancelled);

	/**
	 * Returns whether this event has been cancelled
	 * @return Boolean
	 */
	boolean isCancelled();
}