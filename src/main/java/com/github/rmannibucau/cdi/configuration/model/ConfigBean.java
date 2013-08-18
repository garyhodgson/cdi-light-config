package com.github.rmannibucau.cdi.configuration.model;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class ConfigBean {
    private final String name;
    private final String classname;
    private final String factoryClass;
    private final String factoryMethod;
    private final String scope;
    private final String qualifier;
    private final boolean constructor;
    private final Map<String, String> directAttributes = new HashMap<String, String>();
    private final Map<String, String> refAttributes = new HashMap<String, String>();
    private final Collection<String> attributeOrder = new CopyOnWriteArrayList<String>();

    public ConfigBean(final String name, final String classname, final String scope, final String qualifier,
                      final String factoryClass, final String factoryMethod,
                      final boolean constructor) {
        this.name = name;
        this.classname = classname;
        this.scope = scope;
        this.qualifier = qualifier;
        this.factoryClass = factoryClass;
        this.constructor = constructor;
        if (factoryMethod != null) {
            this.factoryMethod = factoryMethod;
        } else {
            this.factoryMethod = "create";
        }
    }

    public String getName() {
        return name;
    }

    public String getClassname() {
        return classname;
    }

    public String getFactoryClass() {
        return factoryClass;
    }

    public String getFactoryMethod() {
        return factoryMethod;
    }

    public String getQualifier() {
        return qualifier;
    }

    public String getScope() {
        return scope;
    }

    public boolean isConstructor() {
        return constructor;
    }

    public Map<String, String> getDirectAttributes() {
        return directAttributes;
    }

    public Map<String, String> getRefAttributes() {
        return refAttributes;
    }

    public Collection<String> getAttributeOrder() {
        return attributeOrder;
    }
}