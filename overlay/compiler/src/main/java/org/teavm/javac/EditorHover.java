package org.teavm.javac;

import org.teavm.jso.JSExport;
import org.teavm.jso.JSProperty;

public final class EditorHover {
    private final String detail;
    private final String documentation;
    private final String docKeys;
    private final int from;
    private final int to;

    EditorHover(String detail, String documentation, String docKeys, int from, int to) {
        this.detail = detail;
        this.documentation = documentation;
        this.docKeys = docKeys;
        this.from = from;
        this.to = to;
    }

    @JSExport
    @JSProperty
    public String getDetail() {
        return detail;
    }

    @JSExport
    @JSProperty
    public String getDocumentation() {
        return documentation;
    }

    @JSExport
    @JSProperty
    public String getDocKeys() {
        return docKeys;
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
