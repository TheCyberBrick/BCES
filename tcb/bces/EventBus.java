package tcb.bces;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import tcb.bces.event.IEvent;
import tcb.bces.event.IEventCancellable;
import tcb.bces.event.impl.EventException;
import tcb.bces.listener.IListener;
import tcb.bces.listener.Subscribe;
import tcb.bces.listener.filter.IFilter;

/**
 * This event bus should only be used for a low amount of listening methods. The 
 * maximum amount of possible listening methods is {@link EventBus#MAX_METHODS}. 
 * Use {@link MultiEventBus} for high amounts of listening methods.
 * This event bus supports listener priorities and an event filter.
 * The filter allows the user to filter and cancel any event before 
 * it's passed to it's listener on a per listening method basis. The event
 * itself will not be cancelled. Filters work on any type of listener or event,
 * the event doesn't have to be cancellable.
 * 
 * @author TCB
 *
 */
public class EventBus implements IEventBus {
	/**
	 * Holds the compiled method and an array of the registered listeners
	 */
	private static final class MethodStubImpl implements IMethodStub {
		/**
		 * Array of all registered listeners. Used in the compiled postEventInternal method.
		 */
		@SuppressWarnings("unused")
		private final IListener[] listenerArray;

		/**
		 * Array of all filters. Used in the compiled postEventInternal method.
		 */
		@SuppressWarnings("unused")
		private final IFilter[] filterArray;

		public MethodStubImpl(IListener[] listenerArray, IFilter[] filterArray) {
			this.listenerArray = listenerArray;
			this.filterArray = filterArray;
		}

		/**
		 * Distributes the event to all registered listeners that use that event.
		 * @param event IEvent
		 * @return IEvent
		 */
		@Override
		public final IEvent postEventInternal(IEvent event) {
			return event;
		}

		/**
		 * Distributes the event to all registered listeners that use that event.
		 * @param event IEventCancellable
		 * @return IEvent
		 */
		@Override
		public final IEvent postEventInternalCancellable(IEventCancellable event) {
			return event;
		}

		/**
		 * Returns a new instance of EventBus and passes the listener array to the new instance. Accessed via reflection
		 * from the EventBus.
		 * @param listenerArray Listener[]
		 * @return EventBus
		 */
		@SuppressWarnings("unused")
		private final static MethodStubImpl getNewInstance(IListener[] listenerArray, IFilter[] filterArray) {
			return new MethodStubImpl(listenerArray, filterArray);
		}
	}

	/**
	 * Holds data of a verified listening method
	 */
	private static final class ListenerMethodEntry {
		private final IListener instance;
		private final Method method;
		private final String methodName;
		private final boolean forced;
		private final int priority;
		private final IFilter filter;
		private Subscribe handlerAnnotation;
		public ListenerMethodEntry(IListener instance, Method method, Subscribe handlerAnnotation, IFilter filter) {
			this.instance = instance;
			this.method = method;
			this.methodName = method.getName();
			this.handlerAnnotation = handlerAnnotation;
			this.forced = handlerAnnotation.forced();
			this.priority = handlerAnnotation.priority();
			this.filter = filter;
		}
		private IListener getInstance() {
			return this.instance;
		}
		private Method getMethod() {
			return this.method;
		}
		private String getMethodName() {
			return this.methodName;
		}
		private boolean isForced() {
			return this.forced;
		}
		private int getPriority() {
			return this.priority;
		}
		private IFilter getFilter() {
			return this.filter;
		}
		private Subscribe getHandlerAnnotation() {
			return this.handlerAnnotation;
		}
	}

	/**
	 * The {@link MethodEntry} holds information about a registered listener
	 * and it's listening {@link Method}.
	 */
	public static final class MethodEntry {
		private final Class<? extends IEvent> eventClass;
		private final IListener listener;
		private final Method method;
		private final Subscribe handlerAnnotation;
		private IFilter filter;

		/**
		 * The {@link MethodEntry} holds information about a registered listener
		 * and it's listening {@link Method}.
		 * @param eventClass {@link Class}
		 * @param listener {@link IListener}
		 * @param method {@link Method}
		 * @param handlerAnnotation {@link Subscribe}
		 */
		private MethodEntry(Class<? extends IEvent> eventClass, IListener listener, Method method, Subscribe handlerAnnotation) {
			this.eventClass = eventClass;
			this.listener = listener;
			this.method = method;
			this.handlerAnnotation = handlerAnnotation;
		}

		/**
		 * Returns the event type of this {@link MethodEntry}.
		 * @return {@link IEvent}
		 */
		public Class<? extends IEvent> getEventClass() {
			return this.eventClass;
		}

		/**
		 * Returns the {@link IListener} instance.
		 * @return {@link IListener}
		 */
		public IListener getListener() {
			return this.listener;
		}

