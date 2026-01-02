package com.example.aemtransformer.components;

import com.example.aemtransformer.model.AemPage.ComponentNode;

/**
 * Interface for all AEM Core Component representations.
 */
public interface AemComponent {

    /**
     * Returns the Sling resource type for this component.
     */
    String getResourceType();

    /**
     * Generates the component node for AEM JSON export.
     */
    ComponentNode toComponentNode();

    /**
     * Returns a unique name for this component instance.
     */
    String getComponentName();
}
