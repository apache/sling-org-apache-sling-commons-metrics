/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.commons.metrics.internal;

import java.lang.management.ManagementFactory;
import javax.management.MBeanServerNotification;
import javax.management.NotificationListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.MBeanServerDelegate;
import javax.management.MalformedObjectNameException;
import javax.management.Notification;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.commons.metrics.MetricsService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This ServiceFactory allows to export JMX object names as metrics (gauge).
 *
 */

@Component()
@Designate(ocd=JmxExporterFactory.Config.class, factory=true)
public class JmxExporterFactory {

    
    @ObjectClassDefinition(name="JMX to Metrics Exporter")
    public @interface Config {
        
        @AttributeDefinition(name="objectnames", description="export all attribute of the MBeans matching these objectnames as Sling Metrics"
                + "(see https://docs.oracle.com/en/java/javase/11/docs/api/java.management/javax/management/ObjectName.html")
        String[] objectnames();
        
        @AttributeDefinition
        String webconsole_configurationFactory_nameHint() default "Pattern: {objectnames}"; //NOSONAR
    }

    String[] patterns;
    
    
    private static final Logger LOG = LoggerFactory.getLogger(JmxExporterFactory.class);
    
    @Reference
    MetricsService metrics;
    
    MBeanServer server;

    NotificationListener listener = new NotificationListener() {
        public void handleNotification(Notification notification, Object handback) {
            if (MBeanServerNotification.REGISTRATION_NOTIFICATION.equals(notification.getType())) {
                ObjectName objectname = null;
                try {
                    if(notification instanceof MBeanServerNotification) {
                        MBeanServerNotification mbeanNotification = (MBeanServerNotification) notification;
                        objectname = mbeanNotification.getMBeanName();
                        LOG.debug("JMX Notification : match {} with pattern = {}", objectname, Arrays.asList(patterns));
                        for (String pattern : patterns) {
                            if (objectname.toString().matches(pattern)) {
                                LOG.debug("JMX Notification : register metrics for MBean: {}", objectname);
                                registerMBeanProperties(objectname);
                                break;
                            }
                        }
                    } else {
                        LOG.debug("JMX Notification : Cannot register metrics for objectname = {}", objectname);
                    }
                } catch (InstanceNotFoundException | ReflectionException | IntrospectionException e) {
                    LOG.error("JMX Notification : Cannot register metrics for objectname = {}", objectname, e);
                }
            }
        }
    };

    @Activate
    @Modified
    public void activate(Config config) {
        server = ManagementFactory.getPlatformMBeanServer();
        patterns = config.objectnames();
        try {
            server.addNotificationListener(MBeanServerDelegate.DELEGATE_NAME, listener, null, null);
        } catch (InstanceNotFoundException e) {
            LOG.error("Cannot add notification listener to MBeanServerDelegate", e);
        }
        registerMetrics(patterns);
    }


    /**
     * Register all applicable metrics for an objectname pattern
     * @param pattern describes a objectname pattern
     */
    private void registerMetrics(String[] patterns) {
        
        for (String patternString : patterns) {
            try {
                ObjectName pattern = new ObjectName(patternString);
                Set<ObjectName> allMBeans = server.queryNames(pattern, null);
                if (allMBeans.isEmpty()) {
                    LOG.info("pattern {} does not match any MBean", patternString);
                } else {
                    allMBeans.forEach(objectname -> {
                        LOG.debug("registering properties for {}", objectname);
                        try {
                            registerMBeanProperties(objectname);
                        } catch (IntrospectionException | InstanceNotFoundException | ReflectionException e) {
                            LOG.error("Cannot register metrics for objectname = {}", objectname, e);
                        }
                    });
                }
            } catch (MalformedObjectNameException e) {
                LOG.error("cannot create an objectname from pattern {}",patternString,e);
            }
        }
        
    }
    
    
    protected void registerMBeanProperties(ObjectName objectname) throws InstanceNotFoundException, ReflectionException, IntrospectionException {
        MBeanInfo info = server.getMBeanInfo(objectname);
        MBeanAttributeInfo[] attributes = info.getAttributes();
        for (MBeanAttributeInfo attr : attributes) {
            LOG.debug("Checking mbean = {}, name = {}, type={}",objectname, attr.getName(), attr.getType());
            
            Supplier<?> supplier = null;
            if ("int".equals(attr.getType())) {
                supplier = getSupplier(objectname, attr.getName(),0);
            } else if ("long".equals(attr.getType())) {
                supplier = getSupplier(objectname, attr.getName(),0L);
            } else if ("java.lang.String".equals(attr.getType())) {
                supplier = getSupplier(objectname,attr.getName(),"");
            } else if ("double".equals(attr.getType())) {
                supplier = getSupplier(objectname,attr.getName(), Double.valueOf(0.0));
            } else if ("boolean".equals(attr.getType())) {
                supplier = getSupplier(objectname,attr.getName(), Boolean.FALSE);
            }
            
            if (supplier != null) {
                String metricName = toMetricName(objectname, attr.getName());
                LOG.info("Registering metric {} from MBean (objectname=[{}], name={}, type={})", 
                        metricName, objectname, attr.getName(), attr.getType());
                metrics.gauge(metricName, supplier);
            }
        }
    }
    
    
    private <T> Supplier<T> getSupplier ( ObjectName name, String attributeName, T defaultValue ) {
        
        return () -> {
            try {
                return (T) server.getAttribute(name, attributeName);
            } catch (InstanceNotFoundException | AttributeNotFoundException | ReflectionException
                    | MBeanException e) {
                LOG.warn("error when retrieving value for MBean (objectname=[{}], attribute={})",name, attributeName,e);
                return defaultValue;
            }
            
        };
    }
    
    
    protected String toMetricName(ObjectName objectName, String attributeName) {
        String name = "sling"; // default domain

        if (!StringUtils.isBlank(objectName.getDomain())) {
            name = objectName.getDomain();
        }
        Hashtable<String,String> allkeys = objectName.getKeyPropertyList();
        List<String> keyValues = new ArrayList<>(allkeys.values());
        Collections.sort(keyValues);
        
        StringBuilder builder = new StringBuilder(name);
        for (String s: keyValues) {
            builder.append( "." + s);
        }
        builder.append("." + attributeName);
        return builder.toString();
    }
    
}
