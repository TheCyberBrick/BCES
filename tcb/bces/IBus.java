package tcb.bces;

import tcb.bces.event.IEvent;
import tcb.bces.event.IEventCancellable;

/**
 * Every bus implements this interface
 * 
 * @author TCB
 *
 */
public interface IBus {
	/**
	 * Posts an event and returns the posted event. This method will not be interrupted if an event is cancelled.
	 * This method should be used if performance is a big concern and cancellable events are not needed.
	 * @param event IEvent
	 * @return IEvent
	 */
	public <T extends IEvent> T postEvent(T event);
	
	/**
	 * Posts a cancellable event and returns the posted event. This method will be interrupted if an event is cancelled.
	 * @param event IEventCancellable
	 * @return IEventCancellable
	 */
	public <T extends IEventCancellable> T postEventCancellable(T event);
	
	/**
	 * Returns a new instance of this bus with the same
	 * properties.
	 * Used for {@link MultiEventBus} to create copies of
	 * the specified bus.
	 * @return IBus
	 */
	public IBus copyBus();
}
