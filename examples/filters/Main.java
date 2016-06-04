package examples.filters;

import tcb.bces.bus.EventBusFactory;
import tcb.bces.bus.EventBusFactory.WrappedEventBus;

public class Main {
	public static void main(String[] args) {
		WrappedEventBus bus = EventBusFactory.createDRCEventBus();

		FilterExampleListener listener = new FilterExampleListener();

		bus.register(listener);
		bus.bind();

		FilterExampleEvent eventSuccessful = new FilterExampleEvent("#1", true); 	//This event passes the filter
		FilterExampleEvent eventFail = new FilterExampleEvent("#2", false); 		//This event is stopped by the filter
		
		bus.post(eventSuccessful); 	//passes
		bus.post(eventFail); 		//fails
		bus.post(eventSuccessful); 	//passes
		bus.post(eventFail); 		//fails
	}
}
