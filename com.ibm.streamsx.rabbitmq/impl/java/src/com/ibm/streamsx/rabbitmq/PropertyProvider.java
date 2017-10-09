/*******************************************************************************
 * Copyright (C) 2016, International Business Machines Corporation
 * All Rights Reserved
 *******************************************************************************/

package com.ibm.streamsx.rabbitmq;

import java.util.Map;

import com.ibm.streams.operator.ProcessingElement;
import com.ibm.streamsx.rabbitmq.i18n.Messages;

// This class provides configuration data stored in PE
public class PropertyProvider {
	
	private ProcessingElement pe;
	private String configurationName;
	private Map<String,String> configuration;

	/**
	 * Creates a new PropertyProvider and initializes it with the current application configuration.
	 * @param pe   The PE
	 * @param configurationName The name of the application configuration
	 */
	public PropertyProvider(ProcessingElement pe, String configurationName) {
		this.pe = pe;
		this.configurationName = configurationName;
		this.loadConfiguration();
		
		if(configuration.isEmpty()) {
			throw new IllegalArgumentException(Messages.getString("APP_CFG_NOT_FOUND_OR_EMPTY", configurationName )); //$NON-NLS-1$
		}
	}
	
    /**
     * get a property value by name
     * @param name the property name
     * @param reloadConfig if set to <tt>true</tt>, the configuration is reloaded to get the latest property value.
     * @return The property value or <tt>null</tt> if the named property is not present.
     */
    public String getProperty(String name, boolean reloadConfig) {
        if (reloadConfig) {
            this.loadConfiguration();
        }
        return configuration.get(name);
    }

    /**
     * Get a property value by name without fetching the application configuration from the PE.
     * @param name the property name
     * @return The property value or <tt>null</tt> if the named property is not present.
     */
	public String getProperty(String name) {
		return configuration.get(name);
	}
	
    /**
     * Tests the existence of a given property
     * @param name the property name
     * @param reloadConfig if set to <tt>true</tt>, the configuration is reloaded to get the latest property value.
     * @return <tt>true</tt> if the property exists, <tt>false</tt> otherwise
     */
    public boolean contains(String name, boolean reloadConfig) {
        if (reloadConfig) {
            this.loadConfiguration();
        }
        return configuration.containsKey(name);
    }

    /**
     * tests the existence of a given property without fetching the application configuration from the PE.
     * @param name the property name
     * @return <tt>true</tt> if the property exists, <tt>false</tt> otherwise
     */
	public boolean contains(String name) {
		return configuration.containsKey(name);
	}
	
    /**
     * Loads the configuration and gets all properties as a map, that maps property names to their values.
     * @return a map that maps property names to their values
     */
	public Map<String, String> getAllProperties() {
		this.loadConfiguration();
		return configuration;
	}
	
	private void loadConfiguration() {
		configuration = pe.getApplicationConfiguration(configurationName);
	}
}
