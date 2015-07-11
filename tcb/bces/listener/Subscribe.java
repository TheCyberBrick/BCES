package tcb.bces.listener;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import tcb.bces.EventBus;
import tcb.bces.listener.filter.IFilter;

/**
 * When used inside a class that implements {@link IListener}, the annotated method will be registered when the listener is
 * added to the EventBus using {@link EventBus#addListener(IListener)}
 * 
 * @author TCB
 *
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Subscribe {
	/**
	 * Set this to true if {@link IListener#isEnabled()} should be ignored.
	 * False by default.
	 * @return Boolean
	 */
	boolean forced() default false;

	/**
	 * Sets the listener priority. Higher priorities are called before lower priorities.
	 * The priority is by default 0.
	 * @return Priority
	 */
	int priority() default 0;

	/**
	 * Sets the event filter. If the filter returns false, the event is not passed to
	 * the listener. The filter must implement {@link IFilter} and have a custom no-arg constructor 
	 * or a default no-arg constructor and the class must be public and must not be abstract or interface. 
	 * A SubscriptionException is thrown if no such constructor can be found or if the class
	 * is not public or has invalid modifiers such as abstract or interface.
	 * @return IFilter
	 */
	Class<? extends IFilter> filter() default IFilter.class;
}