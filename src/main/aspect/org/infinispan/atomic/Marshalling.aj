package org.infinispan.atomic;

import org.infinispan.atomic.object.Reference;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.infinispan.atomic.utils.AOFUtils.unreference;

/**
 * @author Pierre Sutra
 */
public aspect Marshalling {

   interface Marshallable extends Externalizable {}

   public void Marshallable.writeExternal(ObjectOutput objectOutput) throws IOException {
      try {
         for (Field field : this.getClass().getFields()) {
            if (!Modifier.isTransient(field.getModifiers())) {
               objectOutput.writeObject(field.get(this));
            }
         }
      } catch (IllegalAccessException e) {
         e.printStackTrace();
      }
   }

   public void Marshallable.readExternal(ObjectInput objectInput) throws IOException, ClassNotFoundException {
      AtomicObjectFactory factory = AtomicObjectFactory.getSingleton();
      assert factory!=null;
      try {
         for (Field field : this.getClass().getFields()) {
            if (!Modifier.isTransient(field.getModifiers())) {
               Object value = objectInput.readObject();
               if (value instanceof Reference) {
                  value = unreference((Reference) value, factory);
               }
               field.set(this,value);
            }
         }
      } catch (IllegalAccessException e) {
         e.printStackTrace();
      }
   }

   declare parents: @DistClass * implements Marshallable;

}
