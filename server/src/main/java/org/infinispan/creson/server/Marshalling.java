package org.infinispan.creson.server;

import org.infinispan.commons.marshall.Marshaller;
import org.infinispan.marshall.core.JBossMarshaller;

/**
 * @author Pierre Sutra
 */
public class Marshalling {

    public static byte[] marshall(Object object) {
        Marshaller marshaller = new JBossMarshaller();
        try {
            if (object instanceof byte[])
                return (byte[]) object;
            return marshaller.objectToByteBuffer(object);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Object unmarshall(Object object) {
        Marshaller marshaller = new JBossMarshaller();
        try {
            if (object instanceof byte[])
                return marshaller.objectFromByteBuffer((byte[]) object);
            return object;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

}