		/**
		 * Returns the {@link Method} this {@link MethodEntry} belongs to.
		 * @return {@link Method}
		 */
		public Method getMethod() {
			return this.method;
		}

		/**
		 * Returns the {@link Subscribe} annotation that belongs to this
		 * {@link MethodEntry}.
		 * @return {@link Subscribe}
		 */
		public Subscribe getHandlerAnnotation() {
			return this.handlerAnnotation;
		}

		/**
		 * Returns the {@link IFilter} that has been assigned to this {@link MethodEntry}.
		 * @return {@link IFilter}
		 */
		public IFilter getFilter() {
			return this.filter;
		}

		/**
		 * This method can be used to set custom filters. If this method is used instead
		 * of the {@link Subscribe#filter()} method, a custom constructor
		 * can be used. The method {@link IFilter#init(IListener)} will not be called if
		 * this method is used.
		 * @param filter {@link IFilter}
		 * @return {@link MethodEntry}
		 */
		public MethodEntry setFilter(IFilter filter) {
			this.filter = filter;
			return this;
		}
	}

	/**
	 * The maximum amount of registered method entries
	 */
	public static final int MAX_METHODS = 256;

	/**
	 * Holds the method names used for reflection and bytecode construction
	 */
	private static final String METHODSTUB_CREATE_NEW_INSTANCE = "getNewInstance";
	private static final String METHODSTUB_POST_EVENT_INTERNAL = "postEventInternal";
	private static final String METHODSTUB_POST_EVENT_INTERNAL_CANCELLABLE = "postEventInternalCancellable";
	private static final String METHODSTUB_LISTENER_ARRAY = "listenerArray";
	private static final String METHODSTUB_FILTER_ARRAY = "filterArray";
	private static final String IFILTER_FILTER = "filter";
	private static final String IFILTER_INIT = "init";
	private static final String ILISTENER_ISENABLED = "isEnabled";
	private static final String IEVENTCANCELLABLE_ISCANCELLED = "isCancelled";

	/**
	 * Contains all registered listeners
	 */
	private final HashMap<Class<? extends IEvent>, List<ListenerMethodEntry>> registeredEntries = new HashMap<Class<? extends IEvent>, List<ListenerMethodEntry>>();

	/**
	 * Index lookup map, used for creating the bytecode
	 */
	private final HashMap<IListener, Integer> indexLookup = new HashMap<IListener, Integer>();

	/**
	 * Filter index lookup map, used for creating the bytecode
	 */
	private final HashMap<ListenerMethodEntry, Integer> filterIndexLookup = new HashMap<ListenerMethodEntry, Integer>();

	/**
	 * Contains all registered listeners
	 */
	private IListener[] listenerArray;

	/**
	 * Contains all filters
	 */
	private IFilter[] filterArray;

	/**
	 * Holds the instance of the recompiled {@link MethodStubImpl}
	 */
	private IMethodStub stubImplInstance = null;

	/**
	 * Holds the amount of registered MethodEntries
	 */
	private int methodCount = 0;

	/**
	 * The default EventBus has a limit of {@link EventBus#MAX_METHODS} listening methods. 
	 * If you want to add more listening methods use {@link MultiEventBus} instead.
	 */
	public EventBus() { }

	/**
	 * Adds a listener to the EventBus. EventBus has to be updated with {@link EventBus#update()} for the new listener to take effect.
	 * The default EventBus has a limit of {@link EventBus#MAX_METHODS} listening methods. If more than {@link EventBus#MAX_METHODS} listening methods 
	 * are registered an IndexOutOfBoundsException is thrown.
	 * A SubscriptionException is thrown if an invalid method has been found.
	 * Returns a read-only list of all found valid method entries.
	 * @param listener {@link IListener}
	 * @return {@link List} read-only
	 */
	public final List<MethodEntry> addListener(IListener listener) throws SubscriptionException, IndexOutOfBoundsException {
		List<MethodEntry> entryList = EventBus.analyzeListener(listener);
		if(this.methodCount > MAX_METHODS) {
			throw new IndexOutOfBoundsException("Too many registered methods. Max: " + MAX_METHODS);
		} else if(this.methodCount + entryList.size() > MAX_METHODS) {
			throw new IndexOutOfBoundsException("Registering this listener exceeds the maximum " +
					"amount of registered methods. " +
					"Current: " + this.methodCount +
					" Max: " + MAX_METHODS);
		}
		for(MethodEntry entry : entryList) {
			Class<? extends IEvent> paramType = (Class<? extends IEvent>) entry.getEventClass();
			List<ListenerMethodEntry> lle = this.registeredEntries.get(paramType);
			if(lle == null) {
				lle = new ArrayList<ListenerMethodEntry>();
				this.registeredEntries.put(paramType, lle);
			}
			lle.add(new ListenerMethodEntry(listener, entry.getMethod(), entry.getHandlerAnnotation(), entry.getFilter()));
			this.methodCount++;
		}
		this.updateArray();
		return Collections.unmodifiableList(entryList);
	}

