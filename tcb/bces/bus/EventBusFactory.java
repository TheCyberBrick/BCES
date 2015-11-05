package tcb.bces.bus;

import tcb.bces.bus.async.DRCAsyncEventBus;
import tcb.bces.bus.async.feedback.IFeedbackHandler;
import tcb.bces.event.Event;
import tcb.bces.listener.IListener;

/**
 * The bus factory class lets the user easily create a simple event bus
 * 
 * @author TCB
 *
 */
public class EventBusFactory {
	public static final class EventBus implements IEventBus, ICompilableBus {
		private final IEventBus wrappedBus;

		private EventBus(IEventBus wrappedBus) {
			this.wrappedBus = wrappedBus;
		}

		@Override
		public void register(IListener listener) {
			this.wrappedBus.register(listener);
		}

		@Override
		public void unregister(IListener listener) {
			this.wrappedBus.unregister(listener);
		}

		@Override
		public <T extends Event> T post(T event) {
			return this.wrappedBus.post(event);
		}

		@Override
		public final void bind() {
			if(this.wrappedBus instanceof ICompilableBus) {
				((ICompilableBus)this.wrappedBus).bind();
			}
		}
	}

	/**
	 * Creates a new DRC event bus
	 * 
	 * @return
	 */
	public static EventBus createDRCEventBus() {
		return new EventBus(new DRCExpander<DRCEventBus>(new DRCEventBus()));
	}

	/**
	 * Creates a new mapped event bus
	 * 
	 * @return
	 */
	public static EventBus createMapEventBus() {
		return new EventBus(new MappedEventBus());
	}

	/**
	 * Creates a new asynchronous event bus
	 * 
	 * @param threads Dispatcher threads
	 * @param feedbackHandler Feedback handler (can be null if not required)
	 * @return
	 */
	public static EventBus createAsyncEventBus(int threads, IFeedbackHandler feedbackHandler) {
		return new EventBus(new DRCAsyncEventBus(threads, false).setFeedbackHandler(feedbackHandler));
	}
}
