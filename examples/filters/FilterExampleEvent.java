package examples.filters;

import tcb.bces.event.Event;

public class FilterExampleEvent extends Event {
	public final String id;
	public final boolean shouldPass;

	public FilterExampleEvent(String id, boolean shouldPass) {
		this.id = id;
		this.shouldPass = shouldPass;
	}
}