	/**
	 * Verifies the specified listener and returns a list of all found valid method entries.
	 * A SubscriptionException is thrown if an invalid method has been found.
	 * @param listener {@link IListener}
	 * @return {@link List}
	 * @throws {@link SubscriptionException}
	 */
	public static final List<MethodEntry> analyzeListener(IListener listener) throws SubscriptionException {
		List<MethodEntry> entryList = new ArrayList<MethodEntry>();
		Method[] listenerMethods = listener.getClass().getDeclaredMethods();
		for(Method method : listenerMethods) {
			if(method.getParameterTypes().length != 1 || !IEvent.class.isAssignableFrom(method.getParameterTypes()[0])) continue;
			Subscribe handlerAnnotation = method.getAnnotation(Subscribe.class);
			if(handlerAnnotation != null) {
				int methodModifiers = method.getModifiers();
				if((methodModifiers & Modifier.STATIC) != 0 ||
						(methodModifiers & Modifier.ABSTRACT) != 0 ||
						(methodModifiers & Modifier.PRIVATE) != 0 ||
						(methodModifiers & Modifier.PROTECTED) != 0) {
					throw new SubscriptionException("Invalid method modifiers for method " + method.getName());
				}
				if(method.getReturnType() != void.class) {
					throw new SubscriptionException("Return type is not void for method " + method.getName());
				}
				@SuppressWarnings("unchecked")
				Class<? extends IEvent> paramType = (Class<? extends IEvent>) method.getParameterTypes()[0];
				entryList.add(EventBus.initFilter(new MethodEntry(paramType, listener, method, handlerAnnotation)));
			}
		}
		return entryList;
	}

	/**
	 * Adds a single {@link MethodEntry} to the {@link EventBus}. The {@link EventBus} has to be updated with {@link EventBus#update()} for the new {@link IListener} to take effect.
	 * The default {@link EventBus} has a limit of {@link EventBus#MAX_METHODS} method entries. If more than {@link EventBus#MAX_METHODS} method entries are registered an {@link IndexOutOfBoundsException} is thrown.
	 * @param entry {@link MethodEntry}
	 */
	public final void addMethodEntry(MethodEntry entry) throws IndexOutOfBoundsException {
		if(this.methodCount > MAX_METHODS) {
			throw new IndexOutOfBoundsException("Too many registered methods. Max: " + MAX_METHODS);
		} else if(this.methodCount >= MAX_METHODS) {
			throw new IndexOutOfBoundsException("Registering this method entry exceeds the maximum " +
					"amount of registered methods. Max: " + MAX_METHODS);
		}
		List<ListenerMethodEntry> lle = this.registeredEntries.get(entry.getEventClass());
		if(lle == null) {
			lle = new ArrayList<ListenerMethodEntry>();
			this.registeredEntries.put(entry.getEventClass(), lle);
		}
		lle.add(new ListenerMethodEntry(entry.getListener(), entry.getMethod(), entry.getHandlerAnnotation(), entry.getFilter()));
		this.methodCount++;
		this.updateArray();
	}

	/**
	 * Returns a read-only list of all registered method entries.
	 * @return {@link List}
	 */
	public final List<MethodEntry> getMethodEntries() {
		List<MethodEntry> result = new ArrayList<MethodEntry>();
		for(Entry<Class<? extends IEvent>, List<ListenerMethodEntry>> e : this.registeredEntries.entrySet()) {
			for(ListenerMethodEntry lme : e.getValue()) {
				result.add(new MethodEntry(e.getKey(), lme.getInstance(), lme.getMethod(), lme.getHandlerAnnotation()));
			}
		}
		return Collections.unmodifiableList(result);
	}

