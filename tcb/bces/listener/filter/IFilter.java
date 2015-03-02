package tcb.bces.listener.filter;

import tcb.bces.EventBus.MethodEntry;
import tcb.bces.event.IEvent;
import tcb.bces.listener.Subscribe;

/**
 * This interface must be implemented in order to create a functional and valid filter.
 * 
 * @author TCB
 *
 */
public interface IFilter {
	/**
	 * This method is called after the IFilter constructor has
	 * been called. Only called if the filter has been set through
	 * {@link Subscribe#filter()}.
	 * @param entry MethodEntry
	 */
	public void init(MethodEntry entry);
	
	/**
	 * This method filters the given event for the given listener.
	 * Return false to cancel the event post for the given
	 * specific listening method. This does not cancel the event itself.
	 * @param event IEvent
	 * @return Boolean
	 */
	public boolean filter(IEvent event);
}
