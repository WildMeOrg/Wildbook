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

package org.ecocean.interconnect;

import java.io.File;

class GetImageFile {
  String s = null;
  String ext = null;

  public GetImageFile(String buf) {

    s = buf.replaceAll(".fgp", ".jpg");
    ext = new String(".jpg");
    File f = new File(s);
    if (f.exists())
      return;

    s = buf.replaceAll(".fgp", ".JPG");
    ext = new String(".JPG");
    f = new File(s);
    if (f.exists())
      return;

    s = buf.replaceAll(".fgp", ".gif");
    ext = new String(".gif");
    f = new File(s);
    if (f.exists())
      return;

    s = buf.replaceAll(".fgp", ".GIF");
    ext = new String(".GIF");
    f = new File(s);
    if (f.exists())
      return;

    s = buf.replaceAll(".fgp", ".bmp");
    ext = new String(".bmp");
    f = new File(s);
    if (f.exists())
      return;

    s = buf.replaceAll(".fgp", ".BMP");
    ext = new String(".BMP");
    f = new File(s);
    if (f.exists())
      return;

    s = buf.replaceAll(".fgp", ".png");
    ext = new String(".png");
    f = new File(s);
    if (f.exists())
      return;
    s = buf.replaceAll(".fgp", ".PNG");
    ext = new String(".PNG");
    f = new File(s);
    if (f.exists())
      return;
    s = buf.replaceAll(".fgp", ".tif");
    ext = new String(".tif");
    f = new File(s);
    if (f.exists())
      return;
    s = buf.replaceAll(".fgp", ".TIF");
    ext = new String(".TIF");
    f = new File(s);
    if (f.exists())
      return;
    s = new String("");
    return;
  }

  public String getImageString() {
    return s;
  }

  public String getImageExtension() {
    return ext;
  }
}
