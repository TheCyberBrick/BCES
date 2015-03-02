package tcb.bces.async;

import java.lang.Thread.State;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

import tcb.bces.EventBus;
import tcb.bces.IBus;
import tcb.bces.MultiEventBus;
import tcb.bces.async.feedback.IFeedbackHandler;
import tcb.bces.event.IEvent;
import tcb.bces.event.IEventCancellable;

/**
 * This event bus allows asynchronous event posting. It still has all the features of
 * {@link EventBus}, but the events are dispatched asynchronously. Posting an event adds
 * it to a queue which is dispatched by the specified amount of dispatchers/threads.
 * The event post result can be retrieved by setting a custom {@link IFeedbackHandler}.
 * If {@link MultiEventBus} is used to wrap this bus, the feedback handler has to be set
 * before this bus is wrapped in order for it to work correctly.
 * 
 * @author TCB
 *
 */
public class AsyncEventBus extends EventBus {
	protected final BlockingQueue<IEvent> eventQueue = new LinkedBlockingDeque<IEvent>();
	protected final BlockingQueue<IEventCancellable> eventQueueCancellable = new LinkedBlockingDeque<IEventCancellable>();
	protected IFeedbackHandler feedbackHandler = null;
	private final ArrayList<Dispatcher> dispatchers = new ArrayList<Dispatcher>();
	private final ArrayList<Dispatcher> sleepers = new ArrayList<Dispatcher>();
	private final int cthreads;
	private final boolean manualDispatcherManagement;

	/**
	 * The default asynchronous event bus has a limit of {@link EventBus#MAX_METHODS} listening methods. 
	 * If you want to add more listening methods use {@link EventBusManager} instead.
	 * The amount of dispatchers/threads can be specified.
	 * The dispatchers will go to sleep automatically after {@link Dispatcher#THREAD_SLEEP_DELAY} and
	 * they will be notified if an event is posted.
	 * @param threads Integer
	 */
	public AsyncEventBus(int threads) {
		this.cthreads = threads;
		this.manualDispatcherManagement = false;
		for(int i = 0; i < threads; i++) {
			Dispatcher dispatcher = new Dispatcher(this, new EventBus());
			this.dispatchers.add(dispatcher);
		}
	}

	/**
	 * The default asynchronous event bus has a limit of {@link EventBus#MAX_METHODS} listening methods. 
	 * If you want to add more listening methods use {@link EventBusManager} instead.
	 * The amount of dispatchers/threads can be specified. The dispatchers can be managed
	 * manually by setting manualDispatcherManagement to true. Dispatchers don't go to
	 * sleep if management is set to manual and won't be notified if an event is posted.
	 * @param threads Integer
	 * @param manualDispatcherManagement Boolean
	 */
	public AsyncEventBus(int threads, boolean manualDispatcherManagement) {
		this.cthreads = threads;
		this.manualDispatcherManagement = manualDispatcherManagement;
		for(int i = 0; i < threads; i++) {
			Dispatcher dispatcher = new Dispatcher(this, new EventBus());
			this.dispatchers.add(dispatcher);
		}
	}

	/**
	 * Only used for {@link IBus#copyBus()}.
	 * Shares the threads with the parent bus.
	 * @param threads Integer
	 * @param dispatchers ArrayList<Dispatcher>
	 * @param sleepers ArrayList<Dispatcher>
	 */
	private AsyncEventBus(int threads, boolean manualDispatcherManagement, IFeedbackHandler feedbackHandler) {
		this.cthreads = threads;
		this.manualDispatcherManagement = manualDispatcherManagement;
		this.feedbackHandler = feedbackHandler;
		for(int i = 0; i < threads; i++) {
			Dispatcher dispatcher = new Dispatcher(this, new EventBus());
			this.dispatchers.add(dispatcher);
		}
	}

	/**
	 * Returns the feedback handler of this bus.
	 * @return IFeedbackHandler
	 */
	public final IFeedbackHandler getFeedbackHandler() {
		return this.feedbackHandler;
	}

	/**
	 * Sets the feedback handler of this bus.
	 * @param feedbackHandler IFeedbackHandler
	 * @return AsyncEventBus
	 */
	public final AsyncEventBus setFeedbackHandler(IFeedbackHandler feedbackHandler) {
		this.feedbackHandler = feedbackHandler;
		return this;
	}

	/**
	 * Returns true if management is set to manual.
	 * @return Boolean
	 */
	public final boolean hasManualManagement() {
		return this.manualDispatcherManagement;
	}

