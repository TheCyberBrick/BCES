package tcb.bces.bus;

import tcb.bces.event.Event;

class TypeConversions {
	protected static String getClassType(Class<?> clazz) {
		return clazz.getName().replace(".", "/");
	}

	protected static String getClassParamType(Class<?> clazz) {
		return "L" + getClassType(clazz) + ";";
	}

	protected static String getArrayClassParamType(Class<?> clazz) {
		return "[" + getClassParamType(clazz);
	}

	protected static String getListenerMethodType(String eventClassName) {
		return "(L" + eventClassName + ";)V";
	}

	protected static String getFilterMethodType() {
		return "(L" + getClassType(Event.class) + ";)Z";
	}
}
