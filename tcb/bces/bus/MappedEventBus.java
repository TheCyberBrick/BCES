package tcb.bces.bus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import tcb.bces.bus.DRCEventBus.MethodEntry;
import tcb.bces.event.Event;
import tcb.bces.event.EventCancellable;
import tcb.bces.listener.IListener;

/**
 * This event bus implementation uses a {@link HashMap} to map the registered listeners to the event classes.
 * Using listeners that accept subclasses could slow down the event dispatching because the event types 
 * have to be compared during the dispatching.
 * 
 * @author TCB
 *
 */
public class MappedEventBus implements IEventBus {
	private final Map<Class<? extends Event>, List<MethodEntry>> eventListenerMap = new HashMap<Class<? extends Event>, List<MethodEntry>>();
	private final List<MethodEntry> subclassListeners = new ArrayList<MethodEntry>();

	@Override
	public void register(IListener listener) {
		List<MethodEntry> methodEntries = DRCEventBus.analyzeListener(listener);
		List<List<MethodEntry>> modifiedLists = new ArrayList<List<MethodEntry>>();
		boolean subclassListenersModified = false;
		for(MethodEntry me : methodEntries) {
			List<MethodEntry> listeners = this.eventListenerMap.get(me.getEventClass());
			if(listeners == null) {
				listeners = new ArrayList<MethodEntry>();
				this.eventListenerMap.put(me.getEventClass(), listeners);
			}
			if(!listeners.contains(me)) {
				listeners.add(me);
				if(!modifiedLists.contains(listeners)) {
					modifiedLists.add(listeners);
				}
				if(me.getHandlerAnnotation().acceptSubclasses()) {
					this.subclassListeners.add(me);
					subclassListenersModified = true;
				}
			}
		}
		if(subclassListenersModified) {
			modifiedLists.add(this.subclassListeners);
		}
		//Check for any registered subclasses of the event types and add listeners to that list
		Iterator<Entry<Class<? extends Event>, List<MethodEntry>>> entryIterator = this.eventListenerMap.entrySet().iterator();
		while(entryIterator.hasNext()) {
			Entry<Class<? extends Event>, List<MethodEntry>> entry = entryIterator.next();
			Class<? extends Event> eventClass = entry.getKey();
			List<MethodEntry> methodEntryList = entry.getValue();
			for(MethodEntry me : methodEntries) {
				if(!methodEntryList.contains(me) && me.getHandlerAnnotation().acceptSubclasses() && me.getEventClass().isAssignableFrom(eventClass)) {
					methodEntryList.add(me);
					if(!modifiedLists.contains(methodEntryList)) {
						modifiedLists.add(methodEntryList);
					}
				}
			}
		}
		//Update priorities
		Comparator<MethodEntry> prioritySorter = new Comparator<MethodEntry>() {
			@Override
			public int compare(MethodEntry e1, MethodEntry e2) {
				return e2.getHandlerAnnotation().priority() - e1.getHandlerAnnotation().priority();
			}
		};
		for(List<MethodEntry> methodEntryList : modifiedLists) {
			Collections.sort(methodEntryList, prioritySorter);
		}
	}

	@Override
	public void unregister(IListener listener) {
		for(Entry<Class<? extends Event>, List<MethodEntry>> mapEntry : this.eventListenerMap.entrySet()) {
			List<MethodEntry> methodEntries = mapEntry.getValue();
			Iterator<MethodEntry> methodEntryIterator = methodEntries.iterator();
			while(methodEntryIterator.hasNext()) {
				MethodEntry me = methodEntryIterator.next();
				if(me.getListener() == listener) {
					methodEntryIterator.remove();
				}
			}
		}
		while(this.subclassListeners.remove(listener));
	}

	@Override
	public <T extends Event> T post(T event) {
		Class<?> eventClass = event.getClass();
		List<MethodEntry> methodEntries = this.eventListenerMap.get(eventClass);
		EventCancellable eventCancellable = null;
		if(event instanceof EventCancellable) {
			eventCancellable = (EventCancellable) event;
		}
		boolean contained = false;
		if(methodEntries != null) {
			contained = true;
			for(MethodEntry me : methodEntries) {
				if(me.getHandlerAnnotation().forced() || me.getListener().isEnabled()) {
					try {
						me.invoke(event);
						if(eventCancellable != null && eventCancellable.isCancelled()) {
							return event;
						}
					} catch(Exception ex){
						throw new RuntimeException(ex);
					}
				}
			}
		}
		if(!contained) {
			for(MethodEntry me : this.subclassListeners) {
				if(me.getHandlerAnnotation().forced() || me.getListener().isEnabled()) {
					if(me.getEventClass() != eventClass) {
						try {
							me.invoke(event);
							if(eventCancellable != null && eventCancellable.isCancelled()) {
								return event;
							}
						} catch(Exception ex){
							throw new RuntimeException(ex);
						}
					}
				}
			}
		}
		return event;
	}
}
