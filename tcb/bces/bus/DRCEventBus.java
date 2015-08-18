package tcb.bces.bus;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
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
import org.objectweb.asm.Type;
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

import tcb.bces.bus.compilation.CompilationNode;
import tcb.bces.bus.compilation.Dispatcher;
import tcb.bces.bus.compilation.DispatcherException;
import tcb.bces.event.Event;
import tcb.bces.event.EventCancellable;
import tcb.bces.listener.IListener;
import tcb.bces.listener.Subscribe;
import tcb.bces.listener.SubscriptionException;
import tcb.bces.listener.filter.IFilter;

/**
 * This event bus should only be used for a low amount of listening methods. The 
 * maximum amount of possible listening methods is {@link DRCEventBus#MAX_METHODS}. 
 * Use {@link DRCExpander} for high amounts of listening methods.
 * <p>
 * This event bus supports listener priorities and an event filter.
 * The filter allows the user to filter and cancel any event before 
 * it's passed to its listener. The event itself will not be cancelled. 
 * Filters work on any type of listener or event, even
 * if the event is not a subclass of {@link EventCancellable}.
 * <p>
 * It is recommended to set a custom dispatcher to increase performace. More information
 * about custom dispatchers at {@link Dispatcher}, {@link DRCEventBus#setDispatcher(Class)} and
 * {@link Dispatcher#dispatch()}
 * 
 * @author TCB
 *
 */
public class DRCEventBus implements IEventBus, ICopyable {
	/**
	 * The internal private dispatcher implementation
	 */
	private static final class DispatcherImpl extends Dispatcher {
		@Override
		public final <T extends Event> T dispatchEvent(T event) {
			return event;
		}

		@Override
		public void init(IListener[] listenerArray, IFilter[] filterArray) {
			this.listenerArray = listenerArray;
			this.filterArray = filterArray;
		}
	}

