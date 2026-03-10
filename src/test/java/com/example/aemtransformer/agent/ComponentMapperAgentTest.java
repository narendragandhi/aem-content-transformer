package com.example.aemtransformer.agent;

import com.example.aemtransformer.model.ComponentMapping;
import com.example.aemtransformer.model.ContentAnalysis;
import com.example.aemtransformer.model.ContentBlock;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ComponentMapperAgentTest {

    private final ComponentMapperAgent mapperAgent = new ComponentMapperAgent();

    @Test
    void mapComponents_listBlock_mapsToTextComponent() {
        ContentBlock listBlock = ContentBlock.builder()
                .type(ContentBlock.BlockType.LIST)
                .listItems(List.of("A", "B"))
                .isOrdered(true)
                .build();

        ContentAnalysis analysis = ContentAnalysis.builder()
                .blocks(List.of(listBlock))
                .build();

        List<ComponentMapping> mappings = mapperAgent.mapComponents(analysis);

        assertEquals(1, mappings.size());
        ComponentMapping mapping = mappings.get(0);
        assertEquals(ComponentMapping.AemComponentType.TEXT, mapping.getTargetComponent());
        assertEquals("<ol><li>A</li><li>B</li></ol>", mapping.getProperties().get("text"));
        assertEquals(true, mapping.getProperties().get("textIsRich"));
    }
}
