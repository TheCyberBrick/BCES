package examples.contexts;

import tcb.bces.listener.IListener;
import tcb.bces.listener.Subscribe;

public class ContextExampleListener implements IListener {
	@Override
	public boolean isEnabled() {
		return true;
	}

	@Subscribe
	public void onEvent(ContextExampleEvent event) {
		//Retrieve the context
		ExampleContext context = event.getContext(ExampleContext.class);

		//Make sure the context isn't null or of some other type
		if(context != null) {
			//Print out someContextualString of the context
			System.out.println(String.format("Event %s: %s", event.id, context.someContextualString));
		}
	}
}
