package examples.contexts;

import tcb.bces.event.IContext;

public class ExampleContext implements IContext {
	public final String someContextualString;

	public ExampleContext(String someString) {
		this.someContextualString = someString;
	}
}
