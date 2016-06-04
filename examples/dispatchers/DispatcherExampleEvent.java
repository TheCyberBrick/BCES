package examples.dispatchers;

import tcb.bces.event.Event;

public class DispatcherExampleEvent extends Event {
	public final String id;

	public DispatcherExampleEvent(String id) {
		this.id = id;
	}
}