	/**
	 * Removes a {@link IListener} from the {@link EventBus}. The {@link EventBus} has to be updated with {@link EventBus#update()} for this to take effect.
	 * @param listener {@link IListener}
	 */
	public final void removeListener(IListener listener) {
		Method[] listenerMethods = listener.getClass().getDeclaredMethods();
		for(Method method : listenerMethods) {
			if(method.getParameterTypes().length != 1 || !IEvent.class.isAssignableFrom(method.getParameterTypes()[0])) continue;
			Subscribe handlerAnnotation = method.getAnnotation(Subscribe.class);
			if(handlerAnnotation != null) {
				int methodModifiers = method.getModifiers();
				if((methodModifiers & Modifier.STATIC) != 0 ||
						(methodModifiers & Modifier.ABSTRACT) != 0 ||
						(methodModifiers & Modifier.PRIVATE) != 0 ||
						(methodModifiers & Modifier.PROTECTED) != 0) {
					continue;
				}
				if(method.getReturnType() != void.class) {
					continue;
				}
				@SuppressWarnings("unchecked")
				Class<? extends IEvent> paramType = (Class<? extends IEvent>) method.getParameterTypes()[0];
				List<ListenerMethodEntry> lle = this.registeredEntries.get(paramType);
				if(lle == null) {
					this.registeredEntries.remove(paramType);
					return;
				}
				ListenerMethodEntry toRemove = null;
				for(ListenerMethodEntry le : lle) {
					if(le.getInstance().getClass().equals(listener.getClass()) &&
							le.getMethodName().equals(method.getName())) {
						toRemove = le;
						break;
					}
				}
				if(toRemove != null) {
					lle.remove(toRemove);
					this.methodCount--;
				}
				if(lle.size() == 0) {
					this.registeredEntries.remove(paramType);
					return;
				}
				if(toRemove != null) {
					break;
				}
			}
		}
		this.updateArray();
	}

	/**
	 * Removes a {@link MethodEntry} from the {@link EventBus}. The {@link EventBus} has to be updated with {@link EventBus#update()} for this to take effect.
	 * @param methodEntry {@link MethodEntry}
	 */
	public final void removeMethodEntry(MethodEntry methodEntry) {
		List<ListenerMethodEntry> lle = this.registeredEntries.get(methodEntry.getEventClass());
		if(lle == null) {
			this.registeredEntries.remove(methodEntry.getEventClass());
			return;
		}
		ListenerMethodEntry toRemove = null;
		for(ListenerMethodEntry le : lle) {
			if(le.getInstance().getClass().equals(methodEntry.getListener().getClass()) &&
					le.getMethodName().equals(methodEntry.getMethod().getName())) {
				toRemove = le;
				break;
			}
		}
		if(toRemove != null) {
			lle.remove(toRemove);
			this.methodCount--;
		}
		if(lle.size() == 0) {
			this.registeredEntries.remove(methodEntry.getEventClass());
			return;
		}
	}

	/**
	 * This method sets the {@link IFilter} of the {@link MethodEntry} that was specified
	 * with {@link Subscribe#filter()}.
	 * Throws a {@link SubscriptionException} if the filter class is abstract or interface
	 * or doesn't have a no-arg constructor.
	 * @param entry {@link MethodEntry}
	 * @return {@link MethodEntry}
	 * @throws {@link SubscriptionException}
	 */
	private static final MethodEntry initFilter(MethodEntry entry) throws SubscriptionException {
		if(entry.getFilter() != null) {
			return entry;
		}
		Class<? extends IFilter> filterClass = entry.handlerAnnotation.filter();
		if(filterClass == IFilter.class) {
			return entry;
		}
		int classModifiers = filterClass.getModifiers();
		if((classModifiers & Modifier.ABSTRACT) != 0 ||
				(classModifiers & Modifier.INTERFACE) != 0) {
			throw new SubscriptionException("Filter class must not be abstract or interface. Class modifiers: " + classModifiers);
		}
		if((classModifiers & Modifier.PUBLIC) == 0) {
			throw new SubscriptionException("Filter class must be public. Class modifiers: " + classModifiers);
		}
		try {
			IFilter instance = null;
			Constructor<? extends IFilter> ctor = filterClass.getDeclaredConstructor();
			boolean accessible = ctor.isAccessible();
			ctor.setAccessible(true);
			instance = ctor.newInstance();
			if(!accessible) ctor.setAccessible(false);
			entry.setFilter(instance);
			Method initMethod = filterClass.getDeclaredMethod(IFILTER_INIT, new Class[]{MethodEntry.class});
			initMethod.invoke(instance, entry);
		} catch(Exception ex) {
			throw new SubscriptionException("No valid no-arg constructor was found in the filter class.", ex);
		}
		return entry;
	}

	/**
	 * Removes all registered listeners from this {@link EventBus}.
	 */
	public final void clear() {
		this.registeredEntries.clear();
		this.indexLookup.clear();
		this.filterIndexLookup.clear();
		this.updateArray();
	}

	/**
	 * Returns a read-only list of all registered listeners.
	 * @return {@link List} read-only
	 */
	public final List<IListener> getListeners() {
		ArrayList<IListener> listeners = new ArrayList<IListener>();
		for(Entry<Class<? extends IEvent>, List<ListenerMethodEntry>> e : this.registeredEntries.entrySet()) {
			for(ListenerMethodEntry le : e.getValue()) {
				listeners.add(le.getInstance());
			}
		}
		return Collections.unmodifiableList(listeners);
	}

