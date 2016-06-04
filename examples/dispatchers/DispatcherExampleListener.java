package examples.dispatchers;

import tcb.bces.listener.IListener;
import tcb.bces.listener.Subscribe;

public class DispatcherExampleListener implements IListener {
	@Override
	public boolean isEnabled() {
		return true;
	}

	@Subscribe
	public void onEvent(DispatcherExampleEvent event) {
		System.out.println(String.format("Received event %s!", event.id));
	}
}
