package org.teavm.javac;

import org.teavm.jso.JSExport;
import org.teavm.jso.JSProperty;

public final class EditorLocation {
    private final String fileName;
    private final int from;
    private final int to;

    EditorLocation(String fileName, int from, int to) {
        this.fileName = fileName;
        this.from = from;
        this.to = to;
    }

    @JSExport
    @JSProperty
    public String getFileName() {
        return fileName;
    }

    @JSExport
    @JSProperty
    public int getFrom() {
        return from;
    }

    @JSExport
    @JSProperty
    public int getTo() {
        return to;
    }

}
