package io.github.onec.xmlgen.editor;

import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IdAllocatorTest {

    @Test
    void testAllocation() {
        XmlNode root = XmlNode.builder()
                .name("root")
                .attribute("id", "5")
                .addChild(XmlNode.builder().name("child").attribute("id", "10").build())
                .addChild(XmlNode.builder().name("child").attribute("Id", "2").build())
                .build();
        
        XmlDocument doc = new XmlDocument(null, false, "root", "", Map.of(), root.getChildren(), root);
        
        IdAllocator allocator = new IdAllocator(doc);
        
        // Max is 10, so next should be 11
        assertEquals("11", allocator.nextId());
        assertEquals("12", allocator.nextId());
    }
}
