/*=========================================================================
 * Copyright (c) 2010-2014 Pivotal Software, Inc. All Rights Reserved.
 * This product is protected by U.S. and international copyright
 * and intellectual property laws. Pivotal products are covered by
 * one or more patents listed at http://www.pivotal.io/patents.
 *=========================================================================
 */
/**
 * 
 */
package com.gemstone.gemfire.internal.cache.tier.sockets;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;

import com.gemstone.gemfire.DataSerializer;
import com.gemstone.gemfire.internal.DataSerializableFixedID;

/**
 * A variant of the ObjectPartList which deserializes
 * the values as byte arrays of serialized objects,
 * rather than as deserialized objects.
 *
 * [bruce] THIS CLASS IS OBSOLETE AS OF V7.0.  It is replaced with VersionedObjectList
 *  
 */
public class SerializedObjectPartList extends ObjectPartList651 {
  
  public SerializedObjectPartList () {
    super();
  }
  
  public SerializedObjectPartList(int maximumchunksize, boolean hasKeys) {
   super(maximumchunksize,hasKeys);
  }
  
  @Override
  public void toData(DataOutput out) throws IOException {
    out.writeBoolean(this.hasKeys);
    if (this.objectTypeArray != null) {
      int numObjects = this.objects.size();
      out.writeInt(numObjects);
      for (int index = 0; index < numObjects; ++index) {
        Object value = this.objects.get(index);
        byte objectType = this.objectTypeArray[index];
        if (this.hasKeys) {
          DataSerializer.writeObject(this.keys.get(index), out);
        }
        if ((objectType == KEY_NOT_AT_SERVER)) {
          out.writeByte(KEY_NOT_AT_SERVER);
        } else if (objectType == EXCEPTION) {
          out.writeByte(EXCEPTION);
        } else if (objectType==BYTES) {
          out.writeByte(BYTES);
        } else {
          out.writeByte(OBJECT);
        }
        
        if (objectType == EXCEPTION) {
          // write exception as byte array so native clients can skip it
          DataSerializer
              .writeByteArray(CacheServerHelper.serialize(value), out);
          // write the exception string for native clients
          DataSerializer.writeString(value.toString(), out);
        } else if(value instanceof byte[]) {
          DataSerializer.writeByteArray((byte[]) value, out);
        } else {
          DataSerializer.writeObjectAsByteArray(value, out);
        }
      }
    }
    else {
      out.writeInt(0);
    }
  }

  @Override
  public void fromData(DataInput in) throws IOException, ClassNotFoundException {
    boolean keysPresent = in.readBoolean();
    if (keysPresent) {
      this.keys = new ArrayList();
    }
    this.hasKeys = keysPresent;
    int numObjects = in.readInt();
    this.objectTypeArray = new byte[numObjects];
    if (numObjects > 0) {
      for (int index = 0; index < numObjects; ++index) {
        if (keysPresent) {
          Object key = DataSerializer.readObject(in);
          this.keys.add(key);
        }
        byte objectType = in.readByte();
        this.objectTypeArray[index] = objectType;
        Object value;
        if (objectType==EXCEPTION) {
          byte[] exBytes = DataSerializer.readByteArray(in);
          value = CacheServerHelper.deserialize(exBytes);
          // ignore the exception string meant for native clients
          DataSerializer.readString(in);
        }
        else {
          value = DataSerializer.readByteArray(in);
        }
        this.objects.add(value);
      }
    }
  }
  
  public boolean isBytes(int index) {
    return this.objectTypeArray[index] == BYTES;
  }
  
  public boolean isException(int index) {
    return this.objectTypeArray[index] == EXCEPTION;
  }
  
  @Override
  public int getDSFID() {
    return DataSerializableFixedID.SERIALIZED_OBJECT_PART_LIST;
  }
}
