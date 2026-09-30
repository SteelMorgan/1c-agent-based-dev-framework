package io.github.onec.xmlgen.editor;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/** XG-100: borrow вставлял запись ChildObjects с 6 табами и срывал отступ соседа. */
class ExtensionBorrowIndentXg100Test {

    private String add(String content, String type, String name) throws Exception {
        ExtensionEditor editor = new ExtensionEditor();
        Method m = ExtensionEditor.class.getDeclaredMethod("addToChildObjects", String.class, String.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(editor, content, type, name);
    }

    @Test
    void insertBeforeSibling_keepsCanonicalIndentAndCrlf() throws Exception {
        String cfg = "<Configuration>\r\n\t\t<ChildObjects>\r\n\t\t\t<CommonModule>А</CommonModule>\r\n"
                + "\t\t\t<CommonModule>В</CommonModule>\r\n\t\t</ChildObjects>\r\n</Configuration>";
        String result = add(cfg, "CommonModule", "Б");
        assertThat(result).contains("\t\t\t<CommonModule>А</CommonModule>\r\n\t\t\t<CommonModule>Б</CommonModule>\r\n\t\t\t<CommonModule>В</CommonModule>\r\n");
        assertThat(result).doesNotContain("\t\t\t\t<CommonModule>");
    }

    @Test
    void appendBeforeClosingTag_keepsClosingIndent() throws Exception {
        String cfg = "<Configuration>\n\t\t<ChildObjects>\n\t\t\t<CommonModule>А</CommonModule>\n\t\t</ChildObjects>\n</Configuration>";
        String result = add(cfg, "CommonModule", "Я");
        assertThat(result).contains("\t\t\t<CommonModule>Я</CommonModule>\n\t\t</ChildObjects>");
    }
}
