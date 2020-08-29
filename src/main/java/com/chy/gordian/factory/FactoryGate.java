package com.chy.gordian.factory;


import com.chy.gordian.extend.Gordian;

import java.util.ServiceLoader;

public class FactoryGate {

    static GordianFactory result = null;

    static {
        ServiceLoader<GordianFactory> loaders = ServiceLoader.load(GordianFactory.class);
        for (GordianFactory loader : loaders) {
            if (result == null) {
                result = loader;
                continue;
            }
            if (loader.order() < result.order()) {
                result = loader;
                continue;
            }
        }

        if (result == null) {
            result = new DefaultGordianFactory();
        }

    }

    public static Gordian getInstance(String name) {
        return result.createInstance(name);
    }

}
