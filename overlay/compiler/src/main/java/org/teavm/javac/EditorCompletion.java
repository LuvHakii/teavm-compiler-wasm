package org.teavm.javac;

import org.teavm.jso.JSExport;
import org.teavm.jso.JSProperty;

public final class EditorCompletion {
    private final String name;
    private final String kind;
    private final String detail;
    private final String owner;
    private final String documentation;
    private final String docKeys;
    private final String importName;
    private final String importText;
    private final int importAt;
    private final String snippet;
    private final int from;
    private final int to;

    EditorCompletion(String name, String kind, String detail, String owner, String documentation, String docKeys,
            int from, int to) {
        this(name, kind, detail, owner, documentation, docKeys, null, null, -1, null, from, to);
    }

    EditorCompletion(String name, String detail, String snippet, int from, int to) {
        this(name, "SNIPPET", detail, null, null, null, null, null, -1, snippet, from, to);
    }

    EditorCompletion(String name, String kind, String detail, String owner, String documentation, String docKeys,
            String importName, String importText, int importAt, int from, int to) {
        this(name, kind, detail, owner, documentation, docKeys, importName, importText, importAt, null, from, to);
    }

    private EditorCompletion(String name, String kind, String detail, String owner, String documentation,
            String docKeys, String importName, String importText, int importAt, String snippet, int from, int to) {
        this.snippet = snippet;
        this.name = name;
        this.kind = kind;
        this.detail = detail;
        this.owner = owner;
        this.documentation = documentation;
        this.docKeys = docKeys;
        this.importName = importName;
        this.importText = importText;
        this.importAt = importAt;
        this.from = from;
        this.to = to;
    }

    @JSExport
    @JSProperty
    public String getName() {
        return name;
    }

    @JSExport
    @JSProperty
    public String getKind() {
        return kind;
    }

    @JSExport
    @JSProperty
    public String getDetail() {
        return detail;
    }

    @JSExport
    @JSProperty
    public String getOwner() {
        return owner;
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
    public String getImportName() {
        return importName;
    }

    @JSExport
    @JSProperty
    public String getImportText() {
        return importText;
    }

    @JSExport
    @JSProperty
    public int getImportAt() {
        return importAt;
    }

    @JSExport
    @JSProperty
    public String getSnippet() {
        return snippet;
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
