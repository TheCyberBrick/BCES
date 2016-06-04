package tcb.bces.listener;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import tcb.bces.bus.DRCEventBus;
import tcb.bces.listener.filter.IFilter;

/**
 * When used inside a class that implements {@link IListener}, the annotated method will be registered when the listener is
 * added to the EventBus using {@link DRCEventBus#addListener(IListener)}
 * 
 * @author TCB
 *
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Subscribe {
	/**
	 * Set this to true if subclasses of the event should be accepted.
	 * @return boolean
	 */
	boolean acceptSubclasses() default false;

	/**
	 * Set this to true if {@link IListener#isEnabled()} should be ignored.
	 * False by default.
	 * @return boolean
	 */
	boolean forced() default false;

	/**
	 * Sets the listener priority. Higher priorities are called before lower priorities.
	 * The priority is by default 0.
	 * Priority sorting is neglected if the listener entry accepts subclasses.
	 * @return int
	 */
	int priority() default 0;

	/**
	 * Sets the event filter. If the filter returns false, the event is not passed to
	 * the listener. The filter must implement {@link IFilter} and have a custom no-arg constructor 
	 * or a default no-arg constructor and the class must be public and must not be abstract or interface.
	 * The constructor may have any visibility modifier.
	 * A SubscriptionException is thrown if no such constructor can be found or if the class
	 * is not public or has invalid modifiers such as abstract or interface.
	 * @return {@link Class}
	 */
	Class<? extends IFilter> filter() default IFilter.class;
}