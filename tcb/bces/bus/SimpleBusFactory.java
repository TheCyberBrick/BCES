package tcb.bces.bus;

import tcb.bces.event.Event;
import tcb.bces.listener.IListener;

/**
 * The bus factory class lets the user easily create a simple event bus
 * 
 * @author TCB
 *
 */
public class SimpleBusFactory {
	public static final class SimpleEventBus implements IEventBus, ICompilableBus {
		private final IEventBus wrappedBus;

		private SimpleEventBus(IEventBus wrappedBus) {
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
	 * Creates a simple new event bus
	 * 
	 * @return
	 */
	public static SimpleEventBus createSimpleEventBus() {
		return new SimpleEventBus(new DRCExpander<DRCEventBus>(new DRCEventBus()));
	}
}
