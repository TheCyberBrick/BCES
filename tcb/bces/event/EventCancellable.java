package tcb.bces.event;


/**
 * An abstract cancellable event class
 * 
 * @author TCB
 *
 */
public abstract class EventCancellable extends Event {
	private boolean isCancelled = false;
	
	/**
	 * Sets this event to cancelled
	 */
	public void setCancelled() {
		this.isCancelled = true;
	}
	
	/**
	 * Sets the cancelled state of this event
	 * @param cancelled the cancelled state
	 */
	public void setCancelled(boolean cancelled) {
		this.isCancelled = cancelled;
	}
	
	/**
	 * Returns whether this event is cancelled
	 * @return cancelled
	 */
	public boolean isCancelled() {
		return this.isCancelled;
	}
}