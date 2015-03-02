package tcb.bces.event.impl;

import tcb.bces.event.IEventCancellable;

/**
 * A basic implementation of {@link IEventCancellable}.
 * 
 * @author TCB
 *
 */
public abstract class EventCancellable implements IEventCancellable {
	private boolean isCancelled = false;
	public void setCancelled() {
		this.isCancelled = true;
	}
	public void setCancelled(boolean cancelled) {
		this.isCancelled = cancelled;
	}
	public boolean isCancelled() {
		return this.isCancelled;
	}
}