package tcb.bces.bus;

import tcb.bces.event.Event;
import tcb.bces.listener.IListener;

/**
 * Any event bus implements this interface
 * 
 * @author TCB
 *
 */
public interface IEventBus {
	/**
	 * Registers an {@link IListener} to this bus
	 */
	public void register(IListener listener);
	
	/**
	 * Unregisters an {@link IListener} from this bus
	 * @param listener {@link IListener} to unregister
	 */
	public void unregister(IListener listener);
	
	/**
	 * Posts an {@link Event} and returns the posted event.
	 * @param event {@link Event} to dispatch
	 * @return {@link Event} the posted event
	 */
	public <T extends Event> T post(T event);

	/**
	 * Returns a new instance of this {@link EventBus} with the same
	 * properties.
	 * Used in {@link MultiEventBus} to create copies of
	 * the given bus.
	 * @return {@link IEventBus}
	 */
	public IEventBus copyBus();
}