	/**
	 * Internal private data of a verified listening method
	 */
	private static final class InternalMethodEntry {
		private final IListener instance;
		private final Method method;
		private final String methodName;
		private final boolean forced;
		private final int priority;
		private final IFilter filter;
		private final boolean acceptSubclasses;
		private Subscribe handlerAnnotation;
		public InternalMethodEntry(IListener instance, Method method, Subscribe handlerAnnotation, IFilter filter) {
			this.instance = instance;
			this.method = method;
			this.methodName = method.getName();
			this.handlerAnnotation = handlerAnnotation;
			this.forced = handlerAnnotation.forced();
			this.priority = handlerAnnotation.priority();
			this.filter = filter;
			this.acceptSubclasses = handlerAnnotation.acceptSubclasses();
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
		private boolean acceptsSubclasses() {
			return this.acceptSubclasses;
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
		private final Class<? extends Event> eventClass;
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
		private MethodEntry(Class<? extends Event> eventClass, IListener listener, Method method, Subscribe handlerAnnotation) {
			this.eventClass = eventClass;
			this.listener = listener;
			this.method = method;
			this.handlerAnnotation = handlerAnnotation;
		}

		/**
		 * Returns the event type of this {@link MethodEntry}.
		 * @return {@link Event}
		 */
		public Class<? extends Event> getEventClass() {
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
		 * Used to set a custom filter. If this method is used to set
		 * the filter instead of the {@link Subscribe#filter()} annotation member, a custom constructor
		 * can be used in the filter class. 
		 * <p>
		 * The method {@link IFilter#init(IListener)} will not be called if
		 * the filter was set via this method.
		 * @param filter {@link IFilter}
		 * @return {@link MethodEntry}
		 */
		public MethodEntry setFilter(IFilter filter) {
			this.filter = filter;
			return this;
		}
		
		/**
		 * Invokes the listening method by reflection if the filter test is passed.
		 * @param event {@link Event}
		 * @throws InvocationTargetException
		 * @throws IllegalArgumentException
		 * @throws IllegalAccessException
		 */
		public void invoke(Event event) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
			if(this.filter != null && !this.filter.filter(event)) return;
			this.method.invoke(this.listener, event);
		}
	}

	/**
	 * The maximum amount of registered method entries
	 */
	public static final int MAX_METHODS = 256;

	/**
	 * The method names used for reflection and bytecode construction
	 */
	private static final String DISPATCHER_DISPATCH_EVENT_INTERNAL = "dispatchEvent";
	private static final String DISPATCHER_LISTENER_ARRAY = "listenerArray";
	private static final String DISPATCHER_FILTER_ARRAY = "filterArray";
	private static final String IFILTER_FILTER = "filter";
	private static final String IFILTER_INIT = "init";
	private static final String ILISTENER_IS_ENABLED = "isEnabled";
	private static final String IEVENTCANCELLABLE_IS_CANCELLED = "isCancelled";
	private static final String DISPATCHER_DISPATCH = "dispatch";

	/**
	 * Contains all registered listeners
	 */
	private final HashMap<Class<? extends Event>, List<InternalMethodEntry>> registeredEntries = new HashMap<Class<? extends Event>, List<InternalMethodEntry>>();

	/**
	 * Index lookup map, used for creating the bytecode
	 */
	private final HashMap<IListener, Integer> indexLookup = new HashMap<IListener, Integer>();

	/**
	 * Filter index lookup map, used for creating the bytecode
	 */
	private final HashMap<InternalMethodEntry, Integer> filterIndexLookup = new HashMap<InternalMethodEntry, Integer>();

	/**
	 * Contains all registered listeners
	 */
	private IListener[] listenerArray;

	/**
	 * Contains all filters
	 */
	private IFilter[] filterArray;

	/**
	 * The instance of the recompiled {@link DispatcherImpl}
	 */
	private Dispatcher dispatcherImplInstance = null;

	/**
	 * The dispatcher class
	 */
	private Class<? extends Dispatcher> dispatcherClass = DispatcherImpl.class;

	/**
	 * The amount of registered MethodEntries
	 */
	private int methodCount = 0;

	/**
	 * The compilation data
	 */
	private CompilationNode compilationNode = new CompilationNode("Compilation");

	/**
	 * The default EventBus has a limit of {@link DRCEventBus#MAX_METHODS} listening methods. 
	 * If you want to add more listening methods use {@link DRCExpander} instead.
	 * More information at {@link DRCEventBus}.
	 */
	public DRCEventBus() { }

	/**
	 * Private constructor for copy method
	 */
	private DRCEventBus(Class<? extends Dispatcher> dispatcherClass, CompilationNode compilationNode) { 
		this.dispatcherClass = dispatcherClass;
		this.compilationNode = compilationNode;
	}

	/**
	 * Registers a listener to the {@link DRCEventBus}. The event bus has to be updated with {@link DRCEventBus#bind()} for the new listener to take effect.
	 * <p>
	 * The default EventBus has a limit of {@link DRCEventBus#MAX_METHODS} listening methods. If more than {@link DRCEventBus#MAX_METHODS} listening methods 
	 * are registered an IndexOutOfBoundsException is thrown.
	 * <p>
	 * Registering a listener that accepts subclasses of an event will ignore priority sorting.
	 * <p>
	 * A {@link SubscriptionException} is thrown if an invalid method has been found.
	 * @param listener {@link IListener} to register
	 * @throws SubscriptionException
	 * @throws IndexOutOfBoundsException
	 * @return {@link List} read-only list of all found valid method entries
	 */
	public final List<MethodEntry> registerAndAnalyze(IListener listener) throws SubscriptionException, IndexOutOfBoundsException {
		List<MethodEntry> entryList = DRCEventBus.analyzeListener(listener);
		if(this.methodCount > MAX_METHODS) {
			throw new IndexOutOfBoundsException("Too many registered methods. Max: " + MAX_METHODS);
		} else if(this.methodCount + entryList.size() > MAX_METHODS) {
			throw new IndexOutOfBoundsException("Registering this listener exceeds the maximum " +
					"amount of registered methods. " +
					"Current: " + this.methodCount +
					" Max: " + MAX_METHODS);
		}
		for(MethodEntry entry : entryList) {
			Class<? extends Event> paramType = (Class<? extends Event>) entry.getEventClass();
			List<InternalMethodEntry> lle = this.registeredEntries.get(paramType);
			if(lle == null) {
				lle = new ArrayList<InternalMethodEntry>();
				this.registeredEntries.put(paramType, lle);
			}
			lle.add(new InternalMethodEntry(listener, entry.getMethod(), entry.getHandlerAnnotation(), entry.getFilter()));
			this.methodCount++;
		}
		this.updateArray();
		return Collections.unmodifiableList(entryList);
	}

	/**
	 * Registers a listener to the {@link DRCEventBus}. The event bus has to be updated with {@link DRCEventBus#bind()} for the new listener to take effect.
	 * <p>
	 * The default EventBus has a limit of {@link DRCEventBus#MAX_METHODS} listening methods. If more than {@link DRCEventBus#MAX_METHODS} listening methods 
	 * are registered an {@link IndexOutOfBoundsException} is thrown.
	 * <p>
	 * Registering a listener that accepts subclasses of an event will ignore priority sorting.
	 * <p>
	 * A {@link SubscriptionException} is thrown if an invalid method has been found.
	 * @param listener {@link IListener} to register
	 * @throws SubscriptionException
	 * @throws IndexOutOfBoundsException
	 */
	@Override
	public final void register(IListener listener) throws SubscriptionException, IndexOutOfBoundsException {
		this.registerAndAnalyze(listener);
	}

	/**
	 * Verifies the specified listener and returns a list of all found valid method entries.
	 * A SubscriptionException is thrown if an invalid method has been found.
	 * @param listener {@link IListener}
	 * @throws SubscriptionException
	 * @return {@link List} of all found valid method entries
	 */
	public static final List<MethodEntry> analyzeListener(IListener listener) throws SubscriptionException {
		List<MethodEntry> entryList = new ArrayList<MethodEntry>();
		Method[] listenerMethods = listener.getClass().getDeclaredMethods();
		for(Method method : listenerMethods) {
			if(method.getParameterTypes().length != 1 || !Event.class.isAssignableFrom(method.getParameterTypes()[0])) continue;
			Subscribe handlerAnnotation = method.getAnnotation(Subscribe.class);
			if(handlerAnnotation != null) {
				int methodModifiers = method.getModifiers();
				if((methodModifiers & Modifier.STATIC) != 0 ||
						(methodModifiers & Modifier.ABSTRACT) != 0 ||
						(methodModifiers & Modifier.PRIVATE) != 0 ||
						(methodModifiers & Modifier.PROTECTED) != 0) {
					throw new SubscriptionException("Invalid method modifiers for method " + listener.getClass().getName() + "#" + method.getName());
				}
				if(method.getReturnType() != void.class) {
					throw new SubscriptionException("Return type is not void for method " + listener.getClass().getName() + "#" + method.getName());
				}
				@SuppressWarnings("unchecked")
				Class<? extends Event> paramType = (Class<? extends Event>) method.getParameterTypes()[0];
				if(paramType.isInterface()) {
					throw new SubscriptionException("Parameter for method cannot be an interface: " + listener.getClass().getName() + "#" + method.getName());
				}
				entryList.add(DRCEventBus.initFilter(new MethodEntry(paramType, listener, method, handlerAnnotation)));
			}
		}
		return entryList;
	}

	/**
	 * Registers a single {@link MethodEntry} to the {@link DRCEventBus}. The event bus has to be updated with {@link DRCEventBus#bind()} for the new {@link IListener} to take effect.
	 * <p>
	 * Registering a listener that accepts subclasses of an event will ignore priority sorting.
	 * <p>
	 * The default event bus has a limit of {@link DRCEventBus#MAX_METHODS} method entries. If more than {@link DRCEventBus#MAX_METHODS} method entries are registered an {@link IndexOutOfBoundsException} is thrown.
	 * @param entry {@link MethodEntry} to register
	 * @throws IndexOutOfBoundsException
	 */
	public final void register(MethodEntry entry) throws IndexOutOfBoundsException {
		if(this.methodCount > MAX_METHODS) {
			throw new IndexOutOfBoundsException("Too many registered methods. Max: " + MAX_METHODS);
		} else if(this.methodCount >= MAX_METHODS) {
			throw new IndexOutOfBoundsException("Registering this method entry exceeds the maximum " +
					"amount of registered methods. Max: " + MAX_METHODS);
		}
		List<InternalMethodEntry> lle = this.registeredEntries.get(entry.getEventClass());
		if(lle == null) {
			lle = new ArrayList<InternalMethodEntry>();
			this.registeredEntries.put(entry.getEventClass(), lle);
		}
		lle.add(new InternalMethodEntry(entry.getListener(), entry.getMethod(), entry.getHandlerAnnotation(), entry.getFilter()));
		this.methodCount++;
		this.updateArray();
	}

	/**
	 * Returns a read-only list of all registered method entries.
	 * @return {@link List} read-only list of all registered method entries
	 */
	public final List<MethodEntry> getMethodEntries() {
		List<MethodEntry> result = new ArrayList<MethodEntry>();
		for(Entry<Class<? extends Event>, List<InternalMethodEntry>> e : this.registeredEntries.entrySet()) {
			for(InternalMethodEntry lme : e.getValue()) {
				result.add(new MethodEntry(e.getKey(), lme.getInstance(), lme.getMethod(), lme.getHandlerAnnotation()));
			}
		}
		return Collections.unmodifiableList(result);
	}

	/**
	 * Unregisters an {@link IListener} from the {@link DRCEventBus}. The event bus has to be updated with {@link DRCEventBus#bind()} for this to take effect.
	 * @param listener {@link IListener} to unregister
	 */
	@Override
	public final void unregister(IListener listener) {
		Method[] listenerMethods = listener.getClass().getDeclaredMethods();
		for(Method method : listenerMethods) {
			if(method.getParameterTypes().length != 1 || !Event.class.isAssignableFrom(method.getParameterTypes()[0])) continue;
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
				Class<? extends Event> paramType = (Class<? extends Event>) method.getParameterTypes()[0];
				List<InternalMethodEntry> lle = this.registeredEntries.get(paramType);
				if(lle == null) {
					this.registeredEntries.remove(paramType);
					return;
				}
				InternalMethodEntry toRemove = null;
				for(InternalMethodEntry le : lle) {
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
	 * Unregisters a {@link MethodEntry} from the {@link DRCEventBus}. The event bus has to be updated with {@link DRCEventBus#bind()} for this to take effect.
	 * @param methodEntry {@link MethodEntry} to unregister
	 */
	public final void unregister(MethodEntry methodEntry) {
		List<InternalMethodEntry> lle = this.registeredEntries.get(methodEntry.getEventClass());
		if(lle == null) {
			this.registeredEntries.remove(methodEntry.getEventClass());
			return;
		}
		InternalMethodEntry toRemove = null;
		for(InternalMethodEntry le : lle) {
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
	 * with the {@link Subscribe#filter()} annotation member.
	 * <p>
	 * Throws a {@link SubscriptionException} if the filter class is abstract or interface
	 * or doesn't have a no-arg constructor.
	 * @param entry {@link MethodEntry} to add the filter to
	 * @throws SubscriptionException
	 * @return {@link MethodEntry}
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
			throw new SubscriptionException("Filter class must not be abstract or interface: " + filterClass.getName());
		}
		if((classModifiers & Modifier.PUBLIC) == 0) {
			throw new SubscriptionException("Filter class must be public: " + filterClass.getName());
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
			throw new SubscriptionException("No valid no-arg constructor was found in the filter class: " + filterClass.getName(), ex);
		}
		return entry;
	}

	/**
	 * Removes all registered listeners from this {@link DRCEventBus}.
	 */
	public final void clear() {
		this.registeredEntries.clear();
		this.indexLookup.clear();
		this.filterIndexLookup.clear();
		this.updateArray();
	}

	/**
	 * Returns a read-only list of all registered listeners.
	 * @return {@link List} read-only list of all registered listeners
	 */
	public final List<IListener> getListeners() {
		ArrayList<IListener> listeners = new ArrayList<IListener>();
		for(Entry<Class<? extends Event>, List<InternalMethodEntry>> e : this.registeredEntries.entrySet()) {
			for(InternalMethodEntry le : e.getValue()) {
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
		for(Entry<Class<? extends Event>, List<InternalMethodEntry>> e : this.registeredEntries.entrySet()) {
			List<InternalMethodEntry> listenerEntryList = e.getValue();
			for(InternalMethodEntry listenerEntry : listenerEntryList) {
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
	 * Returns the recompiled version of {@link DispatcherImpl}
	 * @return {@link Dispatcher}
	 */
	private final Dispatcher getCompiledDispatcher() {
		if(this.dispatcherImplInstance == null)  {
			this.dispatcherImplInstance = (Dispatcher)this.compileDispatcher();
		}
		return this.dispatcherImplInstance;
	}

	/**
	 * Recompiles {@link DispatcherImpl} with all registered listeners.
	 * @return {@link Dispatcher}
	 */
	private final Dispatcher compileDispatcher() {
		try {
			Comparator<InternalMethodEntry> prioritySorter = new Comparator<InternalMethodEntry>() {
				@Override
				public int compare(InternalMethodEntry e1, InternalMethodEntry e2) {
					return e2.getPriority() - e1.getPriority();
				}
			};
			for(List<InternalMethodEntry> lle : this.registeredEntries.values()) {
				Collections.sort(lle, prioritySorter);
			}
			ClassLoader customClassLoader = new ClassLoader() {
				@SuppressWarnings("unchecked")
				@Override
				protected Class<?> loadClass(String paramString, boolean paramBoolean) throws ClassNotFoundException {
					if(paramString.equals(DRCEventBus.this.dispatcherClass.getName())) {
						try {
							InputStream is = DRCEventBus.class.getResourceAsStream("/" + paramString.replace('.', '/') + ".class");
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
							CompilationNode mainNode = new CompilationNode(DRCEventBus.this.toString());
							DRCEventBus.this.compilationNode.addChild(mainNode);
							for(MethodNode methodNode : (List<MethodNode>) classNode.methods) {
								if(methodNode.name.equals(DISPATCHER_DISPATCH_EVENT_INTERNAL)) {
									instrumentDispatcherMethod(methodNode, true, mainNode);
								}
							}
							DRCEventBus.this.compilationNode = mainNode;
							ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
							classNode.accept(classWriter);
							bytecode = classWriter.toByteArray();
							return this.defineClass(paramString, bytecode, 0, bytecode.length);
						} catch(Exception ex) {
							throw new DispatcherException("Could not compile dispatching methods", ex);
						}
					}
					return super.loadClass(paramString, paramBoolean);
				}
			};
			Class<?> dispatcherClass = customClassLoader.loadClass(this.dispatcherClass.getName());
			Constructor<?> ctor = dispatcherClass.getDeclaredConstructor();
			ctor.setAccessible(true);
			Dispatcher dispatcher = (Dispatcher) ctor.newInstance();
			ctor.setAccessible(false);
			dispatcher.init(this.listenerArray, this.filterArray);
			return dispatcher;
		} catch(Exception ex) {
			throw new DispatcherException("Could not initialize dispatcher", ex);
		}
	}

	/**
	 * Modifies the internal event dispatching method.
	 * @param methodNode {@link MethodNode}
	 */
	@SuppressWarnings("unchecked")
	private final void instrumentDispatcherMethod(MethodNode methodNode, boolean cancellable, CompilationNode mainNode) {
		InsnList methodInstructionSet = methodNode.instructions;
		ArrayList<AbstractInsnNode> instructionSet = new ArrayList<AbstractInsnNode>();
		Iterator<AbstractInsnNode> it = methodInstructionSet.iterator();
		AbstractInsnNode insn;
		AbstractInsnNode implementationNode = null;
		boolean isNodeDispatcher = false;
		while((insn = it.next()) != null && it.hasNext()) {
			boolean isReturn = this.dispatcherClass == DispatcherImpl.class && 
					(insn.getOpcode() == Opcodes.IRETURN || 
					insn.getOpcode() == Opcodes.LRETURN ||
					insn.getOpcode() == Opcodes.RETURN ||
					insn.getOpcode() == Opcodes.ARETURN ||
					insn.getOpcode() == Opcodes.DRETURN ||
					insn.getOpcode() == Opcodes.FRETURN);
			boolean isDispatcherMethod = this.dispatcherClass != DispatcherImpl.class && insn.getOpcode() == Opcodes.INVOKESTATIC && ((MethodInsnNode)insn).name.equals(DISPATCHER_DISPATCH) && ((MethodInsnNode)insn).owner.equals(Dispatcher.class.getName().replace(".", "/"));
			//Only implement first, throw an error if there are multiple implementations
			if(isDispatcherMethod && implementationNode != null) {
				throw new DispatcherException("The dispatching implementation Dispatcher#dispatch() can only be used once per method");
			}
			if(isReturn || isDispatcherMethod) {
				isNodeDispatcher = isDispatcherMethod;
				implementationNode = insn;
			}
		}

		//No implementation or return node
		if(implementationNode == null) {
			return;
		}

		LabelNode exitNode = new LabelNode();

		CompilationNode eventNode = new CompilationNode((cancellable ? "Cancellable" : "Non cancellable") + " events");
		mainNode.addChild(eventNode);

		for(Entry<Class<? extends Event>, List<InternalMethodEntry>> e : this.registeredEntries.entrySet()) {
			String eventClassName = e.getKey().getName().replace(".", "/");

			CompilationNode eventClassNode = new CompilationNode(e.getKey().getName());
			eventNode.addChild(eventClassNode);

			/*
			 * Pseudo code, runs for every listener method:
			 * 
			 * //Optional check, only present if subclasses are also accepted
			 * if(event instanceof listenerArray[n].eventType) {
			 * //Replacement for when subclasses are not accepted
			 * if(event.getClass() == listenerArray[n].eventType) {
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

			boolean hasNonSCA = false;
			boolean hasSCA = false;

			//Contains all non SCA (SubClasses Accepted) listener entries
			List<InternalMethodEntry> listNonSCA = new ArrayList<InternalMethodEntry>();

			//Contains all SCA listener entries
			List<InternalMethodEntry> listSCA = new ArrayList<InternalMethodEntry>();

			//Check SCA and non SCA listener entries
			for(InternalMethodEntry listenerEntry : e.getValue()) {
				if(!listenerEntry.acceptsSubclasses()) {
					hasNonSCA = true;
					listNonSCA.add(listenerEntry);
					continue;
				}
				hasSCA = true;
				listSCA.add(listenerEntry);
			}

			//Instrument dispatcher methods that accept subclasses
			if(hasSCA) {
				CompilationNode scaNode = new CompilationNode("SCA Listeners");
				eventClassNode.addChild(scaNode);

				//Fail label, jumped to if instanceof fails
				LabelNode instanceofFailLabelNode = new LabelNode();

				//if(event instanceof eventclass  == false) -> jump to instanceofFailLabelNode
				instructionSet.add(new IntInsnNode(Opcodes.ALOAD, 1));
				instructionSet.add(new TypeInsnNode(Opcodes.INSTANCEOF, eventClassName));
				instructionSet.add(new JumpInsnNode(Opcodes.IFEQ, instanceofFailLabelNode));

				for(InternalMethodEntry listenerEntry : listSCA) {
					this.instrumentDispatcher(instructionSet, listenerEntry, e, 
							eventClassName, cancellable, exitNode, 
							instanceofFailLabelNode, scaNode);
				}

				//failLabelNode
				instructionSet.add(instanceofFailLabelNode);
			}

			//Instrument dispatcher methods that don't accept subclasses
			if(hasNonSCA) {
				CompilationNode nonScaNode = new CompilationNode("Non SCA Listeners");
				eventClassNode.addChild(nonScaNode);

				//Fail label, jumped to if class comparison fails
				LabelNode classCompareFailLabelNode = new LabelNode();

				//if(event.getClass() != listenerArray[n].eventType) -> jump to classCompareFailLabelNode
				instructionSet.add(new IntInsnNode(Opcodes.ALOAD, 1));
				instructionSet.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;", false));
				String eventClassNameClass = "L" + eventClassName + ";";
				instructionSet.add(new LdcInsnNode(Type.getType(eventClassNameClass)));
				instructionSet.add(new JumpInsnNode(Opcodes.IF_ACMPNE, classCompareFailLabelNode));

				for(InternalMethodEntry listenerEntry : listNonSCA) {
					this.instrumentDispatcher(instructionSet, listenerEntry, e, 
							eventClassName, cancellable, exitNode, 
							classCompareFailLabelNode, nonScaNode);
				}

				//failLabelNode
				instructionSet.add(classCompareFailLabelNode);
			}
		}

		//exitNode
		instructionSet.add(exitNode);

		//Instrumentation callback to let the user modify the dispatching bytecode
		if(this.instrumentDispatcher(instructionSet, methodNode)) {
			for(AbstractInsnNode insnNode : instructionSet) {
				methodInstructionSet.insertBefore(implementationNode, insnNode);
			}
			if(isNodeDispatcher) {
				methodInstructionSet.remove(implementationNode);
			}
		}
		methodNode.visitMaxs(0, 0);
	}

	/**
	 * Instruments the dispatcher for a listener entry
	 * @param instructionSet
	 * @param listenerEntry
	 * @param entryList
	 * @param eventClassName
	 * @param cancellable
	 * @param exitLabelNode
	 * @param failailLabelNode
	 */
	private final void instrumentDispatcher(ArrayList<AbstractInsnNode> instructionSet, InternalMethodEntry listenerEntry, 
			Entry<Class<? extends Event>, List<InternalMethodEntry>> entryList, String eventClassName, boolean cancellable,
			LabelNode exitLabelNode, LabelNode failailLabelNode, CompilationNode compilationNode) {
		String className = this.dispatcherClass.getName().replace(".", "/");
		String fieldType = "[L" + IListener.class.getName().replace(".", "/") + ";";
		String listenerClassName = listenerEntry.getInstance().getClass().getName().replace(".", "/");
		String listenerMethodName = listenerEntry.getMethodName();
		String listenerMethodType = "(L" + entryList.getKey().getName().replace(".", "/") + ";)V";
		int listenerIndex = this.indexLookup.get(listenerEntry.getInstance());

		CompilationNode listenerNode = new CompilationNode(listenerEntry.getInstance().getClass().getName() + "#" + listenerEntry.methodName);
		compilationNode.addChild(listenerNode);

		CompilationNode indexNode = new CompilationNode("Class index: " + listenerIndex);
		listenerNode.addChild(indexNode);

		CompilationNode priorityNode = new CompilationNode("Priority: " + listenerEntry.getPriority());
		listenerNode.addChild(priorityNode);

		//Used for filter fail jump or subclass check fail jump (only if subclasses are not accepted)
		LabelNode entryFailLabelNode = null;

		//Only implement if filter is not default IFilter class
		//if(!filterArray[n].filter(listenerArray[p], event)) -> jump to entryFailLabelNode
		if(listenerEntry.getFilter() != null) {
			entryFailLabelNode = new LabelNode();
			int filterIndex = this.filterIndexLookup.get(listenerEntry);
			String filterClassName = listenerEntry.getFilter().getClass().getName().replace(".", "/");
			String filterFieldType = "[L" + IFilter.class.getName().replace(".", "/") + ";";
			String filterMethodType = "(L" + Event.class.getName().replace(".", "/") + ";)Z";
			//load instance of this class ('this' keyword)
			instructionSet.add(new IntInsnNode(Opcodes.ALOAD, 0));
			//get filter array
			instructionSet.add(new FieldInsnNode(Opcodes.GETFIELD, className, DISPATCHER_FILTER_ARRAY, filterFieldType));
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
			instructionSet.add(new JumpInsnNode(Opcodes.IFEQ, entryFailLabelNode));
		}

		if(cancellable) {
			LabelNode instanceofEventCancellableFailLabel = new LabelNode();
			//load event
			instructionSet.add(new IntInsnNode(Opcodes.ALOAD, 1));
			//event instanceof EventCancellable
			instructionSet.add(new TypeInsnNode(Opcodes.INSTANCEOF, EventCancellable.class.getName().replace(".", "/")));
			//if(event instanceof EventCancellable == false) -> jump to instanceofEventCancellableFailLabel
			instructionSet.add(new JumpInsnNode(Opcodes.IFEQ, instanceofEventCancellableFailLabel));
			//load event
			instructionSet.add(new IntInsnNode(Opcodes.ALOAD, 1));
			//cast event to EventCancellable
			instructionSet.add(new TypeInsnNode(Opcodes.CHECKCAST, eventClassName));
			//invoke EventCancellable#isCancelled
			instructionSet.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, eventClassName, IEVENTCANCELLABLE_IS_CANCELLED, "()Z", false));
			//if(!EventCancellable#isCancelled()) -> jump to exitNode
			instructionSet.add(new JumpInsnNode(Opcodes.IFNE, exitLabelNode));
			instructionSet.add(instanceofEventCancellableFailLabel);
		}

		//Only implement IListener#isEnabled() check if Receiver#forced() is false
		if(!listenerEntry.isForced()) {
			////////////////////////////// Check if listener is enabled //////////////////////////////
			//load instance of this class ('this' keyword)
			instructionSet.add(new IntInsnNode(Opcodes.ALOAD, 0));
			//get listener array
			instructionSet.add(new FieldInsnNode(Opcodes.GETFIELD, className, DISPATCHER_LISTENER_ARRAY, fieldType));
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
			instructionSet.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, listenerClassName, ILISTENER_IS_ENABLED, "()Z", false));
			//jump to failLabelNode if returned boolean is false
			instructionSet.add(new JumpInsnNode(Opcodes.IFEQ, failailLabelNode));
		}

		///////////////////////////////// Invoke listener method /////////////////////////////////
		//load instance of this class ('this' keyword)
		instructionSet.add(new IntInsnNode(Opcodes.ALOAD, 0));
		//get listener array
		instructionSet.add(new FieldInsnNode(Opcodes.GETFIELD, className, DISPATCHER_LISTENER_ARRAY, fieldType));
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

		CompilationNode forcedNode = new CompilationNode((!listenerEntry.isForced() ? "[ ]" : "[x]") + " Forced");
		listenerNode.addChild(forcedNode);

		if(listenerEntry.getFilter() != null) {
			CompilationNode filterNode = new CompilationNode("[x] Filter");
			listenerNode.addChild(filterNode);
			CompilationNode filterNameNode = new CompilationNode(listenerEntry.getFilter().getClass().getName());
			filterNode.addChild(filterNameNode);
		} else {
			CompilationNode filterNode = new CompilationNode("[ ] Filter");
			listenerNode.addChild(filterNode);
		}

		//Only implement if filter is not default IFilter class or subclasses are not accepted
		if(entryFailLabelNode != null) {
			instructionSet.add(entryFailLabelNode);
		}
	}

	/**
	 * Posts an {@link Event} and returns the posted event.
	 * @param event {@link Event} to dispatch
	 * @return {@link Event} the posted event
	 */
	@Override
	public <T extends Event> T post(T event) {
		return this.getCompiledDispatcher().dispatchEvent(event);
	}

	/**
	 * Compiles the internal event dispatcher and binds all registered listeners. Required for new method entries or dispatcher to take effect.
	 * For optimal performance this method should be called after all listeners have been
	 * registered.
	 */
	public void bind() {
		this.dispatcherImplInstance = (Dispatcher)this.compileDispatcher();
	}

	/**
	 * Sets the event dispatcher class.
	 * Set to null to enable the internal dispatcher of {@link DRCEventBus}.
	 * <p>
	 * More information about custom dispatchers at {@link Dispatcher} and {@link Dispatcher#dispatch()}
	 * <p>
	 * The event bus has to be updated with {@link DRCEventBus#bind()} for the new dispatcher to take effect.
	 * @param dispatcherClass {@link Dispatcher}
	 * @throws DispatcherException
	 */
	public final void setDispatcher(Class<? extends Dispatcher> dispatcherClass) throws DispatcherException {
		try {
			if(dispatcherClass != null) {
				if(dispatcherClass.getDeclaredConstructor() != null) {
					this.dispatcherClass = dispatcherClass;
				}
			} else {
				this.dispatcherClass = DispatcherImpl.class;
			}
		} catch(Exception ex) {
			throw new DispatcherException("Could not set dispatcher", ex);
		}
	}
	
	/**
	 * Returns the compiled dispatcher object.
	 * <p>
	 * <b>Important</b>: The dispatcher object will not be castable to its own
	 * class as it becomes a different type after the compilation. Casting to a
	 * superclass however still works. If there are any methods or fields specific to that
	 * dispatcher class that have to be accessed from outside, the dispatcher has to be
	 * casted to a superclass or interface (which the dispatcher must extend or implement)
	 * of which those specific methods or fields, that have to be accessed from outside,
	 * can be inherited from or implemented.
	 * @return {@link Dispatcher} the dispatcher
	 */
	public final Dispatcher getDispatcher() {
		//Check by name because it's a different class after compilation
		if(this.dispatcherImplInstance != null && this.dispatcherImplInstance.getClass().getName().equals(DispatcherImpl.class.getName())) {
			return null; 
		}
		return this.dispatcherImplInstance;
	}

	/**
	 * Returns a new instance of this {@link DRCEventBus} with the same
	 * properties.
	 * Used in {@link DRCExpander} to create copies of
	 * the given bus.
	 * @return {@link IEventBus}
	 */
	@Override
	public IEventBus copyBus() {
		return new DRCEventBus(this.dispatcherClass, this.compilationNode);
	}

	/**
	 * The compilation node contains a tree structure that describes the compiled dispatcher method.
	 * @return {@link CompilationNode}
	 */
	public final CompilationNode getCompilationNode() {
		return this.compilationNode;
	}

	/**
	 * Called when the dispatching method is being instrumented. Additional instructions can be added to the baseInstructions or directly
	 * to the methodNode. Return false to cancel the method instrumentation.
	 * @param baseInstructions {@link List}
	 * @param methodNode {@link MethodNode}
	 * @param compilationNode {@link CompilationNode}
	 * @return boolean false to cancel the instrumentation
	 */
	protected boolean instrumentDispatcher(List<AbstractInsnNode> baseInstructions, MethodNode methodNode) {
		return true;
	}
	
	public static DRCExpander<DRCEventBus> createUnlimitedEventBus() {
		return new DRCExpander<DRCEventBus>(new DRCEventBus());
	}
}