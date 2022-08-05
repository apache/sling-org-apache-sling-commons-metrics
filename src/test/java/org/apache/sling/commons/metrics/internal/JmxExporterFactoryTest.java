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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.never;

import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import org.apache.sling.commons.metrics.Gauge;
import org.apache.sling.commons.metrics.MetricsService;
import org.apache.sling.testing.mock.osgi.junit.OsgiContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class JmxExporterFactoryTest {
    
    @Rule
    public OsgiContext context = new OsgiContext();
    
    @Captor
    ArgumentCaptor<Supplier<Integer>> intSupplierCaptor;
    
    @Captor
    ArgumentCaptor<Supplier<Long>> longSupplierCaptor;
    
    @Captor
    ArgumentCaptor<Supplier<String>> stringSupplierCaptor;
    
    @Captor
    ArgumentCaptor<Supplier<Double>> doubleSupplierCaptor;
    
    @Captor
    ArgumentCaptor<Supplier<Boolean>> booleanSupplierCaptor;

    @Captor
    ArgumentCaptor<Supplier<List<String>>> listSupplierCaptor;

    
    JmxExporterFactory exporter;
    
    private static final String OBJECT_NAME_0 = "org.apache.sling.whiteboard.jmxexporter.impl0:type=sample1";
    private static final String OBJECT_NAME_1 = "org.apache.sling.whiteboard.jmxexporter.impl0.impl2:type=sample2";
    private static final String OBJECT_NAME_2 = "org.apache.sling.whiteboard.jmxexporter.impl1:type=sample3";
    
    // Query which will only match OBJECT_NAME_0 and OBJECT_NAME_1
    private static final String OBJECT_NAME_QUERY = "org.apache.sling.whiteboard.jmxexporter.impl0*:type=*";
    
    private static final String EXPECTED_0_INT_NAME  = "org.apache.sling.whiteboard.jmxexporter.impl0.sample1.Int";
    private static final String EXPECTED_0_LONG_NAME = "org.apache.sling.whiteboard.jmxexporter.impl0.sample1.Long";
    private static final String EXPECTED_0_BOOLEAN_NAME = "org.apache.sling.whiteboard.jmxexporter.impl0.sample1.Boolean";
    private static final String EXPECTED_0_STRING_NAME = "org.apache.sling.whiteboard.jmxexporter.impl0.sample1.String";
    private static final String EXPECTED_0_DOUBLE_NAME = "org.apache.sling.whiteboard.jmxexporter.impl0.sample1.Double";
    private static final String EXPECTED_0_LIST_NAME = "org.apache.sling.whiteboard.jmxexporter.impl0.sample1.List";
    
    private static final String EXPECTED_1_INT_NAME  = "org.apache.sling.whiteboard.jmxexporter.impl0.impl2.sample2.Int";
    private static final String EXPECTED_1_LONG_NAME = "org.apache.sling.whiteboard.jmxexporter.impl0.impl2.sample2.Long";
    
    private static final String EXPECTED_2_INT_NAME  = "org.apache.sling.whiteboard.jmxexporter.impl1.sample3.Int";
    
    private static final Double STATIC_DOUBLE = 1.0;
    
    MetricsService metrics;
    
    SimpleBean mbeans[] = { new SimpleBean(0,0L), new SimpleBean(1,1L), new SimpleBean(2,2L)};
    
    
    @Before
    public void setup() throws MalformedObjectNameException, InstanceAlreadyExistsException, MBeanRegistrationException, NotCompliantMBeanException {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();

        server.registerMBean(mbeans[0],new ObjectName(OBJECT_NAME_0));        
        server.registerMBean(mbeans[1],new ObjectName(OBJECT_NAME_1));
        server.registerMBean(mbeans[2],new ObjectName(OBJECT_NAME_2));
        
        exporter = new JmxExporterFactory(); 
        metrics = Mockito.mock(MetricsService.class);
        context.registerService(MetricsService.class, metrics);
    }
    
    @After
    public void shutdown() throws MBeanRegistrationException, InstanceNotFoundException, MalformedObjectNameException {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        server.unregisterMBean(new ObjectName(OBJECT_NAME_0));
        server.unregisterMBean(new ObjectName(OBJECT_NAME_1));
        server.unregisterMBean(new ObjectName(OBJECT_NAME_2));
    }
    
    @Test
    public void test() {
        Map<String,Object> props = new HashMap<>();
        props.put("objectnames", new String[]{OBJECT_NAME_QUERY});
        
        // this will query all specified mbeans and create metrics for it
        context.registerInjectActivateService(exporter, props);
        
        // Integer
        Mockito.verify(metrics).gauge(Mockito.eq(EXPECTED_0_INT_NAME), intSupplierCaptor.capture());
        assertEquals(new Integer(0),intSupplierCaptor.getValue().get());
        
        // test that an update in the mbean reflects in the metrics
        mbeans[0].setInt(10); 
        Mockito.verify(metrics).gauge(Mockito.eq(EXPECTED_0_INT_NAME), intSupplierCaptor.capture());
        assertEquals(new Integer(10),intSupplierCaptor.getValue().get());
        
        // Long
        Mockito.verify(metrics).gauge(Mockito.eq(EXPECTED_0_LONG_NAME), longSupplierCaptor.capture());
        assertEquals(new Long(0L),longSupplierCaptor.getValue().get());
        
        // String
        Mockito.verify(metrics).gauge(Mockito.eq(EXPECTED_0_STRING_NAME), stringSupplierCaptor.capture());
        assertEquals("sample",stringSupplierCaptor.getValue().get());
        
        // Boolean
        Mockito.verify(metrics).gauge(Mockito.eq(EXPECTED_0_BOOLEAN_NAME), booleanSupplierCaptor.capture());
        assertFalse(booleanSupplierCaptor.getValue().get());
        
        // Double
        Mockito.verify(metrics).gauge(Mockito.eq(EXPECTED_0_DOUBLE_NAME), doubleSupplierCaptor.capture());
        assertEquals(STATIC_DOUBLE,doubleSupplierCaptor.getValue().get());
        
        // getList()
        Mockito.verify(metrics, never()).gauge(Mockito.eq(EXPECTED_0_LIST_NAME), listSupplierCaptor.capture());
        
        
        // MBean 1
        Mockito.verify(metrics).gauge(Mockito.eq(EXPECTED_1_INT_NAME), intSupplierCaptor.capture());
        assertEquals(new Integer(1),intSupplierCaptor.getValue().get());
        
        Mockito.verify(metrics).gauge(Mockito.eq(EXPECTED_1_LONG_NAME), longSupplierCaptor.capture());
        assertEquals(new Long(1L),longSupplierCaptor.getValue().get());
        
        // verify that no metrics for MBean2 have been registered
        Mockito.verify(metrics, never()).gauge(Mockito.eq(EXPECTED_2_INT_NAME), intSupplierCaptor.capture());
        
    }
    
    @Test
    public void registerNonExistingMBean() {
        Map<String,Object> props = new HashMap<>();
        props.put("objectnames", new String[]{"org.apache.sling:type=nonexistent"}); // there is no such mbean
        
        context.registerInjectActivateService(exporter, props);
        Mockito.verifyNoInteractions(metrics);
    }
    
    @Test
    public void registerInvalidMBean() {
        Map<String,Object> props = new HashMap<>();
        props.put("objectnames", new String[]{"org.apache.sling%type=nonexistent"}); // this is invalid
        
        context.registerInjectActivateService(exporter, props);
        Mockito.verifyNoInteractions(metrics);
    }
    
    
    static class SimpleBean implements SimpleBeanMBean {

        
        int internalInt = 0;
        long internalLong = 0L;
        
        public SimpleBean(int i, long l) {
            internalInt = i;
            internalLong = l;
        }
        
        @Override
        public int getInt() {
            return internalInt;
        }
        
        @Override
        public long getLong() {
            return internalLong;
        }
        
        public void setInt(int value) {
            internalInt = value;
        }
        
        public String getString() {
            return "sample";
        }
        
        public double getDouble() {
            return STATIC_DOUBLE;
        }
        
        public boolean getBoolean() {
            return false;
        }

        public List<String> getList() {
            return Collections.emptyList();
        }
        
    }
    
    
    static public interface SimpleBeanMBean {
        
        public int getInt();
        public long getLong();
        public String getString();
        public double getDouble();
        public boolean getBoolean();
        
        public List<String> getList(); // this type is not supported!
        
    }
    
}