package tcb.bces;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import tcb.bces.EventBus.MethodEntry;
import tcb.bces.event.IEvent;
import tcb.bces.event.IEventCancellable;
import tcb.bces.listener.IListener;

/**
 * This bus allows a theoretical infinite amount of registered methods
 * while still keeping all the functionality of {@link EventBus}.
 * Any properties of the bus to wrap have to be set before the bus is wrapped.
 * 
 * @author TCB
 *
 */
public class MultiEventBus<B extends EventBus> implements IEventBus {
	private final ArrayList<IListener> registeredListeners = new ArrayList<IListener>();
	private final ArrayList<B> busCollection = new ArrayList<B>();
	private final HashMap<Class<? extends IEvent>, List<B>> busMap = new HashMap<Class<? extends IEvent>, List<B>>();
	private final HashMap<IListener, List<MethodEntry>> cachedMethodEntries = new HashMap<IListener, List<MethodEntry>>();
	private EventBus currentBus = null;
	private final int maxMethodEntriesPerBus;
	private boolean singleBus = true;
	private B busInstance;
	private Class<B> busType;

	/**
	 * Initializes a new {@link MultiEventBus} with a maximum method entry limit per bus of 100 method entries (recommended).
	 */
	@SuppressWarnings("unchecked")
	public MultiEventBus(B busInstance) {
		this.maxMethodEntriesPerBus = 100;
		this.busInstance = busInstance;
		this.busType = (Class<B>) busInstance.getClass();
	}

	/**
	 * Initializes a new {@link MultiEventBus} with a specified maximum method entry limit per bus.
	 * Maximum limit of method entries per bus is {@link EventBus#MAX_METHODS}
	 * @param maxMethodsPerBus Integer
	 */
	@SuppressWarnings("unchecked")
	public MultiEventBus(B busInstance, int maxMethodsPerBus) {
		if(maxMethodsPerBus > EventBus.MAX_METHODS) {
			maxMethodsPerBus = EventBus.MAX_METHODS;
		} else if(maxMethodsPerBus < 1) {
			maxMethodsPerBus = 1;
		}
		this.maxMethodEntriesPerBus = maxMethodsPerBus;
		this.busInstance = busInstance;
		this.busType = (Class<B>) busInstance.getClass();
	}

	/**
	 * Returns the bus type this {@link MultiEventBus} uses.
	 * @return
	 */
	public Class<B> getBusType() {
		return this.busType;
	}

	/**
	 * Adds a listener to the {@link MultiEventBus}. {@link MultiEventBus} has to be updated with {@link MultiEventBus#update()} 
	 * for the new listener to take effect.
	 * Returns a read-only list of all found and valid method entries.
	 * @param listener IListener
	 * @throws SubscriptionException
	 * @return List<MethodEntry> read-only
	 */
	public final List<MethodEntry> addListener(IListener listener) throws SubscriptionException {
		List<MethodEntry> entries = EventBus.analyzeListener(listener);
		this.cachedMethodEntries.put(listener, entries);
		this.registeredListeners.add(listener);
		return Collections.unmodifiableList(entries);
	}

	/**
	 * Removes a listener from the {@link MultiEventBus}. {@link MultiEventBus} has to be updated with {@link MultiEventBus#update()} for this to take effect.
	 * @param listener IListener
	 */
	public final void removeListener(IListener listener) {
		this.cachedMethodEntries.remove(listener);
		this.registeredListeners.remove(listener);
	}

