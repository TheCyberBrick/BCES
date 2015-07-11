package tcb.bces.listener.impl;

import tcb.bces.listener.IListener;

/**
 * An abstract listener class that implements {@link IListener}.
 * 
 * @author TCB
 *
 */
public class Listener implements IListener {
	/**
	 * Returns whether this listener should receive events.
	 * True by default.
	 * @return Boolean
	 */
	@Override
	public boolean isEnabled() {
		return true;
	}
}