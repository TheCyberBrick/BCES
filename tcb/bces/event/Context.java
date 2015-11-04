package tcb.bces.event;

import java.util.ArrayList;
import java.util.List;

/**
 * An abstract context class
 * 
 * @author TCB
 *
 */
public abstract class Context implements IContext {
	private Context parent = null;
	
	/**
	 * Sets the parent context
	 * @param parent Context
	 */
	protected final void setContext(Context parent) {
		this.parent = parent;
	}
	
	/**
	 * Returns the parent context
	 * @return {@link Context} parent context
	 */
	public final Context getContext() {
		return this.parent;
	}
	
	/**
	 * Returns a parent context of the specified type if any matches were found
	 * @param type class of the context
	 * @return matching context
	 */
	@SuppressWarnings("unchecked")
	public final <T extends IContext> T getContext(Class<T> type) {
		Context currentContext = this;
		while(currentContext != null) {
			if(currentContext.getClass() == type) return (T) currentContext;
			currentContext = this.parent;
		}
		return null;
	}
	
	/**
	 * Returns a list of all parent contexts of the specified type
	 * @param type class of the context
	 * @return list of all matching contexts
	 */
	@SuppressWarnings("unchecked")
	public final <T extends IContext> List<T> getContexts(Class<T> type) {
		List<T> foundContexts = new ArrayList<T>();
		Context currentContext = this;
		while(currentContext != null) {
			if(currentContext.getClass() == type) foundContexts.add((T) currentContext);
			currentContext = this.parent;
		}
		return foundContexts;
	}
}
