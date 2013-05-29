package com.basho.riak.jmx;

import java.lang.management.ManagementFactory;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.ObjectInstance;
import javax.management.MBeanInfo;
import javax.management.MBeanAttributeInfo;

import org.json.JSONStringer;

/********************************

usage: java -cp riak_jmx.jar com.basho.riak.jmx.Dump HOSTNAME JMX_PORT

Will dump a json object of current JMX values. For debugging, testing, etc...

FYI - This is now the only class that depends on the org.json stuff.

********************************/

public class Dump {

    public static void main(String[] args) throws Exception {
        String host = args[0];
        int port = Integer.decode(args[1]).intValue();
        Map<String,String[]> env = new HashMap<String, String[]>();
        JMXServiceURL address = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + host + ":" + port + "/jmxrmi");
        JMXConnector connector = JMXConnectorFactory.connect(address,env);
        MBeanServerConnection mbs = connector.getMBeanServerConnection();
        ObjectName riakBeanName = new ObjectName("com.basho.riak:type=Riak");
        Set<ObjectInstance> beans = mbs.queryMBeans(riakBeanName,null);

        JSONStringer results = new JSONStringer();
        results.array();

        for( ObjectInstance instance : beans )
        {
            MBeanInfo info = mbs.getMBeanInfo( instance.getObjectName() );
            MBeanAttributeInfo[] attribs = info.getAttributes();
            for (MBeanAttributeInfo attr: attribs)
            {
                String name = attr.getName();
                Object value = mbs.getAttribute(riakBeanName, name);
                results.object().key(name);
                if(value instanceof Number)
                {
                    results.value(((Number)value).longValue());
                } else {
                    results.value(value);
                }
                results.endObject();
            }
        }
        System.out.println(results.endArray().toString());
    }

}
