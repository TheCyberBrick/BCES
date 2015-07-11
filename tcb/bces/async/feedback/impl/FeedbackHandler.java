package tcb.bces.async.feedback.impl;

import tcb.bces.async.feedback.IFeedbackHandler;
import tcb.bces.event.IEvent;

/**
 * An abstract feedback handler class that implements {@link IFeedbackHandler}.
 * 
 * @author TCB
 *
 */
public abstract class FeedbackHandler implements IFeedbackHandler {
	@Override
	public abstract void handleFeedback(IEvent event);
}