	/**
	 * Updates the {@link MultiEventBus}. Required for new method entries to take effect.
	 * @throws IndexOutOfBoundsException
	 * @throws SubscriptionException
	 */
	public final void update() {
		this.busCollection.clear();
		this.singleBus = true;
		for(List<MethodEntry> mel : this.getSortedMethodEntries()) {
			@SuppressWarnings("unchecked")
			B bus = (B) this.busInstance.copyBus();
			/*bus = new EventBus() {
				@SuppressWarnings("unchecked")
				@Override
				protected boolean prePostEvent(IEvent event) {
					return MultiEventBus.this.prePostEvent(event, (B)this);
				}
				@SuppressWarnings("unchecked")
				@Override
				protected void postPostEvent(IEvent event) { 
					MultiEventBus.this.postPostEvent(event, (B)this);
				}
				@SuppressWarnings("unchecked")
				@Override
				protected boolean instrumentDistributor(ArrayList<AbstractInsnNode> baseInstructions, MethodNode methodNode) {
					return MultiEventBus.this.instrumentDistributor(baseInstructions, methodNode, (B)this);
				}
				@SuppressWarnings("unchecked")
				@Override
				protected void onException(Exception ex) { 
					MultiEventBus.this.onException(ex, (B)this);
				}
			};*/
			this.currentBus = bus;
			for(MethodEntry me : mel) {
				bus.addMethodEntry(me);
				List<B> busList = this.busMap.get(me.getEventClass());
				if(busList == null) {
					busList = new ArrayList<B>();
					this.busMap.put(me.getEventClass(), busList);
				}
				if(!busList.contains(bus)) busList.add(bus);
			}
			this.busCollection.add(bus);
		}
		for(EventBus bus : this.busCollection) {
			bus.update();
		}
	}

	/**
	 * Returns the read-only bus map. List of {@link EventBus} sorted by event class.
	 * @return read-only
	 */
	public final Map<Class<? extends IEvent>, List<B>> getBusMap() {
		return Collections.unmodifiableMap(this.busMap);
	}

	/**
	 * Returns a list of all registered method entries grouped by event type.
	 * @return List<List<MethodEntry>>
	 */
	private final List<List<MethodEntry>> getSortedMethodEntries() {
		List<List<MethodEntry>> entryList = new ArrayList<List<MethodEntry>>();
		for(IListener l : this.registeredListeners) {
			try {
				//Listener should already be validated and cached
				entryList.add(this.cachedMethodEntries.get(l));
			} catch (Exception ex){}
		}
		HashMap<Class<? extends IEvent>, List<MethodEntry>> eventListenerMap = new HashMap<Class<? extends IEvent>, List<MethodEntry>>();
		for(List<MethodEntry> lme : entryList) {
			for(MethodEntry me : lme) {
				List<MethodEntry> mel = eventListenerMap.get(me.getEventClass());
				if(mel == null) {
					mel = new ArrayList<MethodEntry>();
					eventListenerMap.put(me.getEventClass(), mel);
				}
				mel.add(me);
			}
		}
		int index = 0;
		Iterator<Entry<Class<? extends IEvent>, List<MethodEntry>>> it = eventListenerMap.entrySet().iterator();
		List<List<MethodEntry>> methodEntryList = new ArrayList<List<MethodEntry>>();
		List<MethodEntry> currentList = new ArrayList<MethodEntry>();
		while(it.hasNext()) {
			List<MethodEntry> mel = it.next().getValue();
			for(MethodEntry me : mel) {
				if(index >= this.maxMethodEntriesPerBus) {
					methodEntryList.add(currentList);
					currentList = new ArrayList<MethodEntry>();
					this.singleBus = false;
					index = 0;
				}
				currentList.add(me);
				index++;
			}
		}
		if(index != 0) methodEntryList.add(currentList);
		return methodEntryList;
	}

	@Override
	public final <T extends IEvent> T postEvent(T event) {
		if(this.singleBus) {
			event = this.currentBus.postEvent(event);
		} else {
			List<B> busList = this.busMap.get(event.getClass());
			if(busList == null) {
				return event;
			}
			for(EventBus bus : busList) {
				event = bus.postEvent(event);
			}
		}
		return event;
	}

	@Override
	public final <T extends IEventCancellable> T postEventCancellable(T event) {
		if(this.singleBus) {
			event = this.currentBus.postEventCancellable(event);
		} else {
			List<B> busList = this.busMap.get(event.getClass());
			for(B bus : busList) {
				event = bus.postEventCancellable(event);
			}
		}
		return event;
	}

	@Override
	public IEventBus copyBus() {
		return new MultiEventBus<B>(this.busInstance, this.maxMethodEntriesPerBus);
	}
}
