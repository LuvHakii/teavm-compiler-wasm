package org.teavm.javac;

import org.teavm.jso.JSExport;
import org.teavm.jso.JSProperty;

public final class MainClass {
    private final String binaryName;
    private final String sourceName;

    MainClass(String binaryName, String sourceName) {
        this.binaryName = binaryName;
        this.sourceName = sourceName;
    }

    @JSExport
    @JSProperty
    public String getBinaryName() {
        return binaryName;
    }

    @JSExport
    @JSProperty
    public String getSourceName() {
        return sourceName;
    }
}
