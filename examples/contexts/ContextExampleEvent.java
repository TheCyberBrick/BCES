package examples.contexts;

import tcb.bces.event.Event;

public class ContextExampleEvent extends Event {
	public final String id;

	public ContextExampleEvent(String id) {
		this.id = id;
	}
}
