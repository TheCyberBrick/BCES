package tcb.bces;

import tcb.bces.event.IEvent;
import tcb.bces.event.IEventCancellable;

/**
 * This stub interface is used for the internal stub in {@link EventBus}.
 * The stub itself has no functionality until it is compiled by the event
 * bus and obtains the functionality to dispatch events.
 * 
 * @author TCB
 *
 */
public interface IMethodStub {
	/**
	 * Distributes the event to all registered listeners that use that event.
	 * @param event IEventCancellable
	 * @return IEvent
	 */
	IEvent postEventInternal(IEvent event);

	/**
	 * Distributes the event to all registered listeners that use that event.
	 * @param event IEventCancellable
	 * @return IEvent
	 */
	IEvent postEventInternalCancellable(IEventCancellable event);
}
