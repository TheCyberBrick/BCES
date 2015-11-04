package tcb.bces.hook;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import tcb.bces.BytecodeHelper;
import tcb.bces.InstrumentationClassLoader;

/**
 * The Hook class is used to create hooks that can dynamically invoke methods
 * without using the Reflection API for the invocation. It is slightly faster than
 * using the Reflection API.
 * 
 * @author TCB
 *
 */
public final class Hook {
	private Hook() { }

	private static final String METHOD_NAME_INVOKE = "invoke";

	public static class InvalidArgumentsException extends RuntimeException {
		private static final long serialVersionUID = -9170715843757768051L;

		private InvalidArgumentsException(Throwable ex) {
			super("Invalid arguments", ex);
		}
	}

	public static abstract class HookContainer {
		protected Hook hook;
		public abstract Object invoke(Object... args);
		public Object getInstance() {
			return this.hook.instance;
		}
	}

	static final class HookContainerImpl extends HookContainer {
		private HookContainerImpl(Hook hook) {
			this.hook = hook;
		}
		@Override
		public Object invoke(Object... args) {
			@SuppressWarnings("unused")
			Object instance = this.getInstance();
			return null;
		}
	}

	private HookContainer container;
	private Object instance;

	public static final class HookBuilder {
		private final Class<?> clazz;
		private final Method method;
		private boolean staticHook = false;

		private HookBuilder(Class<?> clazz, Method method) {
			this.clazz = clazz;
			this.method = method;
		}

		/**
		 * Sets the hook method to static
		 * 
		 * @return
		 */
		public HookBuilder setStatic() {
			this.staticHook = true;
			return this;
		}

		/**
		 * Sets the hook method to static modifier
		 * 
		 * @return
		 */
		public HookBuilder setStatic(boolean staticModifier) {
			this.staticHook = staticModifier;
			return this;
		}

		/**
		 * Compiles the hook
		 * 
		 * @return
		 * @throws Exception
		 */
		public Hook compile() throws Exception {
			//Instrumentation classloader
			InstrumentationClassLoader<HookContainer> instrumentationClassLoader = new InstrumentationClassLoader<HookContainer>(HookContainerImpl.class) {
				@SuppressWarnings("unchecked")
				@Override
				protected byte[] instrument(byte[] bytecode) {
					ClassReader classReader = new ClassReader(bytecode);
					ClassNode classNode = new ClassNode();
					classReader.accept(classNode, ClassReader.SKIP_FRAMES);
					methodNodeLoop:
						for(MethodNode methodNode : (List<MethodNode>) classNode.methods) {
							if(methodNode.name.equals(METHOD_NAME_INVOKE)) {
								InsnList methodInstructionSet = methodNode.instructions;
								Iterator<AbstractInsnNode> it = methodInstructionSet.iterator();
								AbstractInsnNode insn;
								while((insn = it.next()) != null && it.hasNext()) {
									boolean isReturn = (insn.getOpcode() == Opcodes.IRETURN || 
											insn.getOpcode() == Opcodes.LRETURN ||
											insn.getOpcode() == Opcodes.RETURN ||
											insn.getOpcode() == Opcodes.ARETURN ||
											insn.getOpcode() == Opcodes.DRETURN ||
											insn.getOpcode() == Opcodes.FRETURN);
									if(isReturn) {
										String className = BytecodeHelper.getClassType(HookBuilder.this.clazz);

										if(!HookBuilder.this.staticHook) {
											methodInstructionSet.insertBefore(insn, new IntInsnNode(Opcodes.ALOAD, 2));
											methodInstructionSet.insertBefore(insn, new TypeInsnNode(Opcodes.CHECKCAST, className));
										}

										Method method = HookBuilder.this.method;
										Class<?>[] methodParams = method.getParameterTypes();
										for(int i = 0; i < methodParams.length; i++) {
											methodInstructionSet.insertBefore(insn, new IntInsnNode(Opcodes.ALOAD, 1));
											methodInstructionSet.insertBefore(insn, BytecodeHelper.getOptimizedIndex(i));
											methodInstructionSet.insertBefore(insn, new InsnNode(Opcodes.AALOAD));
											Class<?> type = methodParams[i];
											if(!type.isPrimitive()) {
												methodInstructionSet.insertBefore(insn, new TypeInsnNode(Opcodes.CHECKCAST, BytecodeHelper.getClassType(methodParams[i])));
											} else {
												AbstractInsnNode[] converter = BytecodeHelper.getPrimitivesArgumentConverter(type);
												methodInstructionSet.insertBefore(insn, converter[0]);
												methodInstructionSet.insertBefore(insn, converter[1]);
											}
										}

										methodInstructionSet.insertBefore(insn, new MethodInsnNode(HookBuilder.this.staticHook ? Opcodes.INVOKESTATIC : Opcodes.INVOKEVIRTUAL, className, HookBuilder.this.method.getName(), BytecodeHelper.getMethodType(method), false));

										if(method.getReturnType() != void.class) {
											if(method.getReturnType().isPrimitive()) {
												methodInstructionSet.insertBefore(insn, BytecodeHelper.getPrimitivesReturnConverter(method.getReturnType()));
											}

											methodInstructionSet.insertBefore(insn, new InsnNode(Opcodes.ARETURN));
											it.remove();
										}

										break methodNodeLoop;
									}
								}
							}
						}
					ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
					classNode.accept(classWriter);
					return classWriter.toByteArray();
				}
			};
			Hook hook = new Hook();
			HookContainer container = instrumentationClassLoader.createInstance(new Class[]{Hook.class}, hook);
			hook.container = container;
			return hook;
		}

