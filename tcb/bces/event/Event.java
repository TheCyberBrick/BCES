package tcb.bces.event;

/**
 * An abstract event class
 * 
 * @author TCB
 *
 */
public abstract class Event implements IEvent { 
	private IContext context = null;

	@Override
	public final Event setContext(IContext context) {
		this.context = context;
		return this;
	}

	@Override
	@SuppressWarnings("unchecked")
	public final <T extends IContext> T getContext(Class<T> type) {
		if(type == null) return (T) this.context;
		if(this.context != null && this.context.getClass() == type) {
			return (T) this.context;
		}
		return null;
	}

	/**
	 * Returns the context of this event
	 * @return {@link Context} the context
	 */
	public final IContext getContext() {
		return this.context;
	}
}