	/**
	 * Updates the listener array and index lookup map.
	 */
	private final void updateArray() {
		this.indexLookup.clear();
		this.filterIndexLookup.clear();
		ArrayList<IListener> arrayListenerList = new ArrayList<IListener>();
		ArrayList<IFilter> arrayFilterList = new ArrayList<IFilter>();
		for(Entry<Class<? extends IEvent>, List<ListenerMethodEntry>> e : this.registeredEntries.entrySet()) {
			List<ListenerMethodEntry> listenerEntryList = e.getValue();
			for(ListenerMethodEntry listenerEntry : listenerEntryList) {
				arrayListenerList.add(listenerEntry.getInstance());
				this.indexLookup.put(listenerEntry.getInstance(), arrayListenerList.size() - 1);
				if(listenerEntry.getFilter() != null) {
					arrayFilterList.add(listenerEntry.getFilter());
					this.filterIndexLookup.put(listenerEntry, arrayFilterList.size() - 1);
				}
			}
		}
		this.listenerArray = arrayListenerList.toArray(new IListener[0]);
		this.filterArray = arrayFilterList.toArray(new IFilter[0]);
	}

	/**
	 * Returns the recompiled version of {@link MethodStubImpl}
	 * @return {@link IMethodStub}
	 */
	private final IMethodStub getCompiledStub() {
		if(this.stubImplInstance == null)  {
			this.stubImplInstance = (IMethodStub)this.compileStub();
		}
		return this.stubImplInstance;
	}

	/**
	 * Recompiles {@link MethodStubImpl} with all registered listeners.
	 * @return {@link IMethodStub}
	 */
	private final IMethodStub compileStub() {
		try {
			Comparator<ListenerMethodEntry> prioritySorter = new Comparator<ListenerMethodEntry>() {
				@Override
				public int compare(ListenerMethodEntry e1, ListenerMethodEntry e2) {
					return e2.getPriority() - e1.getPriority();
				}
			};
			for(List<ListenerMethodEntry> lle : this.registeredEntries.values()) {
				Collections.sort(lle, prioritySorter);
			}
			ClassLoader customClassLoader = new ClassLoader() {
				@SuppressWarnings("unchecked")
				@Override
				protected Class<?> loadClass(String paramString, boolean paramBoolean) throws ClassNotFoundException {
					if(paramString.equals(MethodStubImpl.class.getName())) {
						try {
							InputStream is = MethodStubImpl.class.getResourceAsStream("/" + paramString.replace('.', '/') + ".class");
							ByteArrayOutputStream baos = new ByteArrayOutputStream();
							int readBytes = 0;
							byte[] buffer = new byte[1024];
							while((readBytes = is.read(buffer)) >= 0) {
								baos.write(buffer, 0, readBytes);
							}
							byte[] bytecode = baos.toByteArray();
							ClassReader classReader = new ClassReader(bytecode);
							ClassNode classNode = new ClassNode();
							classReader.accept(classNode, ClassReader.SKIP_FRAMES);
							for(MethodNode methodNode : (List<MethodNode>) classNode.methods) {
								if(methodNode.name.equals(METHODSTUB_POST_EVENT_INTERNAL)) {
									instrumentDistributorMethod(methodNode, false);
								}
								if(methodNode.name.equals(METHODSTUB_POST_EVENT_INTERNAL_CANCELLABLE)) {
									instrumentDistributorMethod(methodNode, true);
								}
							}
							ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
							classNode.accept(classWriter);
							bytecode = classWriter.toByteArray();
							return this.defineClass(paramString, bytecode, 0, bytecode.length);
						} catch(Exception ex) {
							onException(ex);
						}
					}
					return super.loadClass(paramString, paramBoolean);
				}
			};
			Class<?> dispatcherClass = customClassLoader.loadClass(MethodStubImpl.class.getName());
			Method instanceMethod = dispatcherClass.getDeclaredMethod(METHODSTUB_CREATE_NEW_INSTANCE, new Class[]{IListener[].class, IFilter[].class});
			instanceMethod.setAccessible(true);
			Object distributorInstance = instanceMethod.invoke(null, new Object[]{this.listenerArray, this.filterArray});
			instanceMethod.setAccessible(false);
			return (IMethodStub) distributorInstance;
		} catch(Exception ex) {
			this.onException(ex);
		}
		return null;
	}