	/**
	 * Returns a read-only list of the current dispatchers.
	 * @return List<Dispatcher> read-only
	 */
	public final List<Dispatcher> getDispatchers() {
		return Collections.unmodifiableList(this.dispatchers);
	}

	/**
	 * Used in {@link Dispatcher}.
	 * Adds a dispatcher to the sleeper list.
	 * @param dispatcher Dispatcher
	 */
	protected final void addToSleepers(Dispatcher dispatcher) {
		this.sleepers.add(dispatcher);
	}

	/**
	 * Used in {@link Dispatcher}.
	 * Removes a dispatcher from the sleeper list.
	 * @param dispatcher Dispatcher
	 */
	protected final void removeFromSleepers(Dispatcher dispatcher) {
		this.sleepers.remove(dispatcher);
	}

	/**
	 * Used in {@link Dispatcher}.
	 * Returns the read-only event queue.
	 * @return Collection<IEvent> read-only
	 */
	public final Collection<IEvent> getEventQueue() {
		return Collections.unmodifiableCollection(this.eventQueue);
	}

	/**
	 * Used in {@link Dispatcher}.
	 * Returns the read-only cancellable event queue.
	 * @return Collection<IEventCancellable> read-only
	 */
	public final Collection<IEventCancellable> getEventQueueCancellable() {
		return Collections.unmodifiableCollection(this.eventQueueCancellable);
	}

	/**
	 * Clears the event queue.
	 */
	public final void clearEventQueue() {
		this.eventQueue.clear();
	}

	/**
	 * Clears the cancellable event queue.
	 */
	public final void clearEventQueueCancellable() {
		this.eventQueueCancellable.clear();
	}

	@Override
	public <T extends IEvent> T postEvent(T event) {
		try {
			this.eventQueue.put(event);
			if(!this.manualDispatcherManagement && this.sleepers.size() > 0) {
				for(Dispatcher dispatcher : this.sleepers) {
					dispatcher.setSleeping(false);
				}
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return event;
	}

	@Override
	public <T extends IEventCancellable> T postEventCancellable(T event) {
		try {
			this.eventQueueCancellable.put(event);
			if(!this.manualDispatcherManagement && this.sleepers.size() > 0) {
				for(Dispatcher dispatcher : this.sleepers) {
					dispatcher.setSleeping(false);
				}
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return event;
	}

	/**
	 * Only used internally in {@link Dispatcher}
	 * @param event
	 * @return
	 */
	protected final <T extends IEvent> T postEventS(T event) {
		return super.postEvent(event);
	}

	/**
	 * Only used internally in {@link Dispatcher}
	 * @param event
	 * @return
	 */
	protected final <T extends IEventCancellable> T postEventCancellableS(T event) {
		return super.postEventCancellable(event);
	}

	/**
	 * Starts all dispatchers. Required for the events in
	 * the event queue to be dispatched.
	 */
	public final void startDispatchers() {
		try {
			for(Dispatcher dispatcher : this.dispatchers) {
				if(!dispatcher.isRunning() && dispatcher.getState() != State.TERMINATED) {
					dispatcher.startDispatcher();
				}
			}
			int threadsLeft = this.cthreads - this.dispatchers.size();
			for(int i = 0; i < threadsLeft; i++) {
				Dispatcher dispatcher = new Dispatcher(this, new EventBus());
				dispatcher.startDispatcher();
				this.dispatchers.add(dispatcher);
			}
		} catch(Exception ex) {
			ex.printStackTrace();
		}
	}

	/**
	 * Stops all dispatchers.
	 */
	public final void stopDispatchers() {
		try {
			for(Dispatcher dispatcher : this.dispatchers) {
				dispatcher.stopDispatcher();
			}
			this.dispatchers.clear();
			this.sleepers.clear();
		} catch(Exception ex) {
			ex.printStackTrace();
		}
	}

	@Override
	public void update() {
		for(Dispatcher dispatcher : this.dispatchers) {
			dispatcher.getDispatcherBus().clear();
			for(MethodEntry me : this.getMethodEntries()) {
				dispatcher.getDispatcherBus().addMethodEntry(me);
			}
			dispatcher.getDispatcherBus().update();
		}
	}

	@Override
	public IBus copyBus() {
		return new AsyncEventBus(this.cthreads, this.manualDispatcherManagement, this.feedbackHandler);
	}
}
