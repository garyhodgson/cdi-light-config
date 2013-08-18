package com.github.rmannibucau.cdi.configuration.factory;

import com.github.rmannibucau.cdi.configuration.ConfigurationException;
import com.github.rmannibucau.cdi.configuration.model.ConfigBean;
import org.apache.deltaspike.core.api.provider.BeanProvider;

import java.beans.Introspector;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class ObjectFactory<T> {
    private static final Logger LOGGER = Logger.getLogger(ObjectFactory.class.getName());

    private final ConfigBean model;
    private final Factory<T> factory;

    public ObjectFactory(final ConfigBean bean) {
        model = bean;
        factory = findFactory();
    }

    public T create() {
        return factory.create();
    }

    private static Object convertTo(final Class<?> type, final String value) {
        if (value == null || String.class.equals(type)) {
            return value;
        }
        if (Integer.class.equals(type) || Integer.TYPE.equals(type)) {
            return Integer.parseInt(value);
        }
        if (Long.class.equals(type) || Long.TYPE.equals(type)) {
            return Long.parseLong(value);
        }
        if (Short.class.equals(type) || Short.TYPE.equals(type)) {
            return Short.parseShort(value);
        }
        if (Boolean.class.equals(type) || Boolean.TYPE.equals(type)) {
            return Boolean.parseBoolean(value);
        }
        if (Double.class.equals(type) || Double.TYPE.equals(type)) {
            return Double.parseDouble(value);
        }
        if (Float.class.equals(type) || Float.TYPE.equals(type)) {
            return Float.parseFloat(value);
        }
        throw new ConfigurationException("Can't convert '" + value + "' to " + type.getName());
    }

    private Factory<T> findFactory() {
        final ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try {
            final Class<T> clazz = (Class<T>) loader.loadClass(model.getClassname());
            if (model.getFactoryClass() == null && !model.isConstructor()) {
                return new NewFactory<T>(model);
            }

            if (model.isConstructor()) {
                return new ConstructorFactory<T>(model);
            }

            final Class<?> factoryClass = Thread.currentThread().getContextClassLoader().loadClass(model.getFactoryClass());
            final Method method = factoryClass.getDeclaredMethod(model.getFactoryMethod());
            if (!method.isAccessible()) {
                method.setAccessible(true);
            }
            return new MethodFactory<T>(model, Modifier.isStatic(method.getModifiers()), method);
        } catch (final Exception e) {
            throw new ConfigurationException(e);
        }
    }

    protected static interface Setter {
        Class<?> type();
        void set(final Object mainInstance, final Object value) throws Exception;
    }

    protected static class FieldSetter implements Setter {
        private final Field field;

        public FieldSetter(final Field field) {
            this.field = field;
        }

        @Override
        public Class<?> type() {
            return field.getType();
        }

        @Override
        public void set(final Object mainInstance, final Object value) throws Exception {
            if (!field.isAccessible()) {
                field.setAccessible(true);
            }
            field.set(mainInstance, value);
        }
    }

    protected static class MethodSetter implements Setter {
        private final Method method;

        public MethodSetter(final Method method) {
            this.method = method;
        }

        @Override
        public Class<?> type() {
            return method.getParameterTypes()[0];
        }

        @Override
        public void set(final Object mainInstance, final Object value) throws Exception {
            if (!method.isAccessible()) {
                method.setAccessible(true);
            }
            method.invoke(mainInstance, value);
        }
    }

    protected static interface Factory<T> {
        T create();
    }

    protected static class ConstructorFactory<T> implements Factory<T> {
        private final Constructor<T> constructor;
        private final ConfigBean model;

        public ConstructorFactory(final ConfigBean bean) {
            final int paramNumber = bean.getDirectAttributes().size() + bean.getRefAttributes().size();

            final Class<?> clazz;
            try {
                clazz = Thread.currentThread().getContextClassLoader().loadClass(bean.getClassname());
            } catch (final ClassNotFoundException e) {
                throw new ConfigurationException(e);
            }

            Constructor<?> found = null;
            for (final Constructor<?> c : clazz.getDeclaredConstructors()) {
                if (c.getParameterTypes().length == paramNumber) {
                    found = c;
                    break;
                }
            }

            if (found == null) {
                throw new ConfigurationException("No constructor found matching configuration for " + bean.getName());
            }

            constructor = (Constructor<T>) found;
            model = bean;
        }

        @Override
        public T create() {
            final Class<?>[] parameterTypes = constructor.getParameterTypes();
            final Object[] params = new Object[parameterTypes.length];
            int i = 0;
            for (final String attr : model.getAttributeOrder()) {
                params[i] = findAttribute(parameterTypes[i], attr);
                i++;
            }
            try {
                return (T) constructor.newInstance(params);
            } catch (final Exception e) {
                throw new ConfigurationException(e);
            }
        }

        private Object findAttribute(final Class<?> clazz, final String attr) {
            final String value = model.getDirectAttributes().get(attr);
            if (value != null) {
                return convertTo(clazz, value);
            }

            final String reference = model.getRefAttributes().get(attr);
            return BeanProvider.getContextualReference(reference);
        }
    }

    protected static class NewFactory<T> implements Factory<T> {
        private final Class<T> clazz;
        private final ConfigBean model;
        private final Map<String, Setter> members;

        public NewFactory(final ConfigBean model) {
            this.model = model;
            try {
                this.clazz = (Class<T>) Thread.currentThread().getContextClassLoader().loadClass(model.getClassname());
            } catch (final ClassNotFoundException e) {
                throw new ConfigurationException(e);
            }
            this.members = mapMembers(clazz);
        }

        @Override
        public T create() {
            try {
                final T t = (T) clazz.newInstance();
                for (final Map.Entry<String, String> attribute : model.getDirectAttributes().entrySet()) {
                    final Setter field = members.get(attribute.getKey());
                    if (field != null) {
                        final Object value = convertTo(field.type(), attribute.getValue());
                        field.set(t, value);
                    } else {
                        LOGGER.warning("Can't find field " + attribute.getKey());
                    }
                }
                for (final Map.Entry<String, String> attribute : model.getRefAttributes().entrySet()) {
                    final Setter field = members.get(attribute.getKey());
                    if (field != null) {
                        field.set(t, BeanProvider.getContextualReference(attribute.getValue()));
                    } else {
                        LOGGER.warning("Can't find field " + attribute.getKey());
                    }
                }

                return t;
            } catch (final Exception e) {
                throw new ConfigurationException(e);
            }
        }

        private Map<String, Setter> mapMembers(final Class<T> target) {
            final Map<String, Setter> members = new HashMap<String, Setter>();
            Class<?> current = target;
            do {
                for (final Method method : current.getDeclaredMethods()) {
                    final String name = method.getName();
                    if (name.length() > 3 && name.startsWith("set") && method.getParameterTypes().length == 1) {
                        members.put(Introspector.decapitalize(name.substring(3)), new MethodSetter(method));
                    }
                }

                for (final Field field : current.getDeclaredFields()) {
                    final String name = field.getName();
                    if (members.containsKey(name)) {
                        continue;
                    }

                    members.put(name, new FieldSetter(field));
                }

                current = current.getSuperclass();
            } while (current != null && Object.class.equals(current));
            return members;
        }
    }

    protected static class MethodFactory<T> implements Factory<T> {
        private final boolean staticFactory;
        private final Method method;
        private final NewFactory<Object> delegate;

        public MethodFactory(final ConfigBean mainBean, final boolean staticFactory, final Method method) {
            this.staticFactory = staticFactory;
            this.method = method;

            // reuse this as meta factory for the factory instance if needed
            if (staticFactory) {
                this.delegate = null;
            } else {
                final ConfigBean bean = new ConfigBean(null, mainBean.getFactoryClass(), null, null, null, null, false);
                bean.getDirectAttributes().putAll(mainBean.getDirectAttributes());
                bean.getRefAttributes().putAll(mainBean.getRefAttributes());
                this.delegate = new NewFactory<Object>(bean);
            }
        }

        @Override
        public T create() {
            try {
                final Object factoryInstance;
                if (staticFactory) {
                    factoryInstance = null;
                } else {
                    factoryInstance = delegate.create();
                }
                return (T) method.invoke(factoryInstance);
            } catch (final Exception e) {
                throw new ConfigurationException(e);
            }
        }
    }
}