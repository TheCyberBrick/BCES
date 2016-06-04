package examples.filters;

import tcb.bces.bus.MethodContext;
import tcb.bces.event.Event;
import tcb.bces.listener.filter.IFilter;

public class ExampleFilter implements IFilter {
	@Override
	public void init(MethodContext context) {
		//This method can be used to initialize the filter
		System.out.println(String.format("Initialized filter for %s#%s()", context.getEventClass().getName(), context.getMethod().getName()));
	}

	@Override
	public boolean filter(Event event) {
		//Only let the event through if shouldPass is true
		return ((FilterExampleEvent)event).shouldPass;
	}
}