		/**
		 * Compiles the hook and catches any errors
		 * 
		 * @return
		 */
		public Hook compileSafe() {
			try {
				return this.compile();
			} catch(Throwable ex) { }
			return null;
		}
	}

	/**
	 * Creates a hook builder
	 * 
	 * @param clazz Class
	 * @param method Method
	 * @return
	 */
	public static HookBuilder create(Class<?> clazz, Method method) {
		return new HookBuilder(clazz, method);
	}

	/**
	 * Creates a hook builder
	 * 
	 * @param clazz Class
	 * @param methodName Method name
	 * @param params Method parameters
	 * @return
	 * @throws NoSuchMethodException
	 * @throws SecurityException
	 */
	public static HookBuilder create(Class<?> clazz, String methodName, Class<?>... params) throws NoSuchMethodException, SecurityException {
		return new HookBuilder(clazz, clazz.getDeclaredMethod(methodName, params));
	}

	/**
	 * Creates a hook builder and catches any errors
	 * 
	 * @param clazz Class
	 * @param methodName Method name
	 * @param params Method parameters
	 * @return Returns null if failed
	 */
	public static HookBuilder createSafe(Class<?> clazz, String methodName, Class<?>... params) {
		try {
			return create(clazz, methodName, params);
		} catch(Throwable ex) { }
		return null;
	}

	/**
	 * Creates and compiles the hook and catches any errors
	 * 
	 * @param staticModifier True for static hooks
	 * @param clazz Class
	 * @param methodName Method name
	 * @param params Method parameters
	 * @return Returns null if failed
	 */
	public static Hook createAndCompileSafe(boolean staticModifier, Class<?> clazz, String methodName, Class<?>... params) {
		try {
			return create(clazz, methodName, params).setStatic(staticModifier).compile();
		} catch (Throwable e) { }
		return null;
	}

	/**
	 * Creates and compiles the hook and catches any errors
	 * 
	 * @param instance Instance (use null for a static hook)
	 * @param clazz Class
	 * @param methodName Method name
	 * @param params Method parameters
	 * @return Returns null if failed
	 */
	public static Hook createAndCompileSafe(Object instance, Class<?> clazz, String methodName, Class<?>... params) {
		try {
			return create(clazz, methodName, params).setStatic(instance == null).compile().setInstance(instance);
		} catch (Throwable e) { }
		return null;
	}

	/**
	 * Creates and compiles the static hook and catches any errors
	 * 
	 * @param clazz Class
	 * @param methodName Method name
	 * @param params Method parameters
	 * @return Returns null if failed
	 */
	public static Hook createAndCompileSafe(Class<?> clazz, String methodName, Class<?>... params) {
		try {
			return create(clazz, methodName, params).setStatic(true).compile();
		} catch (Throwable e) { }
		return null;
	}

	/**
	 * Invokes the method. Make sure you explicitly cast all arguments to
	 * avoid any ambiguities
	 * 
	 * @param args Method arguments
	 */
	public Object invoke(Object... args) {
		try {
			return this.container.invoke(args);
		} catch(IncompatibleClassChangeError ex) {
			throw ex;
		} catch(Throwable ex) {
			throw new InvalidArgumentsException(ex);
		}
	}

	/**
	 * Invokes the method and catches any errors. Make sure you explicitly
	 * cast all arguments to avoid any ambiguities
	 * 
	 * @param args Method arguments
	 * @return Returns true if successful
	 */
	public Object invokeSafe(Object... args) {
		try {
			return this.invoke(args);
		} catch(Throwable ex) { }
		return null;
	}

	/**
	 * Invokes the method of the hook and catches any errors. Make sure
	 * you explicitly cast all arguments to avoid any ambiguities
	 * 
	 * @param hook Hook
	 * @param args Method arguments
	 * @return Returns true if successful
	 */
	public static Object invokeSafe(Hook hook, Object... args) {
		try {
			return hook.invoke(args);
		} catch(Exception ex) { }
		return null;
	}

	/**
	 * Sets the instance
	 * 
	 * @param obj Instace
	 * @return
	 */
	public Hook setInstance(Object obj) {
		this.instance = obj;
		return this;
	}
}