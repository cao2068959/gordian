package com.chy.gordian.factory;


import com.chy.gordian.extend.Gordian;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultGordianFactory implements GordianFactory {

    Map<String, Gordian> cache = new ConcurrentHashMap<>();

    @Override
    public Gordian createInstance(String gordianName) {

        Gordian gordian = cache.get(gordianName);
        if (gordian != null) {
            return gordian;
        }
        return doCreateAndCache(gordianName);
    }

    @Override
    public int order() {
        return Integer.MAX_VALUE;
    }

    private Gordian doCreateAndCache(String gordianName) {
        Class<?> gordianClass;
        try {
            gordianClass = Class.forName(gordianName);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            throw new RuntimeException("instance fail: " + gordianName);
        }
        synchronized (gordianClass) {
            Gordian gordian = cache.get(gordianName);
            if (gordian != null) {
                return gordian;
            }
            Gordian instance = createInstanceForReflect(gordianClass);
            cache.put(gordianName, instance);
            return instance;
        }
    }

    private Gordian createInstanceForReflect(Class<?> gordianClass) {
        try {

            return (Gordian) gordianClass.newInstance();

        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        }
        throw new RuntimeException("instance fail: " + gordianClass.getName());
    }
}
