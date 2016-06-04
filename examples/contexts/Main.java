package examples.contexts;

import tcb.bces.bus.EventBusFactory;
import tcb.bces.bus.EventBusFactory.WrappedEventBus;

public class Main {
	public static void main(String[] args) {
		WrappedEventBus bus = EventBusFactory.createDRCEventBus();

		ContextExampleListener listener = new ContextExampleListener();

		bus.register(listener);
		bus.bind();

		ContextExampleEvent event1 = new ContextExampleEvent("#1");
		ContextExampleEvent event2 = new ContextExampleEvent("#2");

		event1.setContext(new ExampleContext("Context 1"));
		event2.setContext(new ExampleContext("Context 2"));

		bus.post(event1);
		bus.post(event2);
		
		event1.setContext(new ExampleContext("Context 3"));
		event2.setContext(new ExampleContext("Context 4"));
		
		bus.post(event1);
		bus.post(event2);
	}
}
