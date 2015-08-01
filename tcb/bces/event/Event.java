package tcb.bces.event;

/**
 * An abstract event class
 * 
 * @author TCB
 *
 */
public abstract class Event { 
	private Context context = null;

	/**
	 * Sets the context of this event
	 * @param context {@link Context} the context
	 * @return {@link Event}
	 */
	public final Event setContext(Context context) {
		if(this.context != null) {
			context.setContext(this.context);
		}
		this.context = context;
		return this;
	}

	/**
	 * Returns the context of this event
	 * @return {@link Context} the context
	 */
	public final Context getContext() {
		return this.context;
	}

	/**
	 * Returns the casted context of this event if the type matches
	 * @param type class of the context
	 * @return {@link Context} the context
	 */
	@SuppressWarnings("unchecked")
	public final <T extends Context> T getContext(Class<T> type) {
		if(this.context.getClass() == type) {
			return (T) this.context;
		}
		return null;
	}
}