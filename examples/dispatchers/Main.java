package examples.dispatchers;

import tcb.bces.bus.DRCEventBus;
import tcb.bces.bus.DRCExpander;

public class Main {
	public static void main(String[] args) {
		//Create base bus
		DRCEventBus baseBus = new DRCEventBus();
		
		//Set custom dispatcher
		baseBus.setDispatcher(ExampleDispatcher.class);

		//Create expanded bus to allow registering more than DRCEventBus#MAX_METHODS method contexts
		DRCExpander<DRCEventBus> bus = new DRCExpander<DRCEventBus>(baseBus);

		DispatcherExampleListener listener = new DispatcherExampleListener();

		bus.register(listener);
		bus.bind();

		DispatcherExampleEvent event1 = new DispatcherExampleEvent("#1"); //will be received
		DispatcherExampleEvent event2 = new DispatcherExampleEvent("#2"); //won't be received

		bus.post(event1);
		bus.post(event2);
	}
}
