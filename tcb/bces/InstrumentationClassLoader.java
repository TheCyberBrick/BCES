package tcb.bces;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Used to load and instrument classes.
 * Note that once the class has been instrumented it can no longer be casted
 * to that class, unless it's done in the same class loader context. However, it 
 * can still be casted to it's superclass.
 * 
 * @author TCB
 *
 * @param <T> Superclass of the class to be instrumentated
 */
public abstract class InstrumentationClassLoader<T> extends ClassLoader {
	public final Class<? extends T> instrumentedClass;

	/**
	 * Used to load and instrument classes.
	 * Note that once the class has been instrumented it can no longer be casted
	 * to that class, unless it's done in the same class loader context. However, it 
	 * can still be casted to it's superclass.
	 * 
	 * @param dispatcherClass Class to be instrumented
	 */
	public InstrumentationClassLoader(Class<? extends T> dispatcherClass) {
		this.instrumentedClass = dispatcherClass;
	}

	/**
	 * Loads and instrumentates the class and creates an instance
	 * 
	 * @param paramTypes Parameter types
	 * @param params Parameters
	 * @return
	 * @throws ClassNotFoundException
	 * @throws NoSuchMethodException
	 * @throws InvocationTargetException
	 * @throws IllegalAccessException
	 * @throws InstantiationException
	 */
	public T createInstance(Class<?>[] paramTypes, Object... params) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, InstantiationException {
		Class<? extends T> dispatcherClass = this.loadClass();
		Constructor<? extends T> ctor = dispatcherClass.getDeclaredConstructor(paramTypes);
		ctor.setAccessible(true);
		T instance = ctor.newInstance(params);
		return instance;
	}

	/**
	 * Loads and instrumentates the class
	 * 
	 * @return
	 * @throws ClassNotFoundException
	 */
	public Class<? extends T> loadClass() throws ClassNotFoundException {
		return this.loadClass(this.instrumentedClass.getName());
	}

	/**
	 * Loads and instrumentates the class
	 */
	@SuppressWarnings("unchecked")
	@Override
	public Class<? extends T> loadClass(String name) throws ClassNotFoundException {
		return (Class<? extends T>) super.loadClass(name);
	}

	@SuppressWarnings("unchecked")
	@Override
	protected final Class<?extends T> loadClass(String paramString, boolean paramBoolean) throws ClassNotFoundException {
		if(paramString.equals(this.instrumentedClass.getName())) {
			try {
				InputStream is = InstrumentationClassLoader.class.getResourceAsStream("/" + paramString.replace('.', '/') + ".class");
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				int readBytes = 0;
				byte[] buffer = new byte[1024];
				while((readBytes = is.read(buffer)) >= 0) {
					baos.write(buffer, 0, readBytes);
				}
				byte[] bytecode = baos.toByteArray();
				bytecode = this.instrument(bytecode);
				return (Class<T>) this.defineClass(paramString, bytecode, 0, bytecode.length);
			} catch(Exception ex) {
			}
		}
		return (Class<? extends T>) super.loadClass(paramString, paramBoolean);
	}

	/**
	 * Instrumentates the bytecode of the class
	 * 
	 * @param bytecode
	 * @return
	 */
	protected abstract byte[] instrument(byte[] bytecode);
}
