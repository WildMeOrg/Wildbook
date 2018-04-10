/*
 * The Shepherd Project - A Mark-Recapture Framework
 * Copyright (C) 2011 Jason Holmberg
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.ecocean.grid;

//unenhanced comment

//import java.io.Serializable;

/**
 * An <code>encounter</code> object stores the complete data for a single sighting.
 * <code>Encounters</code> are added to MarkedIndividual objects as multiple encounters are associated with
 * known individuals.
 * <p/>
 *
 * @author Jason Holmberg
 * @version 1.2
 */
public class ScanWorkItemResult implements java.io.Serializable {
  static final long serialVersionUID = -146404246317385604L;
  private String uniqueNumWI;
  private String uniqueNumTask;
  private String workItemResultUniqueID;

  private MatchObject result;
  private I3SMatchObject i3sResult;

  /**
   * empty constructor required by JDO Enhancer. DO NOT USE.
   */
  public ScanWorkItemResult() {
  }

  public ScanWorkItemResult(String uniqueNumberOfTask, String uniqueNumberOfWorkItem, MatchObject result) {
    this.result = result;
    this.uniqueNumWI = uniqueNumberOfWorkItem;
    this.uniqueNumTask = uniqueNumberOfTask;
    this.workItemResultUniqueID = uniqueNumberOfWorkItem + "_result";
    //this.i3sResult = i3sResult;
  }

  public MatchObject getResult() {
    return result;
  }

  public I3SMatchObject getI3SResult() {
    return i3sResult;
  }

  /**
   * Returns the unique number for this workItem.
   */
  public String getUniqueNumberWorkItem() {
    return uniqueNumWI;
  }

  public String getUniqueNumberWorkItemResult() {
    return workItemResultUniqueID;
  }

  public String getUniqueNumberTask() {
    return uniqueNumTask;
  }


}