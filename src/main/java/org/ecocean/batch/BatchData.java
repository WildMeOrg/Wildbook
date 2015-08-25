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
package org.ecocean.batch;

import java.util.List;
import java.util.Map;
import org.ecocean.Encounter;
import org.ecocean.MarkedIndividual;
import org.ecocean.Measurement;
import org.ecocean.SinglePhotoVideo;
import org.ecocean.genetics.TissueSample;

/**
 * Data container class to hold parsed batch data from CSV files.
 * Because web requests are stateless, this container is used to hold batch data
 * in the user's session between requests.
 *
 * @author Giles Winstanley
 */
public final class BatchData {
  /** MarkedIndividual instances parsed from batch data CSV files. */
  public final List<MarkedIndividual> listInd;
  /** Encounter instances parsed from batch data CSV files. */
  public final List<Encounter> listEnc;
  /** Measurement instances parsed from batch data CSV files. */
  public List<Measurement> listMea;
  /** BatchMedia instances parsed from batch data CSV files. */
  public List<BatchMedia> listMed;
  /** Map to aid processing of media files. */
  public Map<SinglePhotoVideo, BatchMedia> mapMedia;
  /** TissueSample instances parsed from batch data CSV files. */
  public List<TissueSample> listSam;

  /**
   *
   * @param listInd
   * @param listEnc
   */
  public BatchData(List<MarkedIndividual> listInd, List<Encounter> listEnc) {
    if (listInd == null || listEnc == null)
      throw new NullPointerException();
    this.listInd = listInd;
    this.listEnc = listEnc;
  }

  public void setListMea(List<Measurement> listMea) {
    if (this.listMea != null)
      throw new RuntimeException("Values already assigned");
    if (listMea == null)
      throw new NullPointerException();
    this.listMea = listMea;
  }

  public void setListMed(List<BatchMedia> listMed, Map<SinglePhotoVideo, BatchMedia> mapMedia) {
    if (this.listMed != null || this.mapMedia != null)
      throw new RuntimeException("Values already assigned");
    if (listMed == null || mapMedia == null)
      throw new NullPointerException();
    this.listMed = listMed;
    this.mapMedia = mapMedia;
  }

  public void setListSam(List<TissueSample> listSam) {
    if (this.listSam != null)
      throw new RuntimeException("Values already assigned");
    if (listSam == null)
      throw new NullPointerException();
    this.listSam = listSam;
  }

  public int getUnassignedEncounterCount()
  {
    if (listEnc == null)
      return 0;
    int count = 0;
    for (Encounter enc : listEnc) {
      if (enc.getIndividualID() == null)// || "unassigned".equals(enc.getIndividualID().toLowerCase()))
        count++;
    }
    return count;
  }

  /**
   * @return The count of encounters that are assigned to individuals
   */
  public int getAssignedToExistingIndividualCount()
  {
    if (listEnc == null)
      return 0;
    int count = 0;
    for (Encounter enc : listEnc) {
      String iid = enc.getIndividualID();
      if (iid != null) {
        boolean found = false;
        for (MarkedIndividual ind : listInd) {
          if (iid.equals(ind.getIndividualID())) {
            found = true;
            break;
          }
        }
        if (!found)
          count++;
      }
    }
    return count;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("BatchData[");
    sb.append("listIndSize:").append(listInd.size()).append(", ");
    sb.append("listEncSize:").append(listEnc.size()).append(", ");
    if (listMea != null)
      sb.append("listMeaSize:").append(listMea.size()).append(", ");
    if (listMed != null)
      sb.append("listMedSize:").append(listMed.size()).append(", ");
    if (listSam != null)
      sb.append("listSamSize:").append(listSam.size()).append(", ");
    sb.setLength(sb.length() - 2);
    sb.append("]");
    return sb.toString();
  }
}
