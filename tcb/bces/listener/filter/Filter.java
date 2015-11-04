package tcb.bces.listener.filter;

import tcb.bces.bus.DRCEventBus.MethodEntry;
import tcb.bces.event.Event;
import tcb.bces.listener.Subscribe;

/**
 * An abstract filter class
 * 
 * @author TCB
 *
 */
public abstract class Filter implements IFilter {
	private MethodEntry methodEntry;

	/**
	 * A no-arg constructor must be present if this filter is set through
	 * {@link Subscribe#filter()}.
	 */
	private Filter() {}

	/**
	 * Returns the method entry that uses this filter.
	 * @return MethodEntry
	 */
	public MethodEntry getMethodEntry() {
		return this.methodEntry;
	}

	/**
	 * This method is called after the IFilter constructor has
	 * been called. Only called if the filter has been set through
	 * {@link Subscribe#filter()}.
	 */
	protected void init() { }

	@Override
	public final void init(MethodEntry entry) {
		this.methodEntry = entry;
		this.init();
	}

	@Override
	public abstract boolean filter(Event event);
}
