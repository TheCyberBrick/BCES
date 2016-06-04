package examples.dispatchers;

import tcb.bces.bus.compilation.Dispatcher;
import tcb.bces.event.Event;
import tcb.bces.listener.IListener;
import tcb.bces.listener.filter.IFilter;

public class ExampleDispatcher extends Dispatcher {

	@Override
	public <T extends Event> T dispatchEvent(T event) {
		System.out.println(String.format("Called dispatcher for event %s", event));

		boolean shouldDispatch = false;
		
		if(event instanceof DispatcherExampleEvent) {
			DispatcherExampleEvent exampleEvent = (DispatcherExampleEvent) event;

			if("#1".equals(exampleEvent.id)) {
				System.out.println(String.format("Event with ID %s will be dispatched", exampleEvent.id));
				shouldDispatch = true;
			} else {
				System.out.println(String.format("Event with ID %s won't be dispatched", exampleEvent.id));
			}

			//Only dispatches events with the id "#1".
			//This can for example be used for premature filtering that affects all listeners
			//or for debugging and logging purposes etc.
		} else {
			//We don't care about this event
			shouldDispatch = true;
		}

		//Run the default dispatcher if the event should be dispatched to the listeners
		//The method Dispatcher#dispatch() can only be implemented once and inside the method dispatchEvent!
		if(shouldDispatch)
			Dispatcher.dispatch();

		//This is NOT allowed and will crash with a DispatcherException!
		/*if(someCondition) {
			//Do something
			Dispatcher.dispatch();
		} else {
			//Do something else
			Dispatcher.dispatch();
		}*/

		return event;
	}

	@Override
	public void init(IListener[] listenerArray, IFilter[] filterArray) {
		super.init(listenerArray, filterArray);
		//The dispatcher can be initialized here.
		//Make sure to call super.init(listenerArray, filterArray); if you use the default dispatching (see Dispatcher#dispatch())
	}
}
