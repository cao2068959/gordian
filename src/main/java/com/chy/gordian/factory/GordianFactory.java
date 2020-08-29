package com.chy.gordian.factory;


import com.chy.gordian.extend.Gordian;

public interface GordianFactory {

    public Gordian createInstance(String gordianName);

    public int order();
}
