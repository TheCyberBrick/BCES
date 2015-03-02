package tcb.bces.event.impl;

import tcb.bces.event.IEvent;

/**
 * This event is posted when an exception is caused by a listening method.
 * 
 * @author TCB
 *
 */
public class EventException implements IEvent {
	private final Exception exception;
	private final IEvent event;

	public EventException(Exception ex, IEvent event) {
		this.exception = ex;
		this.event = event;
	}

	public Exception getException() {
		return this.exception;
	}

	public IEvent getPostedEvent() {
		return this.event;
	}
}