package tcb.bces.async;

import tcb.bces.EventBus;
import tcb.bces.event.IEvent;
import tcb.bces.event.IEventCancellable;

/**
 * This dispatcher dispatches the events of a {@link AsyncEventBus} asynchronously.
 * The dispatcher automatically go to sleep after {@link Dispatcher#THREAD_SLEEP_DELAY}
 * if no event has been posted through the {@link AsyncEventBus} if management was not set
 * to manual.
 * 
 * @author TCB
 *
 */
public class Dispatcher extends Thread {
	private final EventBus dispatcherBus;
	private final AsyncEventBus bus;
	private boolean runDispatcher = false;
	private boolean sleeping = false;
	private long startTime;

	/**
	 * The delay until the thread goes to sleep automatically.
	 */
	public static final int THREAD_SLEEP_DELAY = 1000;

	/**
	 * Creates a new dispatcher.
	 * @param bus AsyncEventBus
	 * @param dispatcherBus EventBus
	 */
	protected Dispatcher(AsyncEventBus bus, EventBus dispatcherBus) {
		this.bus = bus;
		this.dispatcherBus = dispatcherBus;
	}

	/**
	 * Returns the dispatcher bus for this dispatcher.
	 * @return EventBus
	 */
	protected final EventBus getDispatcherBus() {
		return this.dispatcherBus;
	}
	
	@Override
	public void run() {
		this.startTime = System.currentTimeMillis();
		while(this.runDispatcher) {
			if(this.bus.getEventQueue().size() > 0) {
				try {
					IEvent event = this.bus.eventQueue.take();
					event = this.dispatcherBus.postEvent(event);
					if(this.bus.feedbackHandler != null) {
						synchronized(this.bus.feedbackHandler) {
							this.bus.feedbackHandler.handleFeedback(event);
						}
					}
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				this.startTime = System.currentTimeMillis();
			}
			if(this.bus.getEventQueueCancellable().size() > 0) {
				try {
					IEventCancellable event = this.bus.eventQueueCancellable.take();
					event = this.dispatcherBus.postEventCancellable(event);
					if(this.bus.feedbackHandler != null) {
						synchronized(this.bus.feedbackHandler) {
							this.bus.feedbackHandler.handleFeedback(event);
						}
					}
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				this.startTime = System.currentTimeMillis();
			}
			if(!this.bus.hasManualManagement() && this.runDispatcher &&
					this.bus.getEventQueue().size() == 0 &&
					this.bus.getEventQueueCancellable().size() == 0) {
				if(System.currentTimeMillis() - this.startTime >= THREAD_SLEEP_DELAY) {
					this.setSleeping(true);
				}
			}
		}
	}

	/**
	 * Returns whether this thread is currently sleeping.
	 * @return
	 */
	public synchronized boolean isSleeping() {
		return this.sleeping;
	}

	/**
	 * Sets the thread sleeping state.
	 * @param sleep
	 */
	public synchronized void setSleeping(boolean sleep) {
		if(sleep && !this.isSleeping()) {
			try {
				synchronized(this) {
					this.sleeping = true;
					this.bus.addToSleepers(this);
					this.wait();
					this.bus.removeFromSleepers(this);
					this.sleeping = false;
				}
			} catch(Exception ex) { 
				ex.printStackTrace();
			}
		} else if(!sleep && this.isSleeping()) {
			synchronized(this) {
				this.notify();
			}
		}
	}

	/**
	 * Returns whether this thread is currently running or if it has terminated already.
	 * @return Boolean
	 */
	public synchronized boolean isRunning() {
		return this.isAlive() && this.runDispatcher && this.getState() != State.TERMINATED;
	}

	/**
	 * Starts the dispatcher.
	 */
	public synchronized void startDispatcher() {
		this.runDispatcher = true;
		this.start();
		this.setSleeping(false);
	}

	/**
	 * Stops the dispatcher.
	 */
	public synchronized void stopDispatcher() {
		this.runDispatcher = false;
		this.setSleeping(false);
	}
}
