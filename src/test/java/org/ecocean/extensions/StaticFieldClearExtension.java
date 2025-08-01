package org.ecocean.extensions;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;

/**
 * JUnit 5 extension that clears the contents of static fields before each test.
 * <p>
 * Supports Maps, Collections, and any custom types that implement clear().
 * </p>
 */
public class StaticFieldClearExtension implements BeforeEachCallback {

    private final Class<?> targetClass;
    private final String fieldName;

    public StaticFieldClearExtension(Class<?> targetClass, String fieldName) {
        this.targetClass = targetClass;
        this.fieldName = fieldName;
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        Field field = targetClass.getDeclaredField(fieldName);
        field.setAccessible(true);

        if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
            throw new IllegalStateException("Field '" + fieldName + "' must be static.");
        }

        Object value = field.get(null); // static fields => pass 'null' for instance

        if (value == null) {
            // Nothing to clear if null
            return;
        }

        if (value instanceof Map) {
            ((Map<?, ?>) value).clear();
        } else if (value instanceof Collection) {
            ((Collection<?>) value).clear();
        } else {
            try {
                // Try to find a 'clear' method by reflection
                value.getClass().getMethod("clear").invoke(value);
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException("Unsupported static field type: "
                        + field.getType().getName() + ". Only Map, Collection, or classes with a clear() method are supported.");
            }
        }
    }
}
