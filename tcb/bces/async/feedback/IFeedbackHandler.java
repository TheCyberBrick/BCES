package tcb.bces.async.feedback;

import tcb.bces.async.AsyncEventBus;
import tcb.bces.event.IEvent;

/**
 * This feedback handler makes it possible to retrieve the result of an event post posted
 * by an {@link AsyncEventBus}.
 * 
 * @author TCB
 *
 */
public interface IFeedbackHandler {
	/**
	 * This method is called after an event has been posted asynchronously by
	 * an {@link AsyncEventBus}.
	 * @param event IEvent
	 */
	public void handleFeedback(IEvent event);
}
