/*=========================================================================
 * Copyright (c) 2002-2014 Pivotal Software, Inc. All Rights Reserved.
 * This product is protected by U.S. and international copyright
 * and intellectual property laws. Pivotal products are covered by
 * more patents listed at http://www.pivotal.io/patents.
 *=========================================================================
 */
   
   
package com.gemstone.gemfire.internal.admin.remote;

import com.gemstone.gemfire.*;
import com.gemstone.gemfire.cache.*;
//import com.gemstone.gemfire.internal.*;
//import com.gemstone.gemfire.internal.admin.*;
import com.gemstone.gemfire.distributed.internal.*;
import java.io.*;
import java.util.*;
import com.gemstone.gemfire.distributed.internal.membership.*;

/**
 * Responds to {@link ObjectDetailsRequest}.
 */
public final class ObjectDetailsResponse extends AdminResponse implements Cancellable {
  // instance variables
  private Object objectValue;
  private Object userAttribute;
  private RemoteCacheStatistics stats;  
  private transient boolean cancelled;
  
  /**
   * Returns a <code>ObjectValueResponse</code> that will be returned to the
   * specified recipient. The message will contains a copy of the local manager's
   * system config.
   */
  public static ObjectDetailsResponse create(DistributionManager dm, InternalDistributedMember recipient) {
    ObjectDetailsResponse m = new ObjectDetailsResponse();
    m.setRecipient(recipient);
    return m;
  }
  
  void buildDetails(Region r, Object objName, int inspectionType) {
    try {
      objName = getObjectName(r, objName);
      if (cancelled) return;
      if (r.containsKey(objName)) {
        if (cancelled) return;
        // @todo darrel: race condition; could be unloaded between isPresent and get call.
        Region.Entry e = r.getEntry(objName);
        Object v = e.getValue();
        if (cancelled) return;
        objectValue = CacheDisplay.getCachedObjectDisplay(v, inspectionType);
        if (cancelled) return;
        userAttribute = CacheDisplay.getCachedObjectDisplay(e.getUserAttribute(),
                                                              inspectionType);
        if (cancelled) return;
        try {
          stats = new RemoteCacheStatistics(e.getStatistics());
        } catch (StatisticsDisabledException ignore) {}
      }
    } catch (CacheException ex) {
      throw new GemFireCacheException(ex);
    }
  }

  public synchronized void cancel() {
    cancelled = true;
  }  

  // instance methods
  public Object getObjectValue() {
    return this.objectValue;
  }

  public Object getUserAttribute() {
    return this.userAttribute;
  }

  public CacheStatistics getStatistics() {
    return this.stats;
  }

  public int getDSFID() {
    return OBJECT_DETAILS_RESPONSE;
  }

  @Override
  public void toData(DataOutput out) throws IOException {
    super.toData(out);
    DataSerializer.writeObject(this.objectValue, out);
    DataSerializer.writeObject(this.userAttribute, out);
    DataSerializer.writeObject(this.stats, out);
  }

  @Override
  public void fromData(DataInput in)
    throws IOException, ClassNotFoundException {
    super.fromData(in);
    this.objectValue = DataSerializer.readObject(in);
    this.userAttribute = DataSerializer.readObject(in);
    this.stats = (RemoteCacheStatistics)DataSerializer.readObject(in);
  }

  @Override
  public String toString() {
    return "ObjectDetailsResponse from " + this.getRecipient();
  }


  // Holds the last result of getObjectName to optimize the next call
  static private Object lastObjectNameFound = null;
  
  static Object getObjectName(Region r, Object objName) throws CacheException {
    if (objName instanceof RemoteObjectName) {
      synchronized(ObjectDetailsResponse.class) {
        if (objName.equals(lastObjectNameFound)) {
          return lastObjectNameFound;
        }
      }
      Object obj = null;
      Set keys = r.keys();
      synchronized(r) {
        Iterator it = keys.iterator();
        while (it.hasNext()) {
          Object o = it.next();
          if (objName.equals(o)) {
            synchronized(ObjectDetailsResponse.class) {
              lastObjectNameFound = o;
            }
            obj = o;
            break;
          }
        }
      } 
      if (obj != null) {
        return obj;
      }
      // Didn't find it so just return the input RemoteObjectName instance.
      // This should fail on the lookup and give a reasonable error.
      synchronized(ObjectDetailsResponse.class) {
        lastObjectNameFound = objName;
      }
    }
    return objName;
  }
}
