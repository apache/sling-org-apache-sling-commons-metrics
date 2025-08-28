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

import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.MBeanServerNotification;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import org.junit.Test;
import org.mockito.Mockito;

public class JmxNotificationListenerTest {

    JmxExporterFactory exporter = new JmxExporterFactory();
    NotificationListener listener = exporter.listener;

    @Test
    public void testHandleNotification() throws Exception {
        exporter.patterns = new String[] {"test:type=Test"};
        exporter.server = Mockito.mock(MBeanServer.class);
        MBeanInfo m = Mockito.mock(MBeanInfo.class);
        MBeanServerNotification notification = Mockito.mock(MBeanServerNotification.class);
        Mockito.when(notification.getType()).thenReturn("JMX.mbean.registered");
        ObjectName objectName = new ObjectName("test:type=Test");
        Mockito.when(notification.getMBeanName()).thenReturn(objectName);
        Mockito.when(exporter.server.getMBeanInfo(Mockito.eq(objectName))).thenReturn(m);
        Mockito.when(m.getAttributes()).thenReturn(new javax.management.MBeanAttributeInfo[0]);
        listener.handleNotification(notification, null);

        // Assert that MBeanInfo attribute methid is called
        Mockito.verify(m, Mockito.times(1)).getAttributes();
    }
}