	/**
	 * Modifies the internal event distribution method.
	 * @param methodNode {@link MethodNode}
	 */
	@SuppressWarnings("unchecked")
	private final void instrumentDistributorMethod(MethodNode methodNode, boolean cancellable) {
		InsnList methodInstructionSet = methodNode.instructions;
		ArrayList<AbstractInsnNode> instructionSet = new ArrayList<AbstractInsnNode>();
		Iterator<AbstractInsnNode> it = methodInstructionSet.iterator();
		AbstractInsnNode insn;
		while((insn = it.next()) != null && it.hasNext()) {
			if(insn.getOpcode() == Opcodes.IRETURN || 
					insn.getOpcode() == Opcodes.LRETURN ||
					insn.getOpcode() == Opcodes.RETURN ||
					insn.getOpcode() == Opcodes.ARETURN ||
					insn.getOpcode() == Opcodes.DRETURN ||
					insn.getOpcode() == Opcodes.FRETURN) {
				LabelNode exitNode = new LabelNode();
				for(Entry<Class<? extends IEvent>, List<ListenerMethodEntry>> e : this.registeredEntries.entrySet()) {
					if(cancellable && !IEventCancellable.class.isAssignableFrom(e.getKey())) {
						continue;
					}
					String eventClassName = e.getKey().getName().replace(".", "/");

					/*
					 * Pseudo code, runs for every listener method:
					 * 
					 * if(event instanceof listenerArray[n].eventType) {
					 * 
					 *   //Optional check, only present if filter is not default IFilter class
					 *   if(filterArray[p].filter(listenerArray[n], event) {
					 *   
					 *     //Optional check, only present if the compiled method with the cancellable code is used
					 *     if(event instanceof IEventCancellable == false ||
					 *        !((IEventCancellable)event).isCancelled()) {
					 *        
					 *       //Invokes the listener method
					 *       listenerArray[n].invokeMethod(event);
					 *       
					 *       //Optional check, only present if the compiled method with the cancellable code is used
					 *       if(event instanceof IEventCancellable) {
					 *       
					 *         //Optional check, only present if the compiled method with the cancellable code is used
					 *         if(((IEventCancellable)event).isCancelled()) return;
					 *         
					 *       }
					 *     }
					 *   }
					 *   
					 * }
					 * 
					 * ...
					 */

					//Fail label, jumped to if instanceof fails
					LabelNode instanceofFailLabelNode = new LabelNode();

					//if(event instanceof eventclass  == false) -> jump to instanceofFailLabelNode
					instructionSet.add(new IntInsnNode(Opcodes.ALOAD, 1));
					instructionSet.add(new TypeInsnNode(Opcodes.INSTANCEOF, eventClassName));
					instructionSet.add(new JumpInsnNode(Opcodes.IFEQ, instanceofFailLabelNode));

					//Inside if body
					for(ListenerMethodEntry listenerEntry : e.getValue()) {
						String className = MethodStubImpl.class.getName().replace(".", "/");
						String fieldType = "[L" + IListener.class.getName().replace(".", "/") + ";";
						String listenerClassName = listenerEntry.getInstance().getClass().getName().replace(".", "/");
						String listenerMethodName = listenerEntry.getMethodName();
						String listenerMethodType = "(L" + e.getKey().getName().replace(".", "/") + ";)V";
						int listenerIndex = this.indexLookup.get(listenerEntry.getInstance());

						//if(!filterArray[n].filter(listenerArray[p], event)) -> jump to filterFailLabelNode
						LabelNode filterFailLabelNode = null;
						//Only implement if filter is not default IFilter class
						if(listenerEntry.getFilter() != null) {
							filterFailLabelNode = new LabelNode();
							int filterIndex = this.filterIndexLookup.get(listenerEntry);
							String filterClassName = listenerEntry.getFilter().getClass().getName().replace(".", "/");
							String filterFieldType = "[L" + IFilter.class.getName().replace(".", "/") + ";";
							String filterMethodType = "(L" + IEvent.class.getName().replace(".", "/") + ";)Z";
							//load instance of this class ('this' keyword)
							instructionSet.add(new IntInsnNode(Opcodes.ALOAD, 0));
							//get filter array
							instructionSet.add(new FieldInsnNode(Opcodes.GETFIELD, className, METHODSTUB_FILTER_ARRAY, filterFieldType));
							//push index onto stack
							//some tiny bytecode optimization
							if(filterIndex <= 5) {
								switch(filterIndex) {
								case 0:
									instructionSet.add(new InsnNode(Opcodes.ICONST_0));
									break;
								case 1:
									instructionSet.add(new InsnNode(Opcodes.ICONST_1));
									break;
								case 2:
									instructionSet.add(new InsnNode(Opcodes.ICONST_2));
									break;
								case 3:
									instructionSet.add(new InsnNode(Opcodes.ICONST_3));
									break;
								case 4:
									instructionSet.add(new InsnNode(Opcodes.ICONST_4));
									break;
								case 5:
									instructionSet.add(new InsnNode(Opcodes.ICONST_5));
									break;
								}
							} else if(filterIndex <= Byte.MAX_VALUE) {
								instructionSet.add(new IntInsnNode(Opcodes.BIPUSH, filterIndex));
							} else if(filterIndex <= Short.MAX_VALUE) {
								instructionSet.add(new IntInsnNode(Opcodes.SIPUSH, filterIndex));
							} else {
								instructionSet.add(new LdcInsnNode(filterIndex));
							}
							//load filter from array
							instructionSet.add(new InsnNode(Opcodes.AALOAD));
							//cast filter type
							instructionSet.add(new TypeInsnNode(Opcodes.CHECKCAST, filterClassName));
							//load event
							instructionSet.add(new IntInsnNode(Opcodes.ALOAD, 1));
							//invoke verify(listener, event), return boolean
							instructionSet.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, filterClassName, IFILTER_FILTER, filterMethodType, false));
							//jump if false
							instructionSet.add(new JumpInsnNode(Opcodes.IFEQ, filterFailLabelNode));
						}

						if(cancellable) {
							LabelNode instanceofEventCancellableFailLabel = new LabelNode();
							//load event
							instructionSet.add(new IntInsnNode(Opcodes.ALOAD, 1));
							//event instanceof EventCancellable
							instructionSet.add(new TypeInsnNode(Opcodes.INSTANCEOF, IEventCancellable.class.getName().replace(".", "/")));
							//if(event instanceof EventCancellable == false) -> jump to instanceofEventCancellableFailLabel
							instructionSet.add(new JumpInsnNode(Opcodes.IFEQ, instanceofEventCancellableFailLabel));
							//load event
							instructionSet.add(new IntInsnNode(Opcodes.ALOAD, 1));
							//cast event to EventCancellable
							instructionSet.add(new TypeInsnNode(Opcodes.CHECKCAST, eventClassName));
							//invoke EventCancellable#isCancelled
							instructionSet.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, eventClassName, IEVENTCANCELLABLE_ISCANCELLED, "()Z", false));
							//if(!EventCancellable#isCancelled()) -> jump to exitNode
							instructionSet.add(new JumpInsnNode(Opcodes.IFNE, exitNode));
							instructionSet.add(instanceofEventCancellableFailLabel);
						}

						//Only implement IListener#isEnabled() check if Receiver#forced() is false
						if(!listenerEntry.isForced()) {
							////////////////////////////// Check if listener is enabled //////////////////////////////
							//load instance of this class ('this' keyword)
							instructionSet.add(new IntInsnNode(Opcodes.ALOAD, 0));
							//get listener array
							instructionSet.add(new FieldInsnNode(Opcodes.GETFIELD, className, METHODSTUB_LISTENER_ARRAY, fieldType));
							//push index onto stack
							//some tiny bytecode optimization
							if(listenerIndex <= 5) {
								switch(listenerIndex) {
								case 0:
									instructionSet.add(new InsnNode(Opcodes.ICONST_0));
									break;
								case 1:
									instructionSet.add(new InsnNode(Opcodes.ICONST_1));
									break;
								case 2:
									instructionSet.add(new InsnNode(Opcodes.ICONST_2));
									break;
								case 3:
									instructionSet.add(new InsnNode(Opcodes.ICONST_3));
									break;
								case 4:
									instructionSet.add(new InsnNode(Opcodes.ICONST_4));
									break;
								case 5:
									instructionSet.add(new InsnNode(Opcodes.ICONST_5));
									break;
								}
							} else if(listenerIndex <= Byte.MAX_VALUE) {
								instructionSet.add(new IntInsnNode(Opcodes.BIPUSH, listenerIndex));
							} else if(listenerIndex <= Short.MAX_VALUE) {
								instructionSet.add(new IntInsnNode(Opcodes.SIPUSH, listenerIndex));
							} else {
								instructionSet.add(new LdcInsnNode(listenerIndex));
							}
							//load listener from array
							instructionSet.add(new InsnNode(Opcodes.AALOAD));
							//cast listener type
							instructionSet.add(new TypeInsnNode(Opcodes.CHECKCAST, listenerClassName));
							//invoke isEnabled, get returned boolean
							instructionSet.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, listenerClassName, ILISTENER_ISENABLED, "()Z", false));
							//jump to failLabelNode if returned boolean is false
							instructionSet.add(new JumpInsnNode(Opcodes.IFEQ, instanceofFailLabelNode));
						}

						///////////////////////////////// Invoke listener method /////////////////////////////////
						//load instance of this class ('this' keyword)
						instructionSet.add(new IntInsnNode(Opcodes.ALOAD, 0));
						//get listener array
						instructionSet.add(new FieldInsnNode(Opcodes.GETFIELD, className, METHODSTUB_LISTENER_ARRAY, fieldType));
						//push index onto stack
						//some tiny bytecode optimization
						if(listenerIndex <= 5) {
							switch(listenerIndex) {
							case 0:
								instructionSet.add(new InsnNode(Opcodes.ICONST_0));
								break;
							case 1:
								instructionSet.add(new InsnNode(Opcodes.ICONST_1));
								break;
							case 2:
								instructionSet.add(new InsnNode(Opcodes.ICONST_2));
								break;
							case 3:
								instructionSet.add(new InsnNode(Opcodes.ICONST_3));
								break;
							case 4:
								instructionSet.add(new InsnNode(Opcodes.ICONST_4));
								break;
							case 5:
								instructionSet.add(new InsnNode(Opcodes.ICONST_5));
								break;
							}
						} else if(listenerIndex <= Byte.MAX_VALUE) {
							instructionSet.add(new IntInsnNode(Opcodes.BIPUSH, listenerIndex));
						} else if(listenerIndex <= Short.MAX_VALUE) {
							instructionSet.add(new IntInsnNode(Opcodes.SIPUSH, listenerIndex));
						} else {
							instructionSet.add(new LdcInsnNode(listenerIndex));
						}
						//load listener from array
						instructionSet.add(new InsnNode(Opcodes.AALOAD));
						//cast listener type
						instructionSet.add(new TypeInsnNode(Opcodes.CHECKCAST, listenerClassName));
						//load parameter
						instructionSet.add(new IntInsnNode(Opcodes.ALOAD, 1));
						//cast parameter type
						instructionSet.add(new TypeInsnNode(Opcodes.CHECKCAST, eventClassName));
						//invoke method
						instructionSet.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, listenerClassName, listenerMethodName, listenerMethodType, false));

						//Only implement if filter is not default IFilter class
						if(filterFailLabelNode != null) {
							instructionSet.add(filterFailLabelNode);
						}
					}

					//failLabelNode
					instructionSet.add(instanceofFailLabelNode);
				}
				instructionSet.add(exitNode);
				if(this.instrumentDistributor(instructionSet, methodNode)) {
					for(AbstractInsnNode insnNode : instructionSet) {
						methodInstructionSet.insertBefore(insn, insnNode);
					}
				}
			}
		}
		methodNode.visitMaxs(0, 0);
	}

	/**
	 * Posts an {@link IEvent} and returns the posted event. This method will not be interrupted if an event is cancelled.
	 * This method should be used if performance is a big concern and cancellable events are not required.
	 * @param event {@link IEvent}
	 * @return {@link IEvent}
	 */
	@SuppressWarnings("unchecked")
	@Override
	public <T extends IEvent> T postEvent(T event) {
		try {
			if (!this.prePostEvent(event)) return event;
			IEvent result = this.getCompiledStub().postEventInternal(event);
			this.postPostEvent(event);
			return (T) result;
		} catch (Exception ex) {
			if (!(event instanceof EventException)) {
				postEvent(new EventException(ex, event));
			}
		}
		return event;
	}

	/**
	 * Posts a cancellable {@link IEvent} and returns the posted event. This method will be interrupted if an event is cancelled.
	 * @param event {@link IEventCancellable}
	 * @return {@link IEventCancellable}
	 */
	@SuppressWarnings("unchecked")
	@Override
	public <T extends IEventCancellable> T postEventCancellable(T event) {
		try {
			if (!this.prePostEvent(event)) return event;
			IEvent result = this.getCompiledStub().postEventInternalCancellable(event);
			this.postPostEvent(event);
			return (T) result;
		} catch (Exception ex) {
			if (!(event instanceof EventException)) {
				postEvent(new EventException(ex, event));
			}
		}
		return event;
	}

	/**
	 * Updates the internal event handler. Required for new method entries to take effect.
	 * For optimal performance this method should be called after all listeners have been
	 * registered.
	 */
	public void update() {
		this.stubImplInstance = (IMethodStub)this.compileStub();
	}

	/**
	 * Returns a new instance of this {@link EventBus} with the same
	 * properties.
	 * Used for {@link MultiEventBus} to create copies of
	 * the specified bus.
	 * @return {@link IEventBus}
	 */
	@Override
	public IEventBus copyBus() {
		return new EventBus();
	}

	////////////////////////// API Methods //////////////////////////
	/**
	 * Called when an event is about to get posted. Return true if event should be posted.
	 * @param event {@link IEvent}
	 * @return boolean
	 */
	protected boolean prePostEvent(IEvent event) {
		return true;
	}

	/**
	 * Called after an event has been posted.
	 * @param event {@link IEvent}
	 */
	protected void postPostEvent(IEvent event) { }

	/**
	 * Called when the distributor method is being instrumented. Additional instructions can be added to the baseInstructions or directly
	 * to the methodNode. Return false to cancel the method instrumentation.
	 * @param baseInstructions {@link List}
	 * @param methodNode {@link MethodNode}
	 * @return boolean
	 */
	protected boolean instrumentDistributor(List<AbstractInsnNode> baseInstructions, MethodNode methodNode) {
		return true;
	}

	/**
	 * Called when an exception inside the {@link EventBus} occurs.
	 * @param ex {@link Exception}
	 */
	protected void onException(Exception ex) { }
}