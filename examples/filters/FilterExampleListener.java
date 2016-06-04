package examples.filters;

import tcb.bces.listener.IListener;
import tcb.bces.listener.Subscribe;

public class FilterExampleListener implements IListener {
	@Override
	public boolean isEnabled() {
		return true;
	}

	//Set the filter for this method
	@Subscribe(filter = ExampleFilter.class)
	public void onEvent(FilterExampleEvent event) {
		//The FilterExampleEvent should now only pass through if FilterExampleEvent#shouldPass is true
		System.out.println(String.format("Event %s passed!", event.id));
	}
}
