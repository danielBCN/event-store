package org.infinispan.creson.object;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.UUID;

/**
 * @author Pierre Sutra
 */
public class CallInvoke extends Call implements Externalizable {

    public String method;
    public Object[] arguments;

    @Deprecated     // Not for use. Needed for externalization.
    public CallInvoke() {}

    public CallInvoke(Reference reference, UUID callID, String m, Object[] args) {
        super(reference, callID);
        method = m;
        arguments = args;
    }

    @Override
    public String toString() {
        StringBuilder args = new StringBuilder(" ");
        for (Object a : arguments) {
            args.append(a == null ? "null" : a.toString()).append(" ");
        }
        return super.toString() + "-INV-" + method + "(" + args + ")";
    }

    @Override
    public void writeExternal(ObjectOutput objectOutput) throws IOException {
        super.writeExternal(objectOutput);
        objectOutput.writeObject(method);
        objectOutput.writeObject(arguments);
    }

    @Override
    public void readExternal(ObjectInput objectInput) throws IOException, ClassNotFoundException {
        super.readExternal(objectInput);
        method = (String) objectInput.readObject();
        arguments = (Object[]) objectInput.readObject();
    }
}
