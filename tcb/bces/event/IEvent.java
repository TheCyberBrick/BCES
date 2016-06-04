package tcb.bces.event;

/**
 * Any event implements this interface
 * 
 * @author TCB
 *
 */
public interface IEvent {
	/**
	 * Sets the context of this event
	 * @param context {@link Context} the context
	 * @return the instance this was called on
	 */
	public IEvent setContext(IContext context);

	/**
	 * Returns the casted context of this event if the type matches.
	 * Use null as parameter to get any context.
	 * @param type class of the context
	 * @return {@link Context} the context
	 */
	public <T extends IContext> T getContext(Class<T> type);
}